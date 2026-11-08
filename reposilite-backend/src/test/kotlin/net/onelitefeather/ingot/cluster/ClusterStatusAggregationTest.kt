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
import com.reposilite.ReposiliteObjectMapper
import com.reposilite.VERSION
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.plugin.Extensions
import com.reposilite.plugin.api.TokenStateChangedEvent
import com.reposilite.status.FailureFacade
import com.reposilite.status.StatusFacade
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import panda.std.reactive.Reference
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The cluster view of the status: the status.snapshot event, the per-instance store with its stale and drop rules, and the
 * cluster field of the status response. Time comes from a clock the test moves by hand, so no test waits on the wall clock.
 */
internal class ClusterStatusAggregationTest {

    private val hub = InMemoryClusterHub()
    private val clock = ManualClock(Instant.parse("2026-11-08T10:15:30Z"))
    private val url = "tls://hub.example:4222"
    private val oneMinute = 60_000L

    @AfterEach
    fun closeHub() {
        hub.close()
    }

    /** A clock that only moves when a test advances it. */
    private class ManualClock(start: Instant) : Clock() {

        @Volatile
        private var current = start

        fun advanceMillis(millis: Long) {
            current = current.plusMillis(millis)
        }

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = current
    }

    private class Instance(val extensions: Extensions, val plugin: ClusterPlugin, val status: StatusFacade)

    private fun startInstance(instanceId: String): Instance {
        val logger = InMemoryLogger()
        val variables = mapOf("INGOT_CLUSTER_URL" to url, "INGOT_CLUSTER_INSTANCE_ID" to instanceId)
        val extensions = Extensions(logger)
        val status = StatusFacade(
            testEnv = true,
            startTime = clock.instant().toEpochMilli(),
            maxThreads = Reference.reference(40),
            status = { true },
            remoteVersionUrl = "",
            failureFacade = FailureFacade(logger),
            clock = clock,
        )
        extensions.registerFacade(status)
        val plugin = ClusterPlugin(
            sources = ClusterSources(property = { null }, variable = variables::get, hostName = { "pod-0.internal" }),
            transports = mapOf("tls" to hub.factory()),
            clusterIdentifier = { "cluster-1" },
            clock = clock,
        )
        extensions.registerPlugin(plugin)
        plugin.initialize()
        return Instance(extensions, plugin, status)
    }

    private fun Instance.collectSnapshots(): MutableList<ClusterEnvelope> {
        val received = CopyOnWriteArrayList<ClusterEnvelope>()
        plugin.onReceived(ClusterEventType.STATUS_SNAPSHOT) { received.add(it) }
        return received
    }

    /**
     * Sends a token change from [source] and waits until [receiver] has applied it. The bus delivers in order, so every
     * message sent before the barrier has been applied when the barrier arrives.
     */
    private fun awaitBarrier(source: Instance, receiver: Instance) {
        val barrier = CountDownLatch(1)
        receiver.plugin.onReceived(ClusterEventType.TOKEN_CHANGED) { barrier.countDown() }
        source.extensions.emitEvent(TokenStateChangedEvent("barrier"))
        assertThat(barrier.await(5, TimeUnit.SECONDS)).describedAs("the bus delivered the barrier within five seconds").isTrue()
    }

    private fun statusJson(instance: Instance): JsonNode =
        ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER.valueToTree(instance.status.fetchInstanceStatus())

    private fun JsonNode.instanceNamed(id: String): JsonNode? =
        path("cluster").path("instances").firstOrNull { it.path("id").asText() == id }

    @Test
    fun `a status snapshot is published once per recorded snapshot`() {
        // given: two instances on one bus, the second one collects the status snapshots it receives
        val source = startInstance("ingot-a")
        val receiver = startInstance("ingot-b")
        val received = receiver.collectSnapshots()

        // when: the first instance records two snapshots
        source.status.recordStatusSnapshot()
        source.status.recordStatusSnapshot()
        awaitBarrier(source, receiver)

        // then: the second instance received exactly one event per recorded snapshot
        assertThat(received).describedAs("status snapshot events received").hasSize(2)
    }

    @Test
    fun `a status snapshot carries the instance figures and the latest sample and no host name`() {
        // given: an instance that records a snapshot, and a second instance that collects it
        val source = startInstance("ingot-a")
        val receiver = startInstance("ingot-b")
        val received = receiver.collectSnapshots()

        // when: the snapshot is recorded and delivered
        source.status.recordStatusSnapshot()
        awaitBarrier(source, receiver)

        // then: the payload carries exactly the figures of the design, the sample and the interval, and nothing else
        val payload = received.single().payload
        assertThat(payload.fieldNames().asSequence().toSet()).containsExactlyInAnyOrder(
            "version", "uptimeStart", "usedMemory", "maxMemory", "usedThreads", "maxThreads", "failuresCount",
            "health", "interval", "sample",
        )
        assertThat(payload.path("version").asText()).isEqualTo(VERSION)
        assertThat(payload.path("uptimeStart").asLong()).isEqualTo(clock.instant().toEpochMilli())
        assertThat(payload.path("interval").asLong()).isEqualTo(oneMinute)
        assertThat(payload.path("sample").path("at").asLong()).isEqualTo(clock.instant().toEpochMilli())
        assertThat(payload.toString()).describedAs("the payload carries no host name").doesNotContain("pod-0.internal")
    }

    @Test
    fun `the cluster section lists a received instance with its figures and its sample`() {
        // given: two instances on one bus, the first one has recorded a snapshot
        val source = startInstance("ingot-a")
        val receiver = startInstance("ingot-b")
        source.status.recordStatusSnapshot()
        awaitBarrier(source, receiver)

        // when: the second instance reads its status
        val remote = statusJson(receiver).instanceNamed("ingot-a")

        // then: the remote instance is listed with the figures it published and is not stale
        assertThat(remote).describedAs("the received instance is listed").isNotNull
        remote!!
        assertThat(remote.path("version").asText()).isEqualTo(VERSION)
        assertThat(remote.path("health").asText()).isEqualTo("up")
        assertThat(remote.path("stale").asBoolean()).isFalse()
        assertThat(remote.path("interval").asLong()).isEqualTo(oneMinute)
        assertThat(remote.path("maxMemory").asInt()).isEqualTo(statusJson(source).path("maxMemory").asInt())
        assertThat(remote.path("maxThreads").asInt()).isEqualTo(40)
        assertThat(remote.path("samples").map { it.path("at").asLong() })
            .containsExactly(clock.instant().toEpochMilli())
    }

    @Test
    fun `the cluster section includes the local instance`() {
        // given: an instance that has received nothing yet
        val receiver = startInstance("ingot-b")

        // when: the instance reads its status
        val local = statusJson(receiver).instanceNamed("ingot-b")

        // then: the local instance is listed with its own figures and is not stale
        assertThat(local).describedAs("the local instance is listed").isNotNull
        local!!
        assertThat(local.path("stale").asBoolean()).isFalse()
        assertThat(local.path("health").asText()).isEqualTo("up")
        assertThat(local.path("interval").asLong()).isEqualTo(oneMinute)
    }

    @Test
    fun `an instance is shown as stale only after one interval without a snapshot`() {
        // given: a received snapshot of one instance
        val source = startInstance("ingot-a")
        val receiver = startInstance("ingot-b")
        source.status.recordStatusSnapshot()
        awaitBarrier(source, receiver)

        // when: exactly one interval passes without a new snapshot
        clock.advanceMillis(oneMinute)

        // then: the instance is still current
        assertThat(statusJson(receiver).instanceNamed("ingot-a")!!.path("stale").asBoolean())
            .describedAs("stale at exactly one interval").isFalse()

        // when: one millisecond more passes
        clock.advanceMillis(1)

        // then: the instance is shown as stale
        assertThat(statusJson(receiver).instanceNamed("ingot-a")!!.path("stale").asBoolean())
            .describedAs("stale after one interval").isTrue()
    }

    @Test
    fun `an instance is dropped only after three intervals without a snapshot`() {
        // given: a received snapshot of one instance
        val source = startInstance("ingot-a")
        val receiver = startInstance("ingot-b")
        source.status.recordStatusSnapshot()
        awaitBarrier(source, receiver)

        // when: three intervals pass without a new snapshot
        clock.advanceMillis(3 * oneMinute)

        // then: the instance is still listed, as stale
        assertThat(statusJson(receiver).instanceNamed("ingot-a")!!.path("stale").asBoolean())
            .describedAs("still listed at exactly three intervals").isTrue()

        // when: one millisecond more passes
        clock.advanceMillis(1)

        // then: the instance is dropped
        assertThat(statusJson(receiver).instanceNamed("ingot-a"))
            .describedAs("dropped after three intervals").isNull()
    }

    @Test
    fun `a stale instance is current again once it records a new snapshot`() {
        // given: an instance that has gone stale on the receiving side
        val source = startInstance("ingot-a")
        val receiver = startInstance("ingot-b")
        source.status.recordStatusSnapshot()
        awaitBarrier(source, receiver)
        clock.advanceMillis(oneMinute + 1)
        assertThat(statusJson(receiver).instanceNamed("ingot-a")!!.path("stale").asBoolean()).isTrue()

        // when: the instance records a new snapshot
        source.status.recordStatusSnapshot()
        awaitBarrier(source, receiver)

        // then: it is no longer stale
        assertThat(statusJson(receiver).instanceNamed("ingot-a")!!.path("stale").asBoolean()).isFalse()
    }

    @Test
    fun `the interval of a snapshot is one hour once twelve snapshots are kept`() {
        // given: an instance whose first snapshot was recorded at start, and a receiver that collects the events
        val source = startInstance("ingot-a")
        val receiver = startInstance("ingot-b")
        val received = receiver.collectSnapshots()

        // when: eleven more snapshots are recorded, which makes twelve kept with the one from start
        repeat(11) { source.status.recordStatusSnapshot() }
        awaitBarrier(source, receiver)

        // then: the tenth event still announces the minute interval, the eleventh the hourly one
        val expected: List<Long> = List(10) { oneMinute } + 3_600_000L
        assertThat(received.map { it.payload.path("interval").asLong() })
            .describedAs("interval carried by each event")
            .containsExactlyElementsOf(expected)
    }

    @Test
    fun `without a bus the status response has no cluster field and keeps its fields`() {
        // given: an instance whose status is read without a cluster bus
        val logger = InMemoryLogger()
        val status = StatusFacade(
            testEnv = true,
            maxThreads = Reference.reference(40),
            status = { true },
            remoteVersionUrl = "",
            failureFacade = FailureFacade(logger),
            clock = clock,
        )

        // when: the status is read as json
        val json = ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER.valueToTree<JsonNode>(status.fetchInstanceStatus())

        // then: the response has exactly the fields it had before the cluster bus existed
        assertThat(json.fieldNames().asSequence().toSet()).containsExactlyInAnyOrder(
            "version", "latestVersion", "uptime", "usedMemory", "maxMemory", "usedThreads", "maxThreads", "failuresCount",
        )
    }
}
