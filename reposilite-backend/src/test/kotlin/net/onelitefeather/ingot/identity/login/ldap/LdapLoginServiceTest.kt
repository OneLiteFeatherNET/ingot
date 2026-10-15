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
import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import net.onelitefeather.ingot.identity.login.LoginOutcome
import net.onelitefeather.ingot.identity.login.LoginProviderType
import net.onelitefeather.ingot.identity.login.LoginService
import net.onelitefeather.ingot.identity.login.LoginSettings
import net.onelitefeather.ingot.identity.login.MutableClock
import net.onelitefeather.ingot.identity.login.PasswordHasher
import net.onelitefeather.ingot.identity.login.external.ExternalAccountService
import net.onelitefeather.ingot.identity.login.external.ExternalIdentityStore
import net.onelitefeather.ingot.identity.login.storage.LoginStore
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.storage.IdentityStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.reactive.Reference
import java.nio.file.Path
import java.time.Instant

/** The LDAP provider through [LoginService]: the same failure limit, audit and announcements as the local login. */
internal class LdapLoginServiceTest {

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

    private class Fixture(val service: LoginService, val audit: MutableList<AuditEvent>, val identities: IdentityStore)

    private fun withService(block: (Fixture) -> Unit) {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            val identities = IdentityStore(connection.database, clock)
            identities.createPrincipal(PrincipalKind.USER, "admin", "Admin", false, clock.instant())
            val settings = Reference.reference(
                LoginSettings(
                    maxFailedAttempts = 3,
                    lockoutMinutes = 5,
                    ldap = LdapLoginSettings(
                        enabled = true,
                        url = directory.url,
                        bindDn = InMemoryDirectory.SERVICE_DN,
                        bindPassword = InMemoryDirectory.SERVICE_PASSWORD,
                        userBaseDn = "ou=people,${InMemoryDirectory.BASE_DN}",
                    ),
                ),
            )
            val audit = mutableListOf<AuditEvent>()
            val accounts = ExternalAccountService(identities, ExternalIdentityStore(connection.database))
            val service = LoginService(
                identities = identities,
                credentials = LoginStore(connection.database, clock),
                hasher = PasswordHasher(),
                settings = settings,
                clock = clock,
                audit = audit::add,
                externalPasswordProviders = {
                    val ldap = settings.get().ldap
                    if (ldap.enabled) listOf(LdapLoginProvider(ldap, accounts)) else emptyList()
                },
                externalAnnouncements = {
                    val ldap = settings.get().ldap
                    if (ldap.enabled) {
                        listOf(net.onelitefeather.ingot.identity.login.ProviderAnnouncement(id = "ldap", type = LoginProviderType.LDAP, label = ldap.label, icon = "LDAP"))
                    } else {
                        emptyList()
                    }
                },
            )
            block(Fixture(service, audit, identities))
        }
    }

    @Test
    fun `failures of one user name lock it, and the locked attempt never reaches the directory`() {
        withService { fixture ->
            // given: three wrong passwords for the same user name from one address
            repeat(3) {
                assertThat(fixture.service.login("ldap", "alice", "wrong", ip = "10.0.0.1")).isEqualTo(LoginOutcome.Rejected)
            }

            // when: the directory is gone, so a request would be answered as unavailable
            directory.stop()
            val locked = fixture.service.login("ldap", "alice", "alice-secret", ip = "10.0.0.1")

            // then: the lock answers first, and the right password is not even checked
            assertThat(locked).isInstanceOf(LoginOutcome.Blocked::class.java)
        }
    }

    @Test
    fun `a directory that does not answer does not count as a wrong password`() {
        withService { fixture ->
            directory.stop()
            repeat(5) {
                assertThat(fixture.service.login("ldap", "alice", "alice-secret", ip = "10.0.0.1")).isEqualTo(LoginOutcome.Unavailable)
            }

            assertThat(fixture.service.login("ldap", "alice", "alice-secret", ip = "10.0.0.1"))
                .describedAs("still not locked").isEqualTo(LoginOutcome.Unavailable)
        }
    }

    @Test
    fun `a successful directory login is audited once, with the user name and the address`() {
        withService { fixture ->
            val outcome = fixture.service.login("ldap", "alice", "alice-secret", ip = "10.0.0.9")

            assertThat(outcome).isInstanceOf(LoginOutcome.Succeeded::class.java)
            val succeeded = fixture.audit.filter { it.action == AuditAction.LOGIN_SUCCEEDED }
            assertThat(succeeded).hasSize(1)
            assertThat(succeeded.single().actor.name).isEqualTo("alice")
        }
    }

    @Test
    fun `the ldap provider is announced only while it is enabled`() {
        withService { fixture ->
            assertThat(fixture.service.announcements().map { it.id }).contains("ldap")
        }
    }

    @Test
    fun `an unknown provider id does not reach the directory`() {
        withService { fixture ->
            assertThat(fixture.service.login("nope", "alice", "alice-secret", ip = "10.0.0.1")).isEqualTo(LoginOutcome.UnknownProvider)
        }
    }
}
