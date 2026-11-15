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
package net.onelitefeather.ingot.identity.login.storage

import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import net.onelitefeather.ingot.identity.login.external.ExternalIdentityStore
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.storage.IdentityStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Changes of what a credential can do advance the policy revision, so that the other replicas drop the credentials they
 * cached. Usage of a credential, such as spending a recovery code, does not change it and does not advance the revision.
 */
internal class CredentialRevisionTest {

    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)
    private val at: Instant = Instant.parse("2026-10-15T09:00:00Z")

    @TempDir
    lateinit var workingDirectory: Path

    @Test
    fun `saving a password advances the policy revision by exactly one`() {
        withStores { identity, login, _, _ ->
            // given: a user without a password yet
            val alice = identity.createPrincipal(PrincipalKind.USER, "alice", "Alice", disabled = false, createdAt = at)!!
            val before = identity.policyRevision()

            // when: a password is set
            login.saveCredential(alice.id, "hash", mustChangePassword = false)

            // then: the revision advanced once
            assertThat(identity.policyRevision()).describedAs("a password change advances the revision once").isEqualTo(before + 1)
        }
    }

    @Test
    fun `revoking the sessions of a principal advances the policy revision by exactly one`() {
        withStores { identity, login, _, _ ->
            // given: a user
            val alice = identity.createPrincipal(PrincipalKind.USER, "alice", "Alice", disabled = false, createdAt = at)!!
            val before = identity.policyRevision()

            // when: all refresh tokens of the user are revoked
            login.revokeAllOfPrincipal(alice.id, at)

            // then: the revision advanced once
            assertThat(identity.policyRevision()).describedAs("a session revocation advances the revision once").isEqualTo(before + 1)
        }
    }

    @Test
    fun `replacing the recovery codes advances the policy revision by exactly one`() {
        withStores { identity, _, second, _ ->
            // given: a user
            val alice = identity.createPrincipal(PrincipalKind.USER, "alice", "Alice", disabled = false, createdAt = at)!!
            val before = identity.policyRevision()

            // when: new recovery codes are issued
            second.replaceRecoveryCodes(alice.id, listOf("code-hash"), at)

            // then: the revision advanced once
            assertThat(identity.policyRevision()).describedAs("issuing recovery codes advances the revision once").isEqualTo(before + 1)
        }
    }

    @Test
    fun `resetting the second factors of a principal advances the policy revision by exactly one`() {
        withStores { identity, _, second, _ ->
            // given: a user
            val alice = identity.createPrincipal(PrincipalKind.USER, "alice", "Alice", disabled = false, createdAt = at)!!
            val before = identity.policyRevision()

            // when: all second factors are removed
            second.deleteAllFactors(alice.id)

            // then: the revision advanced once
            assertThat(identity.policyRevision()).describedAs("removing the second factors advances the revision once").isEqualTo(before + 1)
        }
    }

    @Test
    fun `linking an external identity advances the policy revision by exactly one`() {
        withStores { identity, _, _, external ->
            // given: a user
            val alice = identity.createPrincipal(PrincipalKind.USER, "alice", "Alice", disabled = false, createdAt = at)!!
            val before = identity.policyRevision()

            // when: the user is linked to an external subject
            external.link("oidc", "subject-1", alice.id)

            // then: the revision advanced once
            assertThat(identity.policyRevision()).describedAs("linking an external identity advances the revision once").isEqualTo(before + 1)
        }
    }

    @Test
    fun `spending a recovery code does not advance the policy revision`() {
        withStores { identity, _, second, _ ->
            // given: a user with one recovery code
            val alice = identity.createPrincipal(PrincipalKind.USER, "alice", "Alice", disabled = false, createdAt = at)!!
            second.replaceRecoveryCodes(alice.id, listOf("code-hash"), at)
            val before = identity.policyRevision()

            // when: the recovery code is used to sign in
            second.claimRecoveryCode(alice.id, "code-hash", at)

            // then: the usage does not change what the credential can do, so the revision stays
            assertThat(identity.policyRevision()).describedAs("using a recovery code is usage, not a change").isEqualTo(before)
        }
    }

    private fun withStores(block: (IdentityStore, LoginStore, SecondFactorStore, ExternalIdentityStore) -> Unit) {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            val database = connection.database
            block(
                IdentityStore(database, clock),
                LoginStore(database, clock),
                SecondFactorStore(database, clock),
                ExternalIdentityStore(database),
            )
        }
    }
}
