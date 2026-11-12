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

@file:OptIn(net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi::class)

package net.onelitefeather.ingot.search.database

import net.onelitefeather.ingot.search.ArtifactFilePage
import net.onelitefeather.ingot.search.ArtifactFilesQuery
import net.onelitefeather.ingot.search.ArtifactSearchPage
import net.onelitefeather.ingot.search.ArtifactSearchQuery
import net.onelitefeather.ingot.search.IndexStatus
import net.onelitefeather.ingot.search.SearchFacets
import net.onelitefeather.ingot.search.access.SearchAccess
import net.onelitefeather.ingot.search.backend.ArtifactSearchBackend
import net.onelitefeather.ingot.search.backend.BackendHealth
import net.onelitefeather.ingot.search.backend.IndexAction
import net.onelitefeather.ingot.search.backend.KeyBatch
import net.onelitefeather.ingot.search.backend.SamplePage
import net.onelitefeather.ingot.search.backend.StoredRow
import org.jetbrains.exposed.v1.jdbc.Database
import java.time.Clock
import kotlin.random.Random

/**
 * The index in the tables of the instance's database (search backend seam, D2). It joins the three database classes that
 * existed before the seam, and adds nothing to their SQL: the reads go to [DatabaseArtifactSearchEngine], the writes to
 * [DatabaseArtifactIndex], and the row maintenance to [IndexRowStore].
 */
internal class DatabaseSearchBackend(
    database: Database,
    clock: Clock = Clock.systemUTC(),
) : ArtifactSearchBackend {

    private val engine = DatabaseArtifactSearchEngine(database, clock)
    private val index = DatabaseArtifactIndex(database)
    private val rows = IndexRowStore(database)

    override val id: String = ID

    /** The database commits the rows, the cursor and the counters of a rebuild page together. */
    override val joinsDatabaseTransaction: Boolean = true

    /** The database is the server's own and is open before any plugin runs, so there is nothing to open. */
    override fun open() = Unit

    /** The database is closed by the server, not by the index. */
    override fun close() = Unit

    override fun health(): BackendHealth = runCatching { rows.count() }.fold(
        onSuccess = { BackendHealth.Up },
        onFailure = { BackendHealth.Down(it.message ?: it.javaClass.simpleName) },
    )

    override fun search(query: ArtifactSearchQuery, access: SearchAccess): ArtifactSearchPage = engine.search(query, access)

    override fun facets(query: ArtifactSearchQuery, access: SearchAccess): SearchFacets = engine.facets(query, access)

    override fun files(query: ArtifactFilesQuery, access: SearchAccess): ArtifactFilePage = engine.files(query, access)

    override fun status(): IndexStatus = engine.status()

    override fun apply(actions: List<IndexAction>) = index.apply(actions)

    override fun keyRows(after: Long, batchSize: Int): KeyBatch? = index.keyRows(after, batchSize)

    override fun pathsAt(repository: String, prefix: String, after: String?, limit: Int): List<String> =
        index.pathsAt(repository, prefix, after, limit)

    override fun existing(repository: String, paths: Collection<String>): Map<String, StoredRow> = rows.existing(repository, paths)

    override fun touch(repository: String, paths: Collection<String>, indexedAt: Long): Int = rows.touch(repository, paths, indexedAt)

    override fun deleteStale(repository: String, before: Long, limit: Int): Int = rows.deleteStale(repository, before, limit)

    override fun deleteRepository(repository: String, limit: Int): Int = rows.deleteRepository(repository, limit)

    override fun repositories(): List<String> = rows.repositories()

    override fun count(): Long = rows.count()

    /** The row ids are the cursor. The start is drawn from the id range, as the verify job always did. */
    override fun sampleStart(random: Random): String? =
        rows.idRange()?.let { range -> random.nextLong(range.first, range.last + 1).toString() }

    override fun sample(cursor: String?, limit: Int): SamplePage {
        val startId = cursor?.let { requireNotNull(it.toLongOrNull()) { "the cursor '$it' is not a row id" } } ?: Long.MIN_VALUE
        return rows.sample(startId, limit)
    }

    companion object {
        /** The value of the local key `searchEngine` that selects this backend. */
        const val ID = "database"
    }

}
