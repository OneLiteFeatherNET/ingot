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

package net.onelitefeather.ingot.search.database

import net.onelitefeather.ingot.search.Origin
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.core.min
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

/** What a rebuild needs to know about a row that exists: enough to tell whether the file changed, and the checksums to keep. */
internal data class StoredRow(
    val sizeBytes: Long,
    val lastModified: Long,
    val sha1: String?,
    val sha256: String?,
)

/** A row as a verify run reads it: what storage and the mirror ledger are compared with. */
internal data class SampledRow(
    val id: Long,
    val repository: String,
    val path: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val origin: Origin,
    val sourceHost: String?,
)

/**
 * The reads and bulk deletes of `search_artifact` that rebuild and verify runs make, beyond the upserts of
 * [DatabaseArtifactIndex]. Every method is its own transaction, or joins the one the caller has open.
 */
internal class IndexRowStore(private val database: Database) {

    private val rows = SearchTables(SearchDialect.of(database.vendor)).artifact

    /** The rows of [paths] in [repository], by path. */
    fun existing(repository: String, paths: Collection<String>): Map<String, StoredRow> {
        if (paths.isEmpty()) return emptyMap()
        val hashes = paths.associateBy { pathHash(repository, it) }
        return transaction(database) {
            hashes.keys.chunked(HASH_CHUNK).flatMap { chunk ->
                rows.selectAll()
                    .where { (rows.repository eq repository) and (rows.pathHash inList chunk) }
                    .map { row -> hashes.getValue(row[rows.pathHash]) to StoredRow(row[rows.sizeBytes], row[rows.lastModified], row[rows.sha1], row[rows.sha256]) }
            }.toMap()
        }
    }

    /** Marks the rows of [paths] as seen at [indexedAt] without changing anything else. Returns how many rows exist. */
    fun touch(repository: String, paths: Collection<String>, indexedAt: Long): Int {
        if (paths.isEmpty()) return 0
        return transaction(database) {
            paths.map { pathHash(repository, it) }.chunked(HASH_CHUNK).sumOf { chunk ->
                rows.update(where = { (rows.repository eq repository) and (rows.pathHash inList chunk) }, body = {
                    it[rows.indexedAt] = indexedAt
                })
            }
        }
    }

    /** Deletes up to [limit] rows of [repository] that were indexed before [before]. Returns how many were deleted. */
    fun deleteStale(repository: String, before: Long, limit: Int): Int = transaction(database) {
        val ids = rows.select(rows.id)
            .where { (rows.repository eq repository) and (rows.indexedAt less before) }
            .limit(limit)
            .map { it[rows.id] }
        if (ids.isEmpty()) 0 else rows.deleteWhere { rows.id inList ids }
    }

    /** Deletes up to [limit] rows of [repository]. Returns how many were deleted. */
    fun deleteRepository(repository: String, limit: Int): Int = transaction(database) {
        val ids = rows.select(rows.id).where { rows.repository eq repository }.limit(limit).map { it[rows.id] }
        if (ids.isEmpty()) 0 else rows.deleteWhere { rows.id inList ids }
    }

    /** The names of the repositories that have at least one row. */
    fun repositories(): List<String> = transaction(database) {
        rows.select(rows.repository).withDistinct().map { it[rows.repository] }
    }

    /** The smallest and largest row id, or null for an empty table. */
    fun idRange(): LongRange? {
        val bounds: List<Long?> = transaction(database) {
            val row = rows.select(rows.id.min(), rows.id.max()).single()
            listOf(row[rows.id.min()], row[rows.id.max()])
        }
        val low = bounds[0] ?: return null
        val high = bounds[1] ?: return null
        return low..high
    }

    /**
     * Up to [limit] rows with consecutive ids from [startId] upwards. When the table ends before [limit] rows are found, the
     * sample continues from the smallest id, so it never repeats a row and covers a small table completely.
     */
    fun sample(startId: Long, limit: Int): List<SampledRow> = transaction(database) {
        val upper = rows.selectAll().where { rows.id greaterEq startId }.orderBy(rows.id, SortOrder.ASC).limit(limit).map { it.toSampled() }
        if (upper.size >= limit) {
            upper
        } else {
            upper + rows.selectAll().where { rows.id less startId }.orderBy(rows.id, SortOrder.ASC).limit(limit - upper.size).map { it.toSampled() }
        }
    }

    private fun ResultRow.toSampled() = SampledRow(
        id = this[rows.id],
        repository = this[rows.repository],
        path = this[rows.path],
        sizeBytes = this[rows.sizeBytes],
        lastModified = this[rows.lastModified],
        origin = Origin.valueOf(this[rows.origin]),
        sourceHost = this[rows.sourceHost],
    )

    fun count(): Long = transaction(database) { rows.selectAll().count() }

    private companion object {
        const val HASH_CHUNK = 500
    }

}
