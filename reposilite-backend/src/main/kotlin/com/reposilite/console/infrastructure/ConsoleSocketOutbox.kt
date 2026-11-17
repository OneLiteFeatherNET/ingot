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
package com.reposilite.console.infrastructure

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit.MILLISECONDS

/** The number of lines a console WebSocket may hold while its client reads slower than the server logs. */
internal const val CONSOLE_SOCKET_QUEUE_CAPACITY = 1000

/** How often a blocked [ConsoleSocketOutbox.put] checks whether its outbox was closed meanwhile. */
private const val PUT_POLL_MILLIS = 100L

/**
 * The lines of one console WebSocket. Log subscribers [offer] lines and never wait: when the queue is full the line is
 * dropped and counted. A single writer thread sends the queued lines in order, and once there is room again it sends one
 * marker with the number of lines dropped.
 *
 * Replay of cached lines and the keep-alive echo go through [put], which waits for room, because they belong to this
 * connection alone and must not be lost. The waiting thread never holds [lock], so a stalled client cannot block an offer.
 */
internal class ConsoleSocketOutbox(
    private val send: (String) -> Unit,
    /** Called once when the writer stops after a failed send. The owner is expected to disconnect, which closes the outbox. */
    private val onFailure: (Exception) -> Unit,
    capacity: Int = CONSOLE_SOCKET_QUEUE_CAPACITY,
    threadFactory: ThreadFactory = ThreadFactory { Thread(it, "ingot-console-socket-writer").apply { isDaemon = true } },
) {

    private val queue = ArrayBlockingQueue<String>(capacity)

    /** Guards [dropped] and every enqueue of a line or marker, so a marker lands right after the last line queued before the drop. */
    private val lock = Any()

    /** Lines refused since the last marker was queued. Guarded by [lock]. */
    private var dropped = 0

    @Volatile
    private var closed = false

    private val writer = threadFactory.newThread(::runWriter)

    fun start() {
        writer.start()
    }

    /** Queues [line] without waiting. Returns false when the line was dropped because the queue is full, or the outbox is closed. */
    fun offer(line: String): Boolean {
        synchronized(lock) {
            if (closed) {
                return false
            }
            placeDropMarker()
            if (queue.offer(line)) {
                return true
            }
            dropped++
            return false
        }
    }

    /** Queues [line], waiting for room if needed. Returns early without queuing when the outbox is closed. */
    fun put(line: String) {
        synchronized(lock) { placeDropMarker() }
        while (!closed) {
            if (queue.offer(line, PUT_POLL_MILLIS, MILLISECONDS)) {
                return
            }
        }
    }

    /** Stops the writer and releases the queued lines. Lines offered or put afterwards are refused. Safe to call more than once. */
    fun close() {
        synchronized(lock) {
            if (closed) {
                return
            }
            closed = true
            queue.clear()
        }
        writer.interrupt()
    }

    /** Must hold [lock]. Queues the marker for lines dropped so far, if the queue has room for it. */
    private fun placeDropMarker() {
        if (dropped == 0) {
            return
        }
        if (queue.offer(dropMarker(dropped))) {
            dropped = 0
        }
    }

    private fun runWriter() {
        try {
            while (!closed) {
                val line = queue.take()
                if (closed) {
                    return
                }
                send(line)
                synchronized(lock) { placeDropMarker() }
            }
        } catch (_: InterruptedException) {
            // Closed while waiting for a line or for the client, so there is nothing left to send.
        } catch (failure: Exception) {
            if (!closed) {
                onFailure(failure)
            }
        }
    }

    private fun dropMarker(count: Int): String =
        "[console] $count log lines dropped because this client reads too slowly"

}
