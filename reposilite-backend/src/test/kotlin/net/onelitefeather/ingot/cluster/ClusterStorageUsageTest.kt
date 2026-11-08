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
import com.reposilite.plugin.api.StorageUsageFigure
import com.reposilite.plugin.api.StorageUsageRefreshedEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Storage usage snapshots travel over the bus and are handed to the local figures of the receiving instance. */
internal class ClusterStorageUsageTest {

    private val hub = InMemoryClusterHub()

    @AfterEach
    fun closeHub() {
        hub.close()
    }

    private fun startInstance(
        instanceId: String,
        storageUsage: ((StorageUsageRefreshedEvent) -> Unit)? = null,
    ): Extensions {
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
            storageUsage = storageUsage,
        )
        extensions.registerPlugin(plugin)
        plugin.initialize()
        return extensions
    }

    @Test
    fun `a storage usage snapshot on one instance reaches the other instance with its figures`() {
        // given: two instances, the second one records the snapshots it applies
        val source = startInstance("ingot-a")
        val received = CopyOnWriteArrayList<StorageUsageRefreshedEvent>()
        val delivered = CountDownLatch(1)
        startInstance("ingot-b") {
            received.add(it)
            delivered.countDown()
        }
        val snapshot = StorageUsageRefreshedEvent(
            computedAt = Instant.parse("2026-11-08T10:14:00Z"),
            repositories = listOf(StorageUsageFigure("releases", usedBytes = 900L, fileCount = 7L)),
        )

        // when: the first instance finishes a storage usage refresh
        source.emitEvent(snapshot)

        // then: the second instance applies the same figures within a bounded wait
        assertThat(delivered.await(5, TimeUnit.SECONDS)).describedAs("snapshot delivered within five seconds").isTrue()
        assertThat(received).containsExactly(snapshot)
    }
}
