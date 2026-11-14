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

@file:OptIn(ExperimentalIngotSearchApi::class)

package net.onelitefeather.ingot.opensearch

import net.onelitefeather.ingot.search.backend.ArtifactSearchBackend
import net.onelitefeather.ingot.search.backend.ArtifactSearchBackendFactory
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.backend.SEARCH_BACKEND_API_VERSION
import net.onelitefeather.ingot.search.backend.SearchBackendContext
import javax.net.ssl.SSLContext

/**
 * The factory of the `opensearch` backend (API version 1). The settings are read when the backend is created, which the core
 * does off the request threads. A refused setting makes the create call throw, and the core reports the backend as
 * `OPEN_FAILED` with the message of the refusal.
 *
 * [sslContext] replaces the JVM default context of the connections. Only the container tests pass one, to trust the authority of
 * their own cluster; the plugin registers the factory with null.
 *
 * [settings] reads the settings and receives the log sink of the backend, which is the logger of the context at every create.
 */
internal class OpenSearchBackendFactory(
    private val settings: (log: (String) -> Unit) -> OpenSearchSettings,
    private val sslContext: SSLContext? = null,
) : ArtifactSearchBackendFactory {

    override val id: String = ID

    override val apiVersion: Int = SEARCH_BACKEND_API_VERSION

    override fun create(context: SearchBackendContext): ArtifactSearchBackend {
        val logger = context.logger()
        return OpenSearchSearchBackend(settings(logger::warn), context.clock, sslContext, warn = logger::warn)
    }

    companion object {
        const val ID: String = "opensearch"
    }

}

/**
 * The factory for [environment], as the registration of the plugin builds it, for the container tests of the server. The
 * settings are read from [environment] when the backend is created. [sslContext] is for tests only (see [OpenSearchBackendFactory]).
 */
@ExperimentalIngotSearchApi
fun openSearchBackendFactory(
    environment: Map<String, String>,
    sslContext: SSLContext? = null,
): ArtifactSearchBackendFactory = OpenSearchBackendFactory({ log -> OpenSearchSettings.read(environment, log = log) }, sslContext)
