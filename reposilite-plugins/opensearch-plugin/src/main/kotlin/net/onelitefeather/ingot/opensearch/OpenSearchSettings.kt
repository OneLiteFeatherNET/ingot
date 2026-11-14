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

import net.onelitefeather.ingot.search.config.SearchVariables
import net.onelitefeather.ingot.search.config.urls
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

/**
 * The `INGOT_OPENSEARCH_*` variables (design D2). The rules of the variables, the URLs and the secrets are shared with the other search
 * plugins in `reposilite-search-http`; this class only names the variables of this plugin and their defaults.
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

        private val INDEX_NAME = Regex("[a-z0-9][a-z0-9_-]{0,63}")

        /** The warning of every start while plain HTTP is accepted. It names the switch and never a URL, user or password. */
        const val PLAIN_HTTP_WARNING: String =
            "INGOT_OPENSEARCH_ALLOW_HTTP is true, the cluster is reached over plain http and the credentials and the index travel unencrypted"

        /**
         * Reads the settings. A successful read with plain HTTP accepted writes [PLAIN_HTTP_WARNING] to [log], so every start of a
         * backend shows it. [log] receives nothing else, and the default discards it for the callers that do not log.
         */
        fun read(
            environment: Map<String, String>,
            readFile: (String) -> String = { path -> Files.readString(Path.of(path)) },
            log: (String) -> Unit = {},
        ): OpenSearchSettings {
            val variables = SearchVariables("OPENSEARCH", environment, readFile)
            val allowHttp = variables.flag("ALLOW_HTTP")
            val (username, password) = variables.credentials()
            val settings = OpenSearchSettings(
                urls = variables.urls(allowHttp, productName = "OpenSearch"),
                allowHttp = allowHttp,
                index = index(variables),
                username = username,
                password = password,
                bulkSize = variables.integer("BULK_SIZE", DEFAULT_BULK_SIZE, 1..5000),
                timeoutSeconds = variables.integer("TIMEOUT_SECONDS", DEFAULT_TIMEOUT_SECONDS, 1..300),
                replicas = variables.integer("REPLICAS", DEFAULT_REPLICAS, 0..10),
            )
            if (allowHttp) log(PLAIN_HTTP_WARNING)
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
