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

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * A bus that lives in the test process. Like a broker it delivers every message to every subscriber, the publisher included,
 * on its own thread. One hub per test, so no state is shared between tests.
 */
internal class InMemoryClusterHub : AutoCloseable {

    private val subscribers = CopyOnWriteArrayList<(String) -> Unit>()
    private val publishes = ConcurrentHashMap<String, AtomicInteger>()
    private val deliveries: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "in-memory-bus").apply { isDaemon = true } }

    fun factory(): ClusterTransportFactory =
        object : ClusterTransportFactory {
            override fun open(url: ClusterBusUrl, settings: ClusterSettings, cluster: String): ClusterTransport =
                InMemoryClusterTransport(this@InMemoryClusterHub, settings.instanceId)
        }

    /** How many messages the instance with [instanceId] has published on this bus. */
    fun publishedBy(instanceId: String): Int =
        publishes[instanceId]?.get() ?: 0

    internal fun publish(instanceId: String, message: String) {
        publishes.computeIfAbsent(instanceId) { AtomicInteger() }.incrementAndGet()
        deliveries.execute { subscribers.forEach { it(message) } }
    }

    internal fun subscribe(listener: (String) -> Unit) {
        subscribers.add(listener)
    }

    internal fun unsubscribe(listener: (String) -> Unit) {
        subscribers.remove(listener)
    }

    override fun close() {
        subscribers.clear()
        deliveries.shutdownNow()
    }
}

internal class InMemoryClusterTransport(
    private val hub: InMemoryClusterHub,
    private val instanceId: String,
) : ClusterTransport {

    private var listener: ((String) -> Unit)? = null

    override fun publish(message: String) {
        hub.publish(instanceId, message)
    }

    override fun subscribe(listener: (String) -> Unit) {
        this.listener = listener
        hub.subscribe(listener)
    }

    override fun close() {
        listener?.let { hub.unsubscribe(it) }
    }
}
