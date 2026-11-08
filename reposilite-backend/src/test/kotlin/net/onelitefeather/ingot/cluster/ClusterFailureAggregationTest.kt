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

import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.plugin.Extensions
import com.reposilite.status.FailureFacade
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Failure aggregation is an opt-in of the bus. Every wait is bounded by a latch, never by a sleep. */
internal class ClusterFailureAggregationTest {

    private val hub = InMemoryClusterHub()

    @AfterEach
    fun closeHub() {
        hub.close()
    }

    private class Instance(val extensions: Extensions, val plugin: ClusterPlugin) {
        val failures: FailureFacade
            get() = extensions.facade<FailureFacade>()
    }

    private fun startInstance(instanceId: String, failureSwitch: String? = null): Instance {
        val variables = buildMap {
            put("INGOT_CLUSTER_URL", "nats://hub.example:4222")
            put("INGOT_CLUSTER_INSTANCE_ID", instanceId)
            failureSwitch?.let { put("INGOT_CLUSTER_FAILURES", it) }
        }
        val extensions = Extensions(InMemoryLogger())
        extensions.registerFacade(FailureFacade(InMemoryLogger(), Clock.fixed(Instant.parse("2026-11-08T10:15:30Z"), ZoneOffset.UTC)))
        val plugin = ClusterPlugin(
            sources = ClusterSources(property = { null }, variable = variables::get, hostName = { "host" }),
            transports = mapOf("nats" to hub.factory()),
            clusterIdentifier = { "cluster-1" },
            clock = Clock.fixed(Instant.parse("2026-11-08T10:15:30Z"), ZoneOffset.UTC),
        )
        extensions.registerPlugin(plugin)
        plugin.initialize()
        return Instance(extensions, plugin)
    }

    @Test
    fun `failures are not published without the failure switch`() {
        // given: two instances on one bus, and no failure switch in any source
        val source = startInstance("ingot-a")
        val other = startInstance("ingot-b")

        // when: the first instance records a failure
        source.failures.throwException("/api/maven/releases", IllegalStateException("boom"))

        // then: nothing is published and the other instance lists no failure
        assertThat(source.plugin.counters.published).describedAs("messages published by the recording instance").isZero()
        assertThat(other.failures.getFailureRecords()).describedAs("failures listed by the other instance").isEmpty()
    }

    @Test
    fun `a failure recorded on one instance is listed on the other when the switch is on`() {
        // given: two instances on one bus with the failure switch on, the second one reports what it applies
        val source = startInstance("ingot-a", failureSwitch = "true")
        val other = startInstance("ingot-b", failureSwitch = "true")
        val delivered = CountDownLatch(1)
        other.plugin.onReceived(ClusterEventType.FAILURE_RECORDED) { delivered.countDown() }

        // when: the first instance records a failure
        source.failures.throwException("/api/maven/releases", IllegalStateException("boom"))

        // then: the other instance lists it within a bounded wait, with its source, type and message
        assertThat(delivered.await(5, TimeUnit.SECONDS)).describedAs("failure delivered within five seconds").isTrue()
        val listed = other.failures.getFailureRecords().single()
        assertThat(listed.source).isEqualTo("/api/maven/releases")
        assertThat(listed.type).isEqualTo("IllegalStateException")
        assertThat(listed.message).isEqualTo("boom")
    }

    @Test
    fun `the failure count of an instance keeps counting only its own failures`() {
        // given: two instances with the failure switch on, and a failure recorded on the first one
        val source = startInstance("ingot-a", failureSwitch = "true")
        val other = startInstance("ingot-b", failureSwitch = "true")
        val delivered = CountDownLatch(1)
        other.plugin.onReceived(ClusterEventType.FAILURE_RECORDED) { delivered.countDown() }
        source.failures.throwException("/api/maven/releases", IllegalStateException("boom"))
        assertThat(delivered.await(5, TimeUnit.SECONDS)).describedAs("failure delivered within five seconds").isTrue()

        // when: the failure count of the other instance is read, as the instance status does
        val ownCount = other.failures.getFailures().size

        // then: the failure of the first instance is not counted as recorded by the second one
        assertThat(ownCount).isZero()
    }
}
