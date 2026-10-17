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

package net.onelitefeather.ingot.search.rebuild

import com.reposilite.maven.MirrorCacheLedger
import com.reposilite.storage.StorageEntry
import com.reposilite.storage.StorageEntrySource
import com.reposilite.storage.StorageEntryPage
import com.reposilite.storage.api.Location
import net.onelitefeather.ingot.search.ArtifactFileRecord
import net.onelitefeather.ingot.search.ArtifactPathParser
import net.onelitefeather.ingot.search.ArtifactPathResult
import net.onelitefeather.ingot.search.IndexRow
import net.onelitefeather.ingot.search.Origin
import net.onelitefeather.ingot.search.Provenance
import net.onelitefeather.ingot.search.SearchColumn
import net.onelitefeather.ingot.search.database.IndexRowStore
import net.onelitefeather.ingot.search.database.SearchStateKey
import net.onelitefeather.ingot.search.database.SearchStateStore
import net.onelitefeather.ingot.search.database.StoredRow
import net.onelitefeather.ingot.search.outbox.ArtifactIndexWriter
import net.onelitefeather.ingot.search.outbox.IndexAction
import net.onelitefeather.ingot.search.outbox.OutboxStore
import net.onelitefeather.ingot.search.runs.IndexRunStatus
import net.onelitefeather.ingot.search.runs.RepositoryEntries
import net.onelitefeather.ingot.search.runs.RunCounters
import net.onelitefeather.ingot.search.runs.RunCursor
import net.onelitefeather.ingot.search.runs.RunHandle
import net.onelitefeather.ingot.search.runs.RunKind
import net.onelitefeather.ingot.search.runs.RunStatus
import net.onelitefeather.ingot.search.runs.RunStore
import net.onelitefeather.ingot.search.runs.RunSummary
import net.onelitefeather.ingot.search.runs.RunTrigger
import java.time.Clock
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

/**
 * Rebuilds the index from storage, one repository after another, in name order (artifact search spec, 3.6).
 *
 * A run is a row of `search_rebuild_run` and holds the single-run lock `rebuild.active`. Each page of the entry source is
 * one transaction: it writes the rows, commits the cursor and the counters, and extends the lease. A run that dies is
 * continued by [resume] from the committed cursor, on any instance, once its lease has expired.
 *
 * Per repository: list every page, then delete the rows that no page re-indexed (indexed before the run started), which are
 * the files that are gone. A repository that cannot be read counts as an error, keeps its rows, and the run goes on with
 * the next one and ends `FAILED`. A full run also deletes the rows of repositories that no longer exist.
 *
 * Paths with a pending outbox entry are not written: the worker will apply that entry with fresher information. Their
 * existing rows are marked as seen, so the cleanup does not take them for stale.
 *
 * @param pageSize entries per page and per transaction (500 in production)
 */
internal class RebuildService(
    private val owner: String,
    private val runs: RunStore,
    private val rows: IndexRowStore,
    private val index: ArtifactIndexWriter,
    private val outbox: OutboxStore,
    private val repositories: RepositoryEntries,
    private val ledger: MirrorCacheLedger,
    private val state: SearchStateStore,
    private val clock: Clock,
    private val pageSize: Int = PAGE_SIZE,
) {

    private val tokens = AtomicLong()

    /** Takes the lock and creates the run. Returns null when a rebuild or verify run holds its lease. */
    fun start(trigger: RunTrigger, repositories: List<String>? = null): RunHandle? {
        val token = nextToken()
        val id = runs.start(RunKind.REBUILD, trigger, repositories?.distinct(), token) ?: return null
        return RunHandle(id, token, RunKind.REBUILD)
    }

    /** Takes over a rebuild whose lease expired, or returns null when there is none. */
    fun takeOver(): RunHandle? {
        val token = nextToken()
        val run = runs.takeOver(RunKind.REBUILD, token) ?: return null
        return RunHandle(run.id, token, RunKind.REBUILD)
    }

    /** Starts a rebuild and runs it to its end on this thread. Null when the lock is taken. */
    fun rebuild(trigger: RunTrigger, repositories: List<String>? = null): RunSummary? =
        start(trigger, repositories)?.let { execute(it) }

    /** Takes over a dead rebuild and runs it to its end on this thread. Null when there is none to take over. */
    fun resume(): RunSummary? = takeOver()?.let { execute(it) }

    /** Cancels a live run. The instance that runs it stops after the batch in flight. */
    fun cancel(runId: Long): Boolean = runs.cancel(runId)

    /** The live run with its cursor, counters and lease holder, the last runs, and the last full rebuild. */
    fun status(): IndexRunStatus = runs.status()

    /** The trigger of the rebuild the startup rules call for, or null (artifact search spec, 3.7). */
    fun startupTrigger(gapRebuild: Boolean): RunTrigger? = StartupRules.decide(
        rowCount = rows.count(),
        lastFullRebuild = state.get(SearchStateKey.LAST_FULL_REBUILD)?.toLongOrNull()?.let { Instant.ofEpochMilli(it) },
        heartbeat = state.get(SearchStateKey.HEARTBEAT)?.toLongOrNull()?.let { Instant.ofEpochMilli(it) },
        gapRebuild = gapRebuild,
        now = clock.instant(),
    )

    /**
     * Runs the run [handle] holds, from its committed cursor to its end, and returns its final state. Returns early with the
     * stored state when the run was cancelled or taken over. A crash of the process (an [Error]) leaves the run `RUNNING`
     * for the next instance to resume.
     */
    fun execute(handle: RunHandle): RunSummary {
        val start = checkNotNull(runs.get(handle.runId)) { "run ${handle.runId} does not exist" }
        val progress = Progress(start)
        return try {
            run(handle, start, progress)
        } catch (interrupted: RunInterrupted) {
            checkNotNull(runs.get(handle.runId))
        } catch (failure: Exception) {
            progress.error("rebuild failed: ${failure.message ?: failure.javaClass.simpleName}")
            runs.finish(handle, RunStatus.FAILED, progress.counters, progress.lastError)
            checkNotNull(runs.get(handle.runId))
        }
    }

    private fun run(handle: RunHandle, start: RunSummary, progress: Progress): RunSummary {
        val configured = repositories.names().toSortedSet()
        val full = start.repositories == null
        val targets = (start.repositories ?: configured.toList()).filter { it in configured }.distinct().sorted()

        for (name in targets) {
            if (progress.cursorRepository != null && name < progress.cursorRepository!!) continue
            val resumeAt = if (name == progress.cursorRepository) progress.cursorPath else null
            if (resumeAt == RunCursor.DONE) continue
            rebuildRepository(handle, start, progress, name, resumeAt)
        }
        if (full) deleteRemovedRepositories(handle, progress, configured)

        val status = if (progress.counters.errors > 0) RunStatus.FAILED else RunStatus.COMPLETED
        runs.finish(handle, status, progress.counters, progress.lastError)
        if (status == RunStatus.COMPLETED && full) {
            state.put(SearchStateKey.LAST_FULL_REBUILD, clock.millis().toString())
            state.put(SearchStateKey.DROPPED_ENQUEUES, "0")
        }
        return checkNotNull(runs.get(handle.runId))
    }

    private fun rebuildRepository(handle: RunHandle, start: RunSummary, progress: Progress, name: String, resumeAt: String?) {
        try {
            require(name.length <= SearchColumn.REPOSITORY.limit) { "the repository name is longer than ${SearchColumn.REPOSITORY.limit} characters" }
            val source = checkNotNull(repositories.source(name)) { "repository $name is not configured" }
            val directories = ledger.sourceHosts(name)
            var after = resumeAt?.takeUnless { it == RunCursor.LISTED }

            if (resumeAt != RunCursor.LISTED) {
                do {
                    val page = list(source, name, after)
                    writePage(handle, progress, name, page, directories)
                    after = page.next
                } while (after != null)
            }
            deleteStale(handle, start, progress, name)
        } catch (failure: RepositoryUnreadable) {
            progress.error("$name: ${failure.message}")
            progress.counters += RunCounters(errors = 1)
        } catch (failure: IllegalArgumentException) {
            progress.error("$name: ${failure.message}")
            progress.counters += RunCounters(errors = 1)
        } catch (failure: IllegalStateException) {
            progress.error("$name: ${failure.message}")
            progress.counters += RunCounters(errors = 1)
        }
        commit(handle, progress, name, RunCursor.DONE)
    }

    private fun list(source: StorageEntrySource, name: String, after: String?): StorageEntryPage {
        val result = try {
            source.listEntries(Location.empty(), after, pageSize)
        } catch (failure: Exception) {
            throw RepositoryUnreadable(failure.message ?: failure.javaClass.simpleName)
        }
        if (result.isErr) throw RepositoryUnreadable(result.error.message)
        val page = result.get()
        val next = page.next
        if (next != null && next.length > SearchColumn.PATH.limit) {
            throw RepositoryUnreadable("the listing cursor is longer than ${SearchColumn.PATH.limit} characters")
        }
        return page
    }

    /** Writes one page and commits the cursor, the counters and the lease with it. Rolls back when the run is no longer ours. */
    private fun writePage(handle: RunHandle, progress: Progress, name: String, page: StorageEntryPage, directories: Map<String, String?>) {
        val parsed = mutableListOf<Pair<ArtifactFileRecord, StorageEntry>>()
        var unparsed = 0L
        var tooLong = 0L
        for (entry in page.entries) {
            when (val result = ArtifactPathParser.parse(name, entry.path.toString())) {
                is ArtifactPathResult.Row -> parsed += result.file to entry
                is ArtifactPathResult.Unparsed -> unparsed++
                is ArtifactPathResult.TooLong -> tooLong++
                is ArtifactPathResult.Ignored -> Unit
            }
        }

        val now = clock.millis()
        val cursorPath = page.next ?: RunCursor.LISTED
        var upserted = 0
        runs.atomically {
            val pending = outbox.pendingPaths(name, parsed.map { it.first.path })
            val (held, writable) = parsed.partition { it.first.path in pending }
            rows.touch(name, held.map { it.first.path }, now)

            val existing = rows.existing(name, writable.map { it.first.path })
            val actions = writable.map { (file, entry) -> IndexAction.Upsert(rowOf(file, entry, existing[file.path], directories, now)) }
            index.apply(actions)
            upserted = actions.size

            val counters = progress.counters + RunCounters(processed = page.entries.size.toLong(), upserted = upserted.toLong(), unparsed = unparsed, skippedTooLong = tooLong)
            if (!runs.progress(handle, name, cursorPath, counters, progress.lastError)) throw RunInterrupted()
            progress.counters = counters
        }
        progress.cursorRepository = name
        progress.cursorPath = cursorPath
    }

    private fun rowOf(
        file: ArtifactFileRecord,
        entry: StorageEntry,
        existing: StoredRow?,
        directories: Map<String, String?>,
        now: Long,
    ): IndexRow {
        val directory = file.path.substringBeforeLast('/')
        val provenance = if (directories.containsKey(directory)) Provenance(Origin.MIRROR, directories[directory]) else Provenance.HOSTED
        val lastModified = entry.lastModified.toEpochMilli()
        val unchanged = existing != null && existing.sizeBytes == entry.size && existing.lastModified == lastModified
        return IndexRow(
            file = file,
            provenance = provenance,
            sizeBytes = entry.size,
            lastModified = lastModified,
            sha1 = if (unchanged) existing?.sha1 else null,
            sha256 = if (unchanged) existing?.sha256 else null,
            indexedAt = now,
        )
    }

    /** Deletes the rows of [name] that no page of this run re-indexed: their files are gone. */
    private fun deleteStale(handle: RunHandle, start: RunSummary, progress: Progress, name: String) {
        do {
            val deleted = rows.deleteStale(name, start.runStartedAt.toEpochMilli(), DELETE_BATCH)
            progress.counters += RunCounters(deleted = deleted.toLong())
            commit(handle, progress, name, RunCursor.LISTED)
        } while (deleted > 0)
    }

    private fun deleteRemovedRepositories(handle: RunHandle, progress: Progress, configured: Set<String>) {
        for (name in rows.repositories().filter { it !in configured }) {
            do {
                val deleted = rows.deleteRepository(name, DELETE_BATCH)
                progress.counters += RunCounters(deleted = deleted.toLong())
                commit(handle, progress, progress.cursorRepository, progress.cursorPath)
            } while (deleted > 0)
        }
    }

    private fun commit(handle: RunHandle, progress: Progress, repository: String?, cursorPath: String?) {
        if (!runs.progress(handle, repository, cursorPath, progress.counters, progress.lastError)) throw RunInterrupted()
        progress.cursorRepository = repository
        progress.cursorPath = cursorPath
    }

    private fun nextToken(): String = "${owner.take(TOKEN_OWNER_LENGTH)}#${tokens.incrementAndGet()}"

    /** What a run has committed so far, kept in memory between batches. */
    private class Progress(start: RunSummary) {
        var counters: RunCounters = start.counters
        var lastError: String? = start.lastError
        var cursorRepository: String? = start.cursorRepository
        var cursorPath: String? = start.cursorPath

        fun error(message: String) {
            lastError = message
        }
    }

    /** The run was cancelled or taken over: its writes are rolled back and the run stops quietly. */
    private class RunInterrupted : RuntimeException()

    /** The storage of a repository could not be listed. */
    private class RepositoryUnreadable(message: String) : RuntimeException(message)

    private companion object {
        const val PAGE_SIZE = 500
        const val DELETE_BATCH = 1000
        const val TOKEN_OWNER_LENGTH = 50
    }

}
