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

package net.onelitefeather.ingot.search.runs

import com.reposilite.storage.StorageEntrySource
import java.time.Duration
import java.time.Instant

/** Rebuild and verify runs share `search_rebuild_run` (artifact search spec, 2.6). */
enum class RunKind { REBUILD, VERIFY }

/** What started a run. */
enum class RunTrigger { MANUAL, SCHEDULED, STARTUP, GAP }

enum class RunStatus { RUNNING, COMPLETED, FAILED, CANCELLED }

/**
 * The counters of a run. For a rebuild they mean entries read from storage, rows written, rows deleted, files that did not
 * parse, files skipped for a too long value and repositories that could not be read. For a verify run [processed] is the
 * rows checked, [upserted] the rows fixed and [deleted] the rows deleted.
 */
data class RunCounters(
    val processed: Long = 0,
    val upserted: Long = 0,
    val deleted: Long = 0,
    val unparsed: Long = 0,
    val skippedTooLong: Long = 0,
    val errors: Long = 0,
) {

    operator fun plus(other: RunCounters) = RunCounters(
        processed = processed + other.processed,
        upserted = upserted + other.upserted,
        deleted = deleted + other.deleted,
        unparsed = unparsed + other.unparsed,
        skippedTooLong = skippedTooLong + other.skippedTooLong,
        errors = errors + other.errors,
    )

}

/**
 * One row of `search_rebuild_run`, as the maintenance panel shows it. [repositories] is null for a run over all
 * repositories. For a rebuild, [cursorRepository] and [cursorPath] are the position the next batch starts from; the
 * cursor path is opaque and may be a marker such as [RunCursor.LISTED] between two phases of a repository.
 */
data class RunSummary(
    val id: Long,
    val kind: RunKind,
    val trigger: RunTrigger,
    val status: RunStatus,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val leaseOwner: String?,
    val leaseUntil: Instant?,
    val runStartedAt: Instant,
    val repositories: List<String>?,
    val cursorRepository: String?,
    val cursorPath: String?,
    val counters: RunCounters,
    val lastError: String?,
)

/**
 * What the Maintenance panel shows of the index runs (artifact search spec, 6.3 and 8.4): the live run with its cursor,
 * counters and lease holder, the last 20 runs of either kind, and the time of the last completed full rebuild.
 */
data class IndexRunStatus(
    val activeRun: RunSummary?,
    val runs: List<RunSummary>,
    val lastFullRebuild: Instant?,
) {
    companion object {
        val EMPTY = IndexRunStatus(activeRun = null, runs = emptyList(), lastFullRebuild = null)
    }
}

/** A run that this instance holds, with the token that proves it: every write of the run is conditional on the token. */
data class RunHandle(val runId: Long, val token: String, val kind: RunKind)

/** The markers a rebuild stores in `cursor_path` besides a provider cursor. Neither can be a normalised storage path. */
object RunCursor {
    /** The listing of the repository is complete; the stale-row cleanup is next. */
    const val LISTED = "//listed"

    /** The repository is complete, including its cleanup. */
    const val DONE = "//done"
}

/** Timing of the single-run lock (artifact search spec, 3.6). */
object RunPolicy {
    /** How long the lock holds without a heartbeat. Every committed batch extends it. */
    val LEASE: Duration = Duration.ofMinutes(2)

    /** Runs the Maintenance panel and the status endpoint list. */
    const val HISTORY = 20
}

/** The storage listings of the configured repositories, by name. Nothing is cached. */
internal interface RepositoryEntries {

    /** The names of the configured repositories. */
    fun names(): List<String>

    /** The entry source of [repository], or null when no such repository is configured. */
    fun source(repository: String): StorageEntrySource?

}
