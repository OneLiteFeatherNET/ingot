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

package net.onelitefeather.ingot.identity.login.ldap

import com.reposilite.audit.api.AuditAction
import com.reposilite.audit.api.AuditEvent
import com.reposilite.auth.api.Credentials
import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import net.onelitefeather.ingot.identity.login.LoginOutcome
import net.onelitefeather.ingot.identity.login.LoginProviderType
import net.onelitefeather.ingot.identity.login.MutableClock
import net.onelitefeather.ingot.identity.login.external.ExternalRefusal
import net.onelitefeather.ingot.identity.model.PrincipalKind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant

/**
 * The dashboard login with the legacy `authentication.ldap` settings, as 1.1.0 accepted it. The session belongs to the
 * machine principal that owns the token the Maven path creates, so a user has one identity whichever way they sign in.
 */
internal class LegacyLdapLoginTest {

    private val clock = MutableClock(Instant.parse("2026-10-15T10:00:00Z"))

    @TempDir
    lateinit var workingDirectory: Path

    private lateinit var directory: InMemoryDirectory

    @BeforeEach
    fun startDirectory() {
        directory = InMemoryDirectory()
    }

    @AfterEach
    fun stopDirectory() {
        directory.close()
    }

    private fun withEnvironment(block: (LegacyLdapEnvironment) -> Unit) {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            block(LegacyLdapEnvironment(directory, connection.database, clock))
        }
    }

    @Test
    fun `legacy ldap credentials log in as the machine principal that owns the token of the user`() {
        withEnvironment { environment ->
            val service = environment.loginService()

            val outcome = service.login("ldap", "alice", "alice-secret", ip = "10.0.0.1")

            assertThat(outcome).describedAs("the legacy settings alone must open the dashboard login").isInstanceOf(LoginOutcome.Succeeded::class.java)
            val principal = (outcome as LoginOutcome.Succeeded).principal
            assertThat(principal.id).describedAs("the session principal is the owner of the ldap token")
                .isEqualTo(environment.identities.findToken("alice")!!.principalId)
            assertThat(principal.kind).isEqualTo(PrincipalKind.MACHINE)
            assertThat(environment.identities.listPrincipals(PrincipalKind.USER).map { it.name })
                .describedAs("no human principal may be created for the directory user").doesNotContain("alice")
        }
    }

    @Test
    fun `a wrong legacy ldap password is rejected and audited`() {
        withEnvironment { environment ->
            val audit = mutableListOf<AuditEvent>()
            val service = environment.loginService(audit = audit)

            val outcome = service.login("ldap", "alice", "wrong", ip = "10.0.0.1")

            assertThat(outcome).isEqualTo(LoginOutcome.Rejected)
            assertThat(audit.map { it.action }).describedAs("rejected attempts are audited like every provider").contains(AuditAction.LOGIN_FAILED)
            assertThat(environment.identities.findToken("alice")).describedAs("a wrong password creates no token").isNull()
        }
    }

    @Test
    fun `an empty password is rejected before the directory is asked`() {
        withEnvironment { environment ->
            val provider = environment.directories().provider()!!

            directory.stop()

            assertThat(provider.authenticate("alice", ""))
                .describedAs("an empty password is an anonymous bind on many servers and never a login")
                .isEqualTo(LoginOutcome.Rejected)
        }
    }

    @Test
    fun `only the legacy ldap provider is announced while login ldap is off`() {
        withEnvironment { environment ->
            val announced = environment.loginService().announcements().filter { it.type == LoginProviderType.LDAP }

            assertThat(announced.map { it.id }).describedAs("the login dialog needs the ldap switch").containsExactly("ldap")
            assertThat(announced.single().loginUrl).describedAs("a password provider has no redirect").isNull()
        }
    }

    @Test
    fun `a user who already signed in over maven keeps the same principal`() {
        withEnvironment { environment ->
            val service = environment.loginService()
            assertThat(environment.authenticator.authenticate(Credentials("", "alice", "alice-secret")).isOk).isTrue()
            val mavenPrincipal = environment.identities.findToken("alice")!!.principalId

            val outcome = service.login("ldap", "alice", "alice-secret", ip = "10.0.0.1")

            assertThat((outcome as LoginOutcome.Succeeded).principal.id).isEqualTo(mavenPrincipal)
            assertThat(environment.identities.listPrincipals().count { it.name == "alice" }).describedAs("one principal per directory user").isEqualTo(1)
        }
    }

    @Test
    fun `a human with the name of the directory user is refused, and no machine principal is created`() {
        withEnvironment { environment ->
            environment.createHuman("alice")
            val service = environment.loginService()

            val outcome = service.login("ldap", "alice", "alice-secret", ip = "10.0.0.1")

            assertThat(outcome).isEqualTo(LoginOutcome.Refused(ExternalRefusal.USERNAME_TAKEN))
            assertThat(environment.identities.listPrincipals(PrincipalKind.MACHINE)).describedAs("no machine principal for a taken name").isEmpty()
        }
    }

    @Test
    fun `a disabled machine principal of the directory user cannot log in`() {
        withEnvironment { environment ->
            assertThat(environment.authenticator.authenticate(Credentials("", "alice", "alice-secret")).isOk).isTrue()
            val principal = environment.identities.findPrincipal("alice")!!
            environment.identities.updatePrincipal(principal.id, displayName = null, disabled = true)
            val service = environment.loginService()

            assertThat(service.login("ldap", "alice", "alice-secret", ip = "10.0.0.1")).isEqualTo(LoginOutcome.Disabled)
        }
    }

    @Test
    fun `enabled login ldap wins over the legacy settings, even when it finds nobody`() {
        withEnvironment { environment ->
            val login = LdapLoginSettings(
                enabled = true,
                label = "Corporate directory",
                url = directory.url,
                bindDn = InMemoryDirectory.SERVICE_DN,
                bindPassword = InMemoryDirectory.SERVICE_PASSWORD,
                userBaseDn = "ou=groups,${InMemoryDirectory.BASE_DN}",
            )
            val service = environment.loginService(login)

            val outcome = service.login("ldap", "alice", "alice-secret", ip = "10.0.0.1")

            assertThat(outcome).describedAs("no fallback to authentication.ldap").isEqualTo(LoginOutcome.Rejected)
            assertThat(service.announcements().filter { it.type == LoginProviderType.LDAP }.map { it.label })
                .describedAs("a single ldap switch, labelled by login.ldap").containsExactly("Corporate directory")
        }
    }

    @Test
    fun `the failure limit locks a user name on the legacy provider too`() {
        withEnvironment { environment ->
            val service = environment.loginService(maxFailedAttempts = 3)
            repeat(3) {
                assertThat(service.login("ldap", "alice", "wrong", ip = "10.0.0.1")).isEqualTo(LoginOutcome.Rejected)
            }

            val locked = service.login("ldap", "alice", "alice-secret", ip = "10.0.0.1")

            assertThat(locked).describedAs("the right password is not even checked while locked").isInstanceOf(LoginOutcome.Blocked::class.java)
        }
    }

}
