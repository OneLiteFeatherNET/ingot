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

import com.reposilite.auth.BasicAuthenticator
import com.reposilite.auth.api.Credentials
import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.token.AccessTokenFacade
import com.reposilite.token.AccessTokenIdentifier
import com.reposilite.token.AccessTokenPermission.MANAGER
import com.reposilite.token.AccessTokenSecurityProvider
import com.reposilite.token.AccessTokenType.PERSISTENT
import com.reposilite.token.application.AccessTokenComponents
import net.onelitefeather.ingot.identity.model.Action
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.model.Role
import net.onelitefeather.ingot.identity.model.TeamMemberSource
import net.onelitefeather.ingot.identity.storage.IdentityStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Authorization through team grants: the token owner inherits the rights of its teams, a token scope narrows them, and
 * a disabled principal gets nothing. Every database starts empty, and every test seeds exactly what it needs.
 */
internal class IdentityAuthorizationTest {

    private val journalist = InMemoryLogger()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)

    @TempDir
    lateinit var workingDirectory: Path

    @Test
    fun `a deployer writes under the granted path and not outside it`() {
        withWorld { store, facade ->
            // given: a machine principal in a team with DEPLOY on releases under com/example
            val deployers = store.createTeam("deployers", "", null)!!
            store.replaceGrants(deployers.id, listOf(Triple("releases", "com/example", Role.DEPLOY)))
            val ci = machine(store, "ci")
            store.addMember(deployers.id, ci.id, TeamMemberSource.MANUAL)
            val token = token(store, ci, "ci-token", scopes = null)

            // then
            assertThat(facade.hasAccessTo(token, "/releases/com/example/app/1.0/app.jar", Action.WRITE)).describedAs("write under the granted path").isTrue
            assertThat(facade.hasAccessTo(token, "/releases/com/other/app.jar", Action.WRITE)).describedAs("write outside the granted path").isFalse
            assertThat(facade.hasAccessTo(token, "/snapshots/com/example/app.jar", Action.WRITE)).describedAs("write on another repository").isFalse
        }
    }

    @Test
    fun `a deployer may not delete while a maintainer may`() {
        withWorld { store, facade ->
            // given: one principal with DEPLOY and one with MAINTAIN on the same repository
            val deployers = store.createTeam("deployers", "", null)!!
            val maintainers = store.createTeam("maintainers", "", null)!!
            store.replaceGrants(deployers.id, listOf(Triple("releases", null, Role.DEPLOY)))
            store.replaceGrants(maintainers.id, listOf(Triple("releases", null, Role.MAINTAIN)))
            val deployer = machine(store, "deployer")
            val maintainer = machine(store, "maintainer")
            store.addMember(deployers.id, deployer.id, TeamMemberSource.MANUAL)
            store.addMember(maintainers.id, maintainer.id, TeamMemberSource.MANUAL)
            val deployerToken = token(store, deployer, "deployer-token", scopes = null)
            val maintainerToken = token(store, maintainer, "maintainer-token", scopes = null)

            // then
            assertThat(facade.hasAccessTo(deployerToken, "/releases/x.jar", Action.DELETE)).describedAs("deployer delete").isFalse
            assertThat(facade.hasAccessTo(maintainerToken, "/releases/x.jar", Action.DELETE)).describedAs("maintainer delete").isTrue
        }
    }

    @Test
    fun `a token scope narrows the rights of its owner`() {
        withWorld { store, facade ->
            // given: a DEPLOY principal whose token is narrowed to READ
            val deployers = store.createTeam("deployers", "", null)!!
            store.replaceGrants(deployers.id, listOf(Triple("releases", null, Role.DEPLOY)))
            val ci = machine(store, "ci")
            store.addMember(deployers.id, ci.id, TeamMemberSource.MANUAL)
            val narrowed = token(store, ci, "ci-read-only", scopes = setOf(Action.READ))

            // then: the team allows writing, the token does not
            assertThat(facade.hasAccessTo(narrowed, "/releases/x.jar", Action.READ)).describedAs("READ within scope").isTrue
            assertThat(facade.hasAccessTo(narrowed, "/releases/x.jar", Action.WRITE)).describedAs("WRITE outside scope").isFalse
        }
    }

    @Test
    fun `a disabled principal is denied and reported as disabled`() {
        withWorld { store, facade ->
            // given: an active principal with a granted token
            val deployers = store.createTeam("deployers", "", null)!!
            store.replaceGrants(deployers.id, listOf(Triple("releases", null, Role.DEPLOY)))
            val ci = machine(store, "ci")
            store.addMember(deployers.id, ci.id, TeamMemberSource.MANUAL)
            val token = token(store, ci, "ci-token", scopes = null)
            assertThat(facade.hasAccessTo(token, "/releases/x.jar", Action.WRITE)).describedAs("before disabling").isTrue

            // when: the principal is disabled
            store.updatePrincipal(ci.id, displayName = null, disabled = true)

            // then: nothing is allowed and the token is reported as disabled
            assertThat(facade.isDisabled(token)).describedAs("disabled flag").isTrue
            assertThat(facade.hasAccessTo(token, "/releases/x.jar", Action.READ)).describedAs("READ while disabled").isFalse
        }
    }

    @Test
    fun `a disabled principal cannot authenticate even with the right secret`() {
        withWorld { store, facade ->
            // given: a token with a known secret
            val ci = machine(store, "ci")
            store.createToken(ci.id, "ci-token", AccessTokenSecurityProvider.encodeSecret("s3cret"), null, null, LocalDate.of(2026, 10, 1), "")
            val authenticator = BasicAuthenticator(facade, clock)
            assertThat(authenticator.authenticate(Credentials("127.0.0.1", "ci-token", "s3cret")).isOk).describedAs("authentication before disabling").isTrue

            // when: the principal is disabled
            store.updatePrincipal(ci.id, displayName = null, disabled = true)

            // then
            assertThat(authenticator.authenticate(Credentials("127.0.0.1", "ci-token", "s3cret")).isOk).describedAs("authentication after disabling").isFalse
        }
    }

    @Test
    fun `an instance role held through a team gives the manager permission`() {
        withWorld { store, facade ->
            // given: a team holding the instance role, and a token owned by one of its members
            val admins = store.createTeam("admins", "", null)!!
            store.grantInstanceRole(principalId = null, teamId = admins.id)
            val alice = machine(store, "alice")
            store.addMember(admins.id, alice.id, TeamMemberSource.MANUAL)
            val token = token(store, alice, "alice-token", scopes = null)

            // then: the member is a manager and may reach any path
            assertThat(facade.hasPermission(token, MANAGER)).describedAs("manager through a team").isTrue
            assertThat(facade.hasAccessTo(token, "/any/where.jar", Action.DELETE)).describedAs("any action as manager").isTrue
        }
    }

    private fun machine(store: IdentityStore, name: String) =
        store.createPrincipal(PrincipalKind.MACHINE, name, name, disabled = false)!!

    private fun token(store: IdentityStore, principal: net.onelitefeather.ingot.identity.model.PrincipalRecord, name: String, scopes: Set<Action>?): AccessTokenIdentifier {
        val record = store.createToken(principal.id, name, AccessTokenSecurityProvider.encodeSecret("secret"), scopes, null, LocalDate.of(2026, 10, 1), "")!!
        return AccessTokenIdentifier(PERSISTENT, record.id)
    }

    private fun withWorld(block: (IdentityStore, AccessTokenFacade) -> Unit) {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            val facade = AccessTokenComponents(journalist, connection.database).accessTokenFacade()
            block(IdentityStore(connection.database, clock), facade)
        }
    }

}
