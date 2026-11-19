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
import com.reposilite.audit.api.AuditEvent
import com.reposilite.auth.specification.AuthenticationSpecification
import com.reposilite.console.CommandContext
import com.reposilite.console.CommandExecutor
import com.reposilite.console.CommandStatus
import com.reposilite.console.ConsoleFacade
import com.reposilite.console.api.ReposiliteCommand
import com.reposilite.journalist.backend.InMemoryLogger
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
import picocli.CommandLine.Parameters
import java.io.ByteArrayInputStream
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * The version 1 handshake (task 4.2) and the execute, result and confirm frames (task 4.3), driven through the connection and
 * scheduler seam: no server, no socket and no wall-clock wait. Every frame the handler sends has arrived when the call returns.
 */
internal class ConsoleWebSocketV1ProtocolTest : AuthenticationSpecification() {

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
        consoleFacade.registerCommand(V1ProbeCommand(executions))
        consoleFacade.registerCommand(V1DangerousProbeCommand(executions))
        consoleFacade.registerCommand(V1SensitiveProbeCommand(executions))
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
    fun `welcomes a hello that lists protocol 1 with protocol 1`() {
        // when: the hello lists protocols 1 and 2
        handler.handleMessage(connection, hello(protocols = "[1,2]"))

        // then: the only frame is the welcome for protocol 1
        assertThat(connection.json.single()).`as`("the only frame").isInstanceOf(WelcomeFrame::class.java)
        assertThat((connection.json.single() as WelcomeFrame).protocol).`as`("protocol").isEqualTo(1)
        assertThat(connection.closeCode).`as`("close code").isNull()
    }

    @Test
    fun `closes a hello without protocol 1 with 1002 and unsupported_protocol`() {
        // when: the hello lists only protocol 2
        handler.handleMessage(connection, hello(protocols = "[2]"))

        // then: the error names the supported protocols and the connection closes with 1002
        val error = connection.json.single() as ErrorFrame
        assertThat(error.code).`as`("error code").isEqualTo("unsupported_protocol")
        assertThat(error.supported).`as`("supported protocols").containsExactly(1)
        assertThat(connection.closeCode).`as`("close code").isEqualTo(1002)
    }

    @Test
    fun `answers an unknown type with unknown_type and the id, and keeps the connection open`() {
        // given: a welcomed connection
        welcome()

        // when: the client sends a type the protocol does not know
        handler.handleMessage(connection, """{"type":"reboot","id":"r1"}""")

        // then: the error carries the id and the connection stays open
        val error = connection.json.last() as ErrorFrame
        assertThat(error.code).`as`("error code").isEqualTo("unknown_type")
        assertThat(error.id).`as`("request id").isEqualTo("r1")
        assertThat(connection.closeCode).`as`("close code").isNull()
    }

    @Test
    fun `answers a frame over 4 KiB with too_long and keeps the connection open`() {
        // given: a welcomed connection
        welcome()

        // when: the client sends a frame of more than 4 KiB
        handler.handleMessage(connection, """{"type":"execute","id":"e1","command":"${"x".repeat(4096)}"}""")

        // then: the error is too_long and the connection stays open
        assertThat((connection.json.last() as ErrorFrame).code).`as`("error code").isEqualTo("too_long")
        assertThat(connection.closeCode).`as`("close code").isNull()
    }

    @Test
    fun `runs an execute and answers one result with its id, exit code and node`() {
        // given: a welcomed connection
        welcome()

        // when: the client executes a command that succeeds
        handler.handleMessage(connection, execute(id = "e1", command = "probe"))

        // then: exactly one result answers the request, names the node and the command ran
        val result = connection.json.last() as ResultFrame
        assertThat(connection.json.filterIsInstance<ResultFrame>()).`as`("result frames").hasSize(1)
        assertThat(result.id).`as`("request id").isEqualTo("e1")
        assertThat(result.exitCode).`as`("exit code").isEqualTo(0)
        assertThat(result.status).`as`("status").isEqualTo(CommandStatus.SUCCEEDED)
        assertThat(result.node).`as`("node").isEqualTo("node-a")
        assertThat(result.confirmed).`as`("confirmed").isFalse
        assertThat(executions).`as`("executions").containsExactly("probe")
    }

    @Test
    fun `answers a second execute with busy and retryable while a confirmation is pending`() {
        // given: a dangerous execute that waits for its confirmation
        welcome()
        handler.handleMessage(connection, execute(id = "e1", command = "danger-probe"))

        // when: a second execute arrives
        handler.handleMessage(connection, execute(id = "e2", command = "probe"))

        // then: the second is answered busy and retryable, and does not run
        val error = connection.json.last() as ErrorFrame
        assertThat(error.code).`as`("error code").isEqualTo("busy")
        assertThat(error.id).`as`("request id").isEqualTo("e2")
        assertThat(error.retryable).`as`("retryable").isTrue
        assertThat(executions).`as`("executions").isEmpty()
    }

    @Test
    fun `holds a dangerous command until an accepted confirm and then runs it with confirmed true`() {
        // given: a dangerous execute
        welcome()
        handler.handleMessage(connection, execute(id = "e1", command = "danger-probe"))
        assertThat(connection.json.last()).`as`("confirm frame").isEqualTo(ConfirmFrame(id = "e1", command = "danger-probe", reason = V1_DANGER_REASON))
        assertThat(executions).`as`("executions before the answer").isEmpty()

        // when: the client accepts
        handler.handleMessage(connection, confirm(id = "e1", accept = true))

        // then: the command runs and the result says it was confirmed
        val result = connection.json.last() as ResultFrame
        assertThat(executions).`as`("executions").containsExactly("danger-probe")
        assertThat(result.id).`as`("request id").isEqualTo("e1")
        assertThat(result.exitCode).`as`("exit code").isEqualTo(0)
        assertThat(result.confirmed).`as`("confirmed").isTrue
    }

    @Test
    fun `answers a declined confirm with exit code 3 and runs nothing`() {
        // given: a dangerous execute
        welcome()
        handler.handleMessage(connection, execute(id = "e1", command = "danger-probe"))

        // when: the client declines
        handler.handleMessage(connection, confirm(id = "e1", accept = false))

        // then: nothing runs and the result has exit code 3 and confirmed false
        val result = connection.json.last() as ResultFrame
        assertThat(executions).`as`("executions").isEmpty()
        assertThat(result.exitCode).`as`("exit code").isEqualTo(3)
        assertThat(result.confirmed).`as`("confirmed").isFalse
    }

    @Test
    fun `answers a confirm that is not answered within 120 seconds with exit code 3 and runs nothing`() {
        // given: a dangerous execute
        welcome()
        handler.handleMessage(connection, execute(id = "e1", command = "danger-probe"))

        // when: 119 seconds pass, then one more
        scheduler.advance(Duration.ofSeconds(119))
        val resultBeforeDeadline = connection.json.filterIsInstance<ResultFrame>()
        scheduler.advance(Duration.ofSeconds(1))

        // then: no result before the deadline, and then exit code 3 with nothing run
        assertThat(resultBeforeDeadline).`as`("result before the deadline").isEmpty()
        val result = connection.json.last() as ResultFrame
        assertThat(result.exitCode).`as`("exit code").isEqualTo(3)
        assertThat(executions).`as`("executions").isEmpty()
    }

    @Test
    fun `names a sensitive command in the confirm frame without its arguments`() {
        // given: a welcomed connection
        welcome()

        // when: a sensitive command with a secret argument needs confirmation
        handler.handleMessage(connection, execute(id = "e1", command = "token-regenerate ci s3cr3t"))

        // then: the confirm frame names the command and does not contain the secret
        val confirm = connection.json.last() as ConfirmFrame
        assertThat(confirm.command).`as`("command").isEqualTo("token-regenerate")
        assertThat(connection.json.toString()).`as`("frames").doesNotContain("s3cr3t")
    }

    private fun welcome() {
        handler.handleMessage(connection, hello(protocols = "[1]"))
        connection.json.clear()
    }

    private fun hello(protocols: String): String =
        "{\"type\":\"hello\",\"protocols\":$protocols,\"token\":\"deploy-bot:s3cret\"}"

    private fun execute(id: String, command: String): String =
        "{\"type\":\"execute\",\"id\":\"$id\",\"command\":\"$command\"}"

    private fun confirm(id: String, accept: Boolean): String =
        "{\"type\":\"confirm\",\"id\":\"$id\",\"accept\":$accept}"

}

private const val V1_DANGER_REASON = "Dangerous probe"

@Command(name = "probe", description = ["Records that it has been executed"])
private class V1ProbeCommand(private val executions: MutableList<String>) : ReposiliteCommand {

    override fun execute(context: CommandContext) {
        executions += "probe"
    }

}

@Command(name = "danger-probe", description = [V1_DANGER_REASON])
private class V1DangerousProbeCommand(private val executions: MutableList<String>) : ReposiliteCommand {

    override val dangerous: Boolean = true

    override fun execute(context: CommandContext) {
        executions += "danger-probe"
    }

}

@Command(name = "token-regenerate", description = ["Regenerates a token"])
private class V1SensitiveProbeCommand(private val executions: MutableList<String>) : ReposiliteCommand {

    @Parameters(index = "0", arity = "0..*", description = ["Arguments"])
    var arguments: List<String> = emptyList()

    override val dangerous: Boolean = true
    override val sensitiveInput: Boolean = true

    override fun execute(context: CommandContext) {
        executions += "token-regenerate"
    }

}
