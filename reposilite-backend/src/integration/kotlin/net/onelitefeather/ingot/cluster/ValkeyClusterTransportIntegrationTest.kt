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

import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.PortBinding
import com.github.dockerjava.api.model.Ports
import com.reposilite.SharedContainers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Test
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.net.ServerSocket
import java.time.Duration
import java.util.UUID
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The Valkey transport against a real broker. The broker is shared by the test JVM, so every test takes its own cluster
 * identifier: its channel is then private to the test, and no test depends on what another one published.
 */
internal class ValkeyClusterTransportIntegrationTest {

    private val clusterIdentifier = UUID.randomUUID().toString()
    private val transports = mutableListOf<ClusterTransport>()

    @AfterEach
    fun closeTransports() {
        transports.forEach { it.close() }
    }

    @Test
    fun `a message published by one instance reaches another instance within the bound`() {
        // The publisher uses redis://, the subscriber valkey://: both schemes address the same broker and transport.
        val subscriber = openTransport(busUrl("valkey", SharedContainers.valkey), "node-b")
        val received = LinkedBlockingQueue<String>()
        subscriber.subscribe { received.add(it) }
        val publisher = openTransport(busUrl("redis", SharedContainers.valkey), "node-a")

        val message = "round-trip-${UUID.randomUUID()}"

        assertThat(deliver(publisher, received, message, DELIVERY_BOUND))
            .describedAs("the subscriber should receive the published message")
            .isEqualTo(message)
    }

    @Test
    fun `a subscriber receives messages again after the broker restarts`() {
        // A fixed host port: a restart of a container with a random port can move the port, which would leave the URL stale.
        val port = freePort()
        val url = ClusterBusUrl.parse("valkey://127.0.0.1:$port")
        val broker = startBrokerOn(port)
        try {
            val subscriber = openTransport(url, "node-b") as JedisClusterTransport
            val received = LinkedBlockingQueue<String>()
            subscriber.subscribe { received.add(it) }
            val publisher = openTransport(url, "node-a")
            assertThat(deliver(publisher, received, "before-restart", DELIVERY_BOUND)).isEqualTo("before-restart")
            assertThat(subscriber.connectionState()).isEqualTo(ConnectionState.CONNECTED)

            DockerClientFactory.instance().client()
                .restartContainerCmd(broker.containerId)
                .exec()
            received.clear()

            assertThat(deliver(publisher, received, "after-restart", RECONNECT_BOUND))
                .describedAs("the subscriber should reconnect once the broker is back")
                .isEqualTo("after-restart")
            assertThat(subscriber.connectionState()).isEqualTo(ConnectionState.CONNECTED)
        } finally {
            broker.stop()
        }
    }

    @Test
    fun `an instance that starts while the broker is down connects once the broker is up`() {
        val port = freePort()
        val url = ClusterBusUrl.parse("valkey://127.0.0.1:$port")
        val subscriber = openTransport(url, "node-b")
        val received = LinkedBlockingQueue<String>()
        subscriber.subscribe { received.add(it) }
        val publisher = openTransport(url, "node-a")

        // Nothing listens on the port yet. Publishing must return instead of waiting for the broker.
        assertTimeoutPreemptively(PUBLISH_BOUND) { publisher.publish("while-down") }

        val broker = startBrokerOn(port)
        try {
            val message = "after-start-${UUID.randomUUID()}"
            assertThat(deliver(publisher, received, message, RECONNECT_BOUND))
                .describedAs("the subscriber should connect once the broker starts")
                .isEqualTo(message)
        } finally {
            broker.stop()
        }
    }

    private fun openTransport(url: ClusterBusUrl, instanceId: String): ClusterTransport {
        val settings = ClusterSettings(
            url = url,
            credentialsFile = null,
            instanceId = instanceId,
            console = true,
            failures = false,
            warnings = emptyList(),
        )
        val factory = JedisClusterTransports.getValue(url.scheme)
        return factory.open(url, settings, clusterIdentifier).also { transports += it }
    }

    /**
     * Publishes [message] until it arrives or [bound] passes. The subscription starts asynchronously, so the first
     * publications may reach no subscriber yet. Waits are bounded polls on the queue, never fixed sleeps.
     */
    private fun deliver(
        publisher: ClusterTransport,
        received: BlockingQueue<String>,
        message: String,
        bound: Duration,
    ): String? {
        val deadline = System.nanoTime() + bound.toNanos()
        while (System.nanoTime() < deadline) {
            publisher.publish(message)
            received.poll(RETRY_INTERVAL.toMillis(), TimeUnit.MILLISECONDS)?.let { return it }
        }
        return null
    }

    private fun busUrl(scheme: String, container: GenericContainer<*>): ClusterBusUrl =
        ClusterBusUrl.parse("$scheme://${container.host}:${container.getMappedPort(SharedContainers.VALKEY_PORT)}")

    private fun freePort(): Int =
        ServerSocket(0).use { it.localPort }

    /** A broker of its own, published on [port] so that its URL stays valid across restarts. It is not shared. */
    private fun startBrokerOn(port: Int): GenericContainer<*> =
        GenericContainer(DockerImageName.parse(SharedContainers.VALKEY_IMAGE))
            .withExposedPorts(SharedContainers.VALKEY_PORT)
            .withCreateContainerCmdModifier { command ->
                command.hostConfig!!.withPortBindings(
                    PortBinding(Ports.Binding.bindPort(port), ExposedPort(SharedContainers.VALKEY_PORT))
                )
            }
            .waitingFor(Wait.forListeningPort())
            .also { it.start() }

    private companion object {
        val DELIVERY_BOUND: Duration = Duration.ofSeconds(30)
        val RECONNECT_BOUND: Duration = Duration.ofSeconds(90)
        val PUBLISH_BOUND: Duration = Duration.ofSeconds(30)
        val RETRY_INTERVAL: Duration = Duration.ofMillis(200)
    }
}
