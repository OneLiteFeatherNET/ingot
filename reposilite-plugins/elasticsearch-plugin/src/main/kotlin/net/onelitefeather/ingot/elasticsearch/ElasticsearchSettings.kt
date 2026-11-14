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

package net.onelitefeather.ingot.elasticsearch

import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Files
import java.nio.file.Path

/**
 * The `INGOT_ELASTICSEARCH_*` variables (design D4). Each variable falls back to its `REPOSILITE_ELASTICSEARCH_*` spelling, and the
 * `INGOT_` spelling wins when it is set. A blank value counts as unset. An invalid value is refused with an
 * [IllegalArgumentException] that names the variable and never its value, so that no message carries a key, a password or a URL
 * with user information.
 *
 * The API key and the password are read from the plain variable or, when that is unset, once from the file named by the `_FILE`
 * variable through [readFile]. The reader is injected so that the rules can be tested without a file system.
 */
internal class ElasticsearchSettings private constructor(
    /** The cluster base URLs, in the order configured. */
    val urls: List<URI>,
    /** Whether plain `http://` URLs are accepted. The plugin logs a warning at every start when this is true. */
    val allowHttp: Boolean,
    /** The name prefix of the index and of its alias. */
    val index: String,
    /** The encoded API key, preferred over basic authentication. */
    val apiKey: String?,
    val username: String?,
    val password: String?,
    val bulkSize: Int,
    val timeoutSeconds: Int,
    val replicas: Int,
) {

    /**
     * The value of the `Authorization` header of every request: the `ApiKey` scheme of the key, or the basic scheme of the user and
     * the password, or null for a cluster without security. The header never leaves the plugin except in a request.
     */
    val authorization: String? = when {
        apiKey != null -> "ApiKey $apiKey"
        username != null && password != null ->
            "Basic " + java.util.Base64.getEncoder().encodeToString("$username:$password".toByteArray(Charsets.UTF_8))
        else -> null
    }

    companion object {

        const val DEFAULT_INDEX: String = "ingot-artifacts"
        const val DEFAULT_BULK_SIZE: Int = 500
        const val DEFAULT_TIMEOUT_SECONDS: Int = 30
        const val DEFAULT_REPLICAS: Int = 1

        private const val PREFIX = "ELASTICSEARCH_"
        private val INDEX_NAME = Regex("[a-z0-9][a-z0-9_-]{0,63}")

        /** The warning of every start while plain HTTP is accepted. It names the switch and never a URL, key, user or password. */
        const val PLAIN_HTTP_WARNING: String =
            "INGOT_ELASTICSEARCH_ALLOW_HTTP is true, the cluster is reached over plain http and the credentials and the index travel unencrypted"

        /** The warning of every start without any credential. It names the variables and never a value. */
        const val UNAUTHENTICATED_WARNING: String =
            "no INGOT_ELASTICSEARCH_API_KEY or INGOT_ELASTICSEARCH_USERNAME is set, the cluster is reached without authentication"

        /**
         * Reads the settings. [log] receives the warnings of a successful read: the plain HTTP switch and a missing credential.
         * Nothing else is logged, and the default discards the warnings for the callers that do not log.
         */
        fun read(
            environment: Map<String, String>,
            readFile: (String) -> String = { path -> Files.readString(Path.of(path)) },
            log: (String) -> Unit = {},
        ): ElasticsearchSettings {
            val allowHttp = flag(environment, "ALLOW_HTTP")
            val apiKey = secret(environment, readFile, "API_KEY")
            val username = lookup(environment, "USERNAME")?.second
            val password = secret(environment, readFile, "PASSWORD")
            require(apiKey == null || username == null) {
                "INGOT_ELASTICSEARCH_API_KEY and INGOT_ELASTICSEARCH_USERNAME cannot be set together, choose one of them"
            }
            require(username == null || password != null) {
                "INGOT_ELASTICSEARCH_USERNAME is set without INGOT_ELASTICSEARCH_PASSWORD or INGOT_ELASTICSEARCH_PASSWORD_FILE"
            }
            require(password == null || username != null) {
                "INGOT_ELASTICSEARCH_PASSWORD is set without INGOT_ELASTICSEARCH_USERNAME"
            }
            require(apiKey != null || username != null || allowHttp) {
                "INGOT_ELASTICSEARCH_URLS needs an INGOT_ELASTICSEARCH_API_KEY or an INGOT_ELASTICSEARCH_USERNAME with a password, " +
                    "or INGOT_ELASTICSEARCH_ALLOW_HTTP=true for a cluster that runs without security"
            }
            val settings = ElasticsearchSettings(
                urls = urls(environment, allowHttp),
                allowHttp = allowHttp,
                index = index(environment),
                apiKey = apiKey,
                username = username,
                password = password,
                bulkSize = integer(environment, "BULK_SIZE", DEFAULT_BULK_SIZE, 1..5000),
                timeoutSeconds = integer(environment, "TIMEOUT_SECONDS", DEFAULT_TIMEOUT_SECONDS, 1..300),
                replicas = integer(environment, "REPLICAS", DEFAULT_REPLICAS, 0..10),
            )
            if (allowHttp) log(PLAIN_HTTP_WARNING)
            if (apiKey == null && username == null) log(UNAUTHENTICATED_WARNING)
            return settings
        }

        /** The variable that was read and its raw value, or null when neither spelling is set to a non-blank value. */
        private fun lookup(environment: Map<String, String>, name: String): Pair<String, String>? =
            listOf("INGOT_$PREFIX$name", "REPOSILITE_$PREFIX$name").firstNotNullOfOrNull { variable ->
                environment[variable]?.takeIf { it.isNotBlank() }?.let { variable to it }
            }

        private fun urls(environment: Map<String, String>, allowHttp: Boolean): List<URI> {
            val (variable, raw) = lookup(environment, "URLS")
                ?: throw IllegalArgumentException("INGOT_ELASTICSEARCH_URLS is required when the Elasticsearch plugin is selected")
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
                "$variable contains a URL with user information, set INGOT_ELASTICSEARCH_USERNAME and INGOT_ELASTICSEARCH_PASSWORD instead"
            }
            when (uri.scheme?.lowercase()) {
                "https" -> Unit
                "http" -> require(allowHttp) {
                    "$variable contains an http URL, set INGOT_ELASTICSEARCH_ALLOW_HTTP=true to accept plain HTTP"
                }
                else -> throw IllegalArgumentException("$variable only accepts https URLs")
            }
            require(!uri.host.isNullOrBlank()) { "$variable contains a URL without a host" }
            return uri
        }

        /**
         * The plain variable [name], or else the content of the file named by `<name>_FILE` read once. The file content loses its
         * trailing line breaks, and the plain variable wins over the file, so the file is not read when the plain one is set.
         */
        private fun secret(environment: Map<String, String>, readFile: (String) -> String, name: String): String? {
            lookup(environment, name)?.let { (_, value) -> return value }
            val (variable, path) = lookup(environment, "${name}_FILE") ?: return null
            val content = try {
                readFile(path.trim())
            } catch (failure: Exception) {
                throw IllegalArgumentException("$variable cannot be read: ${failure.javaClass.simpleName}")
            }
            val secret = content.trimEnd('\r', '\n')
            require(secret.isNotEmpty()) { "$variable names a file without a value" }
            return secret
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
