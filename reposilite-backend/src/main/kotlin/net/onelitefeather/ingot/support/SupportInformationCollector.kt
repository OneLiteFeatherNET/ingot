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

package net.onelitefeather.ingot.support

import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER

/**
 * One assembled view of the instance: the system information, every file of the bundle, the number of redacted values and the
 * guard that checks the output. The page reads [systemInfo] and the bundle reads [files].
 */
data class SupportSnapshot(
    val systemInfo: SystemInformation,
    val files: Map<String, ByteArray>,
    val redactions: Int,
    val guard: RedactionGuard,
)

/**
 * Builds the system information and the bundle files from [SupportSources]. Every value passes through [Redaction], and the
 * cached log lines through [LogScrubber]. Nothing is read from the disk here.
 */
class SupportInformationCollector(private val sources: SupportSources) {

    fun collect(): SupportSnapshot {
        val tally = RedactionTally()
        val localValues = sources.localValues()
        val environment = sources.environment()
        val systemProperties = sources.systemProperties()
        val rawJvmArguments = sources.jvmArguments()
        val domains = sources.sharedDomains()
        val bus = sources.busUrl()

        val local = Redaction.local(localValues, tally)
        val busUrl = Redaction.busUrl(bus, tally)
        val environmentRedacted = Redaction.environment(environment, tally)
        val propertiesRedacted = Redaction.systemProperties(systemProperties, tally)
        val jvmArguments = Redaction.jvmArguments(rawJvmArguments, tally)
        val shared = Redaction.shared(domains, tally)

        // The guard searches for the known secrets, the log scrub also for the withheld environment and property values
        val knownSecrets = Redaction.knownSecrets(domains, localValues, bus)
        val scrubber = LogScrubber(knownSecrets + Redaction.withheldValues(environment, systemProperties, rawJvmArguments))
        val logLines = sources.cachedLog().map { scrubber.scrub(it, tally) }

        val generatedAt = sources.clock.instant().toString()
        val build = sources.buildInformation()
        val jvm = sources.jvm()
        val operatingSystem = sources.operatingSystem()
        val database = DatabaseDescription.describe(localValues[Redaction.LOCAL_DATABASE])

        val systemInfo = SystemInformation(
            instance = sources.instance,
            generatedAt = generatedAt,
            redactionRuleVersion = Redaction.RULE_VERSION,
            ingot = IngotInformation(
                version = sources.version,
                buildTime = build["buildTime"] ?: UNKNOWN,
                commit = build["commit"] ?: UNKNOWN,
            ),
            jvm = JvmInformation(
                vendor = jvm.vendor,
                version = jvm.version,
                uptimeMillis = jvm.uptimeMillis,
                heapUsedMb = jvm.heapUsedMb,
                heapMaxMb = jvm.heapMaxMb,
                processors = jvm.processors,
                threads = jvm.threads,
                inputArguments = jvmArguments,
            ),
            operatingSystem = OperatingSystemInformation(
                name = operatingSystem.name,
                version = operatingSystem.version,
                architecture = operatingSystem.architecture,
                container = operatingSystem.container,
            ),
            database = DatabaseInformation(kind = database.kind, endpoint = database.endpoint),
            storage = sources.repositoryStorage().map { StorageInformation(it.repository, it.kind, it.region, sources.repositoryDetails()[it.repository].orEmpty()) },
            cluster = sources.cluster(),
            plugins = sources.plugins().map { PluginInformation(it.name, it.version, it.dependencies) },
            environment = environmentRedacted,
            systemProperties = propertiesRedacted,
            configuration = ConfigurationInformation(local = local, shared = shared),
        )

        val files = linkedMapOf<String, ByteArray>()
        files[SYSTEM_INFO] = json(systemInfo)
        files[CONFIGURATION_LOCAL] = json(local)
        files[CONFIGURATION_SHARED] = json(shared)
        files[CLUSTER] = json(sources.cluster() ?: mapOf("enabled" to false))
        files[PLUGINS] = json(systemInfo.plugins)
        files[logFile(sources.instance)] = (logLines.joinToString("\n") + "\n").toByteArray(Charsets.UTF_8)

        val manifest = BundleManifest(
            format = FORMAT,
            redactionRuleVersion = Redaction.RULE_VERSION,
            instance = sources.instance,
            generatedAt = generatedAt,
            redactions = tally.count,
            files = files.map { (path, content) -> ManifestEntry(path, content.size) },
        )
        files[MANIFEST] = json(manifest)

        return SupportSnapshot(
            systemInfo = systemInfo,
            files = files,
            redactions = tally.count,
            guard = RedactionGuard(knownSecrets),
        )
    }

    companion object {
        const val UNKNOWN = "unknown"
        const val FORMAT = "ingot-support-bundle"
        const val MANIFEST = "manifest.json"
        const val SYSTEM_INFO = "system-info.json"
        const val CONFIGURATION_LOCAL = "configuration-local.json"
        const val CONFIGURATION_SHARED = "configuration-shared.json"
        const val CLUSTER = "cluster.json"
        const val PLUGINS = "plugins.json"

        /** The path of the cached log of [instance] inside the bundle. */
        fun logFile(instance: String): String =
            "logs/${instance.replace(Regex("[^A-Za-z0-9._-]"), "_")}.log"

        private fun json(value: Any): ByteArray =
            DEFAULT_OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(value)
    }
}
