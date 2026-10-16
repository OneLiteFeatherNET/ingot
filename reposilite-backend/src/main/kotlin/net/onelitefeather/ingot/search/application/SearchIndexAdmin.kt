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

package net.onelitefeather.ingot.search.application

import com.fasterxml.jackson.databind.ObjectMapper
import net.onelitefeather.ingot.search.database.IndexRowStore
import net.onelitefeather.ingot.search.database.SearchStateKey
import net.onelitefeather.ingot.search.database.SearchStateStore
import net.onelitefeather.ingot.search.outbox.OutboxStore
import net.onelitefeather.ingot.search.outbox.OutboxWorker
import net.onelitefeather.ingot.search.outbox.TickResult
import net.onelitefeather.ingot.search.rebuild.RebuildService
import net.onelitefeather.ingot.search.runs.RepositoryEntries
import net.onelitefeather.ingot.search.runs.RunStore
import net.onelitefeather.ingot.search.runs.RunTrigger
import net.onelitefeather.ingot.search.verify.VerifyService

/**
 * What the index administration endpoints start and read (artifact search spec, 6.3). Starting a run takes its lock
 * synchronously, so a refusal is known in the request; the run itself goes to [dispatch], which the plugin points at the
 * IO pool. A test points it elsewhere to hold a run live or to run it on the calling thread.
 */
internal class SearchIndexAdmin(
    private val runs: RunStore,
    private val rebuild: RebuildService,
    private val verify: VerifyService,
    private val outbox: OutboxStore,
    private val worker: OutboxWorker,
    private val rows: IndexRowStore,
    private val state: SearchStateStore,
    private val repositories: RepositoryEntries,
    dispatch: (() -> Unit) -> Unit,
) {

    /** Runs a started run. Replaced in tests, which decide when a run executes. */
    var dispatch: (() -> Unit) -> Unit = dispatch

    /** Starts a rebuild of [repositories], or of every repository for null, unless a rebuild or verify run is live. */
    fun startRebuild(repositories: List<String>?): RunStart {
        val handle = rebuild.start(RunTrigger.MANUAL, repositories) ?: return RunStart.Busy
        dispatch { rebuild.execute(handle) }
        return RunStart.Started(handle.runId)
    }

    /** Starts a verify run unless a rebuild or verify run is live. */
    fun startVerify(): RunStart {
        val handle = verify.start(RunTrigger.MANUAL) ?: return RunStart.Busy
        dispatch { verify.execute(handle) }
        return RunStart.Started(handle.runId)
    }

    /** Cancels a live run. The instance that runs it notices at its next batch. */
    fun cancel(runId: Long): CancelOutcome = when {
        runs.get(runId) == null -> CancelOutcome.NOT_FOUND
        runs.cancel(runId) -> CancelOutcome.CANCELLED
        else -> CancelOutcome.FINISHED
    }

    /** The counts and the last verify report that the index status adds to the engine status. */
    fun details(): IndexDetails = IndexDetails(
        rows = rows.count(),
        outboxPending = outbox.pending(),
        repositories = repositories.names().sorted(),
        lastVerify = state.get(SearchStateKey.VERIFY_LAST_REPORT)?.let(::parseVerifyReport),
    )

    /** One tick of this replica's outbox worker, the call the poller makes on its own schedule. */
    fun drainOutbox(): TickResult = worker.tick()

    private fun parseVerifyReport(json: String): LastVerifyReport? = runCatching {
        val node = MAPPER.readTree(json)
        LastVerifyReport(
            runId = node.get("runId").asLong(),
            at = node.get("at").asLong(),
            sampled = node.get("sampled").asInt(),
            missing = node.get("missing").asInt(),
            changed = node.get("changed").asInt(),
            reorigined = node.get("reorigined").asInt(),
            removedRepositoryRows = node.get("removedRepositoryRows").asLong(),
            drift = node.get("drift").asDouble(),
        )
    }.getOrNull()

    private companion object {
        val MAPPER = ObjectMapper()
    }

}

/** The result of asking for a run: started with its id, or refused because a run of either kind is live. */
internal sealed interface RunStart {
    data class Started(val runId: Long) : RunStart
    object Busy : RunStart
}

internal enum class CancelOutcome { CANCELLED, FINISHED, NOT_FOUND }

/** The index counts and the last verify report, read per request. */
internal data class IndexDetails(
    val rows: Long,
    val outboxPending: Long,
    val repositories: List<String>,
    val lastVerify: LastVerifyReport?,
)

/** The last completed verify run (3.8). [at] is the time the report was written, in epoch milliseconds as stored. */
internal data class LastVerifyReport(
    val runId: Long,
    val at: Long,
    val sampled: Int,
    val missing: Int,
    val changed: Int,
    val reorigined: Int,
    val removedRepositoryRows: Long,
    val drift: Double,
)
