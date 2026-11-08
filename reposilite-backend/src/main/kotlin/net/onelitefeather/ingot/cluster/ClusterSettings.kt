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

package net.onelitefeather.ingot.cluster

import java.net.InetAddress
import java.nio.file.Path

/**
 * Where the cluster settings come from: system properties, environment variables and the host name. Injected so that
 * tests never read the process environment.
 */
data class ClusterSources(
    val property: (String) -> String?,
    val variable: (String) -> String?,
    val hostName: () -> String,
) {

    companion object {

        fun system(): ClusterSources =
            ClusterSources(
                property = { System.getProperty(it) },
                variable = { System.getenv(it) },
                hostName = { systemHostName() },
            )

        private fun systemHostName(): String =
            System.getenv("HOSTNAME")?.takeIf { it.isNotBlank() }
                ?: runCatching { InetAddress.getLocalHost().hostName }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: "ingot"
    }
}

/**
 * The cluster bus settings of this instance. Without a [url] the bus is off and nothing else here is used.
 *
 * The bus URL comes from the system property `ingot.cluster.url`, then `INGOT_CLUSTER_URL`, then the legacy
 * `REPOSILITE_CLUSTER_URL`. A password the URL does not carry comes from `INGOT_CLUSTER_PASSWORD`, then
 * `REPOSILITE_CLUSTER_PASSWORD`. The NATS credentials file comes from `ingot.cluster.credentials`, then
 * `INGOT_CLUSTER_CREDENTIALS`. Only the path is read here; its contents are never read by this class.
 */
data class ClusterSettings(
    val url: ClusterBusUrl?,
    val credentialsFile: Path?,
    val instanceId: String,
    val console: Boolean,
    val failures: Boolean,
    val warnings: List<String>,
    /** The poll interval of the database transport, in milliseconds. Other transports do not poll. */
    val pollIntervalMillis: Long = DEFAULT_POLL_INTERVAL_MILLIS,
) {

    companion object {

        const val DEFAULT_POLL_INTERVAL_MILLIS = 1000L
        const val MINIMUM_POLL_INTERVAL_MILLIS = 200L

        private val REDIS_FAMILY = setOf("redis", "rediss", "valkey", "valkeys")
        private val NATS_FAMILY = setOf("nats", "tls")

        /**
         * The default of `INGOT_CLUSTER_CONSOLE` for a bus scheme. Console lines are on by default, except on the database
         * transport, where every log line would become a row of `cluster_event`.
         */
        internal fun consoleFanOutDefault(scheme: String?): Boolean =
            scheme != ClusterBusUrl.DATABASE_SCHEME

        /**
         * @throws ClusterConfigurationException when a value is malformed, which stops the startup
         */
        fun read(sources: ClusterSources): ClusterSettings {
            val url = firstDefined(
                { sources.property("ingot.cluster.url") },
                { sources.variable("INGOT_CLUSTER_URL") },
                { sources.variable("REPOSILITE_CLUSTER_URL") },
            )?.let { ClusterBusUrl.parse(it) }

            val variablePassword = firstDefined(
                { sources.variable("INGOT_CLUSTER_PASSWORD") },
                { sources.variable("REPOSILITE_CLUSTER_PASSWORD") },
            )
            val resolvedUrl = url?.let { if (it.password == null && variablePassword != null) it.copy(password = variablePassword) else it }

            val warnings = mutableListOf<String>()
            val credentials = firstDefined(
                { sources.property("ingot.cluster.credentials") },
                { sources.variable("INGOT_CLUSTER_CREDENTIALS") },
            )
            val credentialsFile = when {
                resolvedUrl == null -> null
                resolvedUrl.scheme in NATS_FAMILY -> credentials?.let { Path.of(it) }
                credentials != null && resolvedUrl.scheme in REDIS_FAMILY -> {
                    warnings += "The cluster credentials file is ignored for ${resolvedUrl.scheme}, which uses no credentials file"
                    null
                }
                else -> null
            }

            if (resolvedUrl != null && (resolvedUrl.username != null || resolvedUrl.password != null) && !resolvedUrl.encrypted) {
                warnings += "Cluster bus credentials are sent over the unencrypted scheme ${resolvedUrl.scheme}, use tls, rediss or valkeys to encrypt them"
            }

            val onDatabase = resolvedUrl?.scheme == ClusterBusUrl.DATABASE_SCHEME
            val pollInterval = if (onDatabase) pollIntervalOf(sources.variable("INGOT_CLUSTER_POLL_INTERVAL_MS"), warnings) else DEFAULT_POLL_INTERVAL_MILLIS

            return ClusterSettings(
                url = resolvedUrl,
                credentialsFile = credentialsFile,
                instanceId = firstDefined({ sources.variable("INGOT_CLUSTER_INSTANCE_ID") }) ?: sources.hostName(),
                console = booleanOf(
                    "INGOT_CLUSTER_CONSOLE",
                    sources.variable("INGOT_CLUSTER_CONSOLE"),
                    default = consoleFanOutDefault(resolvedUrl?.scheme),
                ),
                failures = booleanOf("INGOT_CLUSTER_FAILURES", sources.variable("INGOT_CLUSTER_FAILURES"), default = false),
                warnings = warnings,
                pollIntervalMillis = pollInterval,
            )
        }

        /** The configured interval, raised to the minimum with one warning that names the effective interval. */
        private fun pollIntervalOf(value: String?, warnings: MutableList<String>): Long {
            val configured = value?.trim()?.takeIf { it.isNotEmpty() } ?: return DEFAULT_POLL_INTERVAL_MILLIS
            val millis = configured.toLongOrNull()
                ?: throw ClusterConfigurationException("INGOT_CLUSTER_POLL_INTERVAL_MS must be a whole number of milliseconds")
            if (millis < MINIMUM_POLL_INTERVAL_MILLIS) {
                warnings += "INGOT_CLUSTER_POLL_INTERVAL_MS is $millis ms, below the minimum, polling every $MINIMUM_POLL_INTERVAL_MILLIS ms"
                return MINIMUM_POLL_INTERVAL_MILLIS
            }
            return millis
        }

        /** The first lookup that defines a non-blank value. */
        private fun firstDefined(vararg lookups: () -> String?): String? =
            lookups.asSequence().mapNotNull { it()?.takeIf { value -> value.isNotBlank() } }.firstOrNull()

        private fun booleanOf(name: String, value: String?, default: Boolean): Boolean =
            when (value?.trim()?.lowercase()) {
                null, "" -> default
                "true", "1", "on", "yes" -> true
                "false", "0", "off", "no" -> false
                else -> throw ClusterConfigurationException("$name must be true or false")
            }
    }
}
