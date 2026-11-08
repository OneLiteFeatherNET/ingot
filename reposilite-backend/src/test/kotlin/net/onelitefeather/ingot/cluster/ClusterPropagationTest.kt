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
import com.reposilite.plugin.api.TokenStateChangedEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Two instances of one cluster on an in-memory bus. Every wait is bounded by a latch, never by a sleep. */
internal class ClusterPropagationTest {

    private val hub = InMemoryClusterHub()

    @AfterEach
    fun closeHub() {
        hub.close()
    }

    private fun startInstance(instanceId: String): Pair<Extensions, ClusterPlugin> {
        val variables = mapOf(
            "INGOT_CLUSTER_URL" to "nats://hub.example:4222",
            "INGOT_CLUSTER_INSTANCE_ID" to instanceId,
        )
        val extensions = Extensions(InMemoryLogger())
        val plugin = ClusterPlugin(
            sources = ClusterSources(property = { null }, variable = variables::get, hostName = { "host" }),
            transports = mapOf("nats" to hub.factory()),
            clusterIdentifier = { "cluster-1" },
            clock = Clock.fixed(Instant.parse("2026-11-08T10:15:30Z"), ZoneOffset.UTC),
        )
        extensions.registerPlugin(plugin)
        plugin.initialize()
        return extensions to plugin
    }

    @Test
    fun `a token change on one instance is applied on the other instance`() {
        // given: two instances of one cluster, the second one records the token names it receives
        val (sourceExtensions, _) = startInstance("ingot-a")
        val (_, receiver) = startInstance("ingot-b")
        val names = CopyOnWriteArrayList<String>()
        val delivered = CountDownLatch(1)
        receiver.onReceived(ClusterEventType.TOKEN_CHANGED) { envelope ->
            names.add(envelope.payload.get("name").asText())
            delivered.countDown()
        }

        // when: a token changes on the first instance
        sourceExtensions.emitEvent(TokenStateChangedEvent("ci-bot"))

        // then: the second instance applies the change within a bounded wait
        assertThat(delivered.await(5, TimeUnit.SECONDS)).describedAs("token change delivered within five seconds").isTrue()
        assertThat(names).containsExactly("ci-bot")
    }

    @Test
    fun `applying a received event publishes nothing on the receiving instance`() {
        // given: two instances, the second one applies what it receives
        val (sourceExtensions, _) = startInstance("ingot-a")
        val (_, receiver) = startInstance("ingot-b")
        val delivered = CountDownLatch(1)
        receiver.onReceived(ClusterEventType.TOKEN_CHANGED) { delivered.countDown() }

        // when: a token changes on the first instance and the change is applied on the second
        sourceExtensions.emitEvent(TokenStateChangedEvent("ci-bot"))
        assertThat(delivered.await(5, TimeUnit.SECONDS)).describedAs("token change delivered within five seconds").isTrue()

        // then: only the originating instance published, so applying the event caused no echo
        assertThat(hub.publishedBy("ingot-a")).isEqualTo(1)
        assertThat(hub.publishedBy("ingot-b")).isZero()
    }
}
