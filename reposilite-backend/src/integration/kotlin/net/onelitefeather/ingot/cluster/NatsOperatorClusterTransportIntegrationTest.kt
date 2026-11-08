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

import com.reposilite.natsUrl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import org.testcontainers.containers.GenericContainer
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.LinkedBlockingQueue

/**
 * A broker in operator (decentralised JWT) mode. It accepts no user and password: an instance joins only with the `.creds`
 * file of a user the operator signed. The operator, account, user and credentials are generated for this test class and
 * never leave the test run.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class NatsOperatorClusterTransportIntegrationTest {

    private val fixture = NatsOperatorFixture(Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneOffset.UTC))
    private val transports = mutableListOf<NatsClusterTransport>()
    private lateinit var broker: GenericContainer<*>

    @BeforeAll
    fun startOperatorBroker() {
        broker = natsBroker(configuration = fixture.serverConfiguration()).also { it.start() }
    }

    @AfterAll
    fun stopOperatorBroker() {
        transports.forEach { it.close() }
        broker.stop()
    }

    @Test
    fun `an instance joins an operator mode broker with its credentials file and exchanges events`(@TempDir directory: Path) {
        // given: the user's credentials file, handed to the instance through the credentials setting
        val credentials = directory.resolve("ingot.creds")
        Files.writeString(credentials, fixture.credentialsFile())
        val url = broker.natsUrl()
        val publisher = openNatsTransport(url, cluster = "operator", credentials = credentials).also { transports += it }
        val subscriber = openNatsTransport(url, cluster = "operator", credentials = credentials).also { transports += it }
        val received = LinkedBlockingQueue<String>()
        subscriber.subscribe { received.offer(it) }

        // when: both instances connect with the credentials file
        assertThat(publisher.awaitState(ConnectionState.CONNECTED, CONNECT_BOUND)).describedAs("publisher joined the operator broker").isTrue()
        assertThat(subscriber.awaitState(ConnectionState.CONNECTED, CONNECT_BOUND)).describedAs("subscriber joined the operator broker").isTrue()
        assertThat(subscriber.flushSubscriptions(CONNECT_BOUND)).isTrue()
        publisher.publish(OPERATOR_MESSAGE)

        // then: the event crosses the broker
        assertThat(received.awaitMessage(OPERATOR_MESSAGE)).describedAs("the event reached the subscriber").isTrue()
    }

    private companion object {
        const val OPERATOR_MESSAGE = """{"type":"token.changed","name":"operator-bot"}"""
    }
}
