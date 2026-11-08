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

import java.net.URI
import java.net.URISyntaxException

/**
 * The endpoint of the cluster bus, parsed once from the configured URL. The password is never part of the string form,
 * and [redacted] is the only form that may reach a log or a status output.
 */
data class ClusterBusUrl(
    val scheme: String,
    val host: String,
    val port: Int?,
    val username: String?,
    val password: String?,
) {

    /** Whether the scheme encrypts the connection, so that credentials may travel over it. */
    val encrypted: Boolean
        get() = scheme in ENCRYPTED_SCHEMES

    /** The URL with its credentials replaced by a placeholder, safe to log. */
    fun redacted(): String {
        val userInfo = if (username != null || password != null) "$REDACTED@" else ""
        val endpoint = if (port != null) "$host:$port" else host
        return "$scheme://$userInfo$endpoint"
    }

    override fun toString(): String =
        "ClusterBusUrl(${redacted()})"

    companion object {

        const val REDACTED = "REDACTED"

        /** The database transport. It has no endpoint, so its URL is `database:` or `database://` and nothing else. */
        const val DATABASE_SCHEME = "database"

        val ACCEPTED_SCHEMES: Set<String> = linkedSetOf("nats", "tls", "redis", "rediss", "valkey", "valkeys", DATABASE_SCHEME)

        private val ENCRYPTED_SCHEMES = setOf("tls", "rediss", "valkeys")

        /**
         * Parses a bus URL. Refusals never echo the URL, because it may carry credentials.
         *
         * @throws ClusterConfigurationException when the URL cannot be parsed or its scheme is not accepted
         */
        fun parse(raw: String): ClusterBusUrl {
            if (raw.trim().lowercase().startsWith("$DATABASE_SCHEME:")) {
                return databaseUrl(raw.trim())
            }

            val uri = try {
                URI(raw)
            } catch (exception: URISyntaxException) {
                throw ClusterConfigurationException("Cluster bus URL cannot be parsed")
            }

            val scheme = uri.scheme?.lowercase()
            if (scheme == null || scheme !in ACCEPTED_SCHEMES) {
                throw ClusterConfigurationException(
                    "Cluster bus URL uses an unsupported scheme, the accepted schemes are ${ACCEPTED_SCHEMES.joinToString(", ")}"
                )
            }

            val host = uri.host ?: throw ClusterConfigurationException("Cluster bus URL has no host")
            val userInfo = uri.userInfo
            return ClusterBusUrl(
                scheme = scheme,
                host = host,
                port = uri.port.takeIf { it > 0 },
                username = userInfo?.substringBefore(':'),
                password = userInfo?.takeIf { it.contains(':') }?.substringAfter(':'),
            )
        }

        /**
         * The database scheme takes no endpoint. A URL that carries one keeps a placeholder host, so the transport can refuse
         * it without the host, the credentials or the raw text reaching a log. The refusal itself is the transport's decision.
         */
        private fun databaseUrl(raw: String): ClusterBusUrl {
            val rest = raw.substringAfter(':').trim()
            val bare = rest.isEmpty() || rest == "//"
            return ClusterBusUrl(
                scheme = DATABASE_SCHEME,
                host = if (bare) "" else REDACTED,
                port = null,
                username = null,
                password = null,
            )
        }
    }
}

/** A configuration value that stops the startup. The message never contains a credential. */
class ClusterConfigurationException(message: String) : IllegalArgumentException(message)
