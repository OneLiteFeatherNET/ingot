/*
 * Copyright (c) 2026 OneLiteFeather
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.onelitefeather.ingot.nexusimport

import com.reposilite.audit.AuditFacade
import com.reposilite.configuration.shared.SecretReferenceResolver
import com.reposilite.configuration.shared.SettingsSecretReferences
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.maven.MavenFacade
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.ReposiliteDisposeEvent
import com.reposilite.plugin.api.ReposilitePlugin
import com.reposilite.plugin.event
import com.reposilite.plugin.facade
import com.reposilite.plugin.reposilite
import com.reposilite.web.api.RoutingSetupEvent
import net.onelitefeather.ingot.nexusimport.jobs.JobScheduler
import net.onelitefeather.ingot.nexusimport.jobs.JobStore
import net.onelitefeather.ingot.nexusimport.jobs.NexusImportService
import net.onelitefeather.ingot.nexusimport.nexus.JdkNexusTransport
import net.onelitefeather.ingot.readonly.ReadOnlyFacade
import java.nio.file.Files
import java.time.Clock
import java.time.Duration

/**
 * The Nexus import, bundled in the image and loaded only when `INGOT_NEXUS_IMPORT_ENABLED` is true (or the `REPOSILITE_`
 * fallback). Invalid settings fail the start, and the message names the variable. The job tables are created here, so an
 * instance without the plugin has none of them.
 */
@Plugin(name = "nexus-import", dependencies = ["failure", "shared-configuration", "maven", "audit", "read-only"])
class NexusImportPlugin : ReposilitePlugin() {

    override fun initialize(): NexusImportFacade {
        val environment = System.getenv()
        val settings = NexusImportSettings.read(environment)
        val clock = Clock.systemUTC()
        val instance = reposilite()
        val readOnly = facade<ReadOnlyFacade>()
        val secretReferences: SecretReferenceResolver? = SettingsSecretReferences.read(environment)
        val sleeper: (Duration) -> Unit = { duration -> Thread.sleep(duration.toMillis()) }

        val store = JobStore(instance.database, clock)
        val tempDirectory = Files.createDirectories(instance.parameters.workingDirectory.resolve("nexus-import"))
        val service = NexusImportService(
            settings = settings,
            store = store,
            clock = clock,
            transport = JdkNexusTransport(),
            sleeper = sleeper,
            ports = IngotImportPorts(facade<MavenFacade>(), facade<SharedConfigurationFacade>(), facade<AuditFacade>()),
            readOnly = { readOnly.isReadOnly() },
            resolveReference = secretReferences?.let { resolver -> { reference: String -> resolver.resolve(reference) } },
            tempDir = tempDirectory,
        )
        val scheduler = JobScheduler(service, store, clock, readOnly = { readOnly.isReadOnly() }) { failure ->
            logger.error("NEXUS IMPORT | A scheduler tick failed: ${failure.javaClass.simpleName}")
        }
        val interval = tickInterval()
        if (!interval.isZero) {
            scheduler.start(interval)
        }

        val connections = NexusConnectionService(settings, JdkNexusTransport(), clock, sleeper, secretReferences)
        event { event: RoutingSetupEvent -> event.registerRoutes(NexusImportEndpoints(connections, service)) }
        event { _: ReposiliteDisposeEvent -> scheduler.close() }

        logger.info("NEXUS IMPORT | Enabled with ${settings.concurrency} files in flight and ${settings.maxRequestsPerSecond} requests per second")
        return NexusImportFacade(scheduler)
    }

    /**
     * The period of the background tick. The default is five seconds. The property `ingot.nexusImport.tickSeconds` is a test
     * seam: `0` turns the background ticker off, so the integration tests decide when a job runs. It is not an operator setting.
     */
    private fun tickInterval(): Duration =
        System.getProperty(TICK_PROPERTY)?.trim()?.toLongOrNull()?.let(Duration::ofSeconds) ?: DEFAULT_TICK

    private companion object {
        const val TICK_PROPERTY = "ingot.nexusImport.tickSeconds"
        val DEFAULT_TICK: Duration = Duration.ofSeconds(5)
    }
}
