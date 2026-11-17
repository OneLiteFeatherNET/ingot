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

package com.reposilite.auth

import com.reposilite.auth.api.Credentials
import com.reposilite.auth.application.BruteForceProtectionSettings
import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.unauthorized
import com.reposilite.token.AccessTokenFacade
import com.reposilite.token.AccessTokenSecurityProvider
import com.reposilite.token.AccessTokenType.TEMPORARY
import com.reposilite.token.ExportService
import com.reposilite.token.api.AccessTokenDto
import com.reposilite.token.api.CreateAccessTokenRequest
import com.reposilite.token.infrastructure.InMemoryAccessTokenRepository
import net.onelitefeather.ingot.identity.login.LoginAttemptLimiter
import net.onelitefeather.ingot.identity.login.MutableClock
import net.onelitefeather.ingot.identity.login.SharedFailedAddressStore
import net.onelitefeather.ingot.identity.login.storage.AttemptRecord
import net.onelitefeather.ingot.identity.login.storage.LoginStore
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.Result
import panda.std.ResultAssertions.assertError
import panda.std.ResultAssertions.assertOk
import panda.std.reactive.toMutableReference
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

/**
 * The legacy per-address counter of `authentication.bruteForceProtection`, once it is kept in the shared login tables. Each
 * test gets a database of its own, and two facades on that database stand for two replicas of Ingot.
 */
internal class SharedBasicAddressCounterTest {

    private val clock = MutableClock(Instant.parse("2026-10-15T10:00:00Z"))
    private val journalist = InMemoryLogger()
    private val tokens = AccessTokenFacade(
        journalist = journalist,
        temporaryRepository = InMemoryAccessTokenRepository(clock),
        persistentRepository = InMemoryAccessTokenRepository(clock),
        exportService = ExportService(),
        bcryptStrength = AccessTokenSecurityProvider.MIN_BCRYPT_STRENGTH,
    )
    private val settings = BruteForceProtectionSettings(enabled = true, maxAttempts = 3, banDurationSeconds = 300).toMutableReference()

    @TempDir
    lateinit var workingDirectory: Path

    /** One replica: a facade and the counting authenticator behind it. */
    private class Replica(val facade: AuthenticationFacade, val authenticator: CountingAuthenticator) {

        fun authenticate(address: String, name: String, secret: String): Result<out AccessTokenDto, ErrorResponse> =
            facade.authenticateByCredentials(Credentials(address, name, secret))

    }

    /** Counts the requests that reach the real authenticator. A banned address never gets there. */
    private class CountingAuthenticator(private val delegate: Authenticator) : Authenticator {

        var calls = 0
            private set

        override fun authenticate(credentials: Credentials): Result<AccessTokenDto, ErrorResponse> {
            calls++
            return delegate.authenticate(credentials)
        }

        override fun enabled(): Boolean = delegate.enabled()

        override fun priority(): Double = delegate.priority()

        override fun realm(): String = delegate.realm()

    }

    /** Builds a replica. With [store] given, its counter is the shared one on that store; without it, the counter is local. */
    private fun replica(store: LoginStore?): Replica {
        val authenticator = CountingAuthenticator(BasicAuthenticator(tokens, clock))
        val facade = AuthenticationFacade(
            journalist = journalist,
            authenticators = sortedSetOf(Authenticator.priorityComparator, authenticator),
            accessTokenFacade = tokens,
            bruteForceProtectionSettings = settings,
            clock = clock,
        )
        store?.let { facade.installSharedFailedAddresses(SharedFailedAddressStore(it, clock)) }
        return Replica(facade, authenticator)
    }

    private fun withDatabase(block: (Database, LoginStore, LoginStore) -> Unit) {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            block(connection.database, LoginStore(connection.database, clock), LoginStore(connection.database, clock))
        }
    }

    @Test
    fun `a failure counted on one replica bans the address on the other replica`() {
        withDatabase { _, first, second ->
            // given: three wrong secrets from one address, all sent to replica A
            createToken("deploy-bot", "secret")
            val replicaA = replica(first)
            val replicaB = replica(second)
            repeat(3) { replicaA.authenticate(ADDRESS, "deploy-bot", "wrong-secret") }

            // when: the correct secret is sent from the same address to replica B
            val response = replicaB.authenticate(ADDRESS, "deploy-bot", "secret")

            // then: replica B refuses the address without checking the secret
            assertError(unauthorized("Invalid authorization credentials"), response)
            assertThat(replicaB.authenticator.calls)
                .describedAs("calls of the authenticator on replica B while the address is banned by replica A")
                .isZero()
        }
    }

    @Test
    fun `the ban counted on one replica ends when the injected clock passes the ban period`() {
        withDatabase { _, first, second ->
            // given: an address banned on replica A by three wrong secrets
            createToken("deploy-bot", "secret")
            val replicaA = replica(first)
            val replicaB = replica(second)
            repeat(3) { replicaA.authenticate(ADDRESS, "deploy-bot", "wrong-secret") }

            // when: 299 seconds pass, and then one more second
            clock.advance(Duration.ofSeconds(299))
            val stillBanned = replicaB.authenticate(ADDRESS, "deploy-bot", "secret")
            clock.advance(Duration.ofSeconds(1))

            // then: the ban holds until the full period has passed, and replica B accepts the address afterwards
            assertError(unauthorized("Invalid authorization credentials"), stillBanned)
            assertOk(replicaB.authenticate(ADDRESS, "deploy-bot", "secret"))
        }
    }

    @Test
    fun `a valid login on one replica clears the failures counted by the other replica`() {
        withDatabase { _, first, second ->
            // given: two wrong secrets on replica A, then a valid login from the same address on replica B
            createToken("deploy-bot", "secret")
            val replicaA = replica(first)
            val replicaB = replica(second)
            repeat(2) { replicaA.authenticate(ADDRESS, "deploy-bot", "wrong-secret") }
            assertOk(replicaB.authenticate(ADDRESS, "deploy-bot", "secret"))

            // when: two more wrong secrets follow on replica A, which would reach the limit only without the reset
            repeat(2) { replicaA.authenticate(ADDRESS, "deploy-bot", "wrong-secret") }

            // then: the address is not banned by replica B
            assertOk(replicaB.authenticate(ADDRESS, "deploy-bot", "secret"))
        }
    }

    @Test
    fun `without the shared counter a failure on one replica is not counted by the other, and no row is written`() {
        withDatabase { _, first, _ ->
            // given: two replicas without the shared counter, which is the state while the account throttle is off
            createToken("deploy-bot", "secret")
            val replicaA = replica(store = null)
            val replicaB = replica(store = null)

            // when: three wrong secrets are sent to replica A
            repeat(3) { replicaA.authenticate(ADDRESS, "deploy-bot", "wrong-secret") }

            // then: replica B still verifies the secret, as it does today, and the login table holds no counter for the address
            assertOk(replicaB.authenticate(ADDRESS, "deploy-bot", "secret"))
            assertThat(first.findAttempt(LoginAttemptLimiter.basicAddressKey(ADDRESS)))
                .describedAs("row of the address in login_attempt while the shared counter is off")
                .isNull()
        }
    }

    @Test
    fun `a database error while reading the shared counter stops the request before the secret is checked`() {
        withDatabase { database, _, _ ->
            // given: a replica whose reads of the login table fail
            createToken("deploy-bot", "secret")
            val failing = object : LoginStore(database, clock) {
                override fun findAttempt(attemptKey: String): AttemptRecord? =
                    throw IllegalStateException("login table unavailable")
            }
            val replicaA = replica(failing)

            // when: a correct secret is sent
            // then: the request fails instead of being answered, and the secret is not checked
            assertThatThrownBy { replicaA.authenticate(ADDRESS, "deploy-bot", "secret") }
                .isInstanceOf(IllegalStateException::class.java)
            assertThat(replicaA.authenticator.calls)
                .describedAs("calls of the authenticator while the counter cannot be read")
                .isZero()
        }
    }

    private fun createToken(name: String, secret: String): AccessTokenDto =
        tokens.createAccessToken(
            CreateAccessTokenRequest(type = TEMPORARY, name = name, secret = secret, expiresAt = null)
        ).accessToken

    private companion object {
        const val ADDRESS = "10.0.0.7"
    }

}
