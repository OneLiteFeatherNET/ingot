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
import com.reposilite.audit.api.AuditActor
import com.reposilite.audit.api.AuditEvent
import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import net.onelitefeather.ingot.identity.login.BreachCheckMode.RANGE
import net.onelitefeather.ingot.identity.login.LoginOutcome.Rejected
import net.onelitefeather.ingot.identity.login.LoginOutcome.Succeeded
import net.onelitefeather.ingot.identity.login.PasswordOutcome.BreachCheckUnavailable
import net.onelitefeather.ingot.identity.login.PasswordOutcome.Changed
import net.onelitefeather.ingot.identity.login.PasswordOutcome.InvalidInput
import net.onelitefeather.ingot.identity.login.SetupOutcome.Created
import net.onelitefeather.ingot.identity.login.storage.LoginStore
import net.onelitefeather.ingot.identity.storage.IdentityStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.reactive.Reference
import java.nio.file.Path
import java.time.Instant

/**
 * Where the breached-password check runs: when a password is set, changed, reset or created during setup, and never at a
 * sign-in. The range service is a fake, so no test reaches the network.
 */
internal class PasswordBreachServiceTest {

    private val clock = MutableClock(Instant.parse("2026-10-15T10:00:00Z"))
    private val password = "correct horse battery"
    private val replacement = "a brand new passphrase"

    @TempDir
    lateinit var workingDirectory: Path

    private class Fixture(
        val service: LoginService,
        val identities: IdentityStore,
        val setupCode: SetupCode,
        val audit: MutableList<AuditEvent>,
    )

    private fun withFixture(settings: LoginSettings, range: FakeBreachRange, block: (Fixture) -> Unit) {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            val setupCode = SetupCode()
            val audit = mutableListOf<AuditEvent>()
            val service = LoginService(
                identities = IdentityStore(connection.database, clock),
                credentials = LoginStore(connection.database, clock),
                hasher = PasswordHasher(Argon2Cost(memoryKib = 8, iterations = 1, parallelism = 1)),
                settings = Reference.reference(settings),
                clock = clock,
                audit = audit::add,
                setupCode = setupCode,
                breachCheck = PasswordBreachCheck(Reference.reference(settings), range),
            )
            block(Fixture(service, IdentityStore(connection.database, clock), setupCode, audit))
        }
    }

    private fun rangeSettings(failClosed: Boolean = false) = LoginSettings(
        passwordPolicy = PasswordPolicySettings(breachCheck = BreachCheckSettings(mode = RANGE, failClosed = failClosed)),
    )

    private fun Fixture.alice(): Int {
        service.createUser("alice", "Alice", password, disabled = false)
        return identities.findPrincipal("alice")!!.id
    }

    private fun Fixture.loginWith(secret: String) =
        service.login(LocalLoginProvider.ID, "alice", secret, ip = "10.0.0.1")

    @Test
    fun `refuses a breached password on a change and keeps the old one`() {
        withFixture(rangeSettings(), FakeBreachRange(answers = FakeBreachRange.listing(replacement))) { fixture ->
            val id = fixture.alice()

            val outcome = fixture.service.changePassword(id, password, replacement)

            assertThat(outcome).isInstanceOfSatisfying(InvalidInput::class.java) { assertThat(it.problem).contains("breached") }
            assertThat(fixture.loginWith(password)).describedAs("a refused change leaves the old password").isInstanceOf(Succeeded::class.java)
        }
    }

    @Test
    fun `refuses a breached password on an administrator's reset`() {
        withFixture(rangeSettings(), FakeBreachRange(answers = FakeBreachRange.listing(replacement))) { fixture ->
            fixture.alice()

            val outcome = fixture.service.resetPassword("alice", replacement, actor = AuditActor.named("root", "10.0.0.9"))

            assertThat(outcome).isInstanceOfSatisfying(InvalidInput::class.java) { assertThat(it.problem).contains("breached") }
            assertThat(fixture.audit.map { it.action }).describedAs("a refused reset is not audited as a reset").doesNotContain(AuditAction.PASSWORD_RESET)
        }
    }

    @Test
    fun `refuses a breached password during the setup and creates no user`() {
        withFixture(rangeSettings(), FakeBreachRange(answers = FakeBreachRange.listing(password))) { fixture ->
            val code = fixture.setupCode.issue()

            val outcome = fixture.service.completeSetup(code, "root", password, displayName = null, ip = "10.0.0.1")

            assertThat(outcome).isInstanceOfSatisfying(SetupOutcome.InvalidInput::class.java) { assertThat(it.problem).contains("breached") }
            assertThat(fixture.identities.findPrincipal("root")).describedAs("the first administrator is not created").isNull()
        }
    }

    @Test
    fun `accepts an unlisted password during the setup`() {
        withFixture(rangeSettings(), FakeBreachRange()) { fixture ->
            val code = fixture.setupCode.issue()

            assertThat(fixture.service.completeSetup(code, "root", password, displayName = null, ip = "10.0.0.1")).isInstanceOf(Created::class.java)
        }
    }

    @Test
    fun `does not check the password at a sign-in`() {
        val range = FakeBreachRange(answers = FakeBreachRange.listing(password))
        withFixture(rangeSettings(), range) { fixture ->
            fixture.alice()

            assertThat(fixture.loginWith(password))
                .describedAs("a user whose password is listed still signs in")
                .isInstanceOf(Succeeded::class.java)
            assertThat(range.prefixes).describedAs("no breach check runs at a sign-in").isEmpty()
        }
    }

    @Test
    fun `allows a change during an outage by default and audits the outage`() {
        withFixture(rangeSettings(failClosed = false), FakeBreachRange(unavailable = true)) { fixture ->
            val id = fixture.alice()

            assertThat(fixture.service.changePassword(id, password, replacement, actor = AuditActor.named("alice", "10.0.0.1")))
                .isEqualTo(Changed)
            assertThat(fixture.audit.map { it.action }).containsOnlyOnce(AuditAction.PASSWORD_BREACH_CHECK_UNAVAILABLE)
        }
    }

    @Test
    fun `refuses a change during an outage when fail-closed is set and stores nothing`() {
        withFixture(rangeSettings(failClosed = true), FakeBreachRange(unavailable = true)) { fixture ->
            val id = fixture.alice()

            val outcome = fixture.service.changePassword(id, password, replacement)

            assertThat(outcome).isInstanceOfSatisfying(BreachCheckUnavailable::class.java) { assertThat(it.retryAfterSeconds).isPositive() }
            assertThat(fixture.loginWith(password)).describedAs("the old password still works").isInstanceOf(Succeeded::class.java)
            assertThat(fixture.loginWith(replacement)).isEqualTo(Rejected)
        }
    }

    @Test
    fun `makes no check while the mode is off`() {
        val range = FakeBreachRange(answers = FakeBreachRange.listing(replacement))
        withFixture(LoginSettings(), range) { fixture ->
            val id = fixture.alice()

            assertThat(fixture.service.changePassword(id, password, replacement)).isEqualTo(Changed)
            assertThat(range.prefixes).isEmpty()
        }
    }

    @Test
    fun `checks the length before it asks the range service`() {
        val range = FakeBreachRange()
        withFixture(rangeSettings(), range) { fixture ->
            val id = fixture.alice()

            fixture.service.changePassword(id, password, "short")

            assertThat(range.prefixes).describedAs("a password that is too short is not sent to the service").isEmpty()
        }
    }

}
