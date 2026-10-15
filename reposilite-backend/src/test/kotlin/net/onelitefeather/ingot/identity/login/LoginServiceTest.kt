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

import com.reposilite.audit.api.AuditAction
import com.reposilite.audit.api.AuditEvent
import com.reposilite.auth.application.BruteForceProtectionSettings
import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import net.onelitefeather.ingot.identity.login.LoginOutcome.Blocked
import net.onelitefeather.ingot.identity.login.LoginOutcome.Disabled
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

internal class LoginServiceTest {

    private val clock = MutableClock(Instant.parse("2026-10-15T10:00:00Z"))
    private val password = "correct horse battery"

    @TempDir
    lateinit var workingDirectory: Path

    private class Fixture(
        val identities: IdentityStore,
        val service: LoginService,
        val audit: MutableList<AuditEvent>,
    )

    private fun withFixture(protection: BruteForceProtectionSettings = BruteForceProtectionSettings(enabled = true, maxAttempts = 3, banDurationSeconds = 300), block: (Fixture) -> Unit) {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            val identities = IdentityStore(connection.database, clock)
            val audit = mutableListOf<AuditEvent>()
            val service = LoginService(
                identities = identities,
                credentials = LoginStore(connection.database, clock),
                hasher = PasswordHasher(Argon2Cost(memoryKib = 8, iterations = 1, parallelism = 1)),
                protection = Reference.reference(protection),
                clock = clock,
                audit = audit::add,
            )
            block(Fixture(identities, service, audit))
        }
    }

    private fun Fixture.userWithPassword(name: String = "alice", mustChange: Boolean = false, disabled: Boolean = false) {
        val principal = identities.createPrincipal(PrincipalKind.USER, name, name, disabled, clock.instant())!!
        service.setPassword(principal, password, mustChange)
    }

    private fun Fixture.loginAs(username: String, password: String) =
        service.login(LOCAL, username, password, ip = "10.0.0.1")

    @Test
    fun `logs in with the right password and reports the principal`() {
        withFixture { fixture ->
            fixture.userWithPassword()

            val outcome = fixture.loginAs("alice", password)

            assertThat(outcome).describedAs("the right password must log the user in").isInstanceOf(Succeeded::class.java)
            assertThat((outcome as Succeeded).principal.name).isEqualTo("alice")
            assertThat(outcome.mustChangePassword).describedAs("a password that was not forced to change must not ask for a change").isFalse()
        }
    }

    @Test
    fun `reports a password that must be changed at the next login`() {
        withFixture { fixture ->
            fixture.userWithPassword(mustChange = true)

            val outcome = fixture.loginAs("alice", password)

            assertThat((outcome as Succeeded).mustChangePassword).describedAs("a forced change must be reported in the login result").isTrue()
        }
    }

    @Test
    fun `finds the user regardless of the letter case of the user name`() {
        withFixture { fixture ->
            fixture.userWithPassword(name = "Alice")

            val outcome = fixture.loginAs("ALICE", password)

            assertThat(outcome).describedAs("user names are case-insensitive, as principal names are").isInstanceOf(Succeeded::class.java)
        }
    }

    @Test
    fun `answers a wrong password and an unknown user with the same outcome`() {
        withFixture { fixture ->
            fixture.userWithPassword()

            val wrongPassword = fixture.loginAs("alice", "wrong password entirely")
            val unknownUser = fixture.loginAs("mallory", "wrong password entirely")

            assertThat(wrongPassword).describedAs("a wrong password must be rejected").isEqualTo(Rejected)
            assertThat(unknownUser).describedAs("an unknown user must look exactly like a wrong password").isEqualTo(wrongPassword)
        }
    }

    @Test
    fun `audits a failed login with the attempted name and address but never the password`() {
        withFixture { fixture ->
            fixture.userWithPassword()

            fixture.loginAs("alice", "wrong password entirely")

            val event = fixture.audit.single()
            assertThat(event.action).describedAs("a failed login must be audited as LOGIN_FAILED").isEqualTo(AuditAction.LOGIN_FAILED)
            assertThat(event.actor.name).isEqualTo("alice")
            assertThat(event.actor.address).isEqualTo("10.0.0.1")
            assertThat(event.toString()).describedAs("the audit record must never contain the password").doesNotContain("wrong password entirely")
        }
    }

    @Test
    fun `refuses a disabled user even when the password is right`() {
        withFixture { fixture ->
            fixture.userWithPassword(disabled = true)

            val outcome = fixture.loginAs("alice", password)

            assertThat(outcome).describedAs("a disabled principal must not get a session").isInstanceOf(Disabled::class.java)
        }
    }

    @Test
    fun `blocks further attempts after too many failures, even with the right password`() {
        withFixture { fixture ->
            fixture.userWithPassword()
            repeat(3) { fixture.loginAs("alice", "wrong password entirely") }

            val outcome = fixture.loginAs("alice", password)

            assertThat(outcome).describedAs("a blocked user name and address must not be let in with the right password").isEqualTo(Blocked)
        }
    }

    @Test
    fun `accepts the right password again once the ban has passed`() {
        withFixture { fixture ->
            fixture.userWithPassword()
            repeat(3) { fixture.loginAs("alice", "wrong password entirely") }

            clock.advance(Duration.ofSeconds(301))

            assertThat(fixture.loginAs("alice", password)).isInstanceOf(Succeeded::class.java)
        }
    }

    @Test
    fun `forgets earlier failures after a successful login`() {
        withFixture { fixture ->
            fixture.userWithPassword()
            repeat(2) { fixture.loginAs("alice", "wrong password entirely") }
            fixture.loginAs("alice", password)

            repeat(2) { fixture.loginAs("alice", "wrong password entirely") }

            assertThat(fixture.loginAs("alice", password))
                .describedAs("failures before a successful login must not count towards the limit")
                .isInstanceOf(Succeeded::class.java)
        }
    }

    @Test
    fun `replaces the password and clears the forced change`() {
        withFixture { fixture ->
            fixture.userWithPassword(mustChange = true)
            val principal = fixture.identities.findPrincipal("alice")!!

            fixture.service.setPassword(principal, "a brand new password", mustChange = false)

            assertThat(fixture.loginAs("alice", password)).describedAs("the old password must stop working").isEqualTo(Rejected)
            assertThat(fixture.loginAs("alice", "a brand new password"))
                .describedAs("the new password must work without a forced change")
                .isInstanceOf(Succeeded::class.java)
        }
    }

    private companion object {
        const val LOCAL = "local"
    }

}
