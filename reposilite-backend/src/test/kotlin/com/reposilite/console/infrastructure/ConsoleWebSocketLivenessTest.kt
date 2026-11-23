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
import com.reposilite.auth.specification.AuthenticationSpecification
import com.reposilite.console.CommandExecutor
import com.reposilite.console.ConsoleFacade
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.shared.ErrorResponse
import com.reposilite.status.FailureFacade
import com.reposilite.token.AccessTokenPermission.MANAGER
import com.reposilite.token.AccessTokenType.TEMPORARY
import com.reposilite.token.api.AccessTokenDto
import com.reposilite.token.api.CreateAccessTokenRequest
import net.onelitefeather.ingot.identity.login.LoginFacade
import net.onelitefeather.ingot.identity.session.SessionCookies
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import panda.std.Result
import panda.std.asSuccess
import panda.std.reactive.Reference
import java.io.ByteArrayInputStream
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * The liveness of a version 1 connection (task 4.7): a ping every 20 seconds, and a close at the expiry time of a browser session. The timers run on the fake scheduler, so no test waits.
 */
internal class ConsoleWebSocketLivenessTest : AuthenticationSpecification() {

    private val journalist = ReposiliteJournalist(InMemoryLogger(), 100, testEnv = true, noColor = true)
    private val scheduler = FakeConsoleScheduler()
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
        // A real session needs the login store, so a stand-in accepts the one cookie value the tests use and ends it at a fixed time.
        val authenticator = object : ConsoleAuthenticator(
            accessTokenFacade,
            authenticationFacade,
            LoginFacade(service = null, sessions = null, ownTokens = null),
        ) {
            override fun authenticateSession(sessionJwt: String, ip: String): Result<AccessTokenDto, ErrorResponse> =
                accessTokenFacade.getAccessToken("deploy-bot")!!.asSuccess()

            override fun sessionExpiry(sessionJwt: String): Instant? = Instant.EPOCH.plus(SESSION_LIFETIME)
        }
        handler = ConsoleWebSocketHandler(
            journalist = journalist,
            consoleAuthenticator = authenticator,
            consoleFacade = ConsoleFacade(journalist, executor),
            forwardedIp = Reference.reference("X-Forwarded-For"),
            audit = {},
            scheduler = scheduler,
            nodeId = "node-a",
        )
    }

    @AfterEach
    fun shutDownJournalist() {
        journalist.shutdown()
    }

    @Test
    fun `sends a ping every 20 seconds`() {
        val connection = connected()

        scheduler.advance(Duration.ofSeconds(19))
        assertThat(connection.pings).`as`("pings after 19 seconds").isEqualTo(0)

        scheduler.advance(Duration.ofSeconds(1))
        assertThat(connection.pings).`as`("pings after 20 seconds").isEqualTo(1)

        scheduler.advance(Duration.ofSeconds(20))
        assertThat(connection.pings).`as`("pings after 40 seconds").isEqualTo(2)
    }

    @Test
    fun `drops a connection whose ping cannot be written and pings it no more`() {
        val connection = FakeConsoleConnection(failPing = true)
        handler.handleMessage(connection, "{\"type\":\"hello\",\"protocols\":[1],\"token\":\"deploy-bot:s3cret\"}")

        scheduler.advance(Duration.ofSeconds(20))
        scheduler.advance(Duration.ofSeconds(20))

        assertThat(connection.disconnected).`as`("disconnected").isTrue()
        assertThat(connection.pings).`as`("ping attempts").isEqualTo(1)
    }

    @Test
    fun `sends no ping to a connection that was released`() {
        val connection = connected()

        handler.release(connection)
        scheduler.advance(Duration.ofMinutes(5))

        assertThat(connection.pings).`as`("pings after the release").isEqualTo(0)
        assertThat(connection.closeCode).`as`("close code after the release").isNull()
    }

    @Test
    fun `closes a browser session at its expiry with 1008 and unauthorized`() {
        val connection = FakeConsoleConnection(
            origin = "https://repo.example.org",
            requestHost = "repo.example.org",
            cookies = mapOf(SessionCookies.SESSION to "jwt-of-browser"),
        )
        handler.handleMessage(connection, "{\"type\":\"hello\",\"protocols\":[1]}")
        connection.json.clear()
        scheduler.advance(Duration.ofSeconds(SESSION_LIFETIME.seconds - 1))
        assertThat(connection.closeCode).`as`("close code before the expiry").isNull()

        scheduler.advance(Duration.ofSeconds(1))

        assertThat((connection.json.last() as ErrorFrame).code).`as`("error code").isEqualTo("unauthorized")
        assertThat(connection.closeCode).`as`("close code at the expiry").isEqualTo(1008)
    }

    @Test
    fun `leaves a token connection open past the time a session would expire`() {
        val connection = connected()

        scheduler.advance(SESSION_LIFETIME.plusSeconds(60))

        assertThat(connection.closeCode).`as`("close code").isNull()
    }

    private fun connected(): FakeConsoleConnection {
        val connection = FakeConsoleConnection()
        handler.handleMessage(connection, "{\"type\":\"hello\",\"protocols\":[1],\"token\":\"deploy-bot:s3cret\"}")
        assertThat(connection.json.first()).`as`("first frame").isInstanceOf(WelcomeFrame::class.java)
        return connection
    }

    private companion object {
        val SESSION_LIFETIME: Duration = Duration.ofMinutes(5)
    }

}
