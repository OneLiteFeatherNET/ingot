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

package net.onelitefeather.ingot.cluster

import com.reposilite.status.ClusterStatusSource
import com.reposilite.status.InstanceFigures
import com.reposilite.status.api.ClusterBusResponse
import com.reposilite.status.api.ClusterStatusResponse

/**
 * The cluster section of the instance status: the connection state, the time of the last message from the bus, the counts
 * and the instances. The local instance is listed from the figures passed in, never from the bus, so it is shown even while
 * the bus is down. The URL is shown with its credentials removed, and no secret is read here.
 */
internal class ClusterStatusView(
    private val instanceId: String,
    private val url: ClusterBusUrl,
    private val counters: ClusterCounters,
    private val store: RemoteInstanceStore,
    private val state: () -> ConnectionState,
) : ClusterStatusSource {

    override fun clusterStatus(local: InstanceFigures): ClusterStatusResponse =
        ClusterStatusResponse(
            bus = ClusterBusResponse(
                state = state().name.lowercase(),
                transport = url.scheme,
                url = url.redacted(),
                lastReceivedAt = counters.lastReceivedAt?.toEpochMilli(),
                published = counters.published,
                applied = counters.applied,
                ignored = counters.ignored,
            ),
            instances = listOf(local.toResponse(instanceId, stale = false)) +
                store.instances().filter { it.id != instanceId },
        )
}
