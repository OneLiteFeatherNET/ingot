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

/**
 * One connection to a bus. Each implementation keeps its own reconnect policy; the plugin sees only these two operations.
 */
interface ClusterTransport : AutoCloseable {

    /** Sends one serialised envelope. Must not block the caller, a bus outage must never delay a request. */
    fun publish(message: String)

    /** Delivers every message received from the bus to [listener], on a thread of the transport. */
    fun subscribe(listener: (String) -> Unit)

    /**
     * The state of the connection, shown in the status output. A transport that cannot lose its connection keeps the default.
     */
    fun connectionState(): ConnectionState = ConnectionState.CONNECTED
}
