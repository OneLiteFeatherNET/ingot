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
import java.time.Instant
import java.time.ZoneOffset

/**
 * The hello of a browser (task 4.6): without a token it authenticates with the session cookie of the upgrade request, and the
 * `Origin` of that request has to name the host it reached. Driven through the connection seam, with no server and no wait.
 */
internal class ConsoleWebSocketCookieHelloTest : AuthenticationSpecification() {

    private val journalist = ReposiliteJournalist(InMemoryLogger(), 100, testEnv = true, noColor = true)
    private val sessionsSeen = mutableListOf<String>()
    private lateinit var handler: ConsoleWebSocketHandler

    @BeforeEach
    fun createHandler() {
        accessTokenFacade.createAccessToken(
            CreateAccessTokenRequest(TEMPORARY, "browser-admin", secret = "s3cret", permissions = setOf(MANAGER))
        )
        val executor = CommandExecutor(
            journalist = journalist,
            failureFacade = FailureFacade(InMemoryLogger(), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)),
            source = ByteArrayInputStream(ByteArray(0)),
        )
        // A real session needs the login store, so a stand-in resolves the one cookie value the tests use.
        val authenticator = object : ConsoleAuthenticator(
            accessTokenFacade,
            authenticationFacade,
            LoginFacade(service = null, sessions = null, ownTokens = null),
        ) {
            override fun authenticateSession(sessionJwt: String, ip: String): Result<AccessTokenDto, ErrorResponse> {
                sessionsSeen += sessionJwt
                return accessTokenFacade.getAccessToken("browser-admin")!!.asSuccess()
            }
        }
        handler = ConsoleWebSocketHandler(
            journalist = journalist,
            consoleAuthenticator = authenticator,
            consoleFacade = ConsoleFacade(journalist, executor),
            forwardedIp = Reference.reference("X-Forwarded-For"),
            audit = {},
            scheduler = FakeConsoleScheduler(),
            nodeId = "node-a",
        )
    }

    @AfterEach
    fun shutDownJournalist() {
        journalist.shutdown()
    }

    @Test
    fun `accepts a hello without a token when the cookie session and the origin name the host`() {
        // given: a browser that sends its session cookie and an Origin of the host it reached
        val connection = browser(origin = "https://repo.example.org", host = "repo.example.org")

        // when: it says hello without a token
        handler.handleMessage(connection, HELLO)

        // then: the session of the cookie was checked and the welcome follows
        assertThat(sessionsSeen).`as`("sessions checked").containsExactly("jwt-of-browser")
        assertThat(connection.json.first()).`as`("first frame").isInstanceOf(WelcomeFrame::class.java)
        assertThat(connection.closeCode).`as`("close code").isNull()
    }

    @Test
    fun `accepts an origin with a port that equals the host with its port`() {
        val connection = browser(origin = "http://localhost:8080", host = "localhost:8080")

        handler.handleMessage(connection, HELLO)

        assertThat(connection.json.first()).`as`("first frame").isInstanceOf(WelcomeFrame::class.java)
    }

    @Test
    fun `closes a cookie hello whose origin names another site with 1008 and unauthorized`() {
        val connection = browser(origin = "https://evil.example.net", host = "repo.example.org")

        handler.handleMessage(connection, HELLO)

        assertThat((connection.json.single() as ErrorFrame).code).`as`("error code").isEqualTo("unauthorized")
        assertThat(connection.closeCode).`as`("close code").isEqualTo(1008)
        assertThat(sessionsSeen).`as`("sessions checked before the origin was refused").isEmpty()
    }

    @Test
    fun `closes a cookie hello without an origin with 1008`() {
        val connection = browser(origin = null, host = "repo.example.org")

        handler.handleMessage(connection, HELLO)

        assertThat(connection.closeCode).`as`("close code").isEqualTo(1008)
        assertThat(sessionsSeen).`as`("sessions checked").isEmpty()
    }

    @Test
    fun `closes a cookie hello whose origin is not a URL with 1008`() {
        val connection = browser(origin = "null", host = "repo.example.org")

        handler.handleMessage(connection, HELLO)

        assertThat(connection.closeCode).`as`("close code").isEqualTo(1008)
    }

    @Test
    fun `lets a token hello pass whatever its origin is`() {
        // given: a client with a token in the hello that happens to send a foreign Origin
        val connection = FakeConsoleConnection(origin = "https://evil.example.net", requestHost = "repo.example.org")

        handler.handleMessage(connection, "{\"type\":\"hello\",\"protocols\":[1],\"token\":\"browser-admin:s3cret\"}")

        // then: a page of another site cannot read the token, so the origin is not asked
        assertThat(connection.json.first()).`as`("first frame").isInstanceOf(WelcomeFrame::class.java)
    }

    @Test
    fun `closes a hello without a token and without a cookie with 1008`() {
        val connection = FakeConsoleConnection(origin = "https://repo.example.org", requestHost = "repo.example.org")

        handler.handleMessage(connection, HELLO)

        assertThat((connection.json.single() as ErrorFrame).code).`as`("error code").isEqualTo("unauthorized")
        assertThat(connection.closeCode).`as`("close code").isEqualTo(1008)
    }

    private fun browser(origin: String?, host: String) =
        FakeConsoleConnection(origin = origin, requestHost = host, cookies = mapOf(SessionCookies.SESSION to "jwt-of-browser"))

    private companion object {
        const val HELLO = "{\"type\":\"hello\",\"protocols\":[1]}"
    }

}
