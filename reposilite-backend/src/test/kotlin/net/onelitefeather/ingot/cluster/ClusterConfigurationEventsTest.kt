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
import com.reposilite.plugin.api.SharedConfigurationChangedEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Two instances of one cluster on an in-memory bus, for shared configuration changes. Every wait is bounded by a latch. */
internal class ClusterConfigurationEventsTest {

    private val hub = InMemoryClusterHub()

    @AfterEach
    fun closeHub() {
        hub.close()
    }

    /** Records the domains whose change the cluster plugin asks the shared configuration to pick up. */
    private class RecordingHooks : ClusterCacheHooks {
        val changedDomains = CopyOnWriteArrayList<String>()

        override fun resolutionInvalidated(repository: String, path: String) {}

        override fun sharedConfigurationChanged(domain: String) {
            changedDomains.add(domain)
        }

        override fun clusterBusOpened() {}
    }

    private fun startInstance(instanceId: String, hooks: ClusterCacheHooks): Pair<Extensions, ClusterPlugin> {
        val variables = mapOf(
            "INGOT_CLUSTER_URL" to "nats://hub.example:4222",
            "INGOT_CLUSTER_INSTANCE_ID" to instanceId,
            // no running instance here: console fan-out needs the journalist of a running instance
            "INGOT_CLUSTER_CONSOLE" to "false",
        )
        val extensions = Extensions(InMemoryLogger())
        val plugin = ClusterPlugin(
            sources = ClusterSources(property = { null }, variable = variables::get, hostName = { "host" }),
            transports = mapOf("nats" to hub.factory()),
            clusterIdentifier = { "cluster-1" },
            clock = Clock.fixed(Instant.parse("2026-11-08T10:15:30Z"), ZoneOffset.UTC),
            cacheHooks = hooks,
        )
        extensions.registerPlugin(plugin)
        plugin.initialize()
        return extensions to plugin
    }

    @Test
    fun `a configuration change received from another instance triggers the reload at once`() {
        // given: two instances, the second one reloads the shared configuration when it hears of a change
        val sourceHooks = RecordingHooks()
        val receiverHooks = RecordingHooks()
        val (sourceExtensions, _) = startInstance("ingot-a", sourceHooks)
        val (_, receiver) = startInstance("ingot-b", receiverHooks)
        val delivered = CountDownLatch(1)
        // the test listener is registered after the cluster plugin, so it runs once the reload has been requested
        receiver.onReceived(ClusterEventType.CONFIG_CHANGED) { delivered.countDown() }

        // when: the first instance stores the maven domain
        sourceExtensions.emitEvent(SharedConfigurationChangedEvent("maven"))

        // then: the second instance requested one reload for that domain within a bounded wait
        assertThat(delivered.await(5, TimeUnit.SECONDS)).describedAs("change delivered within five seconds").isTrue()
        assertThat(receiverHooks.changedDomains).containsExactly("maven")
    }

    @Test
    fun `a configuration change is published once and the receiving instance publishes nothing`() {
        // given: two instances, the second one applies what it receives
        val (sourceExtensions, _) = startInstance("ingot-a", RecordingHooks())
        val (_, receiver) = startInstance("ingot-b", RecordingHooks())
        val delivered = CountDownLatch(1)
        receiver.onReceived(ClusterEventType.CONFIG_CHANGED) { envelope ->
            assertThat(envelope.payload.get("domain").asText()).isEqualTo("maven")
            delivered.countDown()
        }

        // when: the first instance stores the maven domain and the second one applies the change
        sourceExtensions.emitEvent(SharedConfigurationChangedEvent("maven"))
        assertThat(delivered.await(5, TimeUnit.SECONDS)).describedAs("change delivered within five seconds").isTrue()

        // then: only the originating instance published
        assertThat(hub.publishedBy("ingot-a")).isEqualTo(1)
        assertThat(hub.publishedBy("ingot-b")).isZero()
    }
}
