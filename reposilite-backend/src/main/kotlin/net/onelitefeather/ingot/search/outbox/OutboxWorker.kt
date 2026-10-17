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

package net.onelitefeather.ingot.search.outbox

import net.onelitefeather.ingot.search.ArtifactPathParser
import net.onelitefeather.ingot.search.ArtifactPathResult
import net.onelitefeather.ingot.search.IndexRow
import net.onelitefeather.ingot.search.database.SearchStateKey
import net.onelitefeather.ingot.search.database.SearchStateStore
import net.onelitefeather.ingot.search.outbox.IndexAction.Reorigin
import net.onelitefeather.ingot.search.outbox.IndexAction.RemovePath
import net.onelitefeather.ingot.search.outbox.IndexAction.RemovePrefix
import net.onelitefeather.ingot.search.outbox.IndexAction.Upsert
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** What one [OutboxWorker.tick] did. [claimed] drives the poll interval; an idle tick claims nothing. */
data class TickResult(val claimed: Int, val completed: Int, val failed: Int)

/**
 * Drains the outbox (artifact search spec, 3.3 and 3.4). A tick claims a batch, collapses entries that name the same
 * path and operation, reads storage for each of them, and applies the resulting actions in one transaction. A group
 * whose read fails is released with a backoff; the others are unaffected.
 *
 * The worker writes the heartbeat of its replica once a minute (3.7), and extends the leases it still holds every
 * [OutboxPolicy.LEASE_EXTENSION_INTERVAL]. An entry that another instance took over is dropped from the batch.
 */
internal class OutboxWorker(
    private val owner: String,
    private val outbox: OutboxStore,
    private val index: ArtifactIndexWriter,
    private val storage: IndexStorage,
    private val origins: OriginLookup,
    private val state: SearchStateStore,
    private val clock: Clock,
    private val batchSize: Int = OutboxPolicy.BATCH_SIZE,
) {

    private var lastHeartbeat: Instant? = null

    fun tick(): TickResult {
        heartbeatIfDue()
        val batch = outbox.claim(owner, batchSize)
        if (batch.isEmpty()) return TickResult(claimed = 0, completed = 0, failed = 0)

        var held = batch.map { it.id }.toSet()
        var lastExtension = clock.instant()
        val planned = mutableListOf<Planned>()
        var failed = 0

        for (group in collapse(batch)) {
            val now = clock.instant()
            if (Duration.between(lastExtension, now) >= OutboxPolicy.LEASE_EXTENSION_INTERVAL) {
                held = outbox.extend(owner, held)
                lastExtension = now
            }
            val ids = group.map { it.id }.filter { it in held }
            if (ids.isEmpty()) continue
            try {
                planned += Planned(ids, actionsFor(group.first()))
            } catch (failure: Exception) {
                ids.forEach { outbox.fail(owner, it, failure.describe()) }
                failed += ids.size
            }
        }

        val ids = planned.flatMap { it.ids }
        if (ids.isEmpty()) return TickResult(claimed = batch.size, completed = 0, failed = failed)
        return try {
            index.apply(planned.flatMap { it.actions })
            TickResult(claimed = batch.size, completed = outbox.complete(owner, ids), failed = failed)
        } catch (failure: Exception) {
            ids.forEach { outbox.fail(owner, it, failure.describe()) }
            TickResult(claimed = batch.size, completed = 0, failed = failed + ids.size)
        }
    }

    private fun heartbeatIfDue() {
        val now = clock.instant()
        val last = lastHeartbeat
        if (last != null && Duration.between(last, now) < HEARTBEAT_INTERVAL) return
        state.put(SearchStateKey.HEARTBEAT, now.toEpochMilli().toString())
        lastHeartbeat = now
    }

    private fun actionsFor(entry: OutboxEntry): List<IndexAction> = when (entry.op) {
        OutboxOp.CHANGED_PATH -> changedPath(entry.repository, entry.path)
        OutboxOp.REMOVED_PREFIX -> removedPrefix(entry.repository, entry.path)
        OutboxOp.REORIGIN_DIRECTORY -> listOf(Reorigin(entry.repository, entry.path, origins.provenance(entry.repository, entry.path)))
    }

    /** Reads the current state of [path] and decides between an upsert and a delete (3.3). */
    private fun changedPath(repository: String, path: String): List<IndexAction> {
        val parsed = ArtifactPathParser.parse(repository, path)
        if (parsed !is ArtifactPathResult.Row) return listOf(RemovePath(repository, path))

        val file = storage.file(repository, path) ?: return listOf(RemovePath(repository, path))
        val provenance = origins.provenance(repository, path.substringBeforeLast('/'))
        val row = IndexRow(
            file = parsed.file,
            provenance = provenance,
            sizeBytes = file.sizeBytes,
            lastModified = file.lastModified.toEpochMilli(),
            sha1 = checksum(storage.head(repository, "$path.sha1", CHECKSUM_HEAD_BYTES), SHA1_HEX_LENGTH),
            sha256 = checksum(storage.head(repository, "$path.sha256", CHECKSUM_HEAD_BYTES), SHA256_HEX_LENGTH),
            indexedAt = clock.millis(),
        )
        return listOf(Upsert(row))
    }

    /**
     * A removed path. When nothing is stored there any more, its rows go. Otherwise rows below it whose files are gone
     * are removed one page at a time. The cut-off is taken before the storage check, so a row written by a concurrent
     * deployment after the check survives (3.3).
     */
    private fun removedPrefix(repository: String, prefix: String): List<IndexAction> {
        val cutoff = clock.instant()
        if (!storage.exists(repository, prefix)) {
            return listOf(RemovePrefix(repository, prefix, cutoff))
        }
        val actions = mutableListOf<IndexAction>()
        var after: String? = null
        do {
            val page = index.pathsAt(repository, prefix, after, PAGE_SIZE)
            page.filterNot { storage.exists(repository, it) }.forEach { actions += RemovePath(repository, it) }
            after = page.lastOrNull()
        } while (page.size == PAGE_SIZE)
        return actions
    }

    private fun checksum(text: String?, length: Int): String? =
        text?.trim()?.split(WHITESPACE)?.firstOrNull()?.lowercase()
            ?.takeIf { it.length == length && it.all { char -> char in '0'..'9' || char in 'a'..'f' } }

    private fun Exception.describe(): String = (message ?: javaClass.simpleName)

    /** A group of entries that share their path and operation, with the actions their processing produced. */
    private class Planned(val ids: List<Long>, val actions: List<IndexAction>)

    companion object {
        val HEARTBEAT_INTERVAL: Duration = Duration.ofMinutes(1)
        const val CHECKSUM_HEAD_BYTES = 128
        const val SHA1_HEX_LENGTH = 40
        const val SHA256_HEX_LENGTH = 64
        const val PAGE_SIZE = 1000
        private val WHITESPACE = Regex("\\s+")

        /**
         * Entries for the same repository, path and operation are processed once: a deployment with its checksum sidecars
         * produces several entries for one row, and one storage read decides them all. Order of first appearance is kept.
         */
        fun collapse(entries: List<OutboxEntry>): List<List<OutboxEntry>> =
            entries.groupBy { Triple(it.repository, it.path, it.op) }.values.toList()
    }

}
