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

/**
 * The one order of the Jobs page: newest start first, then source id, then local id, descending.
 * Sources and the aggregator both use it, so a page continues where the previous one stopped.
 */
object JobOrder {

    private val cursors: Comparator<JobCursor> = compareByDescending<JobCursor> { it.startedAt.toEpochMilli() }
        .thenBy { it.source }
        .thenByDescending { it.localId }

    val runs: Comparator<JobRun> = Comparator { first, second -> cursors.compare(first.cursor, second.cursor) }

    /** True when [run] comes after [cursor] in the order, which is the rule for a run to belong to the next page. */
    fun isOlderThan(run: JobRun, cursor: JobCursor): Boolean = cursors.compare(run.cursor, cursor) > 0

}
