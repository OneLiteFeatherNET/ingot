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

import com.reposilite.configuration.shared.SettingsSecretReferences
import com.reposilite.plugin.api.Facade
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.ReposilitePlugin
import com.reposilite.plugin.event
import com.reposilite.web.api.RoutingSetupEvent
import net.onelitefeather.ingot.nexusimport.nexus.JdkNexusTransport
import java.time.Clock

/**
 * The Nexus import, bundled in the image and loaded only when `INGOT_NEXUS_IMPORT_ENABLED` is true (or the
 * `REPOSILITE_` fallback). Invalid settings fail the start, and the message names the variable.
 */
@Plugin(name = "nexus-import", dependencies = ["failure", "shared-configuration", "maven", "audit"])
class NexusImportPlugin : ReposilitePlugin() {

    override fun initialize(): Facade? {
        val settings = NexusImportSettings.read(System.getenv())
        val service = NexusConnectionService(
            settings = settings,
            transport = JdkNexusTransport(),
            clock = Clock.systemUTC(),
            sleeper = { duration -> Thread.sleep(duration.toMillis()) },
            secretReferences = SettingsSecretReferences.read(System.getenv()),
        )

        event { event: RoutingSetupEvent ->
            event.registerRoutes(NexusImportEndpoints(service))
        }

        logger.info("NEXUS IMPORT | Enabled with ${settings.concurrency} files in flight and ${settings.maxRequestsPerSecond} requests per second")
        return null
    }
}
