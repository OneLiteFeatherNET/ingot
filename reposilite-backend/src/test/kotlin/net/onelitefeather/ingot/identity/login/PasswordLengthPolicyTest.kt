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
import net.onelitefeather.ingot.identity.login.LoginOutcome.Succeeded
import net.onelitefeather.ingot.identity.login.PasswordOutcome.Changed
import net.onelitefeather.ingot.identity.login.PasswordOutcome.InvalidInput
import net.onelitefeather.ingot.identity.login.storage.LoginStore
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.storage.IdentityStore
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.reactive.Reference
import java.nio.file.Path
import java.time.Instant

/**
 * The length rules of a new password. The default answers exactly what Reposilite answers. A higher minimum is an
 * operator's choice and applies to the next password a user sets; a stored password is never checked again.
 */
internal class PasswordLengthPolicyTest {

    private val clock = MutableClock(Instant.parse("2026-10-15T10:00:00Z"))
    private val emoji = "😀"

    @TempDir
    lateinit var workingDirectory: Path

    private fun withDatabase(block: (Database) -> Unit) {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            block(connection.database)
        }
    }

    private fun serviceOn(database: Database, settings: LoginSettings) =
        LoginService(
            identities = IdentityStore(database, clock),
            credentials = LoginStore(database, clock),
            hasher = PasswordHasher(Argon2Cost(memoryKib = 8, iterations = 1, parallelism = 1)),
            settings = Reference.reference(settings),
            clock = clock,
            audit = {},
        )

    @Test
    fun `refuses an 11 character password under the default and names the minimum of 12`() {
        withDatabase { database ->
            assertThat(serviceOn(database, LoginSettings()).passwordProblem("a".repeat(11)))
                .isEqualTo("Password must have at least 12 characters")
        }
    }

    @Test
    fun `accepts a 12 character password under the default`() {
        withDatabase { database ->
            assertThat(serviceOn(database, LoginSettings()).passwordProblem("a".repeat(12))).isNull()
        }
    }

    @Test
    fun `measures the length in UTF-16 code units as Reposilite does`() {
        withDatabase { database ->
            val service = serviceOn(database, LoginSettings())

            assertThat(service.passwordProblem(emoji.repeat(6)))
                .describedAs("six emoji are 12 UTF-16 code units, so the default accepts them")
                .isNull()
            assertThat(service.passwordProblem(emoji.repeat(5) + "a"))
                .describedAs("five emoji and a letter are 11 UTF-16 code units, so the default refuses them")
                .isEqualTo("Password must have at least 12 characters")
        }
    }

    @Test
    fun `accepts a lower case passphrase with spaces and no digit or symbol`() {
        withDatabase { database ->
            assertThat(serviceOn(database, LoginSettings()).passwordProblem("correct horse battery staple")).isNull()
        }
    }

    @Test
    fun `accepts 256 characters and refuses 257 as today`() {
        withDatabase { database ->
            val service = serviceOn(database, LoginSettings())

            assertThat(service.passwordProblem("a".repeat(256))).isNull()
            assertThat(service.passwordProblem("a".repeat(257))).isEqualTo("Password must not have more than 256 characters")
        }
    }

    @Test
    fun `refuses a 14 character password when the minimum is raised to 15`() {
        withDatabase { database ->
            val service = serviceOn(database, LoginSettings(passwordPolicy = PasswordPolicySettings(minLength = 15)))

            assertThat(service.passwordProblem("a".repeat(14))).isEqualTo("Password must have at least 15 characters")
        }
    }

    @Test
    fun `a stored password below the raised minimum still signs in`() {
        withDatabase { database ->
            val password = "abcdefghijkl"
            val principal = IdentityStore(database, clock).createPrincipal(PrincipalKind.USER, "alice", "Alice", false, clock.instant())!!
            serviceOn(database, LoginSettings()).setPassword(principal, password, mustChange = false)

            val raised = serviceOn(database, LoginSettings(passwordPolicy = PasswordPolicySettings(minLength = 15)))

            assertThat(raised.login(LocalLoginProvider.ID, "alice", password, ip = "10.0.0.1"))
                .describedAs("the minimum applies to the next password a user sets, not to the stored one")
                .isInstanceOf(Succeeded::class.java)
        }
    }

    @Test
    fun `refuses a changed password under the raised minimum`() {
        withDatabase { database ->
            val password = "abcdefghijkl"
            val principal = IdentityStore(database, clock).createPrincipal(PrincipalKind.USER, "alice", "Alice", false, clock.instant())!!
            serviceOn(database, LoginSettings()).setPassword(principal, password, mustChange = false)
            val raised = serviceOn(database, LoginSettings(passwordPolicy = PasswordPolicySettings(minLength = 15)))

            assertThat(raised.changePassword(principal.id, password, "abcdefghijklmn"))
                .isEqualTo(InvalidInput("Password must have at least 15 characters"))
        }
    }

    @Test
    fun `accepts a change to a password of the raised minimum`() {
        withDatabase { database ->
            val password = "abcdefghijkl"
            val principal = IdentityStore(database, clock).createPrincipal(PrincipalKind.USER, "alice", "Alice", false, clock.instant())!!
            val raised = serviceOn(database, LoginSettings(passwordPolicy = PasswordPolicySettings(minLength = 15)))
            raised.setPassword(principal, password, mustChange = false)

            assertThat(raised.changePassword(principal.id, password, "abcdefghijklmno")).isEqualTo(Changed)
        }
    }

}
