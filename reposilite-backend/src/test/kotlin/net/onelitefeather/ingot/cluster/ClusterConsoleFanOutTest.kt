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

import com.reposilite.ReposiliteJournalist
import com.reposilite.journalist.Journalist
import com.reposilite.journalist.Logger
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.plugin.Extensions
import com.reposilite.plugin.api.TokenStateChangedEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Console fan-out between two instances on an in-memory bus. Publish counts are read from the plugin right after a local
 * log call, because publishing is counted on the logging thread. Waits for remote lines are bounded and use a marker line
 * logged after the line under test, so no test sleeps.
 */
internal class ClusterConsoleFanOutTest {

    private val hub = InMemoryClusterHub()
    private val journalists = mutableListOf<ReposiliteJournalist>()
    private val releaseBus = CountDownLatch(1)

    @AfterEach
    fun tearDown() {
        releaseBus.countDown()
        journalists.forEach { it.shutdown() }
        hub.close()
    }

    private fun journalist(): Pair<ReposiliteJournalist, InMemoryLogger> {
        val visibleLog = InMemoryLogger()
        val journalist = ReposiliteJournalist(
            visibleJournalist = object : Journalist {
                override fun getLogger(): Logger = visibleLog
            },
            cachedLogSize = 0,
            testEnv = true,
            noColor = true,
        )
        journalists += journalist
        return journalist to visibleLog
    }

    private fun startInstance(
        instanceId: String,
        journalist: ReposiliteJournalist,
        extraVariables: Map<String, String> = emptyMap(),
        factory: ClusterTransportFactory = hub.factory(),
    ): Pair<Extensions, ClusterPlugin> {
        val variables = mapOf(
            "INGOT_CLUSTER_URL" to "nats://hub.example:4222",
            "INGOT_CLUSTER_INSTANCE_ID" to instanceId,
        ) + extraVariables
        val plugin = ClusterPlugin(
            sources = ClusterSources(property = { null }, variable = variables::get, hostName = { "host" }),
            transports = mapOf("nats" to factory),
            clusterIdentifier = { "cluster-1" },
            clock = Clock.fixed(Instant.parse("2026-11-08T10:15:30Z"), ZoneOffset.UTC),
            consoleJournalist = journalist,
        )
        val extensions = Extensions(InMemoryLogger())
        extensions.registerPlugin(plugin)
        plugin.initialize()
        return extensions to plugin
    }

    /** Collects the lines a journalist delivers to its console subscribers, in order. */
    private class LineCollector(journalist: ReposiliteJournalist) {

        private val incoming = LinkedBlockingQueue<String>()
        private val seen = mutableListOf<String>()

        init {
            journalist.subscribe { entry -> incoming.put(entry.value) }
        }

        /** Waits, bounded, until [line] has been delivered, and returns every line delivered up to it. */
        fun awaitLine(line: String): List<String> {
            while (true) {
                val next = incoming.poll(5, TimeUnit.SECONDS) ?: error("line '$line' was not delivered within five seconds")
                seen += next
                if (next == line) return seen.toList()
            }
        }
    }

    @Test
    fun `a local line is published once`() {
        // given: an instance whose console forwards its lines
        val (journalist, _) = journalist()
        val (_, source) = startInstance("ingot-a", journalist)

        // when: one line at the visible threshold is logged
        journalist.visibleLogger.info("first")

        // then: exactly one publish is counted
        assertThat(source.counters.published).describedAs("publishes after one local line").isEqualTo(1L)
    }

    @Test
    fun `a received line reaches local subscribers labelled with its origin`() {
        // given: two instances, the second one shows the console lines it receives
        val (sourceJournalist, _) = journalist()
        val (receiverJournalist, _) = journalist()
        startInstance("ingot-a", sourceJournalist)
        startInstance("ingot-b", receiverJournalist)
        val receiverLines = LineCollector(receiverJournalist)

        // when: instance A logs a line, followed by a marker line that proves the first one was delivered before it
        sourceJournalist.visibleLogger.info("first")
        sourceJournalist.visibleLogger.info("marker")

        // then: the line reaches B labelled with its origin, exactly once
        val seen = receiverLines.awaitLine("[ingot-a] marker")
        assertThat(seen.filter { it == "[ingot-a] first" })
            .describedAs("copies of the line received on instance B")
            .hasSize(1)
    }

    @Test
    fun `a received line is not written to the local log`() {
        // given: two instances, the receiving one with a captured local log
        val (sourceJournalist, _) = journalist()
        val (receiverJournalist, receiverLog) = journalist()
        startInstance("ingot-a", sourceJournalist)
        startInstance("ingot-b", receiverJournalist)
        val receiverLines = LineCollector(receiverJournalist)

        // when: instance A logs a line and the receiver has delivered it
        sourceJournalist.visibleLogger.info("first")
        sourceJournalist.visibleLogger.info("marker")
        receiverLines.awaitLine("[ingot-a] marker")

        // then: the captured local log of B holds no copy of the received line
        assertThat(receiverLog.contains("first"))
            .describedAs("received line written to the local log of instance B")
            .isFalse()
    }

    @Test
    fun `a received line is not published again`() {
        // given: two instances, the receiving one counts its own publishes
        val (sourceJournalist, _) = journalist()
        val (receiverJournalist, _) = journalist()
        startInstance("ingot-a", sourceJournalist)
        val (_, receiver) = startInstance("ingot-b", receiverJournalist)
        val receiverLines = LineCollector(receiverJournalist)

        // when: instance A logs a line and B has delivered it
        sourceJournalist.visibleLogger.info("first")
        sourceJournalist.visibleLogger.info("marker")
        receiverLines.awaitLine("[ingot-a] marker")

        // then: applying a received line published nothing on instance B
        assertThat(receiver.counters.published).describedAs("publishes on the receiving instance").isZero()
    }

    @Test
    fun `a line below the visibility threshold is not published`() {
        // given: an instance at its default threshold of INFO
        val (journalist, _) = journalist()
        val (_, source) = startInstance("ingot-a", journalist)

        // when: a DEBUG line is logged
        journalist.visibleLogger.debug("quiet")

        // then: nothing is published
        assertThat(source.counters.published).describedAs("publishes after a debug line").isZero()
    }

    @Test
    fun `a line at the visibility threshold is published`() {
        // given: an instance at its default threshold of INFO
        val (journalist, _) = journalist()
        val (_, source) = startInstance("ingot-a", journalist)

        // when: a DEBUG line and then an INFO line are logged
        journalist.visibleLogger.debug("quiet")
        journalist.visibleLogger.info("loud")

        // then: only the INFO line is published
        assertThat(source.counters.published).describedAs("publishes after one info line").isEqualTo(1L)
    }

    @Test
    fun `switch off publishes no log line`() {
        // given: an instance with INGOT_CLUSTER_CONSOLE set to false
        val (journalist, _) = journalist()
        val (_, source) = startInstance("ingot-a", journalist, mapOf("INGOT_CLUSTER_CONSOLE" to "false"))

        // when: a line is logged
        journalist.visibleLogger.info("silent")

        // then: nothing is published
        assertThat(source.counters.published).describedAs("publishes with the console switch off").isZero()
    }

    @Test
    fun `switch off keeps token invalidation working`() {
        // given: two instances, the source with console fan-out switched off
        val (sourceJournalist, _) = journalist()
        val (receiverJournalist, _) = journalist()
        val (sourceExtensions, _) = startInstance("ingot-a", sourceJournalist, mapOf("INGOT_CLUSTER_CONSOLE" to "false"))
        val (_, receiver) = startInstance("ingot-b", receiverJournalist)
        val tokenDelivered = CountDownLatch(1)
        receiver.onReceived(ClusterEventType.TOKEN_CHANGED) { tokenDelivered.countDown() }

        // when: a token changes on the source
        sourceExtensions.emitEvent(TokenStateChangedEvent("ci-bot"))

        // then: the receiver applies the token change within a bounded wait
        assertThat(tokenDelivered.await(5, TimeUnit.SECONDS)).describedAs("token change delivered").isTrue()
    }

    @Test
    fun `a logging call does not block while the bus is slow`() {
        // given: a transport whose publish waits for a release the test controls, bounded by five seconds
        val slowFactory = object : ClusterTransportFactory {
            override fun open(url: ClusterBusUrl, settings: ClusterSettings, clusterIdentifier: String): ClusterTransport =
                object : ClusterTransport {
                    override fun publish(message: String) {
                        releaseBus.await(5, TimeUnit.SECONDS)
                    }

                    override fun subscribe(listener: (String) -> Unit) {}

                    override fun close() {}
                }
        }
        val (journalist, _) = journalist()
        val (_, source) = startInstance("ingot-a", journalist, factory = slowFactory)

        // when: many lines are logged while the bus accepts no message
        // then: the logging calls return within the bound, and every line is counted as published
        assertTimeoutPreemptively(Duration.ofSeconds(5)) {
            repeat(1_000) { journalist.visibleLogger.info("line $it") }
        }
        assertThat(source.counters.published).describedAs("lines published while the bus is slow").isEqualTo(1_000L)
    }
}
