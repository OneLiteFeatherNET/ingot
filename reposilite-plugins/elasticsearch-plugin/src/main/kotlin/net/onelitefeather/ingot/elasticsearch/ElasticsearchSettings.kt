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

import net.onelitefeather.ingot.search.config.SearchVariables
import net.onelitefeather.ingot.search.config.urls
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

/**
 * The `INGOT_ELASTICSEARCH_*` variables (design D4). Each variable falls back to its `REPOSILITE_ELASTICSEARCH_*` spelling, and the
 * `INGOT_` spelling wins when it is set. A blank value counts as unset. An invalid value is refused with an
 * [IllegalArgumentException] that names the variable and never its value, so that no message carries a key, a password or a URL
 * with user information.
 *
 * The rules of the variables, the URLs and the secrets are shared with the other search plugins in `reposilite-search-http`; this
 * class only names the variables of this plugin, its defaults and the rules of the API key.
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

    companion object {

        const val DEFAULT_INDEX: String = "ingot-artifacts"
        const val DEFAULT_BULK_SIZE: Int = 500
        const val DEFAULT_TIMEOUT_SECONDS: Int = 30
        const val DEFAULT_REPLICAS: Int = 1

        private val INDEX_NAME = Regex("[a-z0-9][a-z0-9_-]{0,63}")

        /** The warning of every start while plain HTTP is accepted. It names the switch and never a URL, key, user or password. */
        const val PLAIN_HTTP_WARNING: String =
            "INGOT_ELASTICSEARCH_ALLOW_HTTP is true, the cluster is reached over plain http and the credentials and the index travel unencrypted"

        /**
         * The warning of every start without any credential (design D4, decided). It names the variables and never a value. Plain HTTP
         * without a credential is refused by the URL rule unless the HTTP switch is on, so this warning is in practice logged for https.
         */
        const val UNAUTHENTICATED_WARNING: String =
            "no INGOT_ELASTICSEARCH_API_KEY or INGOT_ELASTICSEARCH_USERNAME is set, the cluster is reached without credentials, " +
                "which is only safe behind mTLS or a trusted network"

        /**
         * Reads the settings. [log] receives the warnings of a successful read: the plain HTTP switch and a missing credential.
         * Nothing else is logged, and the default discards the warnings for the callers that do not log.
         */
        fun read(
            environment: Map<String, String>,
            readFile: (String) -> String = { path -> Files.readString(Path.of(path)) },
            log: (String) -> Unit = {},
        ): ElasticsearchSettings {
            val variables = SearchVariables("ELASTICSEARCH", environment, readFile)
            val allowHttp = variables.flag("ALLOW_HTTP")
            val apiKey = variables.secret("API_KEY", kind = "value")
            val username = variables.lookup("USERNAME")?.second
            val password = variables.secret("PASSWORD", kind = "value")
            require(apiKey == null || username == null) {
                "INGOT_ELASTICSEARCH_API_KEY and INGOT_ELASTICSEARCH_USERNAME cannot be set together, choose one of them"
            }
            require(username == null || password != null) {
                "INGOT_ELASTICSEARCH_USERNAME is set without INGOT_ELASTICSEARCH_PASSWORD or INGOT_ELASTICSEARCH_PASSWORD_FILE"
            }
            require(password == null || username != null) {
                "INGOT_ELASTICSEARCH_PASSWORD is set without INGOT_ELASTICSEARCH_USERNAME"
            }
            val settings = ElasticsearchSettings(
                urls = variables.urls(allowHttp, productName = "Elasticsearch"),
                allowHttp = allowHttp,
                index = index(variables),
                apiKey = apiKey,
                username = username,
                password = password,
                bulkSize = variables.integer("BULK_SIZE", DEFAULT_BULK_SIZE, 1..5000),
                timeoutSeconds = variables.integer("TIMEOUT_SECONDS", DEFAULT_TIMEOUT_SECONDS, 1..300),
                replicas = variables.integer("REPLICAS", DEFAULT_REPLICAS, 0..10),
            )
            if (allowHttp) log(PLAIN_HTTP_WARNING)
            if (apiKey == null && username == null) log(UNAUTHENTICATED_WARNING)
            return settings
        }

        private fun index(variables: SearchVariables): String {
            val (variable, raw) = variables.lookup("INDEX") ?: return DEFAULT_INDEX
            val name = raw.trim()
            require(INDEX_NAME.matches(name)) {
                "$variable must be 1 to 64 lower case letters, digits, dashes or underscores, starting with a letter or digit"
            }
            return name
        }
    }
}
