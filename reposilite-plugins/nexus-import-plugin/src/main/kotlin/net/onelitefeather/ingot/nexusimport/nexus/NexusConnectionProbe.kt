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

package net.onelitefeather.ingot.nexusimport.nexus

/**
 * What the connection test found. The report holds no credential and no value a credential could be derived from; the
 * problem texts name the answer Nexus gave, never the request that produced it.
 */
data class ConnectionReport(
    val reachable: Boolean,
    val statusCode: Int?,
    val version: String?,
    val edition: String?,
    /** Null when the credential was never tried, false when Nexus refused it, true when Nexus identified it. */
    val credentialAccepted: Boolean?,
    val canListRepositories: Boolean?,
    val canReadSettings: Boolean?,
    val warnings: List<String>,
    val problem: String?,
)

/**
 * Probes a Nexus in the order the operator needs to know it: reachability, the version from the `Server` header, whether the
 * credential is accepted, whether the repositories can be listed and whether the Maven settings can be read. A `403` on
 * settings is a result, so the probe stays successful and reports the area as unreadable.
 */
class NexusConnectionProbe(
    private val client: NexusClient,
    private val connection: NexusConnection,
) {

    fun run(): ConnectionReport {
        val warnings = if (connection.baseUri.scheme.equals("http", ignoreCase = true)) {
            listOf(NexusUrlPolicy.PLAIN_HTTP_WARNING)
        } else {
            emptyList()
        }

        val status = try {
            client.status().also { it.body.close() }
        } catch (exception: NexusHttpException) {
            return unreachable(exception.status, warnings, "Nexus answered HTTP ${exception.status} on the status endpoint")
        } catch (exception: NexusUnavailableException) {
            return unreachable(null, warnings, exception.message ?: "Nexus did not answer")
        }
        val (version, edition) = parseServer(status.headers["server"])

        val repositories = try {
            client.repositories()
        } catch (exception: NexusHttpException) {
            return when (exception.status) {
                401 -> report(status.status, version, edition, false, null, null, warnings, CREDENTIAL_REFUSED)
                403 -> report(status.status, version, edition, true, false, null, warnings, "The credential may not list repositories")
                else -> report(status.status, version, edition, true, null, null, warnings, "Nexus answered HTTP ${exception.status} on the repository list")
            }
        } catch (exception: NexusUnavailableException) {
            return report(status.status, version, edition, null, null, null, warnings, exception.message ?: "Nexus did not answer")
        }

        val probeSettings = repositories.firstOrNull { it.format == "maven2" && it.type == "hosted" }
        val canReadSettings: Boolean? = probeSettings?.let { repository ->
            try {
                client.mavenSettings("hosted", repository.name)
                true
            } catch (exception: NexusHttpException) {
                if (exception.status == 401) return report(status.status, version, edition, false, true, null, warnings, CREDENTIAL_REFUSED)
                false
            } catch (exception: NexusUnavailableException) {
                null
            }
        }
        return report(status.status, version, edition, true, true, canReadSettings, warnings, null)
    }

    private fun unreachable(status: Int?, warnings: List<String>, problem: String) =
        report(status, null, null, null, null, null, warnings, problem, reachable = false)

    private fun report(
        status: Int?,
        version: String?,
        edition: String?,
        credentialAccepted: Boolean?,
        canListRepositories: Boolean?,
        canReadSettings: Boolean?,
        warnings: List<String>,
        problem: String?,
        reachable: Boolean = true,
    ) = ConnectionReport(
        reachable = reachable,
        statusCode = status,
        version = version,
        edition = edition,
        credentialAccepted = credentialAccepted,
        canListRepositories = canListRepositories,
        canReadSettings = canReadSettings,
        warnings = warnings,
        problem = problem,
    )

    private fun parseServer(header: String?): Pair<String?, String?> {
        val match = header?.let { SERVER_HEADER.find(it) } ?: return null to null
        return match.groupValues[1] to match.groupValues[2]
    }

    companion object {
        const val CREDENTIAL_REFUSED = "The credential was refused by Nexus (HTTP 401)"
        private val SERVER_HEADER = Regex("""Nexus/(\S+)\s*\((\w+)\)""")
    }
}
