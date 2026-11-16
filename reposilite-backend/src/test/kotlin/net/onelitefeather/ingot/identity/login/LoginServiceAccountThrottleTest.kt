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

import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import net.onelitefeather.ingot.identity.login.LoginOutcome.Blocked
import net.onelitefeather.ingot.identity.login.LoginOutcome.Rejected
import net.onelitefeather.ingot.identity.login.LoginOutcome.Succeeded
import net.onelitefeather.ingot.identity.login.storage.LoginStore
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.storage.IdentityStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.reactive.Reference
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

/**
 * The dashboard password login with the account throttle. The per-address and per-user limits are set high here, so that
 * only the account key decides the answers under test.
 */
internal class LoginServiceAccountThrottleTest {

    private val clock = MutableClock(Instant.parse("2026-10-15T10:00:00Z"))
    private val password = "correct horse battery"
    private val settings = LoginSettings(maxFailedAttempts = 100, accountThrottle = AccountThrottleSettings(threshold = 3))
    private val home = "10.0.0.1"
    private val elsewhere = "10.0.0.9"

    @TempDir
    lateinit var workingDirectory: Path

    private class Fixture(val service: LoginService, val store: LoginStore, val identities: IdentityStore)

    private fun withFixture(enabled: Boolean, block: (Fixture) -> Unit) {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            val store = LoginStore(connection.database, clock)
            val identities = IdentityStore(connection.database, clock)
            val service = LoginService(
                identities = identities,
                credentials = store,
                hasher = PasswordHasher(Argon2Cost(memoryKib = 8, iterations = 1, parallelism = 1)),
                settings = Reference.reference(settings),
                clock = clock,
                audit = {},
                accountThrottle = AccountThrottle(store, Reference.reference(settings), clock, enabled),
            )
            block(Fixture(service, store, identities))
        }
    }

    private fun Fixture.userWithPassword(name: String = "alice") {
        val principal = identities.createPrincipal(PrincipalKind.USER, name, name, false, clock.instant())!!
        service.setPassword(principal, password, mustChange = false)
    }

    private fun Fixture.login(username: String, secret: String, ip: String) =
        service.login(LocalLoginProvider.ID, username, secret, ip)

    @Test
    fun `delays the right password from a new address once the name has failed the threshold`() {
        withFixture(enabled = true) { fixture ->
            fixture.userWithPassword()
            repeat(3) { fixture.login("alice", "wrong guess", home) }

            val outcome = fixture.login("alice", password, elsewhere)

            assertThat(outcome).describedAs("a new address waits for the delay of the name").isEqualTo(Blocked(retryAfterSeconds = 1))
        }
    }

    @Test
    fun `answers an unknown name and an existing name with the same delay`() {
        withFixture(enabled = true) { fixture ->
            fixture.userWithPassword()
            repeat(3) { fixture.login("alice", "wrong guess", home) }
            repeat(3) { fixture.login("nobody", "wrong guess", home) }

            assertThat(fixture.login("alice", "wrong guess", elsewhere)).isEqualTo(Blocked(retryAfterSeconds = 1))
            assertThat(fixture.login("nobody", "wrong guess", elsewhere))
                .describedAs("a name that does not exist must not be told apart by the delay")
                .isEqualTo(Blocked(retryAfterSeconds = 1))
        }
    }

    @Test
    fun `does not delay the right password from an address the name signed in from`() {
        withFixture(enabled = true) { fixture ->
            fixture.userWithPassword()
            fixture.login("alice", password, home)
            repeat(3) { fixture.login("alice", "wrong guess", elsewhere) }

            assertThat(fixture.login("alice", password, home))
                .describedAs("the owner at a known address is not delayed by the account key")
                .isInstanceOf(Succeeded::class.java)
        }
    }

    @Test
    fun `a delayed request is not counted as a failure`() {
        withFixture(enabled = true) { fixture ->
            fixture.userWithPassword()
            repeat(3) { fixture.login("alice", "wrong guess", home) }
            repeat(5) { fixture.login("alice", "wrong guess", elsewhere) }

            clock.advance(Duration.ofSeconds(1))

            assertThat(fixture.login("alice", "wrong guess", elsewhere)).isEqualTo(Rejected)
            assertThat(fixture.login("alice", "wrong guess", elsewhere))
                .describedAs("the next failure doubles the delay, which only the refused requests did not do")
                .isEqualTo(Blocked(retryAfterSeconds = 2))
        }
    }

    @Test
    fun `with the switch off a name is answered as today from any address`() {
        withFixture(enabled = false) { fixture ->
            fixture.userWithPassword()
            repeat(12) { fixture.login("alice", "wrong guess", home) }

            assertThat(fixture.login("alice", password, elsewhere))
                .describedAs("with the switch off the right password from a new address is accepted")
                .isInstanceOf(Succeeded::class.java)
            assertThat(fixture.store.findAttempt(LoginAttemptLimiter.accountKey("alice")))
                .describedAs("no account row is written while the switch is off")
                .isNull()
        }
    }

    @Test
    fun `with the switch off a successful login writes no known address`() {
        withFixture(enabled = false) { fixture ->
            fixture.userWithPassword()

            fixture.login("alice", password, home)

            assertThat(fixture.store.isKnownAddress("alice", AccountThrottle.addressDigest(home), Instant.EPOCH)).isFalse()
        }
    }

}
