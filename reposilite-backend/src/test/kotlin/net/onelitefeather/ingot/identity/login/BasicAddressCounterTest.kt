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

package net.onelitefeather.ingot.identity.login

import com.reposilite.auth.AuthenticationFacade
import com.reposilite.auth.Authenticator
import com.reposilite.auth.BasicAuthenticator
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
import net.onelitefeather.ingot.identity.login.storage.LoginStore
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.Result
import panda.std.ResultAssertions.assertError
import panda.std.ResultAssertions.assertOk
import panda.std.reactive.MutableReference
import panda.std.reactive.toMutableReference
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

/**
 * The per-address counter of Basic and Bearer requests, when the legacy brute force switch is on. The counter lives in the
 * shared login table under the basic address key, on the injected clock. Every test builds its own database and facades.
 */
internal class BasicAddressCounterTest {

    private val clock = MutableClock(Instant.parse("2026-10-15T10:00:00Z"))
    private val journalist = InMemoryLogger()
    private val tokens = AccessTokenFacade(
        journalist = journalist,
        temporaryRepository = InMemoryAccessTokenRepository(clock),
        persistentRepository = InMemoryAccessTokenRepository(clock),
        exportService = ExportService(),
        bcryptStrength = AccessTokenSecurityProvider.MIN_BCRYPT_STRENGTH,
    )

    @TempDir
    lateinit var workingDirectory: Path

    /** One authentication facade on the shared database, with a guard of its own. */
    private inner class Instance(database: Database, val settings: MutableReference<BruteForceProtectionSettings>) {

        val store = LoginStore(database, clock)
        val hook = AfterSuccess(BasicAuthenticator(tokens, clock))
        val facade = AuthenticationFacade(
            journalist = journalist,
            authenticators = sortedSetOf(Authenticator.priorityComparator, hook),
            accessTokenFacade = tokens,
            clock = clock,
            basicAddressGuard = BasicAddressGuard(store, settings, clock),
        )

        fun authenticate(ip: String, name: String, secret: String): Result<out AccessTokenDto, ErrorResponse> =
            facade.authenticateByCredentials(Credentials(ip, name, secret))

    }

    private fun withDatabase(block: (Database) -> Unit) {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            block(connection.database)
        }
    }

    private fun switchOn(maxAttempts: Int = 3, banDurationSeconds: Long = 300) =
        BruteForceProtectionSettings(enabled = true, maxAttempts = maxAttempts, banDurationSeconds = banDurationSeconds).toMutableReference()

    private fun createToken(name: String, secret: String): AccessTokenDto =
        tokens.createAccessToken(CreateAccessTokenRequest(type = TEMPORARY, name = name, secret = secret, expiresAt = null)).accessToken

    @Test
    fun `stores a basic failure under the basic address key in the shared table`() = withDatabase { database ->
        // given: the switch is on and a token exists
        val instance = Instance(database, switchOn())
        createToken("deploy-bot", "secret")

        // when: one Basic request with a wrong secret comes from an address
        instance.authenticate("10.0.0.1", "deploy-bot", "wrong-secret")

        // then: the failure is counted under the basic address key, and not under the dashboard address key
        assertThat(instance.store.findAttempt(LoginAttemptLimiter.basicAddressKey("10.0.0.1")))
            .describedAs("a Basic failure must be counted in the shared table")
            .isNotNull
        assertThat(instance.store.findAttempt(LoginAttemptLimiter.addressKey("10.0.0.1")))
            .describedAs("Basic failures must not count against the dashboard address key")
            .isNull()
    }

    @Test
    fun `shares the basic address counter between two instances on one database`() = withDatabase { database ->
        // given: two instances on the same database and a token
        val first = Instance(database, switchOn())
        val second = Instance(database, switchOn())
        createToken("deploy-bot", "secret")

        // when: two failures reach the first instance and one the second, which makes three
        first.authenticate("10.0.0.2", "deploy-bot", "wrong-secret")
        first.authenticate("10.0.0.2", "deploy-bot", "wrong-secret")
        second.authenticate("10.0.0.2", "deploy-bot", "wrong-secret")

        // then: the first instance refuses the valid credentials of the address
        assertError(unauthorized("Invalid authorization credentials"), first.authenticate("10.0.0.2", "deploy-bot", "secret"))
    }

    @Test
    fun `refuses the address once it reaches the maximum and accepts it again after the ban duration`() = withDatabase { database ->
        // given: three failures from one address reach the limit of the ban of five minutes
        val instance = Instance(database, switchOn(maxAttempts = 3, banDurationSeconds = 300))
        createToken("deploy-bot", "secret")
        repeat(3) { instance.authenticate("10.0.0.3", "deploy-bot", "wrong-secret") }

        // when: 299 seconds pass, and then one more second
        clock.advance(Duration.ofSeconds(299))
        val refused = instance.authenticate("10.0.0.3", "deploy-bot", "secret")
        clock.advance(Duration.ofSeconds(1))
        val accepted = instance.authenticate("10.0.0.3", "deploy-bot", "secret")

        // then: the address is refused until the ban duration has passed on the injected clock
        assertError(unauthorized("Invalid authorization credentials"), refused)
        assertOk(accepted)
    }

    @Test
    fun `does not lift a running ban with a successful request from the same address`() = withDatabase { database ->
        // given: a valid request from an address whose failures reach the limit while its credentials are checked
        val instance = Instance(database, switchOn(maxAttempts = 3, banDurationSeconds = 300))
        createToken("deploy-bot", "secret")
        instance.hook.afterDelegate = {
            instance.hook.afterDelegate = {}
            repeat(3) { instance.authenticate("shared-nat", "deploy-bot", "wrong-secret") }
        }
        assertOk(instance.authenticate("shared-nat", "deploy-bot", "secret"))

        // when: the ban is still running and a valid request comes from the same address
        clock.advance(Duration.ofSeconds(299))

        // then: the request is refused, because the successful request did not clear the ban
        assertError(unauthorized("Invalid authorization credentials"), instance.authenticate("shared-nat", "deploy-bot", "secret"))
    }

    @Test
    fun `clears the basic address counter after a successful request when the address is not banned`() = withDatabase { database ->
        // given: two failures from an address that is not banned
        val instance = Instance(database, switchOn(maxAttempts = 3))
        createToken("deploy-bot", "secret")
        repeat(2) { instance.authenticate("10.0.0.4", "deploy-bot", "wrong-secret") }

        // when: a valid request comes from the address
        assertOk(instance.authenticate("10.0.0.4", "deploy-bot", "secret"))

        // then: the counter is gone, so two more failures do not reach the limit
        assertThat(instance.store.findAttempt(LoginAttemptLimiter.basicAddressKey("10.0.0.4"))).isNull()
        repeat(2) { instance.authenticate("10.0.0.4", "deploy-bot", "wrong-secret") }
        assertOk(instance.authenticate("10.0.0.4", "deploy-bot", "secret"))
    }

    @Test
    fun `writes nothing to the login table while the switch is off`() = withDatabase { database ->
        // given: the switch is off, which is the default
        val instance = Instance(database, BruteForceProtectionSettings(enabled = false).toMutableReference())
        createToken("deploy-bot", "secret")

        // when: many failures come from one address
        repeat(5) { instance.authenticate("10.0.0.5", "deploy-bot", "wrong-secret") }

        // then: no row is written for the address
        assertThat(instance.store.findAttempt(LoginAttemptLimiter.basicAddressKey("10.0.0.5")))
            .describedAs("with the switch off nothing may be counted in the shared table")
            .isNull()
    }

    /** Runs an optional hook right after the wrapped authenticator has answered. */
    private class AfterSuccess(private val delegate: Authenticator) : Authenticator {

        var afterDelegate: () -> Unit = {}

        override fun authenticate(credentials: Credentials): Result<AccessTokenDto, ErrorResponse> =
            delegate.authenticate(credentials).also { afterDelegate() }

        override fun enabled(): Boolean = delegate.enabled()

        override fun priority(): Double = delegate.priority()

        override fun realm(): String = delegate.realm()

    }

}
