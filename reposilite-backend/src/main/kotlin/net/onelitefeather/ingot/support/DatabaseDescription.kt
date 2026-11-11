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

/**
 * The database setting reduced to what a support bundle may show: the kind, and for a networked kind the host and port.
 * [secrets] are the parts that were dropped and must never appear anywhere else in the bundle. [redacted] tells whether any
 * part was dropped at all, so that the redaction count is exact.
 */
data class DatabaseDescription(
    val kind: String,
    val endpoint: String?,
    val secrets: List<String>,
    val redacted: Boolean,
) {

    /** The text the bundle writes: the kind, followed by the endpoint when there is one. */
    fun text(): String =
        endpoint?.let { "$kind $it" } ?: kind

    companion object {

        const val UNPARSED = "unparsed"

        private val EMBEDDED = setOf("sqlite", "h2")
        private val NETWORKED = setOf("mariadb", "mysql", "postgresql")
        private val JDBC_URL = Regex("^jdbc:([a-z0-9]+):(?://([^/?\\s]*))?(?:[/?].*)?$", RegexOption.IGNORE_CASE)
        private val QUERY_PARAMETER = Regex("[?&;]([^=&;]+)=([^&;\\s]*)")
        private val SECRET_QUERY_KEY = Regex("(?i)(password|passwd|pwd|user|secret|token)")

        /**
         * Reads a database setting in any of its forms: the embedded kinds, the positional networked kinds, and a JDBC URL.
         * A setting that matches none of them is described as unparsed, with no secrets, so nothing of it is written.
         */
        fun describe(raw: String?): DatabaseDescription {
            val tokens = raw?.trim()?.split(Regex("\\s+"))?.filter { it.isNotEmpty() }.orEmpty()
            if (tokens.isEmpty()) return unparsed()
            return if (tokens[0].startsWith("jdbc:", ignoreCase = true)) jdbc(tokens) else positional(tokens)
        }

        private fun positional(tokens: List<String>): DatabaseDescription {
            val kind = tokens[0].lowercase()
            return when {
                kind in EMBEDDED ->
                    // The file name and the flags of an embedded database are not written, so any token after the kind is dropped
                    DatabaseDescription(kind, endpoint = null, secrets = emptyList(), redacted = tokens.size > 1)

                kind in NETWORKED && tokens.size >= 2 && '@' !in tokens[1] ->
                    // mariadb <host> <database> <user> <password>: the user and the password are the secrets
                    DatabaseDescription(
                        kind = kind,
                        endpoint = tokens[1],
                        secrets = listOfNotNull(tokens.getOrNull(3), tokens.getOrNull(4)),
                        redacted = tokens.size > 2,
                    )

                else -> unparsed()
            }
        }

        private fun jdbc(tokens: List<String>): DatabaseDescription {
            val url = tokens[0]
            val match = JDBC_URL.matchEntire(url) ?: return unparsed()
            val subkind = match.groupValues[1].lowercase()
            val authority = match.groups[2]?.value.orEmpty()
            val hostPort = authority.substringAfterLast('@')
            val secrets = mutableListOf<String>()
            if ('@' in authority) {
                secrets += authority.substringBeforeLast('@').split(':').filter { it.isNotEmpty() }
            }
            QUERY_PARAMETER.findAll(url).forEach { parameter ->
                if (SECRET_QUERY_KEY.containsMatchIn(parameter.groupValues[1])) secrets += parameter.groupValues[2]
            }
            // The user and the password follow the URL as two separate tokens
            secrets += tokens.drop(1)

            val networked = subkind in NETWORKED
            return DatabaseDescription(
                kind = "jdbc:$subkind",
                endpoint = hostPort.takeIf { networked && it.isNotEmpty() },
                secrets = secrets.filter { it.isNotEmpty() },
                redacted = url.contains('?') || '@' in authority || tokens.size > 1,
            )
        }

        private fun unparsed() =
            DatabaseDescription(UNPARSED, endpoint = null, secrets = emptyList(), redacted = true)
    }
}
