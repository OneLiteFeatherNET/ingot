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

import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

/**
 * The bounded queue between the caller that publishes an event and the transport that sends it. [offer] never waits: when the
 * queue is full, the oldest message is dropped and counted. A drain thread started by [drainTo] hands the messages to the
 * transport, so a slow or unreachable bus delays only the messages behind it and never a request.
 */
class ClusterOutbox(private val capacity: Int) : AutoCloseable {

    private val lock = ReentrantLock()
    private val available = lock.newCondition()
    private val queue = ArrayDeque<String>()
    private val droppedCount = AtomicLong()
    private var closed = false

    /** How many messages were dropped because the queue was full. */
    val dropped: Long
        get() = droppedCount.get()

    fun offer(message: String) {
        lock.withLock {
            if (queue.size >= capacity) {
                queue.removeFirst()
                droppedCount.incrementAndGet()
            }
            queue.addLast(message)
            available.signal()
        }
    }

    /**
     * Starts the drain thread. It stops after [close] once the queue is empty. A failing [sink] call is reported to [onError]
     * and the next message is sent.
     */
    fun drainTo(onError: (Exception) -> Unit = {}, sink: (String) -> Unit): Thread =
        thread(name = "ingot-cluster-outbox", isDaemon = true) {
            while (true) {
                val message = next() ?: return@thread
                try {
                    sink(message)
                } catch (exception: Exception) {
                    onError(exception)
                }
            }
        }

    override fun close() {
        lock.withLock {
            closed = true
            available.signalAll()
        }
    }

    internal fun pendingMessages(): List<String> =
        lock.withLock { queue.toList() }

    private fun next(): String? =
        lock.withLock {
            while (queue.isEmpty() && !closed) {
                available.await()
            }
            queue.pollFirst()
        }
}
