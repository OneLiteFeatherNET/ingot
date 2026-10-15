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
package net.onelitefeather.ingot.identity.storage

import com.reposilite.auth.AuthenticationFacade
import com.reposilite.auth.Authenticator
import com.reposilite.auth.BasicAuthenticator
import com.reposilite.auth.api.Credentials
import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.shared.ErrorResponse
import com.reposilite.token.AccessTokenFacade
import com.reposilite.token.AccessTokenType.PERSISTENT
import com.reposilite.token.api.AccessTokenDto
import com.reposilite.token.api.CreateAccessTokenRequest
import com.reposilite.token.application.AccessTokenComponents
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.Result
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TreeSet

/**
 * When a persistent token and its principal were last used. The clock is moved by the test, so the five minute throttle is
 * exercised without waiting. Every test starts from an empty database.
 */
internal class TokenUsageTrackingTest {

    private val journalist = InMemoryLogger()
    private val start: Instant = Instant.parse("2026-10-15T10:00:00Z")
    private val clock = MovableClock(start)

    @TempDir
    lateinit var workingDirectory: Path

    @Test
    fun `the first use of a token records its time and address, and the time of its principal`() {
        withFacade { facade, store ->
            // given: a persistent token that was never used
            val token = facade.createAccessToken(CreateAccessTokenRequest(PERSISTENT, "ci-bot", secret = "ci-secret")).accessToken
            assertThat(facade.getAccessTokenDetails(token).lastUsedAt).describedAs("last use before any authentication").isNull()

            // when: it is used from an address
            facade.recordTokenUsage(token.identifier, "10.0.0.7")

            // then: the token and its principal carry the time, and the token carries the address
            val details = facade.getAccessTokenDetails(token)
            assertThat(details.lastUsedAt).describedAs("token last used at").isEqualTo(start)
            assertThat(details.lastUsedIp).describedAs("token last used from").isEqualTo("10.0.0.7")
            assertThat(store.findPrincipal("ci-bot")?.lastSeenAt).describedAs("principal last seen at").isEqualTo(start)
        }
    }

    @Test
    fun `a use within five minutes of the last write is not written`() {
        withFacade { facade, _ ->
            // given: a use recorded at the start
            val token = facade.createAccessToken(CreateAccessTokenRequest(PERSISTENT, "ci-bot", secret = "ci-secret")).accessToken
            facade.recordTokenUsage(token.identifier, "10.0.0.7")

            // when: another use comes four minutes later from another address
            clock.advance(Duration.ofMinutes(4))
            facade.recordTokenUsage(token.identifier, "10.0.0.8")

            // then: the first write stands
            val details = facade.getAccessTokenDetails(token)
            assertThat(details.lastUsedAt).describedAs("last use after a throttled call").isEqualTo(start)
            assertThat(details.lastUsedIp).describedAs("address after a throttled call").isEqualTo("10.0.0.7")
        }
    }

    @Test
    fun `a use five minutes after the last write is written`() {
        withFacade { facade, _ ->
            // given: a use recorded at the start
            val token = facade.createAccessToken(CreateAccessTokenRequest(PERSISTENT, "ci-bot", secret = "ci-secret")).accessToken
            facade.recordTokenUsage(token.identifier, "10.0.0.7")

            // when: the throttle window has passed
            clock.advance(Duration.ofMinutes(5))
            facade.recordTokenUsage(token.identifier, "10.0.0.8")

            // then
            val details = facade.getAccessTokenDetails(token)
            assertThat(details.lastUsedAt).describedAs("last use after the window").isEqualTo(start.plus(Duration.ofMinutes(5)))
            assertThat(details.lastUsedIp).describedAs("address after the window").isEqualTo("10.0.0.8")
        }
    }

    @Test
    fun `a second instance on the same database respects the write of the first`() {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            // given: one instance wrote the use
            val first = facadeOn(connection.database)
            val token = first.createAccessToken(CreateAccessTokenRequest(PERSISTENT, "ci-bot", secret = "ci-secret")).accessToken
            first.recordTokenUsage(token.identifier, "10.0.0.7")

            // when: a second instance, with no memory of the first, sees a use one minute later
            clock.advance(Duration.ofMinutes(1))
            val second = facadeOn(connection.database)
            second.recordTokenUsage(token.identifier, "10.0.0.8")

            // then: the database refused to move the stored time, so the first write stands
            assertThat(second.getAccessTokenDetails(token).lastUsedAt).describedAs("last use seen by the second instance").isEqualTo(start)
        }
    }

    @Test
    fun `an older use never moves a stored timestamp backwards`() {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            // given: a use stored at the start
            val first = facadeOn(connection.database)
            val token = first.createAccessToken(CreateAccessTokenRequest(PERSISTENT, "ci-bot", secret = "ci-secret")).accessToken
            first.recordTokenUsage(token.identifier, "10.0.0.7")

            // when: an instance whose clock reads an hour earlier records a use
            clock.moveTo(start.minus(Duration.ofHours(1)))
            facadeOn(connection.database).recordTokenUsage(token.identifier, "10.0.0.9")

            // then
            assertThat(first.getAccessTokenDetails(token).lastUsedAt).describedAs("stored last use").isEqualTo(start)
        }
    }

    @Test
    fun `a failed authentication records no use`() {
        withFacade { facade, _ ->
            // given: a token and an authentication with a wrong secret
            val token = facade.createAccessToken(CreateAccessTokenRequest(PERSISTENT, "ci-bot", secret = "ci-secret")).accessToken
            val authentication = authenticationFacade(facade)

            // when
            val result = authentication.authenticateByCredentials(Credentials(ip = "10.0.0.7", name = "ci-bot", secret = "wrong"))

            // then
            assertThat(result.isErr).describedAs("authentication with a wrong secret").isTrue
            assertThat(facade.getAccessTokenDetails(token).lastUsedAt).describedAs("last use after a failure").isNull()
        }
    }

    @Test
    fun `a cache hit counts as a use, and is throttled like any other use`() {
        withFacade { facade, _ ->
            // given: a token used once, which fills the authentication cache
            val token = facade.createAccessToken(CreateAccessTokenRequest(PERSISTENT, "ci-bot", secret = "ci-secret")).accessToken
            val authenticator = CountingAuthenticator(BasicAuthenticator(facade, clock))
            val authentication = authenticationFacade(facade, authenticator)
            authentication.authenticateByCredentials(Credentials(ip = "10.0.0.7", name = "ci-bot", secret = "ci-secret"))

            // when: six minutes later the same credentials arrive again and are answered from the cache (the cache key
            // includes the address, so the second request comes from the same one)
            clock.advance(Duration.ofMinutes(6))
            val cached = authentication.authenticateByCredentials(Credentials(ip = "10.0.0.7", name = "ci-bot", secret = "ci-secret"))

            // then: the cache answered, the authenticator did not run again, and the use was recorded
            assertThat(cached.isOk).describedAs("cached authentication").isTrue
            assertThat(authenticator.calls).describedAs("calls of the real authenticator").isEqualTo(1)
            val details = facade.getAccessTokenDetails(token)
            assertThat(details.lastUsedAt).describedAs("last use counted from the cache hit").isEqualTo(start.plus(Duration.ofMinutes(6)))
            assertThat(details.lastUsedIp).describedAs("address of the cache hit").isEqualTo("10.0.0.7")
        }
    }

    private fun facadeOn(database: org.jetbrains.exposed.v1.jdbc.Database): AccessTokenFacade =
        AccessTokenComponents(journalist, database).accessTokenFacade(
            persistentRepository = IdentityAccessTokenRepository(IdentityStore(database, clock)),
        )

    private fun authenticationFacade(facade: AccessTokenFacade, authenticator: Authenticator = BasicAuthenticator(facade, clock)): AuthenticationFacade =
        AuthenticationFacade(
            journalist = journalist,
            authenticators = TreeSet(Authenticator.priorityComparator).apply { add(authenticator) },
            accessTokenFacade = facade,
            clock = clock,
        )

    private fun withFacade(block: (AccessTokenFacade, IdentityStore) -> Unit) {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            block(facadeOn(connection.database), IdentityStore(connection.database, clock))
        }
    }

    /** Counts the calls that reach the real authenticator, so a test can tell a cache hit from a fresh check. */
    private class CountingAuthenticator(private val delegate: Authenticator) : Authenticator by delegate {

        var calls = 0

        override fun authenticate(credentials: Credentials): Result<AccessTokenDto, ErrorResponse> {
            calls++
            return delegate.authenticate(credentials)
        }

    }

    /** A clock that only moves when the test says so. */
    private class MovableClock(private var now: Instant) : Clock() {

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = now

        fun advance(duration: Duration) {
            now = now.plus(duration)
        }

        fun moveTo(instant: Instant) {
            now = instant
        }

    }

}
