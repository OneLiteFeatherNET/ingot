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

import com.reposilite.plugin.api.jobs.JobPage
import com.reposilite.plugin.api.jobs.JobQuery
import com.reposilite.plugin.api.jobs.JobSource

/** Answers whether the visitor may read the runs behind a node. */
fun interface JobAccess {
    fun canRead(node: String): Boolean
}

/** The visitor reads none of the sources that are registered. The message names the area, as the maintenance endpoints do. */
class JobsForbiddenException(area: String) : RuntimeException("Only principals with the $area area can access this endpoint")

/** Merges the runs of the readable sources into one page of the Jobs page. */
class JobAggregator(private val registry: JobSourceRegistry) {

    fun page(query: JobQuery, access: JobAccess, size: Int? = null, cursor: String? = null): JobPage {
        val registered = registry.sources()
        val readable = registered.filter { access.canRead(it.requiredNode) }
        if (registered.isNotEmpty() && readable.isEmpty()) {
            throw JobsForbiddenException(MAINTENANCE_AREA)
        }

        val limit = (size ?: DEFAULT_SIZE).coerceIn(1, MAX_SIZE)
        val before = cursor?.let(JobCursorCodec::decode)
        val sourceErrors = mutableListOf<String>()
        val batches = readable
            .filter { matchesKind(it, query.kinds) }
            .mapNotNull { source ->
                try {
                    source.page(query, before, limit)
                } catch (exception: Exception) {
                    sourceErrors += source.id
                    null
                }
            }

        val items = batches.flatten().sortedWith(JobOrder.runs).take(limit)
        val nextCursor = items.lastOrNull()
            ?.takeIf { last -> batches.any { it.size >= limit } }
            ?.let { JobCursorCodec.encode(it.cursor) }

        return JobPage(
            items = items,
            nextCursor = nextCursor,
            sourceErrors = sourceErrors,
            kinds = readable.flatMapTo(sortedSetOf()) { it.kinds },
        )
    }

    /** The run with the id `<source>:<local id>`, or null when it is missing or its source is not readable. */
    fun detail(id: String, access: JobAccess): JobRun? {
        val sourceId = id.substringBefore(':')
        val localId = id.substringAfter(':', missingDelimiterValue = "")
        val source: JobSource = registry.sources()
            .singleOrNull { it.id == sourceId && access.canRead(it.requiredNode) }
            ?: return null
        return source.detail(localId)
    }

    private fun matchesKind(source: JobSource, kinds: Set<String>): Boolean =
        kinds.isEmpty() || kinds.any { it == source.id || it in source.kinds || it.startsWith("${source.id}.") }

    companion object {
        const val MAINTENANCE_AREA = "maintenance"
        const val DEFAULT_SIZE = 50
        const val MAX_SIZE = 100
    }

}
