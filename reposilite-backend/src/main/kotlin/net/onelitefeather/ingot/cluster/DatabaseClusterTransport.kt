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

import com.reposilite.scheduling.ScheduledJobClaims
import java.time.Clock
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * The database transport: publishes an event as an insert into `cluster_event` and reads the events of the other instances
 * with a poll every [pollIntervalMillis]. A failing insert or poll counts as a broker disconnect: the state is logged, and
 * the next attempts wait for [backoff] before they touch the database again.
 *
 * The poll reads the rows above its watermark. The watermark starts at the largest id present at subscription, so stored
 * history is never applied. Old rows are removed by the instance that wins the claim of the current window.
 */
internal class DatabaseClusterTransport(
    private val store: ClusterEventStore,
    private val claims: ScheduledJobClaims,
    private val clock: Clock,
    private val scheduler: PollScheduler,
    private val pollIntervalMillis: Long,
    private val instanceId: String,
    private val backoff: ReconnectBackoff = ReconnectBackoff(),
    private val log: (String) -> Unit = {},
) : ClusterTransport {

    @Volatile
    private var listener: ((String) -> Unit)? = null

    // Guarded by pollLock: only the poll reads and advances these.
    private val pollLock = Any()
    private var watermark = 0L
    private var started = false
    private val delivered = HashSet<Long>()
    private var lastPrunedWindow: Long? = null
    private var poller: AutoCloseable? = null

    // Guarded by stateLock: publishers and the poll both record outcomes.
    private val stateLock = Any()
    private var attempts = 0
    private var retryAtMillis = 0L

    private val connection = ConnectionStateLog(log)

    @Volatile
    private var state = ConnectionState.RECONNECTING

    /** Connected after a poll or insert succeeded, reconnecting after one failed, disabled once closed. Starts reconnecting until the first success. */
    override fun connectionState(): ConnectionState = state

    private fun setState(next: ConnectionState) {
        state = next
        connection.transition(next)
    }
    private val droppedCount = AtomicLong()
    private val droppedLogged = AtomicBoolean(false)

    /** How many envelopes were not stored because they exceed the limits of this transport. */
    val dropped: Long
        get() = droppedCount.get()

    override fun publish(message: String) {
        val envelope = requireNotNull((ClusterEnvelopeCodec.decode(message) as? ClusterDecodeResult.Accepted)?.envelope) {
            "Cluster | only an envelope can be published on the database transport"
        }
        if (!fitsRow(message, envelope)) {
            recordDrop(envelope.type)
            return
        }
        check(!inBackoff(clock.millis())) { "Cluster | the database transport is reconnecting" }

        try {
            store.insert(ClusterEventRow(envelope.cluster, envelope.origin, clock.millis(), message))
        } catch (exception: Exception) {
            recordFailure(exception)
            throw exception
        }
        recordSuccess()
    }

    override fun subscribe(listener: (String) -> Unit) {
        this.listener = listener
        // The first poll takes the watermark, so rows stored before this point are never applied.
        poll()
        synchronized(pollLock) {
            poller = scheduler.every(pollIntervalMillis) { poll() }
        }
    }

    override fun close() {
        synchronized(pollLock) {
            poller?.close()
            poller = null
        }
        state = ConnectionState.DISABLED
    }

    private fun poll() {
        synchronized(pollLock) {
            val now = clock.millis()
            if (inBackoff(now)) return
            try {
                if (!started) {
                    watermark = store.maxId()
                    // Rows already stored in the window are history: they count as delivered, so they are never applied.
                    store.since(watermark - REREAD_IDS).forEach { delivered += it.id }
                    started = true
                }
                // Ids are assigned at insert, not at commit, so a lower id can become visible after a higher one. The window
                // below the watermark is read again, and the delivered set keeps each id to one delivery.
                for (row in store.since(watermark - REREAD_IDS)) {
                    if (!delivered.add(row.id)) continue
                    if (row.id > watermark) watermark = row.id
                    if (row.origin != instanceId) deliver(row.payload)
                }
                delivered.removeIf { it <= watermark - REREAD_IDS }
                pruneIfDue(now)
                recordSuccess()
            } catch (exception: Exception) {
                recordFailure(exception)
            }
        }
    }

    private fun deliver(payload: String) {
        val target = listener ?: return
        try {
            target(payload)
        } catch (exception: Exception) {
            log("Cluster | a received event could not be applied: ${exception.javaClass.simpleName}")
        }
    }

    /** Once per window, one instance removes the rows older than the retention window. The claim decides who runs it. */
    private fun pruneIfDue(now: Long) {
        val window = now / PRUNE_WINDOW_MILLIS
        if (window == lastPrunedWindow) return
        lastPrunedWindow = window
        try {
            if (claims.claim("$PRUNE_KEY_PREFIX$window")) {
                store.deleteCreatedBefore(retentionCutoffMillis(now))
            }
        } catch (exception: Exception) {
            log("Cluster | removing old cluster events failed: ${exception.javaClass.simpleName}")
        }
    }

    private fun fitsRow(message: String, envelope: ClusterEnvelope): Boolean =
        message.toByteArray(Charsets.UTF_8).size <= ClusterEventTable.MAX_PAYLOAD_BYTES &&
            envelope.cluster.length <= ClusterEventTable.CLUSTER_LENGTH &&
            envelope.origin.length <= ClusterEventTable.ORIGIN_LENGTH

    private fun recordDrop(type: ClusterEventType) {
        droppedCount.incrementAndGet()
        if (droppedLogged.compareAndSet(false, true)) {
            log("Cluster | an event of type ${type.wireName} exceeds the database transport limits and is not stored, further drops are only counted")
        }
    }

    private fun inBackoff(now: Long): Boolean =
        synchronized(stateLock) { now < retryAtMillis }

    private fun recordSuccess() {
        synchronized(stateLock) {
            attempts = 0
            retryAtMillis = 0
        }
        setState(ConnectionState.CONNECTED)
    }

    private fun recordFailure(exception: Exception) {
        val delay: Duration = synchronized(stateLock) {
            attempts++
            backoff.delay(attempts).also { retryAtMillis = clock.millis() + it.toMillis() }
        }
        setState(ConnectionState.RECONNECTING)
        log("Cluster | the database bus failed (${exception.javaClass.simpleName}), the next attempt is in ${delay.toMillis()} ms")
    }

    private companion object {
        const val PRUNE_KEY_PREFIX = "cluster-event-prune:"
        const val PRUNE_WINDOW_MILLIS = 5 * 60 * 1000L

        /** How many ids below the watermark every poll reads again. */
        const val REREAD_IDS = 100L
    }
}

private const val RETENTION_MILLIS = 5 * 60 * 1000L

/** Rows created before this instant are removed. A pure function of the clock reading, so tests need no database. */
internal fun retentionCutoffMillis(nowMillis: Long): Long = nowMillis - RETENTION_MILLIS
