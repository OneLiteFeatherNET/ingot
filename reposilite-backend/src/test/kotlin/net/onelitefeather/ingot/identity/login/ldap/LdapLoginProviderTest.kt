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

import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import net.onelitefeather.ingot.identity.login.LoginOutcome
import net.onelitefeather.ingot.identity.login.LoginOutcome.Disabled
import net.onelitefeather.ingot.identity.login.LoginOutcome.Rejected
import net.onelitefeather.ingot.identity.login.LoginOutcome.Succeeded
import net.onelitefeather.ingot.identity.login.LoginOutcome.Unavailable
import net.onelitefeather.ingot.identity.login.MutableClock
import net.onelitefeather.ingot.identity.login.external.ExternalAccountService
import net.onelitefeather.ingot.identity.login.external.ExternalIdentityStore
import net.onelitefeather.ingot.identity.login.external.ExternalRefusal
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.storage.IdentityStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant

internal class LdapLoginProviderTest {

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

    private fun settings(url: String = directory.url, autoCreate: Boolean = true) = LdapLoginSettings(
        enabled = true,
        url = url,
        bindDn = InMemoryDirectory.SERVICE_DN,
        bindPassword = InMemoryDirectory.SERVICE_PASSWORD,
        userBaseDn = "ou=people,${InMemoryDirectory.BASE_DN}",
        autoCreateUsers = autoCreate,
    )

    private class Fixture(val identities: IdentityStore, val accounts: ExternalAccountService)

    /** A fresh database with one local administrator, so that the first LDAP user is not a setup login. */
    private fun withDatabase(block: (Fixture) -> Unit) {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            val identities = IdentityStore(connection.database, clock)
            identities.createPrincipal(PrincipalKind.USER, "admin", "Admin", false, clock.instant())
            block(Fixture(identities, ExternalAccountService(identities, ExternalIdentityStore(connection.database))))
        }
    }

    private fun LoginOutcome.principalName(): String? =
        (this as? Succeeded)?.principal?.name

    @Test
    fun `a user with the right password logs in and is created from the directory entry`() {
        withDatabase { fixture ->
            // when
            val outcome = LdapLoginProvider(settings(), fixture.accounts).authenticate("alice", "alice-secret")

            // then
            assertThat(outcome).isInstanceOf(Succeeded::class.java)
            val principal = (outcome as Succeeded).principal
            assertThat(principal.name).isEqualTo("alice")
            assertThat(principal.displayName).isEqualTo("Alice Example")
        }
    }

    @Test
    fun `a wrong password is rejected and creates nothing`() {
        withDatabase { fixture ->
            val outcome = LdapLoginProvider(settings(), fixture.accounts).authenticate("alice", "guess")

            assertThat(outcome).isEqualTo(Rejected)
            assertThat(fixture.identities.findPrincipal("alice")).isNull()
        }
    }

    @Test
    fun `an unknown user is rejected`() {
        withDatabase { fixture ->
            assertThat(LdapLoginProvider(settings(), fixture.accounts).authenticate("carol", "any-password")).isEqualTo(Rejected)
        }
    }

    @Test
    fun `a user name that matches several entries is rejected`() {
        withDatabase { fixture ->
            directory.add(
                "dn: uid=twin1,ou=people,${InMemoryDirectory.BASE_DN}", "objectClass: inetOrgPerson", "uid: twin1", "cn: Twin",
                "sn: Twin", "userPassword: twin-secret",
            )
            directory.add(
                "dn: uid=twin2,ou=people,${InMemoryDirectory.BASE_DN}", "objectClass: inetOrgPerson", "uid: twin2", "cn: Twin",
                "sn: Twin", "userPassword: twin-secret",
            )
            val provider = LdapLoginProvider(settings().copy(userFilter = "(cn={username})"), fixture.accounts)

            assertThat(provider.authenticate("Twin", "twin-secret")).isEqualTo(Rejected)
        }
    }

    @Test
    fun `a user name that is an injection payload does not match anybody`() {
        withDatabase { fixture ->
            // when: the payload would match every entry if it were not escaped
            val outcome = LdapLoginProvider(settings(), fixture.accounts).authenticate("*)(uid=*", "alice-secret")

            // then
            assertThat(outcome).isEqualTo(Rejected)
            assertThat(fixture.identities.findPrincipal("alice")).isNull()
        }
    }

    @Test
    fun `an empty password is refused before the directory is asked anything`() {
        withDatabase { fixture ->
            // given: the directory is gone, so any request to it would fail as unavailable
            directory.stop()

            // when
            val outcome = LdapLoginProvider(settings(), fixture.accounts).authenticate("alice", "")

            // then: a rejection, not an unavailable directory, so no request was made
            assertThat(outcome).isEqualTo(Rejected)
        }
    }

    @Test
    fun `an unreachable directory answers unavailable, not a wrong password`() {
        withDatabase { fixture ->
            directory.stop()

            assertThat(LdapLoginProvider(settings(), fixture.accounts).authenticate("alice", "alice-secret")).isEqualTo(Unavailable)
        }
    }

    @Test
    fun `a directory user who is disabled in ingot is reported as disabled`() {
        withDatabase { fixture ->
            val provider = LdapLoginProvider(settings(), fixture.accounts)
            val first = provider.authenticate("alice", "alice-secret") as Succeeded
            fixture.identities.updatePrincipal(first.principal.id, displayName = null, disabled = true)

            assertThat(provider.authenticate("alice", "alice-secret")).isEqualTo(Disabled)
        }
    }

    @Test
    fun `without automatic creation an entry that was never linked is refused`() {
        withDatabase { fixture ->
            val outcome = LdapLoginProvider(settings(autoCreate = false), fixture.accounts).authenticate("bob", "bob-secret")

            assertThat(outcome).isEqualTo(LoginOutcome.Refused(ExternalRefusal.USER_NOT_PROVISIONED))
        }
    }

    @Test
    fun `directory groups are synchronised into teams with the ldap reference`() {
        withDatabase { fixture ->
            val devs = fixture.identities.createTeam("ldap:devs", "", "ldap:devs")!!
            val provider = LdapLoginProvider(settings().copy(groupBaseDn = "ou=groups,${InMemoryDirectory.BASE_DN}"), fixture.accounts)

            val outcome = provider.authenticate("alice", "alice-secret") as Succeeded

            assertThat(fixture.identities.listMemberships(devs.id).map { it.principalId }).containsExactly(outcome.principal.id)
        }
    }

    @Test
    fun `an empty instance cannot be claimed through the directory`() {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            val identities = IdentityStore(connection.database, clock)
            val accounts = ExternalAccountService(identities, ExternalIdentityStore(connection.database))

            val outcome = LdapLoginProvider(settings(), accounts).authenticate("alice", "alice-secret")

            assertThat(outcome).isEqualTo(LoginOutcome.Refused(ExternalRefusal.SETUP_REQUIRED))
            assertThat(outcome.principalName()).isNull()
        }
    }
}
