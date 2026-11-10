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
import com.reposilite.plugin.api.ReadOnlyChangedEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Two instances of one cluster on an in-memory bus. Every wait is bounded by a latch, and each test builds its own hub. */
internal class ClusterReadOnlyEventsTest {

    private val hub = InMemoryClusterHub()

    @AfterEach
    fun closeHub() {
        hub.close()
    }

    /** Records how often the cluster plugin asks the read-only state to be re-read. */
    private class RecordingReadOnlyHooks : ClusterCacheHooks {
        val rereads = CopyOnWriteArrayList<Unit>()

        override fun resolutionInvalidated(repository: String, path: String) {}

        override fun sharedConfigurationChanged(domain: String) {}

        override fun javadocInvalidated(repository: String, path: String) {}

        override fun clusterBusOpened() {}

        override fun readOnlyChanged() {
            rereads.add(Unit)
        }
    }

    private fun startInstance(instanceId: String, hooks: ClusterCacheHooks, withBus: Boolean = true): Pair<Extensions, ClusterPlugin> {
        val variables = if (withBus) {
            mapOf(
                "INGOT_CLUSTER_URL" to "nats://hub.example:4222",
                "INGOT_CLUSTER_INSTANCE_ID" to instanceId,
                // no running instance here: console fan-out needs the journalist of a running instance
                "INGOT_CLUSTER_CONSOLE" to "false",
            )
        } else {
            emptyMap()
        }
        val extensions = Extensions(InMemoryLogger())
        val plugin = ClusterPlugin(
            sources = ClusterSources(property = { null }, variable = variables::get, hostName = { "host" }),
            transports = mapOf("nats" to hub.factory()),
            clusterIdentifier = { "cluster-1" },
            clock = Clock.fixed(Instant.parse("2026-11-10T10:15:30Z"), ZoneOffset.UTC),
            cacheHooks = hooks,
        )
        extensions.registerPlugin(plugin)
        plugin.initialize()
        return extensions to plugin
    }

    @Test
    fun `a read-only change from another instance makes the receiver re-read at once`() {
        // given: two instances, the second one re-reads the read-only state when it hears of a change
        val receiverHooks = RecordingReadOnlyHooks()
        val (sourceExtensions, _) = startInstance("ingot-a", RecordingReadOnlyHooks())
        val (_, receiver) = startInstance("ingot-b", receiverHooks)
        val delivered = CountDownLatch(1)
        // the test listener is registered after the cluster plugin, so it runs once the re-read has been requested
        receiver.onReceived(ClusterEventType.READ_ONLY_CHANGED) { delivered.countDown() }

        // when: the first instance turns read-only on
        sourceExtensions.emitEvent(ReadOnlyChangedEvent(enabled = true))

        // then: the second instance re-read the state within a bounded wait
        assertThat(delivered.await(5, TimeUnit.SECONDS)).`as`("change delivered within five seconds").isTrue()
        assertThat(receiverHooks.rereads).`as`("re-reads requested by the receiver").hasSize(1)
    }

    @Test
    fun `a read-only change is published once and the receiving instance publishes nothing`() {
        // given: two instances, the second one applies what it receives
        val (sourceExtensions, _) = startInstance("ingot-a", RecordingReadOnlyHooks())
        val (_, receiver) = startInstance("ingot-b", RecordingReadOnlyHooks())
        val delivered = CountDownLatch(1)
        receiver.onReceived(ClusterEventType.READ_ONLY_CHANGED) { envelope ->
            assertThat(envelope.payload.get("enabled").asText()).`as`("enabled flag in the payload").isEqualTo("true")
            delivered.countDown()
        }

        // when: the first instance turns read-only on and the second one applies the change
        sourceExtensions.emitEvent(ReadOnlyChangedEvent(enabled = true))
        assertThat(delivered.await(5, TimeUnit.SECONDS)).`as`("change delivered within five seconds").isTrue()

        // then: only the originating instance published
        assertThat(hub.publishedBy("ingot-a")).`as`("publications of the originating instance").isEqualTo(1)
        assertThat(hub.publishedBy("ingot-b")).`as`("publications of the receiving instance").isZero()
    }

    @Test
    fun `without a bus a read-only change is neither published nor applied`() {
        // given: an instance with no bus configured
        val hooks = RecordingReadOnlyHooks()
        val (extensions, plugin) = startInstance("ingot-a", hooks, withBus = false)

        // when: the instance turns read-only on
        extensions.emitEvent(ReadOnlyChangedEvent(enabled = true))

        // then: nothing was published and no re-read was requested
        assertThat(plugin.counters.published).`as`("bus publications without a bus").isZero()
        assertThat(hooks.rereads).`as`("re-reads without a bus").isEmpty()
    }

}
