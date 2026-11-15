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

package net.onelitefeather.ingot.lucene

import net.onelitefeather.ingot.search.backend.ArtifactSearchBackend
import net.onelitefeather.ingot.search.backend.ArtifactSearchBackendFactory
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.backend.SEARCH_BACKEND_API_VERSION
import net.onelitefeather.ingot.search.backend.SearchBackendContext
import java.nio.file.Path

/**
 * The factory of the `lucene` backend (API version 1). The settings are read when the backend is created, which the core does off the
 * request threads, so a refused setting makes the create call throw and the core reports the backend as `OPEN_FAILED`.
 *
 * The backend takes the single-writer lease from the lease store of the context. A context without one is refused: a second writer
 * could not be excluded, and the index would be unsafe.
 */
internal class LuceneBackendFactory(
    private val settings: () -> LuceneSettings,
) : ArtifactSearchBackendFactory {

    override val id: String = LuceneSearchBackend.ID

    override val apiVersion: Int = SEARCH_BACKEND_API_VERSION

    override fun create(context: SearchBackendContext): ArtifactSearchBackend {
        val leases = checkNotNull(context.leases) {
            "the lucene backend needs the job lease store of the instance database, which this server does not provide"
        }
        return LuceneSearchBackend(settings(), leases, context.clock, context.logger())
    }

}

/**
 * Registers the factory of this plugin through [accept]. The settings are read from [environment] when the backend is created, and
 * the directory defaults to [defaultDirectory] when no `INGOT_LUCENE_DIRECTORY` is set.
 */
internal fun registerLucene(
    environment: Map<String, String>,
    defaultDirectory: Path,
    accept: (ArtifactSearchBackendFactory) -> Unit,
) {
    accept(LuceneBackendFactory { LuceneSettings.read(environment, defaultDirectory) })
}
