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

package net.onelitefeather.ingot.identity.session

import com.reposilite.auth.AuthenticationFacade
import com.reposilite.auth.Authenticator
import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.token.AccessTokenIdentifier
import com.reposilite.token.AccessTokenType.PRINCIPAL
import com.reposilite.token.application.AccessTokenComponents
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
 * The session details that /api/auth/me answers carry the instance admin flag, decided by the same evaluator that
 * gates every admin endpoint. The flag must hold whichever way the admin right is granted, so a client never shows a
 * different state than the server enforces.
 */
internal class SessionDetailsInstanceAdminTest {

    private val journalist = InMemoryLogger()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-11-01T10:00:00Z"), ZoneOffset.UTC)

    @TempDir
    lateinit var workingDirectory: Path

    private fun withWorld(block: (IdentityStore, AuthenticationFacade) -> Unit) {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            val tokens = AccessTokenComponents(journalist, connection.database).accessTokenFacade()
            val authentication = AuthenticationFacade(
                journalist = journalist,
                authenticators = sortedSetOf<Authenticator>(Authenticator.priorityComparator),
                accessTokenFacade = tokens,
                clock = clock,
            )
            block(IdentityStore(connection.database, clock), authentication)
        }
    }

    private fun user(store: IdentityStore, name: String): PrincipalRecord =
        store.createPrincipal(PrincipalKind.USER, name, name, disabled = false, createdAt = clock.instant())!!

    @Test
    fun `a direct instance admin session reports the instance admin flag`() {
        withWorld { store, authentication ->
            // given: a user holding the instance role itself
            val admin = user(store, "admin")
            store.grantInstanceRole(principalId = admin.id, teamId = null)

            // when: the session details of the browser session are read
            val details = authentication.getSessionDetails(AccessTokenIdentifier(PRINCIPAL, admin.id)).get()

            // then: the flag is set
            assertThat(details.instanceAdmin).describedAs("the instance role of the user makes the session an admin").isTrue()
        }
    }

    @Test
    fun `a session of a member of an instance admin team reports the instance admin flag`() {
        withWorld { store, authentication ->
            // given: a user whose only admin right is the instance role of a team they are a member of
            val alice = user(store, "alice")
            val admins = store.createTeam("admins", "Runs the instance", null)!!
            store.addMember(admins.id, alice.id, TeamMemberSource.MANUAL)
            store.grantInstanceRole(principalId = null, teamId = admins.id)

            // when: the session details of the browser session are read
            val details = authentication.getSessionDetails(AccessTokenIdentifier(PRINCIPAL, alice.id)).get()

            // then: the flag is set, although the user holds no instance role itself
            assertThat(details.instanceAdmin).describedAs("the team instance role makes its members admins").isTrue()
        }
    }

    @Test
    fun `a session of a user without admin rights does not report the instance admin flag`() {
        withWorld { store, authentication ->
            // given: a user in a team that may only deploy to one repository
            val bob = user(store, "bob")
            val deployers = store.createTeam("deployers", "", null)!!
            store.addMember(deployers.id, bob.id, TeamMemberSource.MANUAL)
            store.addGrant(deployers.id, "releases", null, Role.DEPLOY)

            // when: the session details of the browser session are read
            val details = authentication.getSessionDetails(AccessTokenIdentifier(PRINCIPAL, bob.id)).get()

            // then: the flag is not set
            assertThat(details.instanceAdmin).describedAs("a deploy grant is not an instance admin right").isFalse()
        }
    }

}
