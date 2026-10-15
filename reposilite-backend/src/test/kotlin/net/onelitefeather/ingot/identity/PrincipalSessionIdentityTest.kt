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

package net.onelitefeather.ingot.identity

import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.token.AccessTokenFacade
import com.reposilite.token.AccessTokenIdentifier
import com.reposilite.token.AccessTokenPermission.MANAGER
import com.reposilite.token.AccessTokenType.PRINCIPAL
import com.reposilite.token.AccessTokenType.TEMPORARY
import com.reposilite.token.api.CreateAccessTokenRequest
import com.reposilite.token.application.AccessTokenComponents
import net.onelitefeather.ingot.identity.model.Action
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.model.PrincipalRecord
import net.onelitefeather.ingot.identity.model.Role
import net.onelitefeather.ingot.identity.model.TeamMemberSource
import net.onelitefeather.ingot.identity.storage.IdentityStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * A browser session is identified by its user principal, not by a token. These tests pin that the principal identity
 * gets exactly the rights of the principal, and that it can never be mistaken for a token of another kind.
 */
internal class PrincipalSessionIdentityTest {

    private val journalist = InMemoryLogger()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)

    @TempDir
    lateinit var workingDirectory: Path

    private fun user(store: IdentityStore, name: String, disabled: Boolean = false): PrincipalRecord =
        store.createPrincipal(PrincipalKind.USER, name, name, disabled = disabled, createdAt = clock.instant())!!

    private fun withWorld(block: (IdentityStore, AccessTokenFacade) -> Unit) {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            val facade = AccessTokenComponents(journalist, connection.database).accessTokenFacade()
            block(IdentityStore(connection.database, clock), facade)
        }
    }

    @Test
    fun `a session of a user has the rights that the user inherits through a team`() {
        withWorld { store, facade ->
            // given: a user in a team that may deploy to one repository
            val alice = user(store, "alice")
            val deployers = store.createTeam("deployers", "", null)!!
            store.addMember(deployers.id, alice.id, TeamMemberSource.MANUAL)
            store.addGrant(deployers.id, "releases", null, Role.DEPLOY)
            val session = AccessTokenIdentifier(PRINCIPAL, alice.id)

            // then: the session may write there and nowhere else
            assertThat(facade.hasAccessTo(session, "/releases/com/example/app.jar", Action.WRITE)).describedAs("the team grants writes on releases").isTrue()
            assertThat(facade.hasAccessTo(session, "/snapshots/com/example/app.jar", Action.WRITE)).describedAs("the team has no grant on snapshots").isFalse()
        }
    }

    @Test
    fun `a session is an instance admin only through the instance role`() {
        withWorld { store, facade ->
            val admin = user(store, "admin")
            val regular = user(store, "regular")
            store.grantInstanceRole(principalId = admin.id, teamId = null)

            assertThat(facade.hasPermission(AccessTokenIdentifier(PRINCIPAL, admin.id), MANAGER)).describedAs("the instance role makes the session an admin").isTrue()
            assertThat(facade.hasPermission(AccessTokenIdentifier(PRINCIPAL, regular.id), MANAGER)).describedAs("without the role the session is no admin").isFalse()
        }
    }

    @Test
    fun `a principal identity never resolves to a temporary token with the same number`() {
        withWorld { store, facade ->
            // given: a temporary manager token and a user whose principal id is the number of that token
            val temporary = facade.createAccessToken(CreateAccessTokenRequest(TEMPORARY, "bootstrap", secret = "secret")).accessToken
            facade.addPermission(temporary.identifier, MANAGER)
            val bystander = user(store, "bystander")
            val sameNumber = AccessTokenIdentifier(PRINCIPAL, temporary.identifier.value)

            // then: the user identity is not the temporary token, so it does not inherit its manager permission
            assertThat(facade.hasPermission(sameNumber, MANAGER))
                .describedAs("a principal with the number of a temporary token must not become manager through it (principal ${bystander.id})")
                .isFalse()
            assertThat(facade.getAccessTokenById(sameNumber)?.name)
                .describedAs("the principal identity must not be read as the temporary token")
                .isNotEqualTo("bootstrap")
        }
    }

    @Test
    fun `a disabled user has no rights through a session`() {
        withWorld { store, facade ->
            val alice = user(store, "alice", disabled = true)
            val deployers = store.createTeam("deployers", "", null)!!
            store.addMember(deployers.id, alice.id, TeamMemberSource.MANUAL)
            store.addGrant(deployers.id, "releases", null, Role.DEPLOY)
            val session = AccessTokenIdentifier(PRINCIPAL, alice.id)

            assertThat(facade.isDisabled(session)).describedAs("the session must report the disabled principal").isTrue()
            assertThat(facade.hasAccessTo(session, "/releases/a.jar", Action.READ)).describedAs("a disabled user reads nothing").isFalse()
        }
    }

    @Test
    fun `reads the session identity as the token of its user`() {
        withWorld { store, facade ->
            val alice = user(store, "alice")

            val dto = facade.getAccessTokenById(AccessTokenIdentifier(PRINCIPAL, alice.id))

            assertThat(dto?.name).describedAs("the session DTO carries the principal name").isEqualTo("alice")
            assertThat(dto?.identifier).isEqualTo(AccessTokenIdentifier(PRINCIPAL, alice.id))
        }
    }

}
