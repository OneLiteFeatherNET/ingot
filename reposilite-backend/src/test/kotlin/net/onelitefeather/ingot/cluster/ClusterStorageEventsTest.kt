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
import com.reposilite.web.api.HttpServerStartedEvent
import net.onelitefeather.ingot.storage.capability.ClusterBus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Two instances of one cluster on an in-memory bus, for the storage cache invalidation. Every wait is bounded by a latch. */
internal class ClusterStorageEventsTest {

    private val hub = InMemoryClusterHub()

    @AfterEach
    fun closeHub() {
        hub.close()
    }

    private class RecordingHooks(private val delivered: CountDownLatch = CountDownLatch(1)) : ClusterCacheHooks {
        val storageInvalidations = CopyOnWriteArrayList<Pair<String, String>>()
        val buses = CopyOnWriteArrayList<ClusterBus>()

        override fun resolutionInvalidated(repository: String, path: String) {}

        override fun sharedConfigurationChanged(domain: String) {}

        override fun javadocInvalidated(repository: String, path: String) {}

        override fun clusterBusOpened() {}

        override fun storageInvalidated(repository: String, path: String) {
            storageInvalidations.add(repository to path)
            delivered.countDown()
        }

        override fun clusterBusOpened(bus: ClusterBus) {
            buses.add(bus)
        }
    }

    private fun startInstance(instanceId: String, hooks: ClusterCacheHooks): Pair<Extensions, ClusterPlugin> {
        val variables = mapOf(
            "INGOT_CLUSTER_INSTANCE_ID" to instanceId,
            // no running instance here: console fan-out needs the journalist of a running instance
            "INGOT_CLUSTER_CONSOLE" to "false",
            "INGOT_CLUSTER_URL" to "nats://hub.example:4222",
        )
        val extensions = Extensions(InMemoryLogger())
        val plugin = ClusterPlugin(
            sources = ClusterSources(property = { null }, variable = variables::get, hostName = { "host" }),
            transports = mapOf("nats" to hub.factory()),
            clusterIdentifier = { "cluster-1" },
            clock = Clock.fixed(Instant.parse("2026-11-15T10:15:30Z"), ZoneOffset.UTC),
            cacheHooks = hooks,
        )
        extensions.registerPlugin(plugin)
        plugin.initialize()
        return extensions to plugin
    }

    @Test
    fun `a storage invalidation published on the bus reaches the same repository on the other instance`() {
        val sourceHooks = RecordingHooks()
        val receiverHooks = RecordingHooks(CountDownLatch(1))
        val (sourceExtensions, _) = startInstance("ingot-a", sourceHooks)
        val (_, receiver) = startInstance("ingot-b", receiverHooks)
        val delivered = CountDownLatch(1)
        receiver.onReceived(ClusterEventType.STORAGE_INVALIDATED) { delivered.countDown() }

        sourceExtensions.emitEvent(HttpServerStartedEvent)
        val bus = sourceHooks.buses.single()
        bus.publishInvalidation("releases", listOf("org/example/app/1.0/app-1.0.jar"))

        assertThat(delivered.await(5, TimeUnit.SECONDS)).describedAs("invalidation delivered within five seconds").isTrue()
        assertThat(receiverHooks.storageInvalidations)
            .describedAs("the other instance must drop its copy of the same repository and path")
            .containsExactly("releases" to "org/example/app/1.0/app-1.0.jar")
    }

    @Test
    fun `a storage invalidation is published once and applying it on the other instance publishes nothing`() {
        val sourceHooks = RecordingHooks()
        val (sourceExtensions, _) = startInstance("ingot-a", sourceHooks)
        val (_, receiver) = startInstance("ingot-b", RecordingHooks())
        val delivered = CountDownLatch(1)
        receiver.onReceived(ClusterEventType.STORAGE_INVALIDATED) { delivered.countDown() }

        sourceExtensions.emitEvent(HttpServerStartedEvent)
        sourceHooks.buses.single().publishInvalidation("releases", listOf("org/example/app/1.0/app-1.0.jar"))
        assertThat(delivered.await(5, TimeUnit.SECONDS)).describedAs("invalidation delivered within five seconds").isTrue()

        assertThat(hub.publishedBy("ingot-a")).isEqualTo(1)
        assertThat(hub.publishedBy("ingot-b")).isZero()
    }

    @Test
    fun `the wire name of a storage invalidation is storage invalidated`() {
        assertThat(ClusterEventType.fromWireName("storage.invalidated"))
            .describedAs("the event type must be decodable from its wire name")
            .isEqualTo(ClusterEventType.STORAGE_INVALIDATED)
    }

}
