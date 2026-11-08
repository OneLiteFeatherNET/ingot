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
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** The cluster section of the instance status, read from the JSON that the status endpoint returns. */
internal class ClusterStatusSectionTest {

    private val hub = InMemoryClusterHub()
    private val now = Instant.parse("2026-11-08T10:15:30Z")

    @AfterEach
    fun closeHub() {
        hub.close()
    }

    private class Instance(val extensions: Extensions, val plugin: ClusterPlugin, val status: StatusFacade)

    private fun startInstance(
        instanceId: String,
        url: String,
        transports: Map<String, ClusterTransportFactory>,
    ): Instance {
        val logger = InMemoryLogger()
        // no running instance here: console fan-out needs the journalist of a running instance
        val variables = mapOf("INGOT_CLUSTER_URL" to url, "INGOT_CLUSTER_INSTANCE_ID" to instanceId, "INGOT_CLUSTER_CONSOLE" to "false")
        val extensions = Extensions(logger)
        val status = StatusFacade(
            testEnv = true,
            maxThreads = Reference.reference(40),
            status = { true },
            remoteVersionUrl = "",
            failureFacade = FailureFacade(logger),
        )
        extensions.registerFacade(status)
        val plugin = ClusterPlugin(
            sources = ClusterSources(property = { null }, variable = variables::get, hostName = { "host" }),
            transports = transports,
            clusterIdentifier = { "cluster-1" },
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )
        extensions.registerPlugin(plugin)
        plugin.initialize()
        return Instance(extensions, plugin, status)
    }

    private fun statusJson(instance: Instance): JsonNode =
        ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER.valueToTree(instance.status.fetchInstanceStatus())

    private fun JsonNode.bus(): JsonNode = path("cluster").path("bus")

    /** A transport that is reconnecting, as a transport is during an outage. */
    private class ReconnectingFactory : ClusterTransportFactory {
        override fun open(url: ClusterBusUrl, settings: ClusterSettings, clusterIdentifier: String): ClusterTransport =
            object : ClusterTransport {
                override fun publish(message: String) {}

                override fun subscribe(listener: (String) -> Unit) {}

                override fun connectionState(): ConnectionState = ConnectionState.RECONNECTING

                override fun close() {}
            }
    }

    @Test
    fun `the cluster section reports a connected bus with the last received event time and the counts`() {
        // given: two instances on one bus, the second one counts what it receives
        val url = "tls://bot-user:s3cret-pass@hub.example:4222"
        val transports = mapOf("tls" to hub.factory())
        val source = startInstance("ingot-a", url, transports)
        val receiver = startInstance("ingot-b", url, transports)
        val delivered = CountDownLatch(1)
        receiver.plugin.onReceived(ClusterEventType.TOKEN_CHANGED) { delivered.countDown() }

        // when: a message that is not an envelope arrives, then a token change on the first instance
        hub.publish("ingot-a", "not an envelope")
        source.extensions.emitEvent(TokenStateChangedEvent("ci-bot"))
        assertThat(delivered.await(5, TimeUnit.SECONDS)).describedAs("token change delivered within five seconds").isTrue()

        // then: the second instance reports a connected bus with the time of the last event and its counts
        val bus = statusJson(receiver).bus()
        assertThat(bus.path("state").asText()).isEqualTo("connected")
        assertThat(bus.path("lastReceivedAt").asLong()).isEqualTo(now.toEpochMilli())
        assertThat(bus.path("published").asLong()).isZero()
        assertThat(bus.path("applied").asLong()).isEqualTo(1)
        assertThat(bus.path("ignored").asLong()).isEqualTo(1)
    }

    @Test
    fun `the published count rises with each event this instance sends`() {
        // given: an instance on a bus
        val instance = startInstance("ingot-a", "tls://hub.example:4222", mapOf("tls" to hub.factory()))

        // when: two token changes are sent
        instance.extensions.emitEvent(TokenStateChangedEvent("ci-bot"))
        instance.extensions.emitEvent(TokenStateChangedEvent("deploy-bot"))

        // then: the status reports two published events and no applied event
        val bus = statusJson(instance).bus()
        assertThat(bus.path("published").asLong()).isEqualTo(2)
        assertThat(bus.path("applied").asLong()).isZero()
    }

    @Test
    fun `the bus url is shown with its credentials removed and no secret appears in the status json`() {
        // given: a bus url that carries a user name and a password
        val instance = startInstance("ingot-a", "tls://bot-user:s3cret-pass@hub.example:4222", mapOf("tls" to hub.factory()))

        // when: the status is read
        val json = statusJson(instance)

        // then: the url shows a placeholder in place of the credentials, and neither credential is in the document
        assertThat(json.bus().path("url").asText()).isEqualTo("tls://REDACTED@hub.example:4222")
        assertThat(json.toString())
            .describedAs("the status json carries neither the user name nor the password")
            .doesNotContain("bot-user")
            .doesNotContain("s3cret-pass")
    }

    @Test
    fun `the cluster section names the transport by its scheme`() {
        // given: an instance on a tls bus
        val instance = startInstance("ingot-a", "tls://hub.example:4222", mapOf("tls" to hub.factory()))

        // when: the status is read
        val bus = statusJson(instance).bus()

        // then: the transport is named after the scheme of the bus url
        assertThat(bus.path("transport").asText()).isEqualTo("tls")
    }

    @Test
    fun `the cluster section reports a reconnecting bus while the transport is reconnecting`() {
        // given: an instance whose transport is reconnecting
        val instance = startInstance("ingot-a", "tls://hub.example:4222", mapOf("tls" to ReconnectingFactory()))

        // when: the status is read
        val bus = statusJson(instance).bus()

        // then: the state is reconnecting
        assertThat(bus.path("state").asText()).isEqualTo("reconnecting")
    }

    @Test
    fun `the cluster section reports a configured bus without a transport as disabled`() {
        // given: a bus url whose scheme has no transport in this build
        val instance = startInstance("ingot-a", "valkey://valkey.example:6379", transports = emptyMap())

        // when: the status is read
        val bus = statusJson(instance).bus()

        // then: the bus is disabled, has no received event and sent nothing
        assertThat(bus.path("state").asText()).isEqualTo("disabled")
        assertThat(bus.path("transport").asText()).isEqualTo("valkey")
        assertThat(bus.has("lastReceivedAt")).describedAs("no event was received on a disabled bus").isFalse()
    }
}
