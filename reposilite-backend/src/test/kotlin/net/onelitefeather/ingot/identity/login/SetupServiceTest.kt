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
import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import net.onelitefeather.ingot.identity.login.LoginOutcome.Succeeded
import net.onelitefeather.ingot.identity.login.SetupOutcome.Blocked
import net.onelitefeather.ingot.identity.login.SetupOutcome.Created
import net.onelitefeather.ingot.identity.login.SetupOutcome.InvalidInput
import net.onelitefeather.ingot.identity.login.SetupOutcome.NameTaken
import net.onelitefeather.ingot.identity.login.SetupOutcome.NotRequired
import net.onelitefeather.ingot.identity.login.SetupOutcome.WrongCode
import net.onelitefeather.ingot.identity.login.storage.LoginStore
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.storage.IdentityStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.reactive.Reference
import java.nio.file.Path
import java.time.Instant

internal class SetupServiceTest {

    private val clock = MutableClock(Instant.parse("2026-10-15T10:00:00Z"))
    private val password = "correct horse battery"

    @TempDir
    lateinit var workingDirectory: Path

    private class Fixture(
        val identities: IdentityStore,
        val service: LoginService,
        val setupCode: SetupCode,
        val audit: MutableList<AuditEvent>,
    )

    private fun withFixture(block: (Fixture) -> Unit) {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            val identities = IdentityStore(connection.database, clock)
            val setupCode = SetupCode()
            val audit = mutableListOf<AuditEvent>()
            val service = LoginService(
                identities = identities,
                credentials = LoginStore(connection.database, clock),
                hasher = PasswordHasher(Argon2Cost(memoryKib = 8, iterations = 1, parallelism = 1)),
                settings = Reference.reference(LoginSettings(maxFailedAttempts = 3, lockoutMinutes = 5)),
                clock = clock,
                audit = audit::add,
                setupCode = setupCode,
            )
            block(Fixture(identities, service, setupCode, audit))
        }
    }

    @Test
    fun `reports the setup as required while no user exists`() {
        withFixture { fixture ->
            assertThat(fixture.service.hasUsers()).describedAs("a fresh instance has no user").isFalse()
            assertThat(fixture.service.issueSetupCode()).describedAs("the setup code is issued while the setup is required").isNotNull()
        }
    }

    @Test
    fun `creates the first administrator with the setup code`() {
        withFixture { fixture ->
            val code = fixture.service.issueSetupCode()!!

            val outcome = fixture.service.completeSetup(code, "root", password, displayName = "Root", ip = "10.0.0.1")

            assertThat(outcome).describedAs("the right code must create the administrator").isInstanceOf(Created::class.java)
            val principal = (outcome as Created).principal
            assertThat(principal.kind).isEqualTo(PrincipalKind.USER)
            assertThat(principal.displayName).isEqualTo("Root")
            assertThat(fixture.identities.hasDirectInstanceRole(principal.id))
                .describedAs("the first user must be an instance administrator")
                .isTrue()
        }
    }

    @Test
    fun `the administrator can log in with the password chosen during setup`() {
        withFixture { fixture ->
            fixture.service.completeSetup(fixture.service.issueSetupCode()!!, "root", password, displayName = null, ip = "10.0.0.1")

            assertThat(fixture.service.login("local", "root", password, ip = "10.0.0.1")).isInstanceOf(Succeeded::class.java)
        }
    }

    @Test
    fun `refuses a wrong setup code and creates nobody`() {
        withFixture { fixture ->
            fixture.service.issueSetupCode()

            val outcome = fixture.service.completeSetup("WRONGCODE234", "root", password, displayName = null, ip = "10.0.0.1")

            assertThat(outcome).describedAs("a wrong code must be refused").isEqualTo(WrongCode)
            assertThat(fixture.service.hasUsers()).describedAs("no user may be created with a wrong code").isFalse()
        }
    }

    @Test
    fun `refuses setup again once the code has been used`() {
        withFixture { fixture ->
            val code = fixture.service.issueSetupCode()!!
            fixture.service.completeSetup(code, "root", password, displayName = null, ip = "10.0.0.1")

            val second = fixture.service.completeSetup(code, "other", password, displayName = null, ip = "10.0.0.1")

            assertThat(second).describedAs("a consumed setup must not create a second administrator").isEqualTo(NotRequired)
        }
    }

    @Test
    fun `refuses setup when a user already exists, even with the right code`() {
        withFixture { fixture ->
            fixture.service.createUser("existing", "Existing", password, disabled = false)

            assertThat(fixture.service.issueSetupCode()).describedAs("no setup code is issued once a user exists").isNull()
            val outcome = fixture.service.completeSetup(fixture.setupCode.issue(), "root", password, displayName = null, ip = "10.0.0.1")

            assertThat(outcome).isEqualTo(NotRequired)
        }
    }

    @Test
    fun `blocks further setup attempts from an address after too many wrong codes`() {
        withFixture { fixture ->
            val code = fixture.service.issueSetupCode()!!
            repeat(3) { fixture.service.completeSetup("WRONGCODE234", "root", password, displayName = null, ip = "10.0.0.9") }

            val outcome = fixture.service.completeSetup(code, "root", password, displayName = null, ip = "10.0.0.9")

            assertThat(outcome).describedAs("the right code must not be tried after the address is blocked").isInstanceOf(Blocked::class.java)
        }
    }

    @Test
    fun `refuses a short password without creating the administrator`() {
        withFixture { fixture ->
            val outcome = fixture.service.completeSetup(fixture.service.issueSetupCode()!!, "root", "short", displayName = null, ip = "10.0.0.1")

            assertThat(outcome).describedAs("a short password must be refused").isInstanceOf(InvalidInput::class.java)
            assertThat(fixture.service.hasUsers()).describedAs("nothing may be created with an invalid password").isFalse()
        }
    }

    @Test
    fun `refuses a name that a machine principal already holds`() {
        withFixture { fixture ->
            fixture.identities.createPrincipal(PrincipalKind.MACHINE, "root", "root", disabled = false, createdAt = clock.instant())

            val outcome = fixture.service.completeSetup(fixture.service.issueSetupCode()!!, "root", password, displayName = null, ip = "10.0.0.1")

            assertThat(outcome).describedAs("a name held by any principal is taken").isEqualTo(NameTaken)
            assertThat(fixture.service.hasUsers()).describedAs("the setup must not create a user under a taken name").isFalse()
        }
    }

    @Test
    fun `audits a wrong setup code as a failed login`() {
        withFixture { fixture ->
            fixture.service.issueSetupCode()

            fixture.service.completeSetup("WRONGCODE234", "root", password, displayName = null, ip = "10.0.0.7")

            val event = fixture.audit.single()
            assertThat(event.action).isEqualTo(AuditAction.LOGIN_FAILED)
            assertThat(event.actor.address).isEqualTo("10.0.0.7")
            assertThat(event.toString()).doesNotContain("WRONGCODE234")
        }
    }

}
