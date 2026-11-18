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

import com.reposilite.plugin.api.Event

/**
 * A place that keeps a record of background runs and lists them on the Jobs page.
 *
 * A source returns its runs sorted by [net.onelitefeather.ingot.jobs.JobOrder], honours the query by mapping its states and
 * triggers to its own values, and never returns a run twice across pages. It throws for a store error; the aggregator then
 * leaves the source out of that page and names it in [JobPage.sourceErrors].
 */
interface JobSource {

    /** The source id, such as `index`, `cleanup`, `migration` or a plugin source. It contains neither a colon nor a pipe. */
    val id: String

    /** The kinds of run the source keeps, such as `index.verify`. */
    val kinds: Set<String>

    /** The node a visitor needs to read the runs of this source. The default for a plugin is `ingot.admin.*`. */
    val requiredNode: String

    /** The runs strictly older than [before] (all runs when it is null), at most [limit], newest first. */
    fun page(query: JobQuery, before: JobCursor?, limit: Int): List<JobRun>

    /** The run with the given local id, or null when the source has no such run. */
    fun detail(localId: String): JobRun?

}

/** The filters of the Jobs page. An empty set does not filter. A kind may be a source id or a kind of that source. */
data class JobQuery(
    val kinds: Set<String> = emptySet(),
    val states: Set<JobState> = emptySet(),
    val triggers: Set<JobTrigger> = emptySet(),
)

/** One page of the Jobs page. [nextCursor] is null when no source has more rows. */
data class JobPage(
    val items: List<JobRun>,
    val nextCursor: String?,
    val sourceErrors: List<String>,
    val kinds: Set<String>,
)

/** Hands a plugin the means to register its job sources. Closing the returned handle removes the source again. */
interface JobRegistry {

    fun register(source: JobSource): AutoCloseable

}

/** Fired once the plugins are initialised. A source registered after it is logged by the core and not listed. */
class JobRegistrationEvent(val registry: JobRegistry) : Event
