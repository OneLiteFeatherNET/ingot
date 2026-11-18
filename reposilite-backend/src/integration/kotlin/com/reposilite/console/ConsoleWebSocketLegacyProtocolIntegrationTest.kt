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

package com.reposilite.console

import com.fasterxml.jackson.databind.ObjectMapper
import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteSpecification
import com.reposilite.audit.AuditFacade
import com.reposilite.audit.AuditQuery
import com.reposilite.audit.api.AuditAction.CONSOLE_COMMAND_EXECUTED
import com.reposilite.journalist.Channel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

/**
 * Golden tests of the legacy text protocol on `/api/console/sock`: a first frame `Authorization:...` gets raw log lines,
 * a command gets no answer frame, failed authentication gets the error and a disconnect, and the audit event of a legacy
 * command carries no client. The protocol must stay byte compatible for the scripts that use it. Every wait is bounded by
 * a poll timeout that only fires when a reply never arrives.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class ConsoleWebSocketLegacyProtocolIntegrationTest : ReposiliteSpecification() {

    private val json = ObjectMapper()

    private val sockets = mutableListOf<ConsoleSocket>()

    /** The startup log is replayed to an authenticated connection, and only the visible threshold decides what is replayed. */
    @BeforeEach
    fun showConnectionLog() {
        reposilite.journalist.setVisibleThreshold(Channel.INFO)
    }

    @AfterEach
    fun closeConnections() {
        sockets.forEach { it.close() }
    }

    @Test
    fun `answers the first frame of a legacy token with raw log lines and no json frame`() {
        // given: the default manager token, sent as the legacy first frame
        val (name, secret) = useDefaultManagementToken()
        val socket = openSocket()

        // when: the credentials are sent and a keep-alive follows them
        socket.send("Authorization:$name:$secret")
        socket.send("keep-alive")

        // then: the log lines arrive as plain text before the keep-alive answer, and no frame is a json object
        val received = socket.receiveUntil { it == "keep-alive" }
        assertThat(received.filter { it.contains(INFO_LINE) })
            .describedAs("raw log lines before the keep-alive answer")
            .isNotEmpty
        assertThat(received.filter { it.startsWith("{") })
            .describedAs("json frames before the keep-alive answer")
            .isEmpty()
    }

    @Test
    fun `sends no answer frame for a legacy command`() {
        // given: an authenticated legacy connection
        val (name, secret) = useDefaultManagementToken()
        val socket = openSocket()
        socket.send("Authorization:$name:$secret")

        // when: a command is sent and a keep-alive follows it, so the command has been handled once the answer arrives
        socket.send("help")
        socket.send("keep-alive")

        // then: no structured answer frame was sent for the command
        val received = socket.receiveUntil { it == "keep-alive" }
        assertThat(received.filter { it.startsWith("{") })
            .describedAs("structured answer frames for a legacy command")
            .isEmpty()
    }

    @Test
    fun `answers failed authentication with the error and then closes the connection`() {
        // given: the default manager name with a wrong secret
        val (name, _) = useDefaultManagementToken()
        val socket = openSocket()

        // when: the wrong credentials are sent as the first frame
        socket.send("Authorization:$name:wrong-secret")

        // then: the error arrives and the server closes the connection
        assertThat(socket.receiveUntil { it.contains("\"status\":401") })
            .describedAs("error frame of failed authentication")
            .anyMatch { it.contains("Invalid authorization credentials") }
        assertThat(socket.awaitClosed())
            .describedAs("connection closed after failed authentication")
            .isTrue
    }

    @Test
    fun `audits a legacy command as a websocket command without client and without confirmation`() {
        // given: an authenticated legacy connection
        val (name, secret) = useDefaultManagementToken()
        val socket = openSocket()
        socket.send("Authorization:$name:$secret")

        // when: a command is sent and a keep-alive follows it, so the audit event has been written once the answer arrives
        socket.send("help")
        socket.send("keep-alive")
        socket.receiveUntil { it == "keep-alive" }

        // then: the event names the token as actor, the websocket channel, no confirmation and no client
        val record = useFacade<AuditFacade>()
            .find(AuditQuery(actions = setOf(CONSOLE_COMMAND_EXECUTED))).records
            .single { it.details.orEmpty().contains("\"command\":\"help\"") && it.details.orEmpty().contains("\"channel\":\"websocket\"") }
        assertThat(record.actor).describedAs("actor of the legacy command").isEqualTo(name)
        val details = json.readTree(record.details!!)
        assertThat(details.get("confirmed").asBoolean()).describedAs("confirmed flag of a plain command").isFalse
        assertThat(details.has("client") && !details.get("client").isNull)
            .describedAs("client of a legacy command is absent or null")
            .isFalse
    }

    private fun openSocket(): ConsoleSocket =
        ConsoleSocket(URI.create("${base.replace("http", "ws")}/api/console/sock")).also { sockets += it }

    private companion object {
        const val INFO_LINE = " INFO | "
    }

}
