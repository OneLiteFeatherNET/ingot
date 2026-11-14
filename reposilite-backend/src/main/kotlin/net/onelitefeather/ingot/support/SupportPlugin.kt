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

@file:OptIn(net.onelitefeather.ingot.storage.ExperimentalIngotStorageApi::class)

package net.onelitefeather.ingot.support

import com.fasterxml.jackson.databind.JsonNode
import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.VERSION
import com.reposilite.configuration.local.LocalConfiguration
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.configuration.shared.api.SharedSettings
import com.reposilite.maven.application.MavenSettings
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.ReposilitePlugin
import com.reposilite.plugin.event
import com.reposilite.plugin.facade
import com.reposilite.plugin.reposilite
import com.reposilite.status.StatusFacade
import com.reposilite.storage.s3.S3StorageProviderSettings
import net.onelitefeather.ingot.storage.capability.SupportDetails
import com.reposilite.web.api.RoutingSetupEvent
import net.onelitefeather.ingot.cluster.ClusterSettings
import net.onelitefeather.ingot.cluster.ClusterSources
import panda.std.reactive.Reference
import java.io.File
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.util.jar.Attributes
import java.util.jar.JarFile
import kotlin.reflect.full.memberProperties

/**
 * Serves the system information page and the support bundle. It reads the instance through facades that already exist and
 * registers its routes on the routing event. Nothing is written here, and nothing leaves the instance.
 */
@Plugin(name = "support", dependencies = ["local-configuration", "shared-configuration", "status", "audit"])
internal class SupportPlugin : ReposilitePlugin() {

    private companion object {
        val CONTAINER_MARKERS = listOf("docker", "kubepods", "containerd", "libpod")
        const val BUILD_TIME = "Build-Time"
        const val GIT_COMMIT = "Git-Commit"
        const val MEGABYTE = 1024.0 * 1024
    }

    override fun initialize(): SupportBundleService {
        val reposilite = reposilite()
        val setting = reposilite.supportBundleSetting
        val localConfiguration = facade<LocalConfiguration>()
        val shared = facade<SharedConfigurationFacade>()
        val status = facade<StatusFacade>()
        val cluster = runCatching { ClusterSettings.read(ClusterSources.system()) }.getOrNull()

        val sources = SupportSources(
            instance = cluster?.instanceId ?: ClusterSources.system().hostName(),
            clock = Clock.systemUTC(),
            version = VERSION,
            buildInformation = { buildInformation() },
            jvm = { jvmFacts() },
            operatingSystem = { operatingSystemFacts() },
            environment = { System.getenv() },
            systemProperties = { systemProperties() },
            jvmArguments = { ManagementFactory.getRuntimeMXBean().inputArguments },
            localValues = { localValues(localConfiguration) },
            sharedDomains = { sharedDomains(shared) },
            repositoryStorage = { repositoryStorage(shared) },
            repositoryDetails = { repositoryDetails(shared) },
            plugins = {
                reposilite.extensions.getPlugins().values.map { entry ->
                    PluginFact(entry.metadata.name, entry.metadata.version, entry.metadata.dependencies.toList())
                }
            },
            cluster = { status.fetchClusterStatus() },
            busUrl = { cluster?.url },
            cachedLog = { reposilite.journalist.cachedLogger.messages.map { it.value } },
        )

        val service = SupportBundleService(
            collector = SupportInformationCollector(sources),
            clock = Clock.systemUTC(),
            audit = { event -> reposilite.extensions.emitEvent(event) },
        )

        event { event: RoutingSetupEvent ->
            event.registerRoutes(SupportEndpoints(service, setting))
        }

        return service
    }

    private fun buildInformation(): Map<String, String> {
        val attributes: Attributes = runCatching {
            val location = SupportPlugin::class.java.protectionDomain.codeSource.location
            JarFile(File(location.toURI())).use { jar -> jar.manifest?.mainAttributes }
        }.getOrNull() ?: return emptyMap()

        return listOfNotNull(
            attributes.getValue(BUILD_TIME)?.let { "buildTime" to it },
            attributes.getValue(GIT_COMMIT)?.let { "commit" to it },
        ).toMap()
    }

    private fun jvmFacts(): JvmFacts {
        val runtime = Runtime.getRuntime()
        return JvmFacts(
            vendor = System.getProperty("java.vendor") ?: "unknown",
            version = System.getProperty("java.version") ?: "unknown",
            uptimeMillis = ManagementFactory.getRuntimeMXBean().uptime,
            heapUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / MEGABYTE,
            heapMaxMb = (runtime.maxMemory() / MEGABYTE).toInt(),
            processors = runtime.availableProcessors(),
            threads = Thread.activeCount(),
        )
    }

    private fun operatingSystemFacts(): OperatingSystemFacts =
        OperatingSystemFacts(
            name = System.getProperty("os.name") ?: "unknown",
            version = System.getProperty("os.version") ?: "unknown",
            architecture = System.getProperty("os.arch") ?: "unknown",
            container = isContainer(),
        )

    private fun isContainer(): Boolean =
        Files.exists(Path.of("/.dockerenv")) ||
            runCatching { Files.readString(Path.of("/proc/1/cgroup")) }.getOrNull()
                ?.let { cgroup -> CONTAINER_MARKERS.any { cgroup.contains(it) } } == true

    private fun systemProperties(): Map<String, String> =
        System.getProperties().stringPropertyNames().associateWith { System.getProperty(it).orEmpty() }

    private fun localValues(local: LocalConfiguration): Map<String, String?> =
        LocalConfiguration::class.memberProperties.associate { property ->
            val reference = property.getter.call(local) as? Reference<*>
            property.name to reference?.get()?.toString()
        }

    @Suppress("UNCHECKED_CAST")
    private fun sharedDomains(shared: SharedConfigurationFacade): List<SharedDomainDocument> =
        shared.getDomainNames().map { name ->
            val reference = shared.getSettingsReference<SharedSettings>(name)!!
            val type = reference.type as Class<SharedSettings>
            // The resolved view: a reference is replaced by its value, so the known secrets include the values behind references
            val document: JsonNode = DEFAULT_OBJECT_MAPPER.valueToTree(shared.getDomainSettings(type).get())
            SharedDomainDocument(name, type, document)
        }

    private fun repositoryDetails(shared: SharedConfigurationFacade): Map<String, Map<String, String>> =
        shared.getDomainSettings<MavenSettings>().get().repositories.associate { repository ->
            repository.id to ((repository.storageProvider as? SupportDetails)?.describe().orEmpty())
        }

    private fun repositoryStorage(shared: SharedConfigurationFacade): List<RepositoryStorage> =
        shared.getDomainSettings<MavenSettings>().get().repositories.map { repository ->
            val provider = repository.storageProvider
            RepositoryStorage(
                repository = repository.id,
                kind = provider.type,
                region = (provider as? S3StorageProviderSettings)?.region?.takeIf { it.isNotBlank() },
            )
        }

}
