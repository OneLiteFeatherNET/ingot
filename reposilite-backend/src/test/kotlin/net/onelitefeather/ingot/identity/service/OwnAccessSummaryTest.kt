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
import com.reposilite.token.AccessTokenFacade
import com.reposilite.token.AccessTokenIdentifier
import com.reposilite.token.AccessTokenPermission
import com.reposilite.token.AccessTokenType
import com.reposilite.token.api.CreateAccessTokenRequest
import com.reposilite.token.application.AccessTokenComponents
import net.onelitefeather.ingot.identity.api.CreatePrincipalRequest
import net.onelitefeather.ingot.identity.api.CreateTeamRequest
import net.onelitefeather.ingot.identity.api.CreateTokenRequest
import net.onelitefeather.ingot.identity.api.NodeDto
import net.onelitefeather.ingot.identity.login.external.ExternalIdentityStore
import net.onelitefeather.ingot.identity.storage.IdentityStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The caller's own access summary. Each test starts from an empty database with a fixed clock, and builds its own
 * principals, teams and tokens.
 */
internal class OwnAccessSummaryTest {

    private val journalist = InMemoryLogger()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)

    @TempDir
    lateinit var workingDirectory: Path

    @Test
    fun `the summary names the caller with its display name and kind`() {
        withService { service, _, _, _ ->
            service.createPrincipal(CreatePrincipalRequest(kind = "USER", name = "alice", displayName = "Alice Example"))

            val summary = service.ownAccess(principalCaller(service, "alice")).orNull()!!

            assertThat(summary.principal?.name).describedAs("name").isEqualTo("alice")
            assertThat(summary.principal?.displayName).describedAs("display name").isEqualTo("Alice Example")
            assertThat(summary.principal?.kind).describedAs("kind").isEqualTo("USER")
        }
    }

    @Test
    fun `the teams of the caller are listed with their direct parents only`() {
        withService { service, _, _, _ ->
            service.createPrincipal(CreatePrincipalRequest(kind = "USER", name = "alice"))
            service.createTeam(CreateTeamRequest(name = "root"))
            service.createTeam(CreateTeamRequest(name = "maintainers"))
            service.createTeam(CreateTeamRequest(name = "releases-maintainers"))
            service.replaceTeamParents("maintainers", listOf("root"), ifMatch = null)
            service.replaceTeamParents("releases-maintainers", listOf("maintainers"), ifMatch = null)
            service.replaceMembers("releases-maintainers", listOf("alice"), ifMatch = null)

            val summary = service.ownAccess(principalCaller(service, "alice")).orNull()!!

            assertThat(summary.teams.map { it.name }).describedAs("teams").containsExactly("releases-maintainers")
            assertThat(summary.teams.single().parents).describedAs("direct parents only").containsExactly("maintainers")
        }
    }

    @Test
    fun `the private team of a machine account is not listed as a team`() {
        withService { service, _, _, _ ->
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "ci-deploy"))
            service.replacePrincipalNodes("ci-deploy", listOf(NodeDto("ingot.repository.releases.deploy", true)), ifMatch = null)

            val summary = service.ownAccess(principalCaller(service, "ci-deploy")).orNull()!!

            assertThat(summary.teams).describedAs("teams without the private team").isEmpty()
            assertThat(summary.nodes).describedAs("nodes of the machine account").containsExactly("ingot.repository.releases.deploy")
        }
    }

    @Test
    fun `the nodes are the effective nodes of the caller, from its own rules and its teams`() {
        withService { service, _, _, _ ->
            service.createPrincipal(CreatePrincipalRequest(kind = "USER", name = "alice"))
            service.replacePrincipalNodes("alice", listOf(NodeDto("ingot.repository.releases.read", true)), ifMatch = null)
            service.createTeam(CreateTeamRequest(name = "deployers"))
            service.replaceTeamNodes("deployers", listOf(NodeDto("ingot.repository.releases.deploy", true)), ifMatch = null)
            service.replaceMembers("deployers", listOf("alice"), ifMatch = null)

            val summary = service.ownAccess(principalCaller(service, "alice")).orNull()!!

            assertThat(summary.nodes).describedAs("effective nodes")
                .containsExactly("ingot.repository.releases.deploy", "ingot.repository.releases.read")
        }
    }

    @Test
    fun `a token narrows the nodes of its owner and marks the answer as narrowed`() {
        withService { service, store, _, _ ->
            service.createPrincipal(CreatePrincipalRequest(kind = "USER", name = "alice"))
            service.createTeam(CreateTeamRequest(name = "deployers"))
            service.replaceTeamNodes(
                "deployers",
                listOf(NodeDto("ingot.repository.releases.read", true), NodeDto("ingot.repository.releases.deploy", true)),
                ifMatch = null,
            )
            service.replaceMembers("deployers", listOf("alice"), ifMatch = null)
            service.createToken("alice", CreateTokenRequest(name = "alice-read", nodes = listOf(NodeDto("ingot.repository.releases.read", true))))

            val summary = service.ownAccess(persistentCaller(store, "alice-read")).orNull()!!

            assertThat(summary.nodes).describedAs("narrowed nodes").containsExactly("ingot.repository.releases.read")
            assertThat(summary.tokenNarrowed).describedAs("narrowed").isTrue
            assertThat(summary.token).describedAs("token name").isEqualTo("alice-read")
        }
    }

    @Test
    fun `denials are listed apart from the allowed nodes`() {
        withService { service, _, _, _ ->
            service.createPrincipal(CreatePrincipalRequest(kind = "USER", name = "alice"))
            service.replacePrincipalNodes("alice", listOf(NodeDto("ingot.repository.releases.read", true)), ifMatch = null)
            service.createTeam(CreateTeamRequest(name = "readers"))
            service.replaceTeamNodes("readers", listOf(NodeDto("ingot.repository.releases.delete", false)), ifMatch = null)
            service.replaceMembers("readers", listOf("alice"), ifMatch = null)

            val summary = service.ownAccess(principalCaller(service, "alice")).orNull()!!

            assertThat(summary.denials).describedAs("denials").containsExactly("ingot.repository.releases.delete")
            assertThat(summary.nodes).describedAs("allowed nodes").doesNotContain("ingot.repository.releases.delete")
        }
    }

    @Test
    fun `the instance administrator flag is set for an administrator`() {
        withService { service, _, _, _ ->
            val admin = bootstrapAdmin(service)

            val summary = service.ownAccess(admin).orNull()!!

            assertThat(summary.instanceAdmin).describedAs("instance administrator").isTrue
        }
    }

    @Test
    fun `the instance administrator flag is not set for a plain member`() {
        withService { service, _, _, _ ->
            bootstrapAdmin(service)
            service.createPrincipal(CreatePrincipalRequest(kind = "USER", name = "alice"))
            service.replacePrincipalNodes("alice", listOf(NodeDto("ingot.repository.releases.read", true)), ifMatch = null)

            val summary = service.ownAccess(principalCaller(service, "alice")).orNull()!!

            assertThat(summary.instanceAdmin).describedAs("instance administrator").isFalse
        }
    }

    @Test
    fun `the summary carries no token secret and no name of a token the caller does not present`() {
        withService { service, _, _, _ ->
            service.createPrincipal(CreatePrincipalRequest(kind = "USER", name = "alice"))
            val presented = service.createToken("alice", CreateTokenRequest(name = "alice-read")).orNull()!!
            service.createToken("alice", CreateTokenRequest(name = "alice-ci"))

            val summary = service.ownAccess(principalCaller(service, "alice")).orNull()!!
            val rendered = summary.toString()

            assertThat(rendered).describedAs("secret of the presented token").doesNotContain(presented.secret)
            assertThat(rendered).describedAs("name of the other token").doesNotContain("alice-ci")
            assertThat(summary.token).describedAs("no token for a session").isNull()
        }
    }

    @Test
    fun `a temporary token has no principal and no teams, and names the token`() {
        withService { service, _, accessTokens, _ ->
            val temporary = accessTokens.createAccessToken(CreateAccessTokenRequest(type = AccessTokenType.TEMPORARY, name = "setup-token")).accessToken
            accessTokens.addPermission(temporary.identifier, AccessTokenPermission.MANAGER)

            val summary = service.ownAccess(temporary.identifier).orNull()!!

            assertThat(summary.principal).describedAs("principal").isNull()
            assertThat(summary.teams).describedAs("teams").isEmpty()
            assertThat(summary.token).describedAs("token name").isEqualTo("setup-token")
            assertThat(summary.nodes).describedAs("nodes of the token").contains("ingot.admin.*")
        }
    }

    @Test
    fun `a denial that a more specific allow overrides is still listed as the rule the caller holds`() {
        withService { service, _, _, _ ->
            service.createPrincipal(CreatePrincipalRequest(kind = "USER", name = "alice"))
            service.replacePrincipalNodes("alice", listOf(NodeDto("ingot.repository.releases.delete", true)), ifMatch = null)
            service.createTeam(CreateTeamRequest(name = "readers"))
            service.replaceTeamNodes("readers", listOf(NodeDto("ingot.repository.releases.delete", false)), ifMatch = null)
            service.replaceMembers("readers", listOf("alice"), ifMatch = null)

            val summary = service.ownAccess(principalCaller(service, "alice")).orNull()!!

            assertThat(summary.denials).describedAs("raw denying rule of the team").containsExactly("ingot.repository.releases.delete")
        }
    }

    @Test
    fun `an LDAP link and an OIDC link are listed as the provider kinds of the principal`() {
        withService { service, _, _, links ->
            val alice = service.createPrincipal(CreatePrincipalRequest(kind = "USER", name = "alice")).orNull()!!
            links.link("ldap:ldap.example.org", "uid=alice,ou=people,dc=example,dc=org", alice.value.principal.id)
            links.link("oidc:corporate", "subject-of-alice", alice.value.principal.id)

            val summary = service.ownAccess(principalCaller(service, "alice")).orNull()!!

            assertThat(summary.principal?.providers).describedAs("providers").containsExactly("ldap", "oidc")
        }
    }

    @Test
    fun `every node of the summary is allowed by the check and every denial is denied by it`() {
        withService { service, _, _, _ ->
            val admin = bootstrapAdmin(service)
            service.createPrincipal(CreatePrincipalRequest(kind = "USER", name = "alice"))
            service.replacePrincipalNodes("alice", listOf(NodeDto("ingot.repository.releases.read", true)), ifMatch = null)
            service.createTeam(CreateTeamRequest(name = "deployers"))
            service.replaceTeamNodes(
                "deployers",
                listOf(NodeDto("ingot.repository.releases.deploy", true), NodeDto("ingot.repository.releases.delete", false)),
                ifMatch = null,
            )
            service.replaceMembers("deployers", listOf("alice"), ifMatch = null)

            val summary = service.ownAccess(principalCaller(service, "alice")).orNull()!!

            assertThat(summary.nodes).describedAs("nodes").isNotEmpty()
            summary.nodes.forEach { node ->
                assertThat(service.permissionCheck(admin, "alice", node, token = null).orNull()!!.allowed)
                    .describedAs("check of allowed node $node").isTrue
            }
            summary.denials.forEach { node ->
                assertThat(service.permissionCheck(admin, "alice", node, token = null).orNull()!!.allowed)
                    .describedAs("check of denial $node").isFalse
            }
        }
    }

    @Test
    fun `a principal without a password has no login providers`() {
        withService { service, _, _, _ ->
            service.createPrincipal(CreatePrincipalRequest(kind = "USER", name = "alice"))

            val summary = service.ownAccess(principalCaller(service, "alice")).orNull()!!

            assertThat(summary.principal?.providers).describedAs("providers").isEmpty()
        }
    }

    /** Creates an instance admin with a usable token and returns the identifier of that principal. */
    private fun bootstrapAdmin(service: IdentityFacade): AccessTokenIdentifier {
        val admin = service.createPrincipal(CreatePrincipalRequest(kind = "USER", name = "admin")).orNull()!!
        service.createToken("admin", CreateTokenRequest(name = "admin-token"))
        service.grantPrincipalInstanceAdmin("admin", ifMatch = null)
        return AccessTokenIdentifier(type = AccessTokenType.PRINCIPAL, value = admin.value.principal.id)
    }

    private fun principalCaller(service: IdentityFacade, name: String): AccessTokenIdentifier =
        AccessTokenIdentifier(type = AccessTokenType.PRINCIPAL, value = service.getPrincipal(name).orNull()!!.value.principal.id)

    private fun persistentCaller(store: IdentityStore, tokenName: String): AccessTokenIdentifier =
        AccessTokenIdentifier(type = AccessTokenType.PERSISTENT, value = store.findToken(tokenName)!!.id)

    private fun withService(block: (IdentityFacade, IdentityStore, AccessTokenFacade, ExternalIdentityStore) -> Unit) {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            val database = connection.database
            val extensions = Extensions(journalist)
            val accessTokens = AccessTokenComponents(journalist, database).accessTokenFacade(extensions = extensions)
            val store = IdentityStore(database, clock)
            val service = IdentityFacade(
                store = store,
                accessTokens = accessTokens,
                entityTags = EntityTagSigner.ephemeral(),
                extensions = extensions,
                visibilityOf = { repository -> if (repository == "releases") "HIDDEN" else null },
                repositoryNames = { listOf("releases") },
                clock = clock,
            )
            block(service, store, accessTokens, ExternalIdentityStore(database))
        }
    }
}
