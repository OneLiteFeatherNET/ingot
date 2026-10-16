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

import com.reposilite.plugin.api.Facade
import net.onelitefeather.ingot.search.ArtifactIndexSink
import net.onelitefeather.ingot.search.ArtifactSearchEngine
import net.onelitefeather.ingot.search.runs.IndexRunStatus

/**
 * What the rest of Ingot gets from artifact search: the sink its write paths call, the read engine the REST layer queries,
 * and the state of the index runs for the Maintenance panel. [runStatus] is [IndexRunStatus.EMPTY] and [engine] is null
 * while search is disabled.
 */
class SearchFacade internal constructor(
    val sink: ArtifactIndexSink,
    private val runStatus: () -> IndexRunStatus = { IndexRunStatus.EMPTY },
    val engine: ArtifactSearchEngine? = null,
) : Facade {

    /** The live run with its cursor, counters and lease holder, the last 20 runs, and the last full rebuild. */
    fun indexRuns(): IndexRunStatus = runStatus()

}
