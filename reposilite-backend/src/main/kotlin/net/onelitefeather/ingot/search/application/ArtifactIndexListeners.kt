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

import com.reposilite.maven.api.DeleteEvent
import com.reposilite.maven.api.DeployEvent
import com.reposilite.maven.indexChanged
import com.reposilite.maven.provenancePath
import com.reposilite.plugin.api.EventListener
import net.onelitefeather.ingot.search.ArtifactIndexSink

/**
 * Queues the file of a deployment. The event bus has no exception isolation, so a listener that throws would break the
 * deploy request; this listener reports the failure through [onFailure] and returns (artifact search spec, 3.5).
 */
internal class DeployIndexListener(
    private val sink: ArtifactIndexSink,
    private val onFailure: (Throwable) -> Unit,
) : EventListener<DeployEvent> {

    override fun onCall(event: DeployEvent) =
        guarded(onFailure) { sink.indexChanged(event.repository.name, event.gav) }

}

/**
 * Queues the removal of a file or directory. Cleanup runs emit one event per directory and are covered as well. Never
 * throws, for the same reason as [DeployIndexListener].
 */
internal class DeleteIndexListener(
    private val sink: ArtifactIndexSink,
    private val onFailure: (Throwable) -> Unit,
) : EventListener<DeleteEvent> {

    override fun onCall(event: DeleteEvent) =
        guarded(onFailure) { sink.removed(event.repository.name, event.gav.provenancePath()) }

}

private inline fun guarded(onFailure: (Throwable) -> Unit, block: () -> Unit) {
    try {
        block()
    } catch (failure: Throwable) {
        runCatching { onFailure(failure) }
    }
}
