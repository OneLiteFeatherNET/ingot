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

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ThreadFactory
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** The log lines a version 1 connection may receive per second on average. */
internal const val CONSOLE_LOG_RATE_PER_SECOND = 200

/** The number of log lines a version 1 connection may receive at once, the budget that refills at [CONSOLE_LOG_RATE_PER_SECOND]. */
internal const val CONSOLE_LOG_BURST = 500

/** One log line on its way to a version 1 client, before it is framed. */
internal data class LogLine(val seq: Long, val node: String, val level: String, val text: String)

/**
 * The outbound queue of one version 1 connection. Log lines may be refused and counted, and every other frame is queued
 * or the connection is reported as a slow consumer, so a result is never dropped.
 */
internal interface ConsoleV1Outbox {

    /** Queues a log line without waiting. Returns false when the line was dropped by the rate limit or by a full queue. */
    fun offerLog(line: LogLine): Boolean

    /**
     * Queues a frame that the client must receive. Returns false when the outbox is closed, or when the queue is full of
     * such frames, in which case the slow consumer callback of the outbox runs once.
     */
    fun putFrame(frame: Any): Boolean

    /** Stops delivery. Safe to call more than once. */
    fun close()

}

/**
 * The version 1 outbox of one console WebSocket. Logging threads only [offerLog] and never wait for the client. A single
 * writer thread sends the queue in order. A log line is refused when the rate budget is spent or the queue is full, and
 * the count of refused lines is carried by the next log line that is accepted. A frame that is not a log line displaces the
 * oldest queued log line when the queue is full, and is refused only when the queue holds nothing but frames.
 */
internal class ConsoleFrameOutbox(
    private val send: (Any) -> Unit,
    /** Called once when the writer stops after a failed send. The owner is expected to disconnect, which closes the outbox. */
    private val onFailure: (Exception) -> Unit,
    /** Called when a frame is refused because the queue holds only frames. The owner closes the connection. */
    private val onSlowConsumer: () -> Unit,
    private val clock: Clock,
    private val capacity: Int = CONSOLE_SOCKET_QUEUE_CAPACITY,
    threadFactory: ThreadFactory = ThreadFactory { Thread(it, "ingot-console-socket-writer").apply { isDaemon = true } },
) : ConsoleV1Outbox {

    /** A queued log line with the number of lines dropped before it, fixed when it was accepted. */
    private class QueuedLine(val line: LogLine, val dropped: Int)

    private val lock = ReentrantLock()
    private val ready = lock.newCondition()

    /** Guarded by [lock]: queued [QueuedLine]s and frames, in delivery order. */
    private val queue = ArrayDeque<Any>()

    /** Guarded by [lock]: lines refused since the last accepted line. */
    private var dropped = 0

    /** Guarded by [lock]: the rate budget, and the instant it was last refilled. */
    private var tokens = CONSOLE_LOG_BURST.toDouble()
    private var refilledAt: Instant = clock.instant()

    /** Guarded by [lock]. */
    private var closed = false

    private val writer = threadFactory.newThread(::runWriter)

    fun start() {
        writer.start()
    }

    override fun offerLog(line: LogLine): Boolean = lock.withLock {
        if (closed) {
            return false
        }
        refill()
        if (tokens < 1.0 || queue.size >= capacity) {
            dropped++
            return false
        }
        tokens -= 1.0
        queue.addLast(QueuedLine(line, dropped))
        dropped = 0
        ready.signal()
        true
    }

    override fun putFrame(frame: Any): Boolean {
        val queued = lock.withLock {
            when {
                closed -> return false
                queue.size < capacity || evictOldestLine() -> {
                    queue.addLast(frame)
                    ready.signal()
                    true
                }
                else -> false
            }
        }
        if (!queued) {
            onSlowConsumer()
        }
        return queued
    }

    override fun close() = lock.withLock {
        closed = true
        queue.clear()
        ready.signalAll()
    }

    /** Must hold [lock]. Removes the oldest queued log line to make room, and counts it with the lines before it. */
    private fun evictOldestLine(): Boolean {
        val index = queue.indexOfFirst { it is QueuedLine }
        if (index < 0) {
            return false
        }
        val evicted = queue.removeAt(index) as QueuedLine
        dropped += evicted.dropped + 1
        return true
    }

    /** Must hold [lock]. Adds the tokens earned since the last refill, up to the burst. */
    private fun refill() {
        val now = clock.instant()
        val elapsed = Duration.between(refilledAt, now)
        refilledAt = now
        if (elapsed.isNegative) {
            return
        }
        val earned = elapsed.toNanos() * CONSOLE_LOG_RATE_PER_SECOND / NANOS_PER_SECOND
        tokens = minOf(CONSOLE_LOG_BURST.toDouble(), tokens + earned)
    }

    private fun runWriter() {
        try {
            while (true) {
                val next = nextFrame() ?: return
                send(next)
            }
        } catch (failure: Exception) {
            if (!isClosed()) {
                onFailure(failure)
            }
        }
    }

    /** Waits for the next frame, and frames the log line with the count of lines dropped before it. Null once closed. */
    private fun nextFrame(): Any? {
        lock.withLock {
            while (queue.isEmpty() && !closed) {
                ready.await()
            }
            if (closed) {
                return null
            }
            return when (val item = queue.removeFirst()) {
                is QueuedLine -> LogFrame(
                    seq = item.line.seq,
                    node = item.line.node,
                    level = item.line.level,
                    text = item.line.text,
                    dropped = item.dropped,
                )
                else -> item
            }
        }
    }

    private fun isClosed(): Boolean = lock.withLock { closed }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000.0
    }

}
