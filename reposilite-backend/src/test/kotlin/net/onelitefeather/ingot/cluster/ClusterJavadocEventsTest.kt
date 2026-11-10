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
import com.reposilite.plugin.api.JavadocInvalidatedEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Two instances of one cluster on an in-memory bus, for the javadoc cache events. Every wait is bounded by a latch. */
internal class ClusterJavadocEventsTest {

    private val hub = InMemoryClusterHub()

    @AfterEach
    fun closeHub() {
        hub.close()
    }

    /** Records the javadoc invalidations that reach the caches of one instance. */
    private class RecordingHooks : ClusterCacheHooks {
        val javadocInvalidations = CopyOnWriteArrayList<Pair<String, String>>()

        override fun resolutionInvalidated(repository: String, path: String) {}

        override fun sharedConfigurationChanged(domain: String) {}

        override fun javadocInvalidated(repository: String, path: String) {
            javadocInvalidations.add(repository to path)
        }

        override fun clusterBusOpened() {}
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
            clock = Clock.fixed(Instant.parse("2026-11-10T10:15:30Z"), ZoneOffset.UTC),
            cacheHooks = hooks,
        )
        extensions.registerPlugin(plugin)
        plugin.initialize()
        return extensions to plugin
    }

    @Test
    fun `a javadoc invalidation published by one instance is announced with its repository and path`() {
        // given: two instances, the second one listens for the javadoc invalidation after the cluster plugin has applied it
        val (sourceExtensions, _) = startInstance("ingot-a", RecordingHooks())
        val (_, receiver) = startInstance("ingot-b", RecordingHooks())
        val delivered = CountDownLatch(1)
        val payloads = CopyOnWriteArrayList<Map<String, String>>()
        receiver.onReceived(ClusterEventType.JAVADOC_INVALIDATED) { envelope ->
            payloads.add(mapOf("repository" to envelope.payload.path("repository").asText(), "path" to envelope.payload.path("path").asText()))
            delivered.countDown()
        }

        // when: the first instance evicts the artifact of a deployed javadoc jar
        sourceExtensions.emitEvent(JavadocInvalidatedEvent("hosted", "com/example/lib"))

        // then: the second instance received the event with exactly the repository and the artifact path
        assertThat(delivered.await(5, TimeUnit.SECONDS)).describedAs("invalidation delivered within five seconds").isTrue()
        assertThat(payloads).describedAs("payload of the javadoc invalidation").containsExactly(
            mapOf("repository" to "hosted", "path" to "com/example/lib"),
        )
        assertThat(hub.publishedBy("ingot-a")).describedAs("published once by the deploying instance").isEqualTo(1)
    }

    @Test
    fun `a received javadoc invalidation calls the hook once and the receiving instance publishes nothing`() {
        // given: two instances, the second one applies what it receives
        val (sourceExtensions, _) = startInstance("ingot-a", RecordingHooks())
        val receiverHooks = RecordingHooks()
        val (_, receiver) = startInstance("ingot-b", receiverHooks)
        val delivered = CountDownLatch(1)
        receiver.onReceived(ClusterEventType.JAVADOC_INVALIDATED) { delivered.countDown() }

        // when: the first instance evicts an artifact and the second one applies the event
        sourceExtensions.emitEvent(JavadocInvalidatedEvent("hosted", "com/example/lib"))
        assertThat(delivered.await(5, TimeUnit.SECONDS)).describedAs("invalidation delivered within five seconds").isTrue()

        // then: the hook ran once on the second instance and the second instance published nothing
        assertThat(receiverHooks.javadocInvalidations).containsExactly("hosted" to "com/example/lib")
        assertThat(hub.publishedBy("ingot-b")).describedAs("no echo from the receiving instance").isZero()
    }

    @Test
    fun `an invalidation carrying the identifier of the instance itself is ignored`() {
        // given: the first instance listens to the bus as well. The hub delivers to subscribers in the order they subscribed,
        // and the first instance subscribed first, so its receiver has handled its own message once the second instance has.
        val sourceHooks = RecordingHooks()
        val (sourceExtensions, _) = startInstance("ingot-a", sourceHooks)
        val (_, receiver) = startInstance("ingot-b", RecordingHooks())
        val delivered = CountDownLatch(1)
        receiver.onReceived(ClusterEventType.JAVADOC_INVALIDATED) { delivered.countDown() }

        // when: the first instance publishes its invalidation and the bus delivers it back to the first instance
        sourceExtensions.emitEvent(JavadocInvalidatedEvent("hosted", "com/example/lib"))
        assertThat(delivered.await(5, TimeUnit.SECONDS)).describedAs("invalidation delivered within five seconds").isTrue()

        // then: the publishing instance does not apply its own message a second time
        assertThat(sourceHooks.javadocInvalidations).describedAs("own message not applied").isEmpty()
    }
}
