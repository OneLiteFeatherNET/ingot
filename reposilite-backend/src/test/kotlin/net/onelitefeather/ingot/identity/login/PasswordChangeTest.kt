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

import com.reposilite.auth.application.BruteForceProtectionSettings
import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import net.onelitefeather.ingot.identity.login.LoginOutcome.Rejected
import net.onelitefeather.ingot.identity.login.LoginOutcome.Succeeded
import net.onelitefeather.ingot.identity.login.PasswordOutcome.Changed
import net.onelitefeather.ingot.identity.login.PasswordOutcome.InvalidInput
import net.onelitefeather.ingot.identity.login.PasswordOutcome.NotAUser
import net.onelitefeather.ingot.identity.login.PasswordOutcome.NotFound
import net.onelitefeather.ingot.identity.login.PasswordOutcome.WrongCurrentPassword
import net.onelitefeather.ingot.identity.login.storage.LoginStore
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.storage.IdentityStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.reactive.Reference
import java.nio.file.Path
import java.time.Instant

internal class PasswordChangeTest {

    private val clock = MutableClock(Instant.parse("2026-10-15T10:00:00Z"))
    private val password = "correct horse battery"
    private val replacement = "a brand new passphrase"

    @TempDir
    lateinit var workingDirectory: Path

    private class Fixture(val identities: IdentityStore, val service: LoginService)

    private fun withFixture(block: (Fixture) -> Unit) {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            val identities = IdentityStore(connection.database, clock)
            val service = LoginService(
                identities = identities,
                credentials = LoginStore(connection.database, clock),
                hasher = PasswordHasher(Argon2Cost(memoryKib = 8, iterations = 1, parallelism = 1)),
                protection = Reference.reference(BruteForceProtectionSettings()),
                clock = clock,
                audit = {},
            )
            block(Fixture(identities, service))
        }
    }

    private fun Fixture.alice(): Int {
        service.createUser("alice", "Alice", password, disabled = false)
        return identities.findPrincipal("alice")!!.id
    }

    private fun Fixture.login(password: String) =
        service.login("local", "alice", password, ip = "10.0.0.1")

    @Test
    fun `changes the password when the current one is right`() {
        withFixture { fixture ->
            val id = fixture.alice()

            val outcome = fixture.service.changePassword(id, password, replacement)

            assertThat(outcome).isEqualTo(Changed)
            assertThat(fixture.login(password)).describedAs("the old password must stop working").isEqualTo(Rejected)
            assertThat(fixture.login(replacement)).describedAs("the new password must work").isInstanceOf(Succeeded::class.java)
        }
    }

    @Test
    fun `refuses a wrong current password and keeps the old one`() {
        withFixture { fixture ->
            val id = fixture.alice()

            val outcome = fixture.service.changePassword(id, "not the current one", replacement)

            assertThat(outcome).isEqualTo(WrongCurrentPassword)
            assertThat(fixture.login(password)).describedAs("a refused change must leave the password as it was").isInstanceOf(Succeeded::class.java)
        }
    }

    @Test
    fun `refuses a new password that breaks the policy and keeps the old one`() {
        withFixture { fixture ->
            val id = fixture.alice()

            val outcome = fixture.service.changePassword(id, password, "short")

            assertThat(outcome).isInstanceOf(InvalidInput::class.java)
            assertThat(fixture.login(password)).isInstanceOf(Succeeded::class.java)
        }
    }

    @Test
    fun `resets a password and forces a change at the next login`() {
        withFixture { fixture ->
            fixture.alice()

            val outcome = fixture.service.resetPassword("alice", replacement)

            assertThat(outcome).isEqualTo(Changed)
            val login = fixture.login(replacement)
            assertThat(login).isInstanceOf(Succeeded::class.java)
            assertThat((login as Succeeded).mustChangePassword).describedAs("a reset must force a change").isTrue()
        }
    }

    @Test
    fun `clears the forced change once the user chose a new password`() {
        withFixture { fixture ->
            val id = fixture.alice()
            fixture.service.resetPassword("alice", replacement)

            fixture.service.changePassword(id, replacement, password)

            val login = fixture.login(password) as Succeeded
            assertThat(login.mustChangePassword).describedAs("the user's own change ends the forced change").isFalse()
        }
    }

    @Test
    fun `refuses to reset the password of a machine principal`() {
        withFixture { fixture ->
            fixture.identities.createPrincipal(PrincipalKind.MACHINE, "ci-bot", "ci-bot", disabled = false, createdAt = clock.instant())

            assertThat(fixture.service.resetPassword("ci-bot", replacement)).describedAs("machines have no password").isEqualTo(NotAUser)
        }
    }

    @Test
    fun `reports an unknown user on reset`() {
        withFixture { fixture ->
            assertThat(fixture.service.resetPassword("nobody", replacement)).isEqualTo(NotFound)
        }
    }

}
