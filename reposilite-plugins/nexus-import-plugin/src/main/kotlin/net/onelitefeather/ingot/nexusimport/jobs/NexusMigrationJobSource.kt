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
package net.onelitefeather.ingot.nexusimport.jobs

import com.reposilite.plugin.api.jobs.JobQuery
import com.reposilite.plugin.api.jobs.JobSource
import net.onelitefeather.ingot.jobs.JobCursor
import net.onelitefeather.ingot.jobs.JobFailure
import net.onelitefeather.ingot.jobs.JobOrder
import net.onelitefeather.ingot.jobs.JobRun
import net.onelitefeather.ingot.jobs.JobState
import net.onelitefeather.ingot.jobs.JobTrigger
import java.time.Instant

private const val MIGRATION_SOURCE = "migration"
private const val MIGRATION_KIND = "migration.nexus"
private const val ADMIN_NODE = "ingot.admin.*"

/**
 * The nexus import jobs on the Jobs page, visible to the instance admin only. A job has no end time in the table, so its run
 * carries the start only. The table keeps every job until an operator deletes it, and the list is read whole.
 */
internal class NexusMigrationJobSource(private val store: JobStore) : JobSource {

    override val id: String = MIGRATION_SOURCE
    override val kinds: Set<String> = setOf(MIGRATION_KIND)
    override val requiredNode: String = ADMIN_NODE

    override fun page(query: JobQuery, before: JobCursor?, limit: Int): List<JobRun> =
        store.jobs()
            .map(::toRun)
            .filter { query.states.isEmpty() || it.state in query.states }
            .filter { query.triggers.isEmpty() || it.trigger in query.triggers }
            .filter { before == null || JobOrder.isOlderThan(it, before) }
            .sortedWith(JobOrder.runs)
            .take(limit)

    override fun detail(localId: String): JobRun? = localId.toLongOrNull()?.let { store.job(it) }?.let(::toRun)

    private fun toRun(row: JobRow): JobRun = JobRun(
        id = "$MIGRATION_SOURCE:${row.id}",
        source = MIGRATION_SOURCE,
        kind = MIGRATION_KIND,
        label = "Nexus import ${row.id}",
        trigger = JobTrigger.MANUAL,
        state = stateOf(row.status),
        startedAt = Instant.ofEpochMilli(row.created),
        failures = listOfNotNull(row.lastError).map(::JobFailure),
    )

    private fun stateOf(status: JobStatus): JobState = when (status) {
        JobStatus.QUEUED -> JobState.QUEUED
        JobStatus.RUNNING -> JobState.RUNNING
        JobStatus.PAUSED -> JobState.PAUSED
        JobStatus.COMPLETED -> JobState.SUCCEEDED
        JobStatus.FAILED -> JobState.FAILED
        JobStatus.CANCELLED -> JobState.CANCELLED
    }

}
