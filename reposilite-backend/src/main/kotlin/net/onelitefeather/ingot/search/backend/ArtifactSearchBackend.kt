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

package net.onelitefeather.ingot.search.backend

import net.onelitefeather.ingot.search.ArtifactSearchEngine
import net.onelitefeather.ingot.search.IndexRow
import net.onelitefeather.ingot.search.Origin
import net.onelitefeather.ingot.search.Provenance
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

/**
 * Where the artifact index lives (search backend seam). The core runs the outbox, the rebuild, the verify job and the
 * startup rules against this interface only. The read side is [ArtifactSearchEngine], so the REST layer is unchanged.
 *
 * Public and experimental (search backend registration): a plugin supplies a backend through [ArtifactSearchBackendFactory].
 */
@ExperimentalIngotSearchApi
interface ArtifactSearchBackend : ArtifactSearchEngine {

    /**
     * True when a write made with [apply] commits in the same transaction as the cursor and counters of a rebuild page. The
     * database backend does; an external index cannot, so the rebuild applies its page first and commits the cursor after.
     */
    val joinsDatabaseTransaction: Boolean

    /**
     * Connects to the index and prepares it. May block for as long as it takes; the core calls it off the request threads,
     * and a throw counts as a failed open that is retried later.
     */
    fun open()

    /** Releases what [open] took. The core calls it when an open failed after the backend was created. */
    fun close()

    /** Whether the backend answers. The core checks it every few seconds while the backend is open. */
    fun health(): BackendHealth

    /** Applies [actions] in order. Every action is idempotent, so a batch can be applied again after a crash. */
    fun apply(actions: List<IndexAction>)

    /** The paths of the rows at [prefix] and below it, in path order, after [after], at most [limit]. */
    fun pathsAt(repository: String, prefix: String, after: String?, limit: Int): List<String>

    /** The stored state of the rows of [paths] in [repository], by path. Paths without a row are absent. */
    fun existing(repository: String, paths: Collection<String>): Map<String, StoredRow>

    /** Marks the rows of [paths] as seen at [indexedAt] without changing anything else. Returns how many rows exist. */
    fun touch(repository: String, paths: Collection<String>, indexedAt: Long): Int

    /** Deletes up to [limit] rows of [repository] that were indexed before [before]. Returns how many were deleted. */
    fun deleteStale(repository: String, before: Long, limit: Int): Int

    /** Deletes up to [limit] rows of [repository]. Returns how many were deleted. */
    fun deleteRepository(repository: String, limit: Int): Int

    /**
     * Fills the key and the word tokens of up to [batchSize] rows that have no key and an id after [after], in id order. Returns
     * the batch, whose [KeyBatch.lastId] is the cursor of the next call, or null when no such row is left. A backend without
     * word tokens keeps the default (full text change, D6).
     */
    fun keyRows(after: Long, batchSize: Int): KeyBatch? = null

    /** The names of the repositories that have at least one row. */
    fun repositories(): List<String>

    /** The number of rows. */
    fun count(): Long

    /**
     * A cursor where a verify sample starts, drawn from [random], or null for an empty index. The core keeps the random
     * source, so a seeded test gets the same sample as before the seam existed.
     */
    fun sampleStart(random: Random): String?

    /** Up to [limit] rows from [cursor] on, and the cursor of the row after the last one. A null cursor starts at the first row. */
    fun sample(cursor: String?, limit: Int): SamplePage

}

/** The answer of a health check. */
@ExperimentalIngotSearchApi
sealed interface BackendHealth {

    data object Up : BackendHealth

    data class Down(val message: String) : BackendHealth

}

/** One change the core asks a backend to make. A batch is applied in order. */
@ExperimentalIngotSearchApi
sealed interface IndexAction {

    data class Upsert(val row: IndexRow) : IndexAction

    data class RemovePath(val repository: String, val path: String) : IndexAction

    /** Removes the rows at [prefix] and below it that were indexed at or before [notAfter] (3.3). */
    data class RemovePrefix(val repository: String, val prefix: String, val notAfter: Instant) : IndexAction

    data class Reorigin(val repository: String, val directory: String, val provenance: Provenance) : IndexAction

}

/** What a rebuild needs to know about a stored row: enough to tell whether the file changed, and the checksums to keep. */
@ExperimentalIngotSearchApi
data class StoredRow(
    val sizeBytes: Long,
    val lastModified: Long,
    val sha1: String?,
    val sha256: String?,
)

/** A row as a verify run reads it: what storage and the mirror ledger are compared with. */
@ExperimentalIngotSearchApi
data class SampledRow(
    val repository: String,
    val path: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val origin: Origin,
    val sourceHost: String?,
)

/** One sample of rows and the cursor the next sample continues from, or null when the index has no rows. */
@ExperimentalIngotSearchApi
data class SamplePage(
    val rows: List<SampledRow>,
    val next: String?,
)

/** One batch of the backfill: the rows it keyed and the id of its last row, the cursor of the next batch (full text change, D6). */
@ExperimentalIngotSearchApi
data class KeyBatch(val keyed: Int, val lastId: Long)

/**
 * Thrown by [ArtifactSearchBackend.apply] when the backend has no capacity right now. The outbox releases the batch with
 * [retryAfter] and does not count the attempt, so a busy backend never turns its entries into dead letters.
 */
@ExperimentalIngotSearchApi
class BackendBusyException(message: String, val retryAfter: Duration) : RuntimeException(message)
