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

import com.reposilite.SharedContainers
import com.reposilite.natsUrl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.LinkedBlockingQueue

/**
 * The NATS transport against a real broker: one shared broker for the round trip, and per-test brokers for the
 * restart and the late start. Every test uses its own cluster identifier and closes its transports afterwards.
 */
internal class NatsClusterTransportIntegrationTest {

    private val transports = mutableListOf<NatsClusterTransport>()

    @AfterEach
    fun closeTransports() {
        transports.forEach { it.close() }
    }

    private fun open(url: String, cluster: String): NatsClusterTransport =
        openNatsTransport(url, cluster).also { transports += it }

    @Test
    fun `an event published on one connection reaches a subscriber on another`() {
        // given: a publisher and a subscriber on the shared broker, both connected and subscribed
        val url = SharedContainers.nats.natsUrl()
        val publisher = open(url, cluster = "round-trip")
        val subscriber = open(url, cluster = "round-trip")
        val received = LinkedBlockingQueue<String>()
        subscriber.subscribe { received.offer(it) }
        assertThat(publisher.awaitState(ConnectionState.CONNECTED, CONNECT_BOUND)).describedAs("publisher connected").isTrue()
        assertThat(subscriber.awaitState(ConnectionState.CONNECTED, CONNECT_BOUND)).describedAs("subscriber connected").isTrue()
        assertThat(subscriber.flushSubscriptions(CONNECT_BOUND)).describedAs("subscription known to the broker").isTrue()

        // when: the publisher sends one envelope
        publisher.publish(ROUND_TRIP_MESSAGE)

        // then: the subscriber receives it within the bound
        assertThat(received.awaitMessage(ROUND_TRIP_MESSAGE)).describedAs("the event reached the subscriber").isTrue()
    }

    @Test
    fun `a connection reconnects after the broker restarts and delivers events again`() {
        // given: a broker on a fixed port, with a connected publisher and subscriber
        val port = freePort()
        val broker = natsBroker(fixedPort = port)
        broker.start()
        try {
            val url = localNatsUrl(port)
            val publisher = open(url, cluster = "restart")
            val subscriber = open(url, cluster = "restart")
            val received = LinkedBlockingQueue<String>()
            subscriber.subscribe { received.offer(it) }
            assertThat(publisher.awaitState(ConnectionState.CONNECTED, CONNECT_BOUND)).isTrue()
            assertThat(subscriber.awaitState(ConnectionState.CONNECTED, CONNECT_BOUND)).isTrue()
            assertThat(subscriber.flushSubscriptions(CONNECT_BOUND)).isTrue()

            // when: the broker stops and starts again on the same address
            broker.stop()
            assertThat(publisher.awaitState(ConnectionState.RECONNECTING, CONNECT_BOUND)).describedAs("publisher noticed the outage").isTrue()
            assertThat(subscriber.awaitState(ConnectionState.RECONNECTING, CONNECT_BOUND)).describedAs("subscriber noticed the outage").isTrue()
            broker.start()
            assertThat(publisher.awaitState(ConnectionState.CONNECTED, CONNECT_BOUND)).describedAs("publisher reconnected").isTrue()
            assertThat(subscriber.awaitState(ConnectionState.CONNECTED, CONNECT_BOUND)).describedAs("subscriber reconnected").isTrue()
            assertThat(subscriber.flushSubscriptions(CONNECT_BOUND)).describedAs("subscription restored").isTrue()
            publisher.publish(ROUND_TRIP_MESSAGE)

            // then: the event published after the restart reaches the subscriber
            assertThat(received.awaitMessage(ROUND_TRIP_MESSAGE)).describedAs("the event reached the subscriber after the restart").isTrue()
        } finally {
            broker.stop()
        }
    }

    @Test
    fun `an instance started while the broker is down connects once the broker starts`() {
        // given: an address with no broker behind it yet
        val port = freePort()
        val url = localNatsUrl(port)

        // when: the publisher is opened while the broker is down, it neither blocks nor reports itself connected
        val publisher = assertTimeoutPreemptively<NatsClusterTransport>(Duration.ofSeconds(10)) { open(url, cluster = "late-start") }
        assertTimeoutPreemptively<Result<Unit>>(Duration.ofSeconds(5)) { runCatching { publisher.publish("while down") } }
        assertThat(publisher.connectionState()).isNotEqualTo(ConnectionState.CONNECTED)

        // and: the broker starts later
        val broker = natsBroker(fixedPort = port)
        broker.start()
        try {
            val subscriber = open(url, cluster = "late-start")
            val received = LinkedBlockingQueue<String>()
            subscriber.subscribe { received.offer(it) }
            assertThat(publisher.awaitState(ConnectionState.CONNECTED, CONNECT_BOUND)).describedAs("publisher connected later").isTrue()
            assertThat(subscriber.awaitState(ConnectionState.CONNECTED, CONNECT_BOUND)).isTrue()
            assertThat(subscriber.flushSubscriptions(CONNECT_BOUND)).isTrue()
            publisher.publish(ROUND_TRIP_MESSAGE)

            // then: an event published once connected reaches the subscriber
            assertThat(received.awaitMessage(ROUND_TRIP_MESSAGE)).describedAs("the event reached the subscriber").isTrue()
        } finally {
            broker.stop()
        }
    }

    private companion object {
        const val ROUND_TRIP_MESSAGE = """{"type":"token.changed","name":"ci-bot"}"""
    }
}
