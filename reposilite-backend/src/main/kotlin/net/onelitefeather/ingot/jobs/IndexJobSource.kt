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
package net.onelitefeather.ingot.jobs

import net.onelitefeather.ingot.search.runs.RunCounters
import net.onelitefeather.ingot.search.runs.RunKind
import net.onelitefeather.ingot.search.runs.RunStatus
import net.onelitefeather.ingot.search.runs.RunStore
import net.onelitefeather.ingot.search.runs.RunSummary
import net.onelitefeather.ingot.search.runs.RunTrigger
import java.time.Duration

private const val INDEX_SOURCE = "index"
private const val KIND_REBUILD = "index.rebuild"
private const val KIND_VERIFY = "index.verify"
private const val INDEX_NODE = "ingot.admin.maintenance"

/** The search index runs, rebuild and verify, on the Jobs page. The rows stay in `search_rebuild_run`, which nothing prunes. */
internal class IndexJobSource(private val runs: RunStore) : JobSource {

    override val id: String = INDEX_SOURCE
    override val kinds: Set<String> = setOf(KIND_REBUILD, KIND_VERIFY)
    override val requiredNode: String = INDEX_NODE

    override fun page(query: JobQuery, before: JobCursor?, limit: Int): List<JobRun> {
        val runKinds = RunKind.entries.filterTo(mutableSetOf()) { kind ->
            query.kinds.isEmpty() || query.kinds.any { it == INDEX_SOURCE || it == kindName(kind) }
        }
        val statuses = statusesOf(query.states)
        val triggers = triggersOf(query.triggers)
        if (runKinds.isEmpty() || (query.states.isNotEmpty() && statuses.isEmpty())) return emptyList()
        if (query.triggers.isNotEmpty() && triggers.isEmpty()) return emptyList()
        return runs.page(
            kinds = runKinds,
            statuses = statuses,
            triggers = triggers,
            beforeMillis = before?.startedAt?.toEpochMilli(),
            beforeId = before?.localId?.toLongOrNull(),
            limit = limit,
        ).map(::toRun)
    }

    override fun detail(localId: String): JobRun? = localId.toLongOrNull()?.let { runs.get(it) }?.let(::toRun)

    private fun statusesOf(states: Set<JobState>): Set<RunStatus> = states.flatMapTo(mutableSetOf()) { state ->
        when (state) {
            JobState.RUNNING -> setOf(RunStatus.RUNNING)
            JobState.SUCCEEDED -> setOf(RunStatus.COMPLETED)
            JobState.FAILED -> setOf(RunStatus.FAILED)
            JobState.CANCELLED -> setOf(RunStatus.CANCELLED)
            else -> emptySet()
        }
    }

    private fun triggersOf(triggers: Set<JobTrigger>): Set<RunTrigger> =
        RunTrigger.entries.filterTo(mutableSetOf()) { JobStateMapping.indexTrigger(it).first in triggers }

    private fun kindName(kind: RunKind): String = when (kind) {
        RunKind.REBUILD -> KIND_REBUILD
        RunKind.VERIFY -> KIND_VERIFY
    }

    private fun toRun(row: RunSummary): JobRun {
        val (trigger, triggerDetail) = JobStateMapping.indexTrigger(row.trigger)
        return JobRun(
            id = "$INDEX_SOURCE:${row.id}",
            source = INDEX_SOURCE,
            kind = kindName(row.kind),
            label = if (row.kind == RunKind.REBUILD) "Rebuild" else "Verify",
            trigger = trigger,
            triggerDetail = triggerDetail,
            state = JobStateMapping.index(row.status),
            startedAt = row.startedAt,
            finishedAt = row.finishedAt,
            durationMs = row.finishedAt?.let { Duration.between(row.startedAt, it).toMillis() },
            counts = countsOf(row.counters),
            failures = listOfNotNull(row.lastError).map(::JobFailure),
        )
    }

    private fun countsOf(counters: RunCounters): List<JobCount> = listOf(
        JobCount("processed", "Processed", counters.processed),
        JobCount("upserted", "Upserted", counters.upserted),
        JobCount("deleted", "Deleted", counters.deleted),
        JobCount("unparsed", "Unparsed", counters.unparsed),
        JobCount("skippedTooLong", "Skipped, too long", counters.skippedTooLong),
        JobCount("errors", "Errors", counters.errors),
    )

}
