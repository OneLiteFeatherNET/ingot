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
import com.reposilite.maven.ResolutionCache
import com.reposilite.maven.ResolutionCache.Origin
import com.reposilite.plugin.Extensions
import com.reposilite.plugin.api.ResolutionInvalidatedEvent
import com.reposilite.storage.api.toLocation
import com.reposilite.web.api.HttpServerStartedEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Two instances of one cluster on an in-memory bus, for the resolution cache events. Every wait is bounded by a latch. */
internal class ClusterResolutionEventsTest {

    private val hub = InMemoryClusterHub()

    @AfterEach
    fun closeHub() {
        hub.close()
    }

    /** Records what the cluster plugin asks of the caches, and acts on a resolution invalidation through [onInvalidation]. */
    private class RecordingHooks(
        private val onInvalidation: (repository: String, path: String) -> Unit = { _, _ -> },
    ) : ClusterCacheHooks {
        val invalidations = CopyOnWriteArrayList<Pair<String, String>>()
        val busOpened = AtomicInteger()

        override fun resolutionInvalidated(repository: String, path: String) {
            invalidations.add(repository to path)
            onInvalidation(repository, path)
        }

        override fun sharedConfigurationChanged(domain: String) {}

        override fun clusterBusOpened() {
            busOpened.incrementAndGet()
        }
    }

    private fun startInstance(instanceId: String, hooks: ClusterCacheHooks, withBus: Boolean = true): Pair<Extensions, ClusterPlugin> {
        val variables = buildMap {
            put("INGOT_CLUSTER_INSTANCE_ID", instanceId)
            // no running instance here: console fan-out needs the journalist of a running instance
            put("INGOT_CLUSTER_CONSOLE", "false")
            if (withBus) put("INGOT_CLUSTER_URL", "nats://hub.example:4222")
        }
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
    fun `a resolution invalidation published by one instance drops the matching pin on the other`() {
        // given: a cache on the second instance that pins a missing metadata file of the prefix com/example/app
        val cache = ResolutionCache(maxEntries = 4)
        cache.record("com/example/app".toLocation(), authenticated = false, origin = Origin.MissingMetadata)
        val metadata = "com/example/app/maven-metadata.xml".toLocation()
        assertThat(cache.lookup(metadata, authenticated = false)).isEqualTo(Origin.MissingMetadata)

        // and: the second instance drops the pin of the named repository and path when the event arrives
        val delivered = CountDownLatch(1)
        val receiverHooks = RecordingHooks { repository, path ->
            if (repository == "hosted") cache.invalidate(path.toLocation())
        }
        val (sourceExtensions, _) = startInstance("ingot-a", RecordingHooks())
        val (_, receiver) = startInstance("ingot-b", receiverHooks)
        // the test listener is registered after the cluster plugin, so it runs once the pin has been dropped
        receiver.onReceived(ClusterEventType.RESOLUTION_INVALIDATED) { delivered.countDown() }

        // when: the first instance deploys the metadata file
        sourceExtensions.emitEvent(ResolutionInvalidatedEvent("hosted", "com/example/app/maven-metadata.xml"))

        // then: the second instance has received the event and its pin is gone
        assertThat(delivered.await(5, TimeUnit.SECONDS)).describedAs("invalidation delivered within five seconds").isTrue()
        assertThat(receiverHooks.invalidations).containsExactly("hosted" to "com/example/app/maven-metadata.xml")
        assertThat(cache.lookup(metadata, authenticated = false)).describedAs("pin dropped on the second instance").isNull()
    }

    @Test
    fun `a resolution invalidation is published once and applying it on the other instance publishes nothing`() {
        // given: two instances, the second one applies what it receives
        val (sourceExtensions, _) = startInstance("ingot-a", RecordingHooks())
        val (_, receiver) = startInstance("ingot-b", RecordingHooks())
        val delivered = CountDownLatch(1)
        receiver.onReceived(ClusterEventType.RESOLUTION_INVALIDATED) { delivered.countDown() }

        // when: the first instance invalidates a resolution entry and the second one applies it
        sourceExtensions.emitEvent(ResolutionInvalidatedEvent("hosted", "com/example/app/maven-metadata.xml"))
        assertThat(delivered.await(5, TimeUnit.SECONDS)).describedAs("invalidation delivered within five seconds").isTrue()

        // then: only the originating instance published
        assertThat(hub.publishedBy("ingot-a")).isEqualTo(1)
        assertThat(hub.publishedBy("ingot-b")).isZero()
    }

    @Test
    fun `the resolution lifetimes are bounded once the server has started with a bus configured`() {
        // given: an instance with a bus URL, whose server has not started yet
        val hooks = RecordingHooks()
        val (extensions, _) = startInstance("ingot-a", hooks)
        assertThat(hooks.busOpened.get()).describedAs("before the server has started").isZero()

        // when: the server starts
        extensions.emitEvent(HttpServerStartedEvent)

        // then: the cluster bus is reported as configured exactly once
        assertThat(hooks.busOpened.get()).isEqualTo(1)
    }

    @Test
    fun `the resolution lifetimes are never bounded without a bus URL`() {
        // given: an instance without a bus URL
        val hooks = RecordingHooks()
        val (extensions, _) = startInstance("ingot-a", hooks, withBus = false)

        // when: the server starts
        extensions.emitEvent(HttpServerStartedEvent)

        // then: the cluster bus is never reported, so resolution entries keep their current lifetime
        assertThat(hooks.busOpened.get()).isZero()
    }
}
