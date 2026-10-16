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

import com.reposilite.audit.api.AuditActor
import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.plugin.Extensions
import com.reposilite.shared.EntityTagSigner
import com.reposilite.token.AccessTokenSecurityProvider
import com.reposilite.token.application.AccessTokenComponents
import net.onelitefeather.ingot.identity.api.CreatePrincipalRequest
import net.onelitefeather.ingot.identity.api.CreateTeamRequest
import net.onelitefeather.ingot.identity.api.CreateTokenRequest
import net.onelitefeather.ingot.identity.api.GrantRequest
import net.onelitefeather.ingot.identity.api.ReplaceGrantsRequest
import net.onelitefeather.ingot.identity.api.UpdatePrincipalRequest
import net.onelitefeather.ingot.identity.api.UpdateTeamRequest
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.login.OwnTokens
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
 * The rules of the identity administration: names, conflicts, reserved teams, entity tags, one-time secrets and the access
 * explanation. Each test starts from an empty database.
 */
internal class IdentityFacadeTest {

    private val journalist = InMemoryLogger()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)

    @TempDir
    lateinit var workingDirectory: Path

    @Test
    fun `refuses a principal whose name differs only in case`() {
        withService { service, _ ->
            // given: an existing principal
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "ci-bot"))

            // when: another principal is created with the same name in other case
            val duplicate = service.createPrincipal(CreatePrincipalRequest(kind = "USER", name = "CI-BOT"))

            // then
            assertThat(duplicate.isErr).describedAs("case-only duplicate").isTrue
            assertThat(duplicate.error.status).describedAs("status of the duplicate").isEqualTo(409)
        }
    }

    @Test
    fun `refuses a principal name that contains a colon`() {
        withService { service, _ ->
            // when: a name with ':' is requested
            val result = service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "machine:bot"))

            // then: names with ':' are reserved, since they collide with private team names and token names
            assertThat(result.isErr).describedAs("name with colon").isTrue
            assertThat(result.error.status).isEqualTo(400)
        }
    }

    @Test
    fun `a stale entity tag refuses the update of a principal`() {
        withService { service, _ ->
            // given: a principal read once, then changed by someone else
            val first = service.createPrincipal(CreatePrincipalRequest(kind = "USER", name = "alice")).orNull()!!
            service.updatePrincipal("alice", UpdatePrincipalRequest(displayName = "Alice A."), ifMatch = null)

            // when: the update is sent with the tag of the first read
            val result = service.updatePrincipal("alice", UpdatePrincipalRequest(disabled = true), ifMatch = first.etag)

            // then: the write is refused and nothing changed
            assertThat(result.error.status).describedAs("status for a stale tag").isEqualTo(412)
            assertThat(service.getPrincipal("alice").orNull()?.value?.principal?.disabled).describedAs("disabled after refused write").isFalse
        }
    }

    @Test
    fun `the current entity tag allows the update of a principal`() {
        withService { service, _ ->
            // given: a principal and its current tag
            val created = service.createPrincipal(CreatePrincipalRequest(kind = "USER", name = "alice")).orNull()!!

            // when
            val result = service.updatePrincipal("alice", UpdatePrincipalRequest(disabled = true), ifMatch = created.etag)

            // then
            assertThat(result.isOk).describedAs("update with the current tag").isTrue
            assertThat(result.orNull()?.value?.principal?.disabled).isTrue
        }
    }

    @Test
    fun `a token secret is returned once and stored only as a hash`() {
        withService { service, store ->
            // given: a machine principal
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "ci"))

            // when: a token is created with a given secret
            val created = service.createToken("ci", CreateTokenRequest(name = "ci-token", secret = "s3cret-value"), AuditActor.SYSTEM_ACTOR).orNull()!!

            // then: the secret comes back once, the listing has no secret, and the stored value is a hash
            assertThat(created.secret).isEqualTo("s3cret-value")
            assertThat(service.listTokens("ci").orNull()?.map { it.name }).containsExactly("ci-token")
            assertThat(store.findToken("ci-token")?.secretHash).describedAs("stored secret").isNotEqualTo("s3cret-value")
            assertThat(AccessTokenSecurityProvider.matches(store.findToken("ci-token")!!.secretHash, "s3cret-value")).isTrue
        }
    }

    @Test
    fun `a token created without a secret gets a bearer secret that names the token`() {
        withService { service, store ->
            // given: a machine principal
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "ci"))

            // when: a token is created without a secret of its own
            val created = service.createToken("ci", CreateTokenRequest(name = "ci-token"), AuditActor.SYSTEM_ACTOR).orNull()!!
            val stored = store.findToken("ci-token")!!

            // then: the secret has the form of the identity tokens, names the token by its id, and verifies against the hash
            assertThat(created.secret).describedAs("secret of a generated token").startsWith("ingot_${stored.id}_")
            assertThat(AccessTokenSecurityProvider.matches(stored.secretHash, created.secret)).describedAs("stored hash").isTrue
            assertThat(OwnTokens(store, onTokenChanged = {}, audit = {}, clock = clock).nameOfBearer(created.secret))
                .describedAs("the bearer lookup must find the token the secret names")
                .isEqualTo("ci-token")
        }
    }

    @Test
    fun `refuses a token name that already exists`() {
        withService { service, _ ->
            // given: a token named "deploy"
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "ci"))
            service.createToken("ci", CreateTokenRequest(name = "deploy"), AuditActor.SYSTEM_ACTOR)

            // when / then
            assertThat(service.createToken("ci", CreateTokenRequest(name = "deploy"), AuditActor.SYSTEM_ACTOR).error.status).isEqualTo(409)
        }
    }

    @Test
    fun `refuses an empty scope list`() {
        withService { service, _ ->
            // given: a principal
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "ci"))

            // when: a token is created with an empty scope list
            val result = service.createToken("ci", CreateTokenRequest(name = "narrow", scopes = emptyList()), AuditActor.SYSTEM_ACTOR)

            // then: an empty list would mean nothing at all, which is refused
            assertThat(result.error.status).describedAs("status for empty scopes").isEqualTo(400)
        }
    }

    @Test
    fun `refuses a team named like a private team`() {
        withService { service, _ ->
            // when / then: the machine prefix belongs to private teams
            assertThat(service.createTeam(CreateTeamRequest(name = "machine:ci")).error.status).isEqualTo(409)
        }
    }

    @Test
    fun `private teams are neither listed nor readable`() {
        withService { service, store ->
            // given: a machine principal with its private team
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "ci"))
            assertThat(store.findTeam("machine:ci")).describedAs("private team of the machine principal").isNotNull

            // then
            assertThat(service.listTeams().orNull()).describedAs("listed teams").isEmpty()
            assertThat(service.getTeam("machine:ci").error.status).describedAs("status of a private team").isEqualTo(404)
        }
    }

    @Test
    fun `refuses a legacy role in a team grant`() {
        withService { service, _ ->
            // given: a team
            service.createTeam(CreateTeamRequest(name = "deployers"))

            // when: a legacy role is assigned
            val result = service.replaceGrants("deployers", ReplaceGrantsRequest(listOf(GrantRequest(repository = "releases", role = "LEGACY_WRITE"))).grants, ifMatch = null)

            // then
            assertThat(result.error.status).describedAs("status for a legacy role").isEqualTo(400)
        }
    }

    @Test
    fun `a team grant is stored with its path prefix normalised`() {
        withService { service, _ ->
            // given: a team
            service.createTeam(CreateTeamRequest(name = "deployers"))

            // when: a grant with slashes around the path prefix is set
            val detail = service.replaceGrants(
                "deployers",
                listOf(GrantRequest(repository = "releases", pathPrefix = "/com/example/", role = "DEPLOY")),
                ifMatch = null,
            ).orNull()!!.value

            // then
            assertThat(detail.grants).containsExactly(net.onelitefeather.ingot.identity.api.GrantDto("releases", "com/example", "DEPLOY"))
        }
    }

    @Test
    fun `the access explanation lists the team grant with the team as its reason`() {
        withService { service, store ->
            // given: a DEPLOY grant on releases, held by a member principal
            service.createTeam(CreateTeamRequest(name = "deployers"))
            service.replaceGrants("deployers", listOf(GrantRequest(repository = "releases", pathPrefix = "com/example", role = "DEPLOY")), ifMatch = null)
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "ci"))
            store.addMember(store.findTeam("deployers")!!.id, store.findPrincipal("ci")!!.id, TeamMemberSource.MANUAL)

            // when
            val access = service.repositoryAccess("releases").orNull()!!

            // then: the entry names the effective actions and the team it comes from
            assertThat(access.visibility).isEqualTo("HIDDEN")
            val entry = access.entries.single { it.principal == "ci" }
            assertThat(entry.effective).containsExactly("READ", "WRITE")
            assertThat(entry.via.map { it.team to it.role }).containsExactly("deployers" to "DEPLOY")
        }
    }

    @Test
    fun `the access explanation of an unknown repository is not found`() {
        withService { service, _ ->
            assertThat(service.repositoryAccess("missing").error.status).isEqualTo(404)
        }
    }

    @Test
    fun `deleting a principal removes its tokens`() {
        withService { service, store ->
            // given: a machine principal with a token
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "ci"))
            service.createToken("ci", CreateTokenRequest(name = "ci-token"), AuditActor.SYSTEM_ACTOR)

            // when
            service.deletePrincipal("ci", ifMatch = null)

            // then
            assertThat(store.findToken("ci-token")).describedAs("token of the deleted principal").isNull()
            assertThat(store.findPrincipal("ci")).describedAs("deleted principal").isNull()
        }
    }

    @Test
    fun `a team cannot be created twice in another letter case`() {
        withService { service, _ ->
            service.createTeam(CreateTeamRequest(name = "Deployers"))
            assertThat(service.createTeam(CreateTeamRequest(name = "deployers")).error.status).isEqualTo(409)
        }
    }

    @Test
    fun `a team update with a stale tag is refused`() {
        withService { service, _ ->
            // given: a team read once, then changed
            val first = service.createTeam(CreateTeamRequest(name = "deployers")).orNull()!!
            service.updateTeam("deployers", UpdateTeamRequest(description = "changed"), ifMatch = null)

            // when / then
            assertThat(service.updateTeam("deployers", UpdateTeamRequest(description = "again"), ifMatch = first.etag).error.status).isEqualTo(412)
        }
    }

    private fun withService(block: (IdentityFacade, IdentityStore) -> Unit) {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            val database = connection.database
            val accessTokens = AccessTokenComponents(journalist, database).accessTokenFacade()
            val store = IdentityStore(database, clock)
            val service = IdentityFacade(
                store = store,
                accessTokens = accessTokens,
                entityTags = EntityTagSigner.ephemeral(),
                extensions = Extensions(journalist),
                visibilityOf = { repository -> if (repository == "releases") "HIDDEN" else null },
                clock = clock,
            )
            block(service, store)
        }
    }

}
