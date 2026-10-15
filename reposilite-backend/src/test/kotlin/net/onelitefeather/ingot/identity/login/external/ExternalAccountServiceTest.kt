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

package net.onelitefeather.ingot.identity.login.external

import com.reposilite.audit.api.AuditAction
import com.reposilite.audit.api.AuditEvent
import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import net.onelitefeather.ingot.identity.login.MutableClock
import net.onelitefeather.ingot.identity.login.external.ExternalOutcome.Refused
import net.onelitefeather.ingot.identity.login.external.ExternalOutcome.Succeeded
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.storage.IdentityStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant

internal class ExternalAccountServiceTest {

    private val clock = MutableClock(Instant.parse("2026-10-15T10:00:00Z"))

    @TempDir
    lateinit var workingDirectory: Path

    private fun identity(
        subject: String = "subject-1",
        username: String = "alice",
        provider: String = "oidc:corp",
        autoCreate: Boolean = true,
    ) = ExternalIdentity(
        provider = provider,
        subject = subject,
        username = username,
        displayName = "Alice",
        email = null,
        autoCreateUsers = autoCreate,
        allowedEmailDomains = emptyList(),
    )

    @Test
    fun `a second login of a linked subject returns the same principal and creates no user`() {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            val identities = IdentityStore(connection.database, clock)
            identities.createPrincipal(PrincipalKind.USER, "admin", "Admin", false, clock.instant())
            val audit = mutableListOf<AuditEvent>()
            val service = ExternalAccountService(identities, ExternalIdentityStore(connection.database)) { audit.add(it) }

            val first = service.resolve(identity()) as Succeeded
            val second = service.resolve(identity()) as Succeeded

            assertThat(second.principal.id).describedAs("same principal").isEqualTo(first.principal.id)
            assertThat(identities.listPrincipals(PrincipalKind.USER)).describedAs("users").hasSize(2)
            assertThat(audit.count { it.action == AuditAction.PRINCIPAL_CREATED }).describedAs("creations audited").isEqualTo(1)
        }
    }

    @Test
    fun `deleting the principal removes its link, so the next login creates a new user`() {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            val identities = IdentityStore(connection.database, clock)
            identities.createPrincipal(PrincipalKind.USER, "admin", "Admin", false, clock.instant())
            val service = ExternalAccountService(identities, ExternalIdentityStore(connection.database))
            val linked = service.resolve(identity()) as Succeeded

            identities.deletePrincipal(linked.principal)
            val again = service.resolve(identity()) as Succeeded

            assertThat(again.principal.id).describedAs("a fresh principal").isNotEqualTo(linked.principal.id)
        }
    }

    @Test
    fun `the same subject of another provider is a different identity`() {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            val identities = IdentityStore(connection.database, clock)
            identities.createPrincipal(PrincipalKind.USER, "admin", "Admin", false, clock.instant())
            val service = ExternalAccountService(identities, ExternalIdentityStore(connection.database))

            val corp = service.resolve(identity(provider = "oidc:corp", username = "alice"))
            val other = service.resolve(identity(provider = "oidc:other", username = "alice-2"))

            assertThat(corp).isInstanceOf(Succeeded::class.java)
            assertThat(other).isInstanceOf(Succeeded::class.java)
            assertThat((other as Succeeded).principal.name).isEqualTo("alice-2")
        }
    }

    @Test
    fun `an invalid user name is refused and nothing is created`() {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            val identities = IdentityStore(connection.database, clock)
            identities.createPrincipal(PrincipalKind.USER, "admin", "Admin", false, clock.instant())
            val service = ExternalAccountService(identities, ExternalIdentityStore(connection.database))

            val outcome = service.resolve(identity(username = "has space"))

            assertThat(outcome).isEqualTo(Refused(ExternalRefusal.INVALID_USERNAME))
            assertThat(identities.listPrincipals(PrincipalKind.USER)).hasSize(1)
        }
    }
}
