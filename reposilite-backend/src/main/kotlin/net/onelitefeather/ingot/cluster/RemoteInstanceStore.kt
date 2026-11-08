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

import com.fasterxml.jackson.databind.JsonNode
import com.reposilite.status.InstanceFigures
import com.reposilite.status.api.ClusterInstanceResponse
import com.reposilite.status.api.StatusSnapshot
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * The last status snapshot of every other instance, as received from the bus. An entry is kept with the time it arrived on this
 * instance, so the stale and drop rules use this instance's clock and not the clock of the sender.
 */
internal class RemoteInstanceStore(private val clock: Clock) {

    private class Known(val receivedAt: Instant, val figures: InstanceFigures)

    private val known = LinkedHashMap<String, Known>()

    /** Keeps the snapshot of the origin of [envelope]. A payload that lacks a field is ignored. */
    @Synchronized
    fun accept(envelope: ClusterEnvelope) {
        val parsed = parse(envelope.payload) ?: return
        val kept = known[envelope.origin]?.figures?.samples.orEmpty()
        val samples = (kept.filter { it.at != parsed.sample.at } + parsed.sample).takeLast(SAMPLE_CAPACITY)
        known[envelope.origin] = Known(clock.instant(), parsed.figures.copy(samples = samples))
    }

    /**
     * The instances received, sorted by identifier. An instance more than one interval old is stale, and one more than three
     * intervals old is dropped from the store.
     */
    @Synchronized
    fun instances(): List<ClusterInstanceResponse> {
        val now = clock.instant()
        known.entries.removeIf { (_, entry) -> ageOf(entry, now) > entry.figures.interval * DROP_AFTER_INTERVALS }
        return known.entries
            .sortedBy { it.key }
            .map { (id, entry) -> entry.figures.toResponse(id, stale = ageOf(entry, now) > entry.figures.interval) }
    }

    private fun ageOf(entry: Known, now: Instant): Long =
        Duration.between(entry.receivedAt, now).toMillis()

    private class Parsed(val figures: InstanceFigures, val sample: StatusSnapshot)

    private fun parse(payload: JsonNode): Parsed? {
        val sample = payload.path("sample")
        val figures = InstanceFigures(
            version = payload.text("version") ?: return null,
            uptimeStart = payload.long("uptimeStart") ?: return null,
            usedMemory = payload.double("usedMemory") ?: return null,
            maxMemory = payload.int("maxMemory") ?: return null,
            usedThreads = payload.int("usedThreads") ?: return null,
            maxThreads = payload.int("maxThreads") ?: return null,
            failuresCount = payload.int("failuresCount") ?: return null,
            health = payload.text("health")?.takeIf { it in HEALTH_STATES } ?: "unknown",
            interval = payload.long("interval")?.takeIf { it > 0 } ?: return null,
            samples = emptyList(),
        )
        val snapshot = StatusSnapshot(
            at = sample.long("at") ?: return null,
            memory = sample.int("memory") ?: return null,
            threads = sample.int("threads") ?: return null,
        )
        return Parsed(figures, snapshot)
    }

    private fun JsonNode.text(name: String): String? = path(name).takeIf { it.isTextual }?.asText()

    private fun JsonNode.long(name: String): Long? = path(name).takeIf { it.isNumber }?.asLong()

    private fun JsonNode.int(name: String): Int? = path(name).takeIf { it.isNumber }?.asInt()

    private fun JsonNode.double(name: String): Double? = path(name).takeIf { it.isNumber }?.asDouble()

    private companion object {
        /** The same number of snapshots that the status keeps for this instance */
        const val SAMPLE_CAPACITY = 12
        const val DROP_AFTER_INTERVALS = 3
        val HEALTH_STATES = setOf("up", "down", "unknown")
    }
}

/** The response form of these figures for the instance [id]. */
internal fun InstanceFigures.toResponse(id: String, stale: Boolean): ClusterInstanceResponse =
    ClusterInstanceResponse(
        id = id,
        version = version,
        uptimeStart = uptimeStart,
        usedMemory = usedMemory,
        maxMemory = maxMemory,
        usedThreads = usedThreads,
        maxThreads = maxThreads,
        failuresCount = failuresCount,
        health = health,
        stale = stale,
        interval = interval,
        samples = samples,
    )
