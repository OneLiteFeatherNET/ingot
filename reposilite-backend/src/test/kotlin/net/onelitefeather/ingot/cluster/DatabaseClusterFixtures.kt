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

import com.fasterxml.jackson.databind.ObjectMapper
import com.reposilite.scheduling.ScheduledJobClaims
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TreeMap

/** A clock the test moves by hand. Nothing in the unit tests reads the system time. */
internal class MutableClock(var now: Instant) : Clock() {

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = now
}

/** Runs the scheduled tasks only when the test calls [tick]. */
internal class ManualPollScheduler : PollScheduler {

    val intervals = mutableListOf<Long>()
    private val tasks = mutableListOf<() -> Unit>()

    override fun every(intervalMillis: Long, task: () -> Unit): AutoCloseable {
        intervals += intervalMillis
        tasks += task
        return AutoCloseable { tasks.remove(task) }
    }

    fun tick() {
        tasks.toList().forEach { it() }
    }
}

/** Keeps the events in memory with the id order of the database. [reserve] stands for an insert whose transaction has not committed yet. */
internal class InMemoryClusterEventStore : ClusterEventStore {

    private class Entry(val id: Long, val origin: String, val payload: String, val createdAt: Long)

    private val rows = TreeMap<Long, Entry>()
    private var sequence = 0L

    var failing = false
    var reads = 0
        private set
    var deletions = 0
        private set

    /** Hands out the next id before the event is visible, as the database does when an insert is made. */
    fun reserve(): Long = ++sequence

    /** Makes a reserved id visible to readers. */
    fun commit(id: Long, row: ClusterEventRow) {
        rows[id] = Entry(id, row.origin, row.payload, row.createdAt)
    }

    fun ids(): List<Long> = rows.keys.toList()

    override fun insert(row: ClusterEventRow) {
        failOnDemand()
        commit(reserve(), row)
    }

    override fun maxId(): Long {
        failOnDemand()
        return rows.keys.maxOrNull() ?: 0L
    }

    override fun since(lowerExclusive: Long): List<StoredClusterEvent> {
        reads++
        failOnDemand()
        return rows.tailMap(lowerExclusive, false).values.map { StoredClusterEvent(it.id, it.origin, it.payload) }
    }

    override fun deleteCreatedBefore(cutoffMillis: Long) {
        failOnDemand()
        deletions++
        rows.values.removeIf { it.createdAt < cutoffMillis }
    }

    private fun failOnDemand() {
        if (failing) throw IllegalStateException("database unavailable")
    }
}

/** Grants the claims that [grant] accepts and records every key that was asked for. */
internal class RecordingClaims(private val grant: (String) -> Boolean = { true }) : ScheduledJobClaims {

    val keys = mutableListOf<String>()

    override fun claim(key: String): Boolean {
        keys += key
        return grant(key)
    }
}

/** A well-formed `token.changed` envelope from [origin], as a publisher would store it. */
internal fun clusterEnvelope(origin: String, name: String, at: Instant, cluster: String = "cluster-1"): String =
    ClusterEnvelopeCodec.encode(
        ClusterEnvelope(
            type = ClusterEventType.TOKEN_CHANGED,
            cluster = cluster,
            origin = origin,
            at = at,
            payload = ObjectMapper().createObjectNode().put("name", name),
        )
    )

/** The token names of the envelopes in [raw], in order. */
internal fun tokenNames(raw: List<String>): List<String> =
    raw.map { (ClusterEnvelopeCodec.decode(it) as ClusterDecodeResult.Accepted).envelope.payload.get("name").asText() }
