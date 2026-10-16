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

package net.onelitefeather.ingot.search.verify

import com.fasterxml.jackson.databind.ObjectMapper
import com.reposilite.maven.MirrorCacheLedger
import com.reposilite.storage.StorageEntry
import com.reposilite.storage.StorageEntrySource
import com.reposilite.storage.api.Location
import net.onelitefeather.ingot.search.Origin
import net.onelitefeather.ingot.search.Provenance
import net.onelitefeather.ingot.search.database.IndexRowStore
import net.onelitefeather.ingot.search.database.SampledRow
import net.onelitefeather.ingot.search.database.SearchStateKey
import net.onelitefeather.ingot.search.database.SearchStateStore
import net.onelitefeather.ingot.search.outbox.ArtifactIndexWriter
import net.onelitefeather.ingot.search.outbox.IndexAction
import net.onelitefeather.ingot.search.outbox.OutboxOp
import net.onelitefeather.ingot.search.outbox.OutboxStore
import net.onelitefeather.ingot.search.runs.RepositoryEntries
import net.onelitefeather.ingot.search.runs.RunCounters
import net.onelitefeather.ingot.search.runs.RunHandle
import net.onelitefeather.ingot.search.runs.RunKind
import net.onelitefeather.ingot.search.runs.RunStatus
import net.onelitefeather.ingot.search.runs.RunStore
import net.onelitefeather.ingot.search.runs.RunSummary
import net.onelitefeather.ingot.search.runs.RunTrigger
import java.time.Clock
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/** One row of a drift report: the row, and in words how it differs from storage. */
data class DriftEntry(val repository: String, val path: String, val detail: String)

/**
 * What a verify run found (artifact search spec, 3.8).
 *
 * - [missing]: the file is gone from storage, the row was deleted
 * - [changed]: size or modification time differ, a `CHANGED_PATH` entry was queued and a worker repairs the row
 * - [reorigined]: origin or upstream host differ from the mirror ledger, the rows of that directory were fixed
 * - [removedRepositoryRows]: rows of repositories that no longer exist, deleted
 *
 * Files in storage that have no row are not found: only a rebuild finds those.
 */
data class DriftReport(
    val sampled: Int,
    val missing: List<DriftEntry>,
    val changed: List<DriftEntry>,
    val reorigined: List<DriftEntry>,
    val removedRepositoryRows: Long,
) {

    /** (missing + changed) / sampled, or 0 for an empty sample. */
    val driftRatio: Double = if (sampled == 0) 0.0 else (missing.size + changed.size).toDouble() / sampled

    /** A drift above 5 percent sets the index state `DEGRADED` and recommends a rebuild. */
    val degraded: Boolean = driftRatio > DEGRADED_ABOVE

    companion object {
        const val DEGRADED_ABOVE = 0.05
    }

}

/** A finished verify run: its stored row, and the report (null when the run was cancelled or taken over). */
data class VerifyOutcome(val run: RunSummary, val report: DriftReport?)

/**
 * Checks a sample of index rows against storage and repairs what it finds (artifact search spec, 3.8).
 *
 * The sample is the rows with consecutive ids from a random id, [sampleSize] of them. Each row is looked up in the listing
 * of its directory, which the entry source gives with size and modification time, so no `HEAD` per file is needed. The run
 * holds the `verify.active` lock, which refuses to start while a rebuild is live. Rows of repositories that no longer exist
 * are deleted first.
 *
 * Run counters: [RunCounters.processed] rows checked, [RunCounters.upserted] rows fixed or queued for repair,
 * [RunCounters.deleted] rows deleted, [RunCounters.errors] repositories that could not be listed (their rows are left alone).
 *
 * @param random the source of the sample start; a test passes a fixed one
 */
internal class VerifyService(
    private val owner: String,
    private val runs: RunStore,
    private val rows: IndexRowStore,
    private val index: ArtifactIndexWriter,
    private val outbox: OutboxStore,
    private val repositories: RepositoryEntries,
    private val ledger: MirrorCacheLedger,
    private val state: SearchStateStore,
    private val clock: Clock,
    private val random: Random,
    private val sampleSize: () -> Int,
    private val pageSize: Int = PAGE_SIZE,
) {

    private val tokens = AtomicLong()

    /** Takes the verify lock and creates the run. Null when a rebuild or another verify run holds its lease. */
    fun start(trigger: RunTrigger): RunHandle? {
        val token = "${owner.take(TOKEN_OWNER_LENGTH)}#${tokens.incrementAndGet()}"
        val id = runs.start(RunKind.VERIFY, trigger, repositories = null, token = token) ?: return null
        return RunHandle(id, token, RunKind.VERIFY)
    }

    /** Starts a verify run and runs it on this thread. Null when the lock is taken. */
    fun verify(trigger: RunTrigger): VerifyOutcome? = start(trigger)?.let { execute(it) }

    fun execute(handle: RunHandle): VerifyOutcome {
        var counters = RunCounters()
        return try {
            val removedRows = deleteRemovedRepositories(handle)
            val sample = drawSample()
            val checked = check(handle, sample)
            val report = DriftReport(sample.size, checked.missing, checked.changed, checked.reorigined, removedRows)
            repair(report, checked)

            counters = RunCounters(
                processed = sample.size.toLong(),
                upserted = (report.changed.size + report.reorigined.size).toLong(),
                deleted = report.missing.size + removedRows,
                errors = checked.unreadable.size.toLong(),
            )
            val error = checked.unreadable.takeIf { it.isNotEmpty() }?.let { "could not list ${it.sorted().joinToString()}" }
            runs.finish(handle, if (error == null) RunStatus.COMPLETED else RunStatus.FAILED, counters, error)
            state.put(SearchStateKey.VERIFY_LAST_REPORT, summary(handle.runId, report))
            VerifyOutcome(checkNotNull(runs.get(handle.runId)), report)
        } catch (interrupted: RunInterrupted) {
            VerifyOutcome(checkNotNull(runs.get(handle.runId)), report = null)
        } catch (failure: Exception) {
            runs.finish(handle, RunStatus.FAILED, counters, "verify failed: ${failure.message ?: failure.javaClass.simpleName}")
            VerifyOutcome(checkNotNull(runs.get(handle.runId)), report = null)
        }
    }

    /** Deletes the rows of repositories that are no longer configured, and returns how many. */
    private fun deleteRemovedRepositories(handle: RunHandle): Long {
        val configured = repositories.names().toSet()
        var deleted = 0L
        for (name in rows.repositories().filter { it !in configured }) {
            do {
                val batch = rows.deleteRepository(name, DELETE_BATCH)
                deleted += batch
                heartbeat(handle, RunCounters(deleted = deleted))
            } while (batch > 0)
        }
        return deleted
    }

    private fun drawSample(): List<SampledRow> {
        val range = rows.idRange() ?: return emptyList()
        val start = random.nextLong(range.first, range.last + 1)
        return rows.sample(start, sampleSize().coerceAtLeast(1))
    }

    private fun check(handle: RunHandle, sample: List<SampledRow>): Checked {
        val checked = Checked()
        val listings = mutableMapOf<Pair<String, String>, Map<String, StorageEntry>?>()
        val provenance = mutableMapOf<Pair<String, String>, Provenance>()

        for (row in sample) {
            if (row.repository in checked.unreadable) continue
            val directory = row.path.substringBeforeLast('/')
            val key = row.repository to directory
            val listing = listings.getOrPut(key) {
                heartbeat(handle, RunCounters(processed = checked.rowsSeen))
                list(row.repository, directory)
            }
            if (listing == null) {
                checked.unreadable += row.repository
                continue
            }
            checked.rowsSeen++

            val entry = listing[row.path]
            when {
                entry == null -> checked.missing += DriftEntry(row.repository, row.path, "missing from storage")
                differs(row, entry) -> checked.changed += DriftEntry(row.repository, row.path, describe(row, entry))
                else -> {
                    val expected = provenance.getOrPut(key) { provenanceOf(row.repository, directory) }
                    if (row.origin != expected.origin || row.sourceHost != expected.sourceHost) {
                        checked.reorigined += DriftEntry(row.repository, row.path, "origin ${row.origin} -> ${expected.origin}")
                        checked.reoriginDirectories[key] = expected
                    }
                }
            }
        }
        return checked
    }

    /** The files below [directory] by path, or null when the repository cannot be listed. */
    private fun list(repository: String, directory: String): Map<String, StorageEntry>? {
        val source: StorageEntrySource = repositories.source(repository) ?: return null
        val location = runCatching { Location.of(directory) }.getOrNull() ?: return null
        val files = mutableMapOf<String, StorageEntry>()
        var after: String? = null
        do {
            val result = try {
                source.listEntries(location, after, pageSize)
            } catch (failure: Exception) {
                return null
            }
            if (result.isErr) return null
            val page = result.get()
            page.entries.forEach { files[it.path.toString()] = it }
            after = page.next
        } while (after != null)
        return files
    }

    /** Size differs, or the modification time does when storage reports one (the epoch means it does not). */
    private fun differs(row: SampledRow, entry: StorageEntry): Boolean =
        row.sizeBytes != entry.size || (entry.lastModified != Instant.EPOCH && row.lastModified != entry.lastModified.toEpochMilli())

    private fun describe(row: SampledRow, entry: StorageEntry): String =
        if (row.sizeBytes != entry.size) "size ${row.sizeBytes} -> ${entry.size}" else "modification time ${row.lastModified} -> ${entry.lastModified.toEpochMilli()}"

    private fun provenanceOf(repository: String, directory: String): Provenance =
        if (ledger.contains(repository, directory)) Provenance(Origin.MIRROR, ledger.sourceHost(repository, directory)) else Provenance.HOSTED

    private fun repair(report: DriftReport, checked: Checked) {
        val actions = report.missing.map { IndexAction.RemovePath(it.repository, it.path) } +
            checked.reoriginDirectories.map { (key, provenance) -> IndexAction.Reorigin(key.first, key.second, provenance) }
        index.apply(actions)
        report.changed.forEach { outbox.enqueue(it.repository, it.path, OutboxOp.CHANGED_PATH) }
    }

    private fun heartbeat(handle: RunHandle, counters: RunCounters) {
        if (!runs.progress(handle, cursorRepository = null, cursorPath = null, counters = counters, lastError = null)) throw RunInterrupted()
    }

    private fun summary(runId: Long, report: DriftReport): String = MAPPER.writeValueAsString(
        linkedMapOf(
            "runId" to runId,
            "at" to clock.millis(),
            "sampled" to report.sampled,
            "missing" to report.missing.size,
            "changed" to report.changed.size,
            "reorigined" to report.reorigined.size,
            "removedRepositoryRows" to report.removedRepositoryRows,
            "drift" to report.driftRatio,
        )
    )

    /** What the check found, before it is applied. */
    private class Checked {
        var rowsSeen = 0L
        val missing = mutableListOf<DriftEntry>()
        val changed = mutableListOf<DriftEntry>()
        val reorigined = mutableListOf<DriftEntry>()
        val unreadable = mutableSetOf<String>()
        val reoriginDirectories = linkedMapOf<Pair<String, String>, Provenance>()
    }

    /** The run was cancelled or taken over; it stops without writing. */
    private class RunInterrupted : RuntimeException()

    private companion object {
        const val PAGE_SIZE = 500
        const val DELETE_BATCH = 1000
        const val TOKEN_OWNER_LENGTH = 50
        val MAPPER = ObjectMapper()
    }

}
