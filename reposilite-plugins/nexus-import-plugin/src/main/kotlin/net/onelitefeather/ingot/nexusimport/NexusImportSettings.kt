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

/** How the audit trail of a copy is written. `PER_FILE` keeps one deploy event per file, `SUMMARY` only the run event. */
enum class AuditMode { PER_FILE, SUMMARY }

/**
 * The `INGOT_NEXUS_IMPORT_*` settings. Each one falls back to its `REPOSILITE_NEXUS_IMPORT_*` spelling, and the `INGOT_`
 * spelling wins when it is set. An invalid value fails the plugin start and the message names the variable that was read.
 * There is deliberately no setting that turns off certificate verification.
 */
data class NexusImportSettings(
    val concurrency: Int,
    val maxRequestsPerSecond: Int,
    val audit: AuditMode,
    /** Host names a connection may point at, lower case. Null means any host. */
    val allowedHosts: Set<String>?,
) {

    companion object {
        const val PREFIX = "NEXUS_IMPORT_"
        const val DEFAULT_CONCURRENCY = 4
        const val DEFAULT_REQUESTS_PER_SECOND = 5

        fun read(environment: Map<String, String>): NexusImportSettings =
            NexusImportSettings(
                concurrency = integer(environment, "CONCURRENCY", DEFAULT_CONCURRENCY, 1..16),
                maxRequestsPerSecond = integer(environment, "MAX_REQUESTS_PER_SECOND", DEFAULT_REQUESTS_PER_SECOND, 1..100),
                audit = audit(environment),
                allowedHosts = hosts(environment),
            )

        private fun lookup(environment: Map<String, String>, name: String): Pair<String, String>? {
            environment["INGOT_$PREFIX$name"]?.let { return "INGOT_$PREFIX$name" to it.trim() }
            environment["REPOSILITE_$PREFIX$name"]?.let { return "REPOSILITE_$PREFIX$name" to it.trim() }
            return null
        }

        private fun integer(environment: Map<String, String>, name: String, default: Int, range: IntRange): Int {
            val (variable, raw) = lookup(environment, name) ?: return default
            val value = raw.toIntOrNull()
            require(value != null && value in range) {
                "$variable must be a whole number from ${range.first} to ${range.last}, got '$raw'"
            }
            return value
        }

        private fun audit(environment: Map<String, String>): AuditMode {
            val (variable, raw) = lookup(environment, "AUDIT") ?: return AuditMode.PER_FILE
            return when (raw.lowercase()) {
                "per-file" -> AuditMode.PER_FILE
                "summary" -> AuditMode.SUMMARY
                else -> throw IllegalArgumentException("$variable must be per-file or summary, got '$raw'")
            }
        }

        private fun hosts(environment: Map<String, String>): Set<String>? {
            val (_, raw) = lookup(environment, "ALLOWED_HOSTS") ?: return null
            return raw.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet().ifEmpty { null }
        }
    }
}
