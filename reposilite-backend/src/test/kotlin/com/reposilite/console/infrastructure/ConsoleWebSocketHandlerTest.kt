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

package com.reposilite.console.infrastructure

import com.reposilite.ReposiliteJournalist
import com.reposilite.audit.api.AuditActor
import com.reposilite.audit.api.AuditEvent
import com.reposilite.auth.specification.AuthenticationSpecification
import com.reposilite.console.CommandContext
import com.reposilite.console.CommandExecutor
import com.reposilite.console.ConsoleFacade
import com.reposilite.console.api.ReposiliteCommand
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.shared.ErrorResponse
import com.reposilite.status.FailureFacade
import com.reposilite.token.AccessTokenPermission.MANAGER
import com.reposilite.token.AccessTokenType.TEMPORARY
import com.reposilite.token.api.CreateAccessTokenRequest
import net.onelitefeather.ingot.identity.login.LoginFacade
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import panda.std.reactive.Reference
import picocli.CommandLine.Command
import java.io.ByteArrayInputStream
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * Drives the legacy text protocol of the console socket through [ConsoleConnection] and [ConsoleScheduler], with no
 * server, no real socket and no writer thread: every line the handler sends has arrived when the call returns.
 */
internal class ConsoleWebSocketHandlerTest : AuthenticationSpecification() {

    private val journalist = ReposiliteJournalist(InMemoryLogger(), 100, testEnv = true, noColor = true)
    private val scheduler = FakeConsoleScheduler()
    private val audits = mutableListOf<AuditEvent>()
    private val executions = mutableListOf<String>()
    private val connection = FakeConsoleConnection()
    private lateinit var handler: ConsoleWebSocketHandler

    @BeforeEach
    fun createHandler() {
        accessTokenFacade.createAccessToken(
            CreateAccessTokenRequest(TEMPORARY, "deploy-bot", secret = "s3cret", permissions = setOf(MANAGER))
        )
        val executor = CommandExecutor(
            journalist = journalist,
            failureFacade = FailureFacade(InMemoryLogger(), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)),
            source = ByteArrayInputStream(ByteArray(0)),
        )
        val consoleFacade = ConsoleFacade(journalist, executor)
        consoleFacade.registerCommand(ProbeCommand(executions))
        handler = ConsoleWebSocketHandler(
            journalist = journalist,
            consoleAuthenticator = ConsoleAuthenticator(
                accessTokenFacade,
                authenticationFacade,
                LoginFacade(service = null, sessions = null, ownTokens = null),
            ),
            consoleFacade = consoleFacade,
            forwardedIp = Reference.reference("X-Forwarded-For"),
            audit = { audits += it },
            scheduler = scheduler,
            nodeId = "node-a",
        )
    }

    @AfterEach
    fun shutDownJournalist() {
        journalist.shutdown()
    }

    @Test
    fun `replays the visible cached lines to a connection that sends valid token credentials`() {
        // given: a line logged before the connection
        journalist.cachedLogger.info("line from before the connection")

        // when: the legacy first frame carries valid token credentials
        handler.handleMessage(connection, "Authorization:deploy-bot:s3cret")

        // then: the cached line arrives as raw text, and the connection stays open without a json frame
        assertThat(connection.texts).`as`("replayed lines").anyMatch { it.contains("line from before the connection") }
        assertThat(connection.json).`as`("json frames").isEmpty()
        assertThat(connection.disconnected).`as`("disconnected").isFalse
    }

    @Test
    fun `forwards a line logged after authentication to the connection`() {
        // given: an authenticated connection
        handler.handleMessage(connection, "Authorization:deploy-bot:s3cret")

        // when: a visible line is logged
        journalist.visibleLogger.info("live line")

        // then: the line was delivered through the outbox the scheduler opened
        assertThat(connection.texts).`as`("forwarded lines").contains("live line")
    }

    @Test
    fun `keeps a silent socket open and accepts its credentials after eleven seconds`() {
        // given: a new socket that sends nothing for eleven seconds
        scheduler.advance(Duration.ofSeconds(11))

        // when: the legacy first frame arrives late
        handler.handleMessage(connection, "Authorization:deploy-bot:s3cret")

        // then: nothing closed the socket, and the late credentials open the session as before
        assertThat(connection.closeCode).`as`("close code").isNull()
        assertThat(connection.disconnected).`as`("disconnected").isFalse
        assertThat(connection.json).`as`("json frames").isEmpty()
        assertThat(scheduler.outboxes).`as`("opened outboxes").hasSize(1)
    }

    @Test
    fun `answers keep-alive with keep-alive`() {
        // given: an authenticated connection
        handler.handleMessage(connection, "Authorization:deploy-bot:s3cret")

        // when: the client sends a keep-alive
        handler.handleMessage(connection, "keep-alive")

        // then: the last frame is the keep-alive answer
        assertThat(connection.texts.last()).`as`("last frame").isEqualTo("keep-alive")
    }

    @Test
    fun `answers a wrong secret with the error, disconnects and forwards no line`() {
        // when: the first frame carries a wrong secret
        handler.handleMessage(connection, "Authorization:deploy-bot:wrong")
        journalist.visibleLogger.info("live line")

        // then: one 401 error was sent as json, the connection was dropped and no outbox was opened
        assertThat((connection.json.single() as ErrorResponse).status).`as`("error status").isEqualTo(401)
        assertThat(connection.disconnected).`as`("disconnected").isTrue
        assertThat(connection.texts).`as`("text frames").isEmpty()
        assertThat(scheduler.outboxes).`as`("opened outboxes").isEmpty()
    }

    @Test
    fun `refuses a first frame without credentials and names the host of the connection`() {
        // when: the first frame is a command instead of credentials
        handler.handleMessage(connection, "help")

        // then: the error names the missing credentials and the host the connection reports
        val error = connection.json.single() as ErrorResponse
        assertThat(error.message).`as`("error message").contains("missing credentials").contains("203.0.113.7")
        assertThat(connection.disconnected).`as`("disconnected").isTrue
    }

    @Test
    fun `runs a command without an answer frame and audits it as a websocket command without client`() {
        // given: an authenticated connection
        handler.handleMessage(connection, "Authorization:deploy-bot:s3cret")
        val framesBefore = connection.texts.size

        // when: a command is sent
        handler.handleMessage(connection, "probe")

        // then: the command ran, nothing was sent back, and the audit event is the legacy one
        assertThat(executions).`as`("executions").containsExactly("probe")
        assertThat(connection.texts).`as`("text frames after the command").hasSize(framesBefore)
        assertThat(connection.json).`as`("json frames").isEmpty()
        val event = audits.single()
        assertThat(event.actor).`as`("actor").isEqualTo(AuditActor.named("deploy-bot", "203.0.113.7"))
        assertThat(event.details).`as`("details")
            .containsEntry("channel", "websocket")
            .containsEntry("client", null)
            .containsEntry("confirmed", false)
            .containsEntry("exitCode", 0)
    }

    @Test
    fun `stops forwarding lines once the connection is released`() {
        // given: an authenticated connection
        handler.handleMessage(connection, "Authorization:deploy-bot:s3cret")

        // when: the connection is released and a line is logged afterwards
        handler.release(connection)
        journalist.visibleLogger.info("line after close")

        // then: the outbox is closed and the line never arrived
        assertThat(scheduler.outboxes.single().closed).`as`("outbox closed").isTrue
        assertThat(connection.texts).`as`("text frames").doesNotContain("line after close")
    }

    @Test
    fun `runs a scheduled timer only once the fake clock has passed its delay`() {
        // given: a timer in ten seconds
        val fired = mutableListOf<Instant>()
        scheduler.schedule(Duration.ofSeconds(10)) { fired += scheduler.clock.instant() }

        // when: the clock moves nine seconds, then one more
        scheduler.advance(Duration.ofSeconds(9))
        val firedEarly = fired.toList()
        scheduler.advance(Duration.ofSeconds(1))

        // then: the timer did not run early, and ran once at its time
        assertThat(firedEarly).`as`("runs before the delay").isEmpty()
        assertThat(fired).`as`("runs after the delay").containsExactly(Instant.EPOCH.plusSeconds(10))
    }

}

@Command(name = "probe", description = ["Records that it has been executed"])
private class ProbeCommand(private val executions: MutableList<String>) : ReposiliteCommand {

    override fun execute(context: CommandContext) {
        executions += "probe"
    }

}
