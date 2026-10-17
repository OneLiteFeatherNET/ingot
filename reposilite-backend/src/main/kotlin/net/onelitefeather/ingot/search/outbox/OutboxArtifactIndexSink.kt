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

package net.onelitefeather.ingot.search.outbox

import net.onelitefeather.ingot.search.ArtifactIndexSink
import net.onelitefeather.ingot.search.database.SearchStateKey
import net.onelitefeather.ingot.search.database.SearchStateStore

/**
 * The sink the write paths call. Each call inserts one outbox entry in its own short transaction. A failed insert is
 * logged and counted in `dropped_enqueues`, and never reaches the caller: a deployment does not fail because the index
 * is behind (artifact search spec, 3.5).
 */
internal class OutboxArtifactIndexSink(
    private val outbox: OutboxStore,
    private val state: SearchStateStore,
    private val warn: (String) -> Unit,
) : ArtifactIndexSink {

    override fun changed(repository: String, path: String) = enqueue(repository, path, OutboxOp.CHANGED_PATH)

    override fun removed(repository: String, path: String) = enqueue(repository, path, OutboxOp.REMOVED_PREFIX)

    override fun reorigin(repository: String, directory: String) = enqueue(repository, directory, OutboxOp.REORIGIN_DIRECTORY)

    private fun enqueue(repository: String, path: String, op: OutboxOp) {
        try {
            outbox.enqueue(repository, path, op)
        } catch (failure: Throwable) {
            runCatching { warn("SEARCH | Cannot queue $op for $repository/$path: ${failure.message}") }
            runCatching { state.increment(SearchStateKey.DROPPED_ENQUEUES) }
        }
    }

}
