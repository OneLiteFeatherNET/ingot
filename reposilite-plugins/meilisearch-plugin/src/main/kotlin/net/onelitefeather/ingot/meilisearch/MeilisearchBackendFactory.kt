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

/**
 * The factory of the `meilisearch` backend (API version 1). It reads its settings when a backend is created, so a refused
 * setting reaches the core as the reason of `OPEN_FAILED`.
 *
 * The backend itself is not built yet: [create] validates the configuration, then refuses. A selected Meilisearch backend
 * therefore reports `OPEN_FAILED` in this build, and the database keeps serving search only while `searchEngine` is `database`.
 */
internal class MeilisearchBackendFactory(
    private val settings: (log: (String) -> Unit) -> MeilisearchSettings,
) : ArtifactSearchBackendFactory {

    override val id: String = ID

    override val apiVersion: Int = SEARCH_BACKEND_API_VERSION

    override fun create(context: SearchBackendContext): ArtifactSearchBackend {
        settings(context.logger()::warn)
        error("the meilisearch backend is not available in this build yet")
    }

    companion object {
        const val ID: String = "meilisearch"
    }

}
