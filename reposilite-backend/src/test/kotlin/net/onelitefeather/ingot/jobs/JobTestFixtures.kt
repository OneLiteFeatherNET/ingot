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

import com.reposilite.plugin.api.jobs.JobQuery
import com.reposilite.plugin.api.jobs.JobSource
import java.time.Instant

const val MAINTENANCE_NODE = "ingot.admin.maintenance"
const val ADMIN_NODE = "ingot.admin.*"
val BASE_TIME: Instant = Instant.parse("2026-11-01T10:00:00Z")

/** A visitor who reads every node. */
val EVERY_NODE = JobAccess { true }

fun accessTo(vararg nodes: String) = JobAccess { it in nodes }

fun run(
    source: String,
    local: String,
    startedAt: Instant,
    kind: String = "$source.run",
    state: JobState = JobState.SUCCEEDED,
): JobRun = JobRun(
    id = "$source:$local",
    source = source,
    kind = kind,
    label = "$source $local",
    trigger = JobTrigger.MANUAL,
    state = state,
    startedAt = startedAt,
)

class FakeJobSource(
    override val id: String,
    override val kinds: Set<String> = setOf("$id.run"),
    override val requiredNode: String = ADMIN_NODE,
    private val runs: List<JobRun> = emptyList(),
    private val failing: Boolean = false,
) : JobSource {

    val requestedLimits = mutableListOf<Int>()

    override fun page(query: JobQuery, before: JobCursor?, limit: Int): List<JobRun> {
        check(!failing) { "store of $id is unavailable" }
        requestedLimits += limit
        return runs
            .filter { query.kinds.isEmpty() || it.kind in query.kinds }
            .filter { before == null || JobOrder.isOlderThan(it, before) }
            .sortedWith(JobOrder.runs)
            .take(limit)
    }

    override fun detail(localId: String): JobRun? = runs.singleOrNull { it.localId == localId }
}

fun registryOf(vararg sources: JobSource): JobSourceRegistry {
    val registry = JobSourceRegistry()
    sources.forEach { registry.register(it) }
    return registry
}
