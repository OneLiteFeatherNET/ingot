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

import net.onelitefeather.ingot.search.ArtifactFilePage
import net.onelitefeather.ingot.search.ArtifactFilesQuery
import net.onelitefeather.ingot.search.ArtifactSearchPage
import net.onelitefeather.ingot.search.ArtifactSearchQuery
import net.onelitefeather.ingot.search.IndexStatus
import net.onelitefeather.ingot.search.SearchFacets
import net.onelitefeather.ingot.search.access.SearchAccess
import net.onelitefeather.ingot.search.backend.ArtifactSearchBackend
import net.onelitefeather.ingot.search.backend.BackendHealth
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.backend.IndexAction
import net.onelitefeather.ingot.search.backend.SamplePage
import net.onelitefeather.ingot.search.backend.StoredRow
import kotlin.random.Random

/**
 * Placeholder of the `opensearch` backend until the query translation and the write path are implemented. Every operation
 * that would touch the cluster throws, and [health] reports `DOWN`, so an instance that selects this backend answers as
 * unavailable instead of serving partial results.
 */
internal class OpenSearchStubBackend(
    val settings: OpenSearchSettings,
) : ArtifactSearchBackend {

    override val id: String = OpenSearchBackendFactory.ID

    override val joinsDatabaseTransaction: Boolean = false

    override fun open(): Unit = notImplemented()

    override fun close() = Unit

    override fun health(): BackendHealth = BackendHealth.Down(NOT_IMPLEMENTED)

    override fun apply(actions: List<IndexAction>): Unit = notImplemented()

    override fun pathsAt(repository: String, prefix: String, after: String?, limit: Int): List<String> = notImplemented()

    override fun existing(repository: String, paths: Collection<String>): Map<String, StoredRow> = notImplemented()

    override fun touch(repository: String, paths: Collection<String>, indexedAt: Long): Int = notImplemented()

    override fun deleteStale(repository: String, before: Long, limit: Int): Int = notImplemented()

    override fun deleteRepository(repository: String, limit: Int): Int = notImplemented()

    override fun repositories(): List<String> = notImplemented()

    override fun count(): Long = notImplemented()

    override fun sampleStart(random: Random): String? = notImplemented()

    override fun sample(cursor: String?, limit: Int): SamplePage = notImplemented()

    override fun search(query: ArtifactSearchQuery, access: SearchAccess): ArtifactSearchPage = notImplemented()

    override fun facets(query: ArtifactSearchQuery, access: SearchAccess): SearchFacets = notImplemented()

    override fun files(query: ArtifactFilesQuery, access: SearchAccess): ArtifactFilePage = notImplemented()

    override fun status(): IndexStatus = notImplemented()

    private companion object {
        const val NOT_IMPLEMENTED = "The OpenSearch backend is not implemented yet"

        fun notImplemented(): Nothing = throw UnsupportedOperationException(NOT_IMPLEMENTED)
    }

}
