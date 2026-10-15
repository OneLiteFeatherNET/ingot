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
package net.onelitefeather.ingot.identity.service

import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.plugin.Extensions
import com.reposilite.shared.EntityTagSigner
import com.reposilite.token.application.AccessTokenComponents
import net.onelitefeather.ingot.identity.api.CreatePrincipalRequest
import net.onelitefeather.ingot.identity.api.CreateTeamRequest
import net.onelitefeather.ingot.identity.api.CreateTokenRequest
import net.onelitefeather.ingot.identity.api.GrantRequest
import net.onelitefeather.ingot.identity.login.storage.LoginStore
import net.onelitefeather.ingot.identity.storage.IdentityStore
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The list answers of the identity administration carry every summary a list row shows, so that a client needs no
 * request per row. Every test starts from an empty database and a fixed clock.
 */
internal class IdentityListSummaryTest {

    private val journalist = InMemoryLogger()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)

    @TempDir
    lateinit var workingDirectory: Path

    @Test
    fun `a user with a local credential lists the local provider`() {
        withService { service, _, logins ->
            // given: a user that has a password, and a user that has none
            val alice = service.createPrincipal(CreatePrincipalRequest(kind = "USER", name = "alice")).orNull()!!.value.principal
            service.createPrincipal(CreatePrincipalRequest(kind = "USER", name = "bob"))
            logins.saveCredential(alice.id, "argon2id-hash", mustChangePassword = false)

            // when
            val listed = service.listPrincipals(null).orNull()!!.associateBy { it.name }

            // then
            assertThat(listed.getValue("alice").providers).describedAs("providers of a user with a password").containsExactly("local")
            assertThat(listed.getValue("bob").providers).describedAs("providers of a user without a password").isEmpty()
        }
    }

    @Test
    fun `a machine principal lists no provider even when a credential row exists`() {
        withService { service, _, logins ->
            // given: a machine principal that somehow has a credential row
            val machine = service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "ci-bot")).orNull()!!.value.principal
            logins.saveCredential(machine.id, "argon2id-hash", mustChangePassword = false)

            // when
            val listed = service.listPrincipals(null).orNull()!!.single()

            // then
            assertThat(listed.providers).describedAs("providers of a machine principal").isEmpty()
        }
    }

    @Test
    fun `the token count of each principal counts its own tokens`() {
        withService { service, _, _ ->
            // given: one principal with two tokens, one with one, one with none
            listOf("many", "one", "none").forEach { service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = it)) }
            service.createToken("many", CreateTokenRequest(name = "many-a"))
            service.createToken("many", CreateTokenRequest(name = "many-b"))
            service.createToken("one", CreateTokenRequest(name = "one-a"))

            // when
            val counts = service.listPrincipals(null).orNull()!!.associate { it.name to it.tokenCount }

            // then
            assertThat(counts).describedAs("token counts").containsExactlyInAnyOrderEntriesOf(mapOf("many" to 2, "one" to 1, "none" to 0))
        }
    }

    @Test
    fun `a principal with the direct instance role is listed as instance admin`() {
        withService { service, _, _ ->
            // given
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "root"))
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "other"))
            service.grantPrincipalInstanceAdmin("root", ifMatch = null)

            // when
            val admins = service.listPrincipals(null).orNull()!!.associate { it.name to it.instanceAdmin }

            // then
            assertThat(admins).describedAs("instance admin flags").containsExactlyInAnyOrderEntriesOf(mapOf("root" to true, "other" to false))
        }
    }

    @Test
    fun `a member of a team with the instance role is listed as instance admin`() {
        withService { service, _, _ ->
            // given: a team with the role, one member and one outsider
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "member"))
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "outsider"))
            service.createTeam(CreateTeamRequest(name = "operators"))
            service.replaceMembers("operators", listOf("member"), ifMatch = null)
            service.grantTeamInstanceAdmin("operators", ifMatch = null)

            // when
            val admins = service.listPrincipals(null).orNull()!!.associate { it.name to it.instanceAdmin }

            // then
            assertThat(admins).describedAs("instance admin flags").containsExactlyInAnyOrderEntriesOf(mapOf("member" to true, "outsider" to false))
        }
    }

    @Test
    fun `the instance role of a principal in the list is the direct role only`() {
        withService { service, _, _ ->
            // given: one principal with the direct role, one that is admin only through a team
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "direct"))
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "member"))
            service.grantPrincipalInstanceAdmin("direct", ifMatch = null)
            service.createTeam(CreateTeamRequest(name = "operators"))
            service.replaceMembers("operators", listOf("member"), ifMatch = null)
            service.grantTeamInstanceAdmin("operators", ifMatch = null)

            // when
            val roles = service.listPrincipals(null).orNull()!!.associate { it.name to it.instanceRole }

            // then
            assertThat(roles).describedAs("direct role flags").containsExactlyInAnyOrderEntriesOf(mapOf("direct" to true, "member" to false))
        }
    }

    @Test
    fun `the principal list keeps the kind filter`() {
        withService { service, _, _ ->
            // given
            service.createPrincipal(CreatePrincipalRequest(kind = "USER", name = "alice"))
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "ci-bot"))

            // when
            val users = service.listPrincipals("user").orNull()!!

            // then
            assertThat(users.map { it.name }).describedAs("users listed").containsExactly("alice")
        }
    }

    @Test
    fun `a team with the instance role is listed as instance admin`() {
        withService { service, _, _ ->
            // given
            service.createTeam(CreateTeamRequest(name = "operators"))
            service.createTeam(CreateTeamRequest(name = "readers"))
            service.grantTeamInstanceAdmin("operators", ifMatch = null)

            // when
            val admins = service.listTeams().orNull()!!.associate { it.name to it.instanceAdmin }

            // then
            assertThat(admins).describedAs("instance admin flags").containsExactlyInAnyOrderEntriesOf(mapOf("operators" to true, "readers" to false))
        }
    }

    @Test
    fun `the team list counts members and grants per team`() {
        withService { service, _, _ ->
            // given: a team with two members and one grant, and an empty team
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "first"))
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "second"))
            service.createTeam(CreateTeamRequest(name = "deployers"))
            service.createTeam(CreateTeamRequest(name = "empty"))
            service.replaceMembers("deployers", listOf("first", "second"), ifMatch = null)
            service.replaceGrants("deployers", listOf(GrantRequest(repository = "releases", role = "DEPLOY")), ifMatch = null)

            // when
            val teams = service.listTeams().orNull()!!.associateBy { it.name }

            // then
            assertThat(teams.keys).describedAs("private teams stay out of the list").containsExactlyInAnyOrder("deployers", "empty")
            assertThat(teams.getValue("deployers").memberCount).describedAs("members of deployers").isEqualTo(2)
            assertThat(teams.getValue("deployers").grantCount).describedAs("grants of deployers").isEqualTo(1)
            assertThat(teams.getValue("empty").memberCount).describedAs("members of the empty team").isZero
            assertThat(teams.getValue("empty").grantCount).describedAs("grants of the empty team").isZero
        }
    }

    @Test
    fun `listing principals needs the same number of statements however many principals exist`() {
        withService { service, store, _ ->
            // given: a list of two principals, measured
            repeat(2) { service.createPrincipal(CreatePrincipalRequest(kind = "USER", name = "user-$it")) }
            val small = statementsOf(store) { service.listPrincipals(null) }

            // when: ten more principals with tokens exist
            repeat(10) {
                service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "machine-$it"))
                service.createToken("machine-$it", CreateTokenRequest(name = "token-$it"))
            }
            val large = statementsOf(store) { service.listPrincipals(null) }

            // then: no statement is issued per row
            assertThat(small).describedAs("statements to list 2 principals").isPositive
            assertThat(large).describedAs("statements to list 12 principals compared with 2").isEqualTo(small)
        }
    }

    @Test
    fun `listing teams needs the same number of statements however many teams exist`() {
        withService { service, store, _ ->
            // given: a list of two teams, measured
            repeat(2) { service.createTeam(CreateTeamRequest(name = "team-$it")) }
            val small = statementsOf(store) { service.listTeams() }

            // when: ten more teams with a grant exist
            repeat(10) {
                service.createTeam(CreateTeamRequest(name = "more-$it"))
                service.replaceGrants("more-$it", listOf(GrantRequest(role = "READ")), ifMatch = null)
            }
            val large = statementsOf(store) { service.listTeams() }

            // then: no statement is issued per row
            assertThat(small).describedAs("statements to list 2 teams").isPositive
            assertThat(large).describedAs("statements to list 12 teams compared with 2").isEqualTo(small)
        }
    }

    /** The statements issued by [block]. The store joins the surrounding transaction, so they are counted together. */
    private fun statementsOf(store: IdentityStore, block: () -> Unit): Int =
        transaction(store.database()) {
            val before = statementCount
            block()
            statementCount - before
        }

    private fun withService(block: (IdentityFacade, IdentityStore, LoginStore) -> Unit) {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            val database = connection.database
            val accessTokens = AccessTokenComponents(journalist, database).accessTokenFacade()
            val store = IdentityStore(database, clock)
            val service = IdentityFacade(
                store = store,
                accessTokens = accessTokens,
                entityTags = EntityTagSigner.ephemeral(),
                extensions = Extensions(journalist),
                visibilityOf = { null },
                clock = clock,
            )
            block(service, store, LoginStore(database, clock))
        }
    }

}
