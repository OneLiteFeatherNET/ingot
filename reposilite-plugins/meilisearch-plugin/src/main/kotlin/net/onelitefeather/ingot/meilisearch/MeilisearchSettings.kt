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

package net.onelitefeather.ingot.meilisearch

import net.onelitefeather.ingot.search.config.SearchVariables
import net.onelitefeather.ingot.search.config.singleUrl
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

/**
 * The `INGOT_MEILISEARCH_*` variables (design D2). The rules of the URL, the key and the bounded numbers are shared with the other
 * search plugins in `reposilite-search-http`; this class names the variables of this plugin and their defaults.
 *
 * The key is read from the plain variable or, when that is unset, once from the file named by `INGOT_MEILISEARCH_API_KEY_FILE`
 * through [readFile]. The reader is injected so that the rules can be tested without a file system.
 */
internal class MeilisearchSettings private constructor(
    /** The base URL of the server. */
    val url: URI,
    /** Whether a plain `http://` URL is accepted. The plugin logs a warning at every read when this is true. */
    val allowHttp: Boolean,
    /** The API key, sent only in the `Authorization` header. Null when no key is configured. */
    val apiKey: String?,
    /** The index uid of the artifacts. */
    val index: String,
    /** The timeout of one request in seconds. */
    val timeoutSeconds: Int,
    /** How long one batch waits for its tasks in seconds, at most 60 (design D4). */
    val taskWaitSeconds: Int,
    /** Documents per upsert request. */
    val batchSize: Int,
) {

    companion object {

        const val DEFAULT_INDEX: String = "ingot-artifacts"
        const val DEFAULT_TIMEOUT_SECONDS: Int = 30
        const val DEFAULT_TASK_WAIT_SECONDS: Int = 20
        const val DEFAULT_BATCH_SIZE: Int = 1000

        private val INDEX_UID = Regex("[A-Za-z0-9_-]{1,64}")

        /** The warning of every read while plain HTTP is accepted. It names the switch and never the host. */
        const val PLAIN_HTTP_WARNING: String =
            "INGOT_MEILISEARCH_ALLOW_HTTP is true, the server is reached over plain http and the key and the documents travel unencrypted"

        /**
         * Reads the settings. A read with plain HTTP accepted writes [PLAIN_HTTP_WARNING] to [log], so every start of a backend
         * shows it. A refusal names its variable and never echoes a value or the key.
         */
        fun read(
            environment: Map<String, String>,
            readFile: (String) -> String = { path -> Files.readString(Path.of(path)) },
            log: (String) -> Unit = {},
        ): MeilisearchSettings {
            val variables = SearchVariables("MEILISEARCH", environment, readFile)
            val allowHttp = variables.flag("ALLOW_HTTP")
            val settings = MeilisearchSettings(
                url = variables.singleUrl(allowHttp, productName = "Meilisearch"),
                allowHttp = allowHttp,
                apiKey = variables.secret("API_KEY", kind = "key"),
                index = index(variables),
                timeoutSeconds = variables.integer("TIMEOUT_SECONDS", DEFAULT_TIMEOUT_SECONDS, 1..300),
                taskWaitSeconds = variables.integer("TASK_WAIT_SECONDS", DEFAULT_TASK_WAIT_SECONDS, 1..60),
                batchSize = variables.integer("BATCH_SIZE", DEFAULT_BATCH_SIZE, 1..10_000),
            )
            if (allowHttp) log(PLAIN_HTTP_WARNING)
            return settings
        }

        private fun index(variables: SearchVariables): String {
            val (variable, raw) = variables.lookup("INDEX") ?: return DEFAULT_INDEX
            val uid = raw.trim()
            require(INDEX_UID.matches(uid)) {
                "$variable must be 1 to 64 letters, digits, dashes or underscores"
            }
            return uid
        }
    }
}
