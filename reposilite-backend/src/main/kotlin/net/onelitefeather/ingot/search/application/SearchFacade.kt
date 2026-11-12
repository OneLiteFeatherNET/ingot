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
import net.onelitefeather.ingot.repository.index.ArtifactIndexSink
import net.onelitefeather.ingot.repository.index.ArtifactIndexSinkProvider
import net.onelitefeather.ingot.search.ArtifactSearchEngine
import net.onelitefeather.ingot.search.backend.SearchBackendState
import net.onelitefeather.ingot.search.runs.IndexRunStatus

/**
 * What the rest of Ingot gets from artifact search: the sink its write paths call, the read engine the REST layer queries,
 * and the state of the index runs for the Maintenance panel. [engine] and [admin] are null until the backend of this replica
 * is installed; [backend] tells the routes whether that backend is available right now (search backend registration, D3).
 */
class SearchFacade internal constructor(
    override val sink: ArtifactIndexSink,
    private val runStatus: () -> IndexRunStatus = { IndexRunStatus.EMPTY },
    /** False while `searchEnabled` is off: every route answers 404, as if the feature did not exist. */
    internal val enabled: Boolean = false,
    /** The engine and the administration of the installed backend, or null when no factory answers for the configured id. */
    private val live: () -> LiveSearchBackend? = { null },
    /** The state of the backend of this replica, read per request. */
    internal val backend: () -> BackendStatus = { BackendStatus.NOT_CONFIGURED },
) : Facade, ArtifactIndexSinkProvider {

    val engine: ArtifactSearchEngine?
        get() = live()?.engine

    /** The index administration of the Maintenance panel and the REST layer; null while search is disabled. */
    internal val admin: SearchIndexAdmin?
        get() = live()?.admin

    /** The live run with its cursor, counters and lease holder, the last 20 runs, and the last full rebuild. */
    fun indexRuns(): IndexRunStatus = runStatus()

    internal fun liveBackend(): LiveSearchBackend? = live()

}

/** The read engine and the administration of an installed backend. Both exist as soon as the backend is created. */
internal class LiveSearchBackend(
    val engine: ArtifactSearchEngine,
    val admin: SearchIndexAdmin,
)

/**
 * The state of the backend of this replica, as the routes and the Maintenance panel show it. [health] is `UP`, `DOWN` or
 * `UNKNOWN`; [healthDetail] is the message of a down backend, and empty otherwise.
 */
internal data class BackendStatus(
    val id: String,
    val state: SearchBackendState,
    val detail: String,
    val health: String,
    val healthDetail: String,
    val accessMismatches: Long,
) {

    val available: Boolean
        get() = state == SearchBackendState.AVAILABLE

    companion object {
        /** The state while search is disabled: no backend is configured. */
        val NOT_CONFIGURED = BackendStatus("", SearchBackendState.NOT_INSTALLED, "search is disabled", "UNKNOWN", "", 0)

        /** The state of a backend between the post-initialise event and its first tick. */
        fun starting(id: String) = BackendStatus(id, SearchBackendState.OPEN_FAILED, "the server is starting", "UNKNOWN", "", 0)
    }

}
