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

import com.reposilite.cleanup.CleanupRun
import com.reposilite.cleanup.CleanupService
import com.reposilite.plugin.api.jobs.JobQuery
import com.reposilite.plugin.api.jobs.JobSource
import java.time.Duration

private const val CLEANUP_SOURCE = "cleanup"
private const val CLEANUP_KIND = "cleanup.policy"
private const val CLEANUP_NODE = "ingot.admin.maintenance"

/**
 * The cleanup runs on the Jobs page. The cleanup service keeps the newest 20 runs of each policy, so the list is bounded and
 * is read whole and paged in memory with the one order of the Jobs page.
 */
internal class CleanupJobSource(private val cleanup: CleanupService) : JobSource {

    override val id: String = CLEANUP_SOURCE
    override val kinds: Set<String> = setOf(CLEANUP_KIND)
    override val requiredNode: String = CLEANUP_NODE

    override fun page(query: JobQuery, before: JobCursor?, limit: Int): List<JobRun> =
        cleanup.runs(null)
            .map(::toRun)
            .filter { query.states.isEmpty() || it.state in query.states }
            .filter { query.triggers.isEmpty() || it.trigger in query.triggers }
            .filter { before == null || JobOrder.isOlderThan(it, before) }
            .sortedWith(JobOrder.runs)
            .take(limit)

    override fun detail(localId: String): JobRun? = cleanup.runs(null).firstOrNull { it.id == localId }?.let(::toRun)

    private fun toRun(run: CleanupRun): JobRun = JobRun(
        id = "$CLEANUP_SOURCE:${run.id}",
        source = CLEANUP_SOURCE,
        kind = CLEANUP_KIND,
        label = "Cleanup: ${run.policyId}",
        trigger = when (run.trigger.name) {
            "SCHEDULED" -> JobTrigger.SCHEDULED
            else -> JobTrigger.MANUAL
        },
        state = JobStateMapping.cleanup(run.status),
        startedAt = run.startedAt,
        finishedAt = run.finishedAt,
        durationMs = Duration.between(run.startedAt, run.finishedAt).toMillis(),
        counts = listOf(
            JobCount("deletedCount", "Deleted versions", run.deletedCount.toLong()),
            JobCount("deletedBytes", "Deleted bytes", run.deletedBytes),
        ),
        failures = run.failures.map(::JobFailure),
    )

}
