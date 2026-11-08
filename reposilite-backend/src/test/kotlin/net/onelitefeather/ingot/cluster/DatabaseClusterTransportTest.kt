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
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

/** The poll, the delivery order and the failure handling of the database transport, driven without a database or a timer. */
internal class DatabaseClusterTransportTest {

    private val start: Instant = Instant.parse("2026-11-08T10:00:00Z")
    private val clock = MutableClock(start)
    private val store = InMemoryClusterEventStore()
    private val scheduler = ManualPollScheduler()
    private val claims = RecordingClaims()
    private val logs = mutableListOf<String>()
    private val received = mutableListOf<String>()

    private fun transport(
        instanceId: String = "ingot-b",
        store: ClusterEventStore = this.store,
        claims: ScheduledJobClaims = this.claims,
        pollIntervalMillis: Long = 1000,
    ) = DatabaseClusterTransport(
        store = store,
        claims = claims,
        clock = clock,
        scheduler = scheduler,
        pollIntervalMillis = pollIntervalMillis,
        instanceId = instanceId,
        backoff = ReconnectBackoff { it },
        log = { logs += it },
    )

    private fun rowOf(origin: String, name: String, createdAt: Instant = clock.now) =
        ClusterEventRow(cluster = "cluster-1", origin = origin, createdAt = createdAt.toEpochMilli(), payload = clusterEnvelope(origin, name, createdAt))

    @Test
    fun `a late commit with a lower id is delivered once, after the higher id`() {
        // given: a subscribed instance, and two inserts of which the lower id is still uncommitted
        val receiver = transport()
        receiver.subscribe(received::add)
        val lower = store.reserve()
        val higher = store.reserve()

        // when: the higher id becomes visible and is polled, then the lower id commits and is polled
        store.commit(higher, rowOf("ingot-a", "higher"))
        scheduler.tick()
        store.commit(lower, rowOf("ingot-a", "lower"))
        scheduler.tick()
        scheduler.tick()

        // then: both events were applied, each exactly once
        assertThat(tokenNames(received)).containsExactly("higher", "lower")
    }

    @Test
    fun `an event is delivered once across repeated polls`() {
        val receiver = transport()
        receiver.subscribe(received::add)
        store.insert(rowOf("ingot-a", "once"))

        repeat(3) { scheduler.tick() }

        assertThat(tokenNames(received)).containsExactly("once")
    }

    @Test
    fun `own events are not delivered back to the publishing instance`() {
        val instance = transport(instanceId = "ingot-a")
        instance.subscribe(received::add)

        instance.publish(clusterEnvelope("ingot-a", "own", clock.now))
        scheduler.tick()

        assertThat(received).isEmpty()
    }

    @Test
    fun `rows stored before the subscription are not delivered`() {
        store.insert(rowOf("ingot-a", "history"))
        val receiver = transport()
        receiver.subscribe(received::add)

        scheduler.tick()
        assertThat(received).isEmpty()

        store.insert(rowOf("ingot-a", "after"))
        scheduler.tick()
        assertThat(tokenNames(received)).containsExactly("after")
    }

    @Test
    fun `the poll runs at the configured interval`() {
        transport(pollIntervalMillis = 250).subscribe(received::add)

        assertThat(scheduler.intervals).containsExactly(250L)
    }

    @Test
    fun `a row older than five minutes is removed once the window has moved on`() {
        store.insert(rowOf("ingot-a", "stale", createdAt = start))
        transport().subscribe(received::add)

        clock.now = start.plusMillis(Duration.ofMinutes(5).toMillis() + 1)
        scheduler.tick()

        assertThat(store.ids()).`as`("rows left after the retention window").isEmpty()
    }

    @Test
    fun `the prune claim is requested once per window and carries the window index`() {
        transport().subscribe(received::add)
        scheduler.tick()
        scheduler.tick()
        clock.now = start.plusSeconds(300)
        scheduler.tick()

        val windowZero = start.toEpochMilli() / 300_000
        assertThat(claims.keys).containsExactly(
            "cluster-event-prune:$windowZero",
            "cluster-event-prune:${windowZero + 1}",
        )
    }

    @Test
    fun `only the instance whose claim is granted removes the old rows`() {
        store.insert(rowOf("ingot-old", "stale", createdAt = start))
        transport(instanceId = "ingot-a", claims = RecordingClaims { false }).subscribe(received::add)
        clock.now = start.plusSeconds(301)

        scheduler.tick()

        assertThat(store.deletions).`as`("deletions by the instance without the claim").isZero()
        assertThat(store.ids()).hasSize(1)
    }

    @Test
    fun `a failing poll waits for the reconnect backoff before it reads again`() {
        transport().subscribe(received::add)
        val readsBeforeOutage = store.reads
        store.failing = true

        scheduler.tick()
        clock.now = start.plusMillis(999)
        scheduler.tick()
        assertThat(store.reads - readsBeforeOutage).`as`("reads during the first backoff").isEqualTo(1)

        clock.now = start.plusMillis(1000)
        scheduler.tick()
        assertThat(store.reads - readsBeforeOutage).`as`("reads after the first backoff").isEqualTo(2)
        assertThat(logs).anyMatch { it.contains("connection state is reconnecting") }
    }

    @Test
    fun `an event is delivered after the outage ends and the backoff has passed`() {
        transport().subscribe(received::add)
        store.failing = true
        scheduler.tick()
        store.failing = false
        store.insert(rowOf("ingot-a", "after-outage"))

        clock.now = start.plusMillis(1000)
        scheduler.tick()

        assertThat(tokenNames(received)).containsExactly("after-outage")
        assertThat(logs).anyMatch { it.contains("connection state is connected") }
    }

    @Test
    fun `a failing publish is reported and the next publish waits for the backoff`() {
        val publisher = transport(instanceId = "ingot-a")
        publisher.subscribe(received::add)
        store.failing = true
        assertThatThrownBy { publisher.publish(clusterEnvelope("ingot-a", "lost", clock.now)) }
            .isInstanceOf(IllegalStateException::class.java)

        store.failing = false
        assertThatThrownBy { publisher.publish(clusterEnvelope("ingot-a", "too-early", clock.now)) }
            .hasMessageContaining("reconnecting")
        assertThat(store.ids()).isEmpty()

        clock.now = start.plusMillis(1000)
        publisher.publish(clusterEnvelope("ingot-a", "later", clock.now))
        assertThat(store.ids()).hasSize(1)
    }

    @Test
    fun `an envelope above 16 KiB is not stored, counted as dropped and logged once without its payload`() {
        val publisher = transport(instanceId = "ingot-a")
        val oversized = clusterEnvelope("ingot-a", "x".repeat(ClusterEventTable.MAX_PAYLOAD_BYTES), clock.now)

        publisher.publish(oversized)
        publisher.publish(oversized)

        assertThat(store.ids()).isEmpty()
        assertThat(publisher.dropped).isEqualTo(2)
        assertThat(logs.filter { "exceeds the database transport limits" in it })
            .hasSize(1)
            .allMatch { "token.changed" in it && "xxxxx" !in it }
    }

    @Test
    fun `the retention cut-off is five minutes before the injected clock`() {
        assertThat(retentionCutoffMillis(start.toEpochMilli()))
            .isEqualTo(start.minus(Duration.ofMinutes(5)).toEpochMilli())
    }
}
