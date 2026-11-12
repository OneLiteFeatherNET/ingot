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

package net.onelitefeather.ingot.opensearch

import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Files
import java.nio.file.Path

/**
 * The `INGOT_OPENSEARCH_*` variables (design D2). Each variable falls back to its `REPOSILITE_OPENSEARCH_*` spelling, and the
 * `INGOT_` spelling wins when it is set. A blank value counts as unset. An invalid value is refused with an
 * [IllegalArgumentException] that names the variable and never its value, so that no message carries a password or a URL with
 * user information.
 *
 * The password is read from the plain variable or, when that is unset, once from the file named by `INGOT_OPENSEARCH_PASSWORD_FILE`
 * through [readFile]. The reader is injected so that the rules can be tested without a file system.
 */
internal class OpenSearchSettings private constructor(
    /** The cluster base URLs, in the order configured. */
    val urls: List<URI>,
    /** Whether plain `http://` URLs are accepted. The plugin logs a warning at every start when this is true. */
    val allowHttp: Boolean,
    /** The name prefix of the index and of its alias. */
    val index: String,
    val username: String?,
    val password: String?,
    val bulkSize: Int,
    val timeoutSeconds: Int,
    val replicas: Int,
) {

    /** True when no credentials are configured, which is allowed for a cluster that runs without security. */
    val unauthenticated: Boolean
        get() = username == null

    companion object {

        const val DEFAULT_INDEX: String = "ingot-artifacts"
        const val DEFAULT_BULK_SIZE: Int = 500
        const val DEFAULT_TIMEOUT_SECONDS: Int = 30
        const val DEFAULT_REPLICAS: Int = 1

        private const val PREFIX = "OPENSEARCH_"
        private val INDEX_NAME = Regex("[a-z0-9][a-z0-9_-]{0,63}")

        fun read(
            environment: Map<String, String>,
            readFile: (String) -> String = { path -> Files.readString(Path.of(path)) },
        ): OpenSearchSettings {
            val allowHttp = flag(environment, "ALLOW_HTTP")
            val username = lookup(environment, "USERNAME")?.second
            val password = password(environment, readFile)
            require(username == null || password != null) {
                "INGOT_OPENSEARCH_USERNAME is set without INGOT_OPENSEARCH_PASSWORD or INGOT_OPENSEARCH_PASSWORD_FILE"
            }
            require(password == null || username != null) {
                "INGOT_OPENSEARCH_PASSWORD is set without INGOT_OPENSEARCH_USERNAME"
            }
            return OpenSearchSettings(
                urls = urls(environment, allowHttp),
                allowHttp = allowHttp,
                index = index(environment),
                username = username,
                password = password,
                bulkSize = integer(environment, "BULK_SIZE", DEFAULT_BULK_SIZE, 1..5000),
                timeoutSeconds = integer(environment, "TIMEOUT_SECONDS", DEFAULT_TIMEOUT_SECONDS, 1..300),
                replicas = integer(environment, "REPLICAS", DEFAULT_REPLICAS, 0..10),
            )
        }

        /** The variable that was read and its raw value, or null when neither spelling is set to a non-blank value. */
        private fun lookup(environment: Map<String, String>, name: String): Pair<String, String>? =
            listOf("INGOT_$PREFIX$name", "REPOSILITE_$PREFIX$name").firstNotNullOfOrNull { variable ->
                environment[variable]?.takeIf { it.isNotBlank() }?.let { variable to it }
            }

        private fun urls(environment: Map<String, String>, allowHttp: Boolean): List<URI> {
            val (variable, raw) = lookup(environment, "URLS")
                ?: throw IllegalArgumentException("INGOT_OPENSEARCH_URLS is required when the OpenSearch plugin is enabled")
            val entries = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            require(entries.isNotEmpty()) { "$variable lists no URL" }
            return entries.map { entry -> url(variable, entry, allowHttp) }
        }

        private fun url(variable: String, entry: String, allowHttp: Boolean): URI {
            val uri = try {
                URI(entry)
            } catch (failure: URISyntaxException) {
                throw IllegalArgumentException("$variable contains a value that is not a valid URL")
            }
            require(uri.rawAuthority?.contains('@') != true) {
                "$variable contains a URL with user information, set INGOT_OPENSEARCH_USERNAME and INGOT_OPENSEARCH_PASSWORD instead"
            }
            when (uri.scheme?.lowercase()) {
                "https" -> Unit
                "http" -> require(allowHttp) {
                    "$variable contains an http URL, set INGOT_OPENSEARCH_ALLOW_HTTP=true to accept plain HTTP"
                }
                else -> throw IllegalArgumentException("$variable only accepts https URLs")
            }
            require(!uri.host.isNullOrBlank()) { "$variable contains a URL without a host" }
            return uri
        }

        private fun password(environment: Map<String, String>, readFile: (String) -> String): String? {
            lookup(environment, "PASSWORD")?.let { (_, value) -> return value }
            val (variable, path) = lookup(environment, "PASSWORD_FILE") ?: return null
            val content = try {
                readFile(path.trim())
            } catch (failure: Exception) {
                throw IllegalArgumentException("$variable cannot be read: ${failure.javaClass.simpleName}")
            }
            val password = content.trimEnd('\r', '\n')
            require(password.isNotEmpty()) { "$variable names a file without a password" }
            return password
        }

        private fun index(environment: Map<String, String>): String {
            val (variable, raw) = lookup(environment, "INDEX") ?: return DEFAULT_INDEX
            val name = raw.trim()
            require(INDEX_NAME.matches(name)) {
                "$variable must be 1 to 64 lower case letters, digits, dashes or underscores, starting with a letter or digit"
            }
            return name
        }

        private fun flag(environment: Map<String, String>, name: String): Boolean {
            val (variable, raw) = lookup(environment, name) ?: return false
            return when (raw.trim().lowercase()) {
                "true" -> true
                "false" -> false
                else -> throw IllegalArgumentException("$variable must be true or false")
            }
        }

        private fun integer(environment: Map<String, String>, name: String, default: Int, range: IntRange): Int {
            val (variable, raw) = lookup(environment, name) ?: return default
            val value = raw.trim().toIntOrNull()
            require(value != null && value in range) {
                "$variable must be a whole number from ${range.first} to ${range.last}, got '${raw.trim()}'"
            }
            return value
        }
    }
}
