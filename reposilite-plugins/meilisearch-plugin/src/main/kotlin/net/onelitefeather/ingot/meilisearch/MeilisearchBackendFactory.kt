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

package net.onelitefeather.ingot.meilisearch

import net.onelitefeather.ingot.search.backend.ArtifactSearchBackend
import net.onelitefeather.ingot.search.backend.ArtifactSearchBackendFactory
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.backend.SEARCH_BACKEND_API_VERSION
import net.onelitefeather.ingot.search.backend.SearchBackendContext
import net.onelitefeather.ingot.search.http.HttpExchange

/**
 * The factory of the `meilisearch` backend (API version 1). The settings are read when a backend is created, so a refused setting
 * reaches the core as the reason of `OPEN_FAILED`. Creating the backend sends no request: the core opens it afterwards.
 *
 * [exchange] replaces the JDK client of the connections. Only the unit tests pass one; the plugin registers the factory with null.
 */
internal class MeilisearchBackendFactory(
    private val exchange: HttpExchange? = null,
    private val settings: (log: (String) -> Unit) -> MeilisearchSettings,
) : ArtifactSearchBackendFactory {

    override val id: String = ID

    override val apiVersion: Int = SEARCH_BACKEND_API_VERSION

    override fun create(context: SearchBackendContext): ArtifactSearchBackend {
        val logger = context.logger()
        return MeilisearchSearchBackend(settings(logger::warn), context.clock, exchange, SleepingWaiter, logger::info)
    }

    companion object {
        const val ID: String = "meilisearch"
    }

}

/**
 * The factory for [environment], as the registration of the plugin builds it, for the container tests of the server. The settings
 * are read from [environment] when a backend is created. [exchange] is for tests only (see [MeilisearchBackendFactory]).
 */
@ExperimentalIngotSearchApi
fun meilisearchBackendFactory(
    environment: Map<String, String>,
    exchange: HttpExchange? = null,
): ArtifactSearchBackendFactory = MeilisearchBackendFactory(exchange) { log ->
    MeilisearchSettings.read(environment, { path -> java.nio.file.Files.readString(java.nio.file.Path.of(path)) }, log)
}
