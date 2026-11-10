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

package com.reposilite.audit.sink

import com.reposilite.journalist.Logger
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.atomic.AtomicLong

/**
 * Receives the audit events of the instance on their way to the table. [offer] never blocks and never throws, so a
 * request that records an event is not delayed by where the lines go.
 */
interface AuditLineSink : AutoCloseable {

    fun offer(line: AuditSinkLine)

    override fun close() {}

    object Disabled : AuditLineSink {
        override fun offer(line: AuditSinkLine) {}
    }

}

/**
 * Writes the lines on one daemon thread, behind a bounded queue. A full queue drops the new line and a failed write drops
 * its batch; both are counted, and one warning per minute reports the counts with the last error. The writer is never
 * waited for on the request path.
 */
class AuditSink(
    private val writers: List<AuditLineWriter>,
    private val clock: Clock,
    private val logger: Logger,
    capacity: Int = DEFAULT_CAPACITY,
) : AuditLineSink {

    private val queue = ArrayBlockingQueue<AuditSinkLine>(capacity)
    private val dropped = AtomicLong()
    private val failed = AtomicLong()

    @Volatile
    private var lastError: String? = null

    @Volatile
    private var closing = false

    // Read and written by the writer thread only.
    private var lastWarning: Instant? = null
    private var reportedDropped = 0L
    private var reportedFailed = 0L

    private val writerThread = Thread(::runWriter, "ingot-audit-sink").apply {
        isDaemon = true
        start()
    }

    override fun offer(line: AuditSinkLine) {
        if (!queue.offer(line)) {
            dropped.incrementAndGet()
        }
    }

    /** Lines that did not fit into the queue since the start. */
    fun droppedCount(): Long =
        dropped.get()

    /** Lines that could not be encoded or written since the start. */
    fun failedCount(): Long =
        failed.get()

    /** Writes what is queued, waits a bounded time for the writer thread and closes the writers. */
    override fun close() {
        closing = true
        writerThread.join(CLOSE_TIMEOUT_MILLIS)
        if (!writerThread.isAlive) {
            writers.forEach { writer -> runCatching { writer.close() } }
        }
    }

    private fun runWriter() {
        while (true) {
            val first = queue.poll(POLL_INTERVAL_MILLIS, MILLISECONDS)
            if (first != null) {
                val batch = ArrayList<AuditSinkLine>()
                batch += first
                queue.drainTo(batch, MAX_BATCH - 1)
                writeBatch(batch)
            }

            reportIfDue()
            if (closing && queue.isEmpty()) {
                return
            }
        }
    }

    private fun writeBatch(batch: List<AuditSinkLine>) {
        val lines = batch.mapNotNull { line ->
            try {
                line.encode() + "\n"
            } catch (exception: Exception) {
                failed.incrementAndGet()
                lastError = exception.message ?: exception.javaClass.simpleName
                null
            }
        }
        if (lines.isEmpty()) {
            return
        }

        writers.forEach { writer ->
            try {
                writer.write(lines)
            } catch (exception: Exception) {
                failed.addAndGet(lines.size.toLong())
                lastError = exception.message ?: exception.javaClass.simpleName
            }
        }
    }

    private fun reportIfDue() {
        val now = clock.instant()
        val last = lastWarning
        if (last != null && Duration.between(last, now) < WARNING_INTERVAL) {
            return
        }

        val droppedNow = dropped.get()
        val failedNow = failed.get()
        if (droppedNow == reportedDropped && failedNow == reportedFailed) {
            return
        }

        logger.warn(
            "AUDIT | JSON sink lost ${droppedNow - reportedDropped} line(s) on a full queue and " +
                "${failedNow - reportedFailed} line(s) on write errors, last error: ${lastError ?: "none"}"
        )
        lastWarning = now
        reportedDropped = droppedNow
        reportedFailed = failedNow
    }

    companion object {

        const val DEFAULT_CAPACITY = 10_000
        private const val MAX_BATCH = 500
        private const val POLL_INTERVAL_MILLIS = 200L
        private const val CLOSE_TIMEOUT_MILLIS = 5_000L
        private val WARNING_INTERVAL: Duration = Duration.ofMinutes(1)

        /**
         * The sink for the switches in [settings], or [AuditLineSink.Disabled] when none is set, which starts no thread
         * and opens no file.
         */
        fun open(settings: AuditSinkSettings, clock: Clock, logger: Logger): AuditLineSink {
            if (!settings.enabled) {
                return AuditLineSink.Disabled
            }

            val writers = buildList<AuditLineWriter> {
                if (settings.stdout) add(StreamAuditLineWriter(System.out))
                settings.file?.let { add(FileAuditLineWriter(it, clock)) }
            }
            logger.info("AUDIT | JSON lines sink is on, stdout: ${settings.stdout}, file: ${settings.file != null}")
            return AuditSink(writers, clock, logger)
        }

    }

}
