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

package com.reposilite.audit.decision

import com.reposilite.audit.AuditFacade
import com.reposilite.journalist.Logger
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.atomic.AtomicLong

/** Stores one decision record. Throws when the record cannot be stored. */
fun interface DecisionSink {
    fun store(record: DecisionRecord)
}

/** Stores a decision record as an audit event, so that it lands in the audit table. */
class AuditDecisionSink(private val facade: AuditFacade) : DecisionSink {
    override fun store(record: DecisionRecord) {
        facade.record(record.toAuditEvent())
    }
}

/**
 * What became of a record. [FAILED] is the only outcome that the caller must act on: in `sync` mode it refuses the
 * request, and the decision is then not applied.
 */
enum class RecordOutcome { OFF, QUEUED, DROPPED, STORED, FAILED }

/**
 * Where the authorization chain hands its decision records. [enabled] is checked before a record is built, so that the
 * mode `off` builds none. [record] reports a failed store as [RecordOutcome.FAILED] instead of throwing it.
 */
interface DecisionLog {

    val enabled: Boolean

    fun record(record: DecisionRecord): RecordOutcome

    companion object {

        /** The log of the mode `off`: it is never enabled and stores nothing. */
        val Off: DecisionLog = object : DecisionLog {
            override val enabled: Boolean = false
            override fun record(record: DecisionRecord): RecordOutcome = RecordOutcome.OFF
        }

    }

}

/**
 * Keeps decision records in the mode of [DecisionLogSettings]. In `async` mode, [record] only offers the record to a
 * bounded queue and a single daemon writer stores it, so the request is never delayed by the writer. A full queue drops
 * the record and counts it. The counts are logged at most once per minute while they change.
 */
class DecisionRecorder(
    private val mode: DecisionLogMode,
    private val sink: DecisionSink,
    private val logger: Logger,
    private val clock: Clock,
    capacity: Int = DEFAULT_CAPACITY,
) : AutoCloseable, DecisionLog {

    private val queue = ArrayBlockingQueue<DecisionRecord>(capacity)
    private val dropped = AtomicLong()
    private val failed = AtomicLong()

    @Volatile
    private var closing = false

    // Read and written by the writer thread only.
    private var lastWarning: Instant? = null
    private var reportedDropped = 0L
    private var reportedFailed = 0L

    private val writerThread: Thread? =
        if (mode == DecisionLogMode.ASYNC) {
            Thread(::runWriter, "ingot-decision-log").apply {
                isDaemon = true
                start()
            }
        } else {
            null
        }

    /** False in `off` mode. The caller checks it before it builds a record, so that `off` allocates none. */
    override val enabled: Boolean
        get() = mode != DecisionLogMode.OFF

    /** Records that did not fit into the queue since the start. */
    fun droppedCount(): Long =
        dropped.get()

    override fun record(record: DecisionRecord): RecordOutcome =
        when (mode) {
            DecisionLogMode.OFF -> RecordOutcome.OFF
            DecisionLogMode.SYNC -> storeNow(record)
            DecisionLogMode.ASYNC -> if (queue.offer(record)) RecordOutcome.QUEUED else drop()
        }

    /** Stops the writer after it has stored what is queued, waiting a bounded time for it. */
    override fun close() {
        closing = true
        writerThread?.join(CLOSE_TIMEOUT_MILLIS)
    }

    private fun storeNow(record: DecisionRecord): RecordOutcome =
        try {
            sink.store(record)
            RecordOutcome.STORED
        } catch (exception: Exception) {
            logger.warn("DECISION LOG | A record could not be stored before the answer: ${exception.message}")
            RecordOutcome.FAILED
        }

    private fun drop(): RecordOutcome {
        dropped.incrementAndGet()
        return RecordOutcome.DROPPED
    }

    private fun runWriter() {
        while (true) {
            val next = queue.poll(POLL_INTERVAL_MILLIS, MILLISECONDS)
            if (next != null) {
                try {
                    sink.store(next)
                } catch (exception: Exception) {
                    failed.incrementAndGet()
                }
            }

            reportIfDue()
            if (closing && queue.isEmpty()) {
                return
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
            "DECISION LOG | ${droppedNow - reportedDropped} record(s) dropped on a full queue and " +
                "${failedNow - reportedFailed} record(s) not stored, $droppedNow dropped in total"
        )
        lastWarning = now
        reportedDropped = droppedNow
        reportedFailed = failedNow
    }

    companion object {

        const val DEFAULT_CAPACITY = 10_000
        private const val POLL_INTERVAL_MILLIS = 200L
        private const val CLOSE_TIMEOUT_MILLIS = 5_000L
        private val WARNING_INTERVAL: Duration = Duration.ofMinutes(1)

    }

}
