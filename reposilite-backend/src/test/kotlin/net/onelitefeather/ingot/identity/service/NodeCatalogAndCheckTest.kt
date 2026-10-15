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
import com.reposilite.token.AccessTokenIdentifier
import com.reposilite.token.AccessTokenType
import com.reposilite.token.application.AccessTokenComponents
import net.onelitefeather.ingot.identity.api.CreatePrincipalRequest
import net.onelitefeather.ingot.identity.api.CreateTeamRequest
import net.onelitefeather.ingot.identity.api.CreateTokenRequest
import net.onelitefeather.ingot.identity.api.NodeDto
import net.onelitefeather.ingot.identity.storage.IdentityStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The node catalog, the templates and the permission check of the identity API. Each test starts from an empty database
 * with a fixed clock and two repositories.
 */
internal class NodeCatalogAndCheckTest {

    private val journalist = InMemoryLogger()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)

    @TempDir
    lateinit var workingDirectory: Path

    @Test
    fun `the catalog lists every administrative area as a static node`() {
        withService { service, _ ->
            val nodes = service.nodeCatalog(null).orNull()!!.nodes

            assertThat(nodes.filter { it.kind == "STATIC" }.map { it.node })
                .describedAs("static nodes")
                .containsExactlyInAnyOrder(
                    "ingot.admin.settings", "ingot.admin.users", "ingot.admin.teams", "ingot.admin.tokens",
                    "ingot.admin.maintenance", "ingot.admin.audit", "ingot.admin.console", "ingot.admin.dashboard",
                )
        }
    }

    @Test
    fun `the catalog lists the star node and the instance admin node as wildcards`() {
        withService { service, _ ->
            val nodes = service.nodeCatalog(null).orNull()!!.nodes

            assertThat(nodes.single { it.node == "*" }.kind).describedAs("star").isEqualTo("WILDCARD")
            assertThat(nodes.single { it.node == "ingot.admin.*" }.kind).describedAs("admin wildcard").isEqualTo("WILDCARD")
        }
    }

    @Test
    fun `the catalog lists the four actions and the wildcard of every existing repository`() {
        withService { service, _ ->
            val nodes = service.nodeCatalog(null).orNull()!!.nodes.filter { it.kind == "REPOSITORY" }.map { it.node }

            assertThat(nodes).describedAs("repository nodes")
                .contains(
                    "ingot.repository.releases.read", "ingot.repository.releases.deploy",
                    "ingot.repository.releases.delete", "ingot.repository.releases.manage", "ingot.repository.releases.*",
                    "ingot.repository.snapshots.read", "ingot.repository.snapshots.manage", "ingot.repository.snapshots.*",
                )
        }
    }

    @Test
    fun `the catalog lists the action wildcards over every repository`() {
        withService { service, _ ->
            val nodes = service.nodeCatalog(null).orNull()!!.nodes

            assertThat(nodes.filter { it.node.startsWith("ingot.repository.*.") }.map { it.node })
                .describedAs("wildcard repository nodes")
                .containsExactlyInAnyOrder("ingot.repository.*.read", "ingot.repository.*.deploy", "ingot.repository.*.delete", "ingot.repository.*.manage")
        }
    }

    @Test
    fun `the catalog filters by prefix`() {
        withService { service, _ ->
            val nodes = service.nodeCatalog("ingot.admin.u").orNull()!!.nodes.map { it.node }

            assertThat(nodes).describedAs("filtered").containsExactly("ingot.admin.users")
        }
    }

    @Test
    fun `the templates are the five predefined bundles in order`() {
        withService { service, _ ->
            val templates = service.nodeTemplates().orNull()!!.templates

            assertThat(templates.map { it.id }).containsExactly("read", "deploy", "maintain", "admin", "instance-admin")
        }
    }

    @Test
    fun `the admin template holds the four repository actions on the repository placeholder`() {
        withService { service, _ ->
            val admin = service.nodeTemplates().orNull()!!.templates.single { it.id == "admin" }

            assertThat(admin.nodes).containsExactlyInAnyOrder(
                "ingot.repository.{repository}.read", "ingot.repository.{repository}.deploy",
                "ingot.repository.{repository}.delete", "ingot.repository.{repository}.manage",
            )
        }
    }

    @Test
    fun `the instance admin template is the admin node`() {
        withService { service, _ ->
            val instanceAdmin = service.nodeTemplates().orNull()!!.templates.single { it.id == "instance-admin" }

            assertThat(instanceAdmin.nodes).containsExactly("ingot.admin.*")
        }
    }

    @Test
    fun `the check names the team that decides and lists every candidate it considered`() {
        withService { service, _ ->
            // given: alice is in deployers, which may deploy to releases
            val admin = bootstrapAdmin(service)
            service.createPrincipal(CreatePrincipalRequest(kind = "USER", name = "alice"))
            service.createTeam(CreateTeamRequest(name = "deployers"))
            service.replaceTeamNodes("deployers", listOf(NodeDto("ingot.repository.releases.deploy", true)), ifMatch = null)
            service.replaceMembers("deployers", listOf("alice"), ifMatch = null)

            // when
            val check = service.permissionCheck(admin, "alice", "ingot.repository.releases.deploy", token = null).orNull()!!

            // then
            assertThat(check.allowed).describedAs("allowed").isTrue
            assertThat(check.decision.source).describedAs("source").isEqualTo("TEAM")
            assertThat(check.decision.subject).describedAs("deciding team").isEqualTo("deployers")
            assertThat(check.trace.filter { it.matched }.map { it.node }).describedAs("matched candidates")
                .containsExactly("ingot.repository.releases.deploy")
        }
    }

    @Test
    fun `the check reports an inherited team at its distance`() {
        withService { service, _ ->
            val admin = bootstrapAdmin(service)
            service.createPrincipal(CreatePrincipalRequest(kind = "USER", name = "alice"))
            service.createTeam(CreateTeamRequest(name = "deployers"))
            service.createTeam(CreateTeamRequest(name = "release-managers"))
            service.replaceTeamNodes("release-managers", listOf(NodeDto("ingot.repository.releases.read", true)), ifMatch = null)
            service.replaceTeamParents("deployers", listOf("release-managers"), ifMatch = null)
            service.replaceMembers("deployers", listOf("alice"), ifMatch = null)

            val check = service.permissionCheck(admin, "alice", "ingot.repository.releases.read", token = null).orNull()!!

            assertThat(check.decision.source).describedAs("source").isEqualTo("INHERITED_TEAM")
            assertThat(check.decision.subject).describedAs("inherited team").isEqualTo("release-managers")
            assertThat(check.decision.depth).describedAs("distance").isEqualTo(1)
        }
    }

    @Test
    fun `the check shows a token that narrows its owner as the deciding source`() {
        withService { service, _ ->
            val admin = bootstrapAdmin(service)
            service.createPrincipal(CreatePrincipalRequest(kind = "USER", name = "alice"))
            service.createTeam(CreateTeamRequest(name = "deployers"))
            service.replaceTeamNodes("deployers", listOf(NodeDto("ingot.repository.releases.deploy", true)), ifMatch = null)
            service.replaceMembers("deployers", listOf("alice"), ifMatch = null)
            service.createToken("alice", CreateTokenRequest(name = "alice-read", scopes = listOf("READ")))

            val check = service.permissionCheck(admin, "alice", "ingot.repository.releases.deploy", token = "alice-read").orNull()!!

            assertThat(check.allowed).describedAs("narrowed").isFalse
            assertThat(check.decision.source).describedAs("source").isEqualTo("TOKEN")
        }
    }

    @Test
    fun `the check refuses a node with a wildcard`() {
        withService { service, _ ->
            val admin = bootstrapAdmin(service)

            val result = service.permissionCheck(admin, "admin", "ingot.repository.*.read", token = null)

            assertThat(result.error.status).describedAs("status").isEqualTo(400)
        }
    }

    @Test
    fun `the check answers 404 for an unknown principal`() {
        withService { service, _ ->
            val admin = bootstrapAdmin(service)

            assertThat(service.permissionCheck(admin, "nobody", "ingot.repository.releases.read", token = null).error.status).isEqualTo(404)
        }
    }

    @Test
    fun `a caller that is not an admin may check only its own principal`() {
        withService { service, _ ->
            bootstrapAdmin(service)
            val alice = service.createPrincipal(CreatePrincipalRequest(kind = "USER", name = "alice")).orNull()!!
            service.createPrincipal(CreatePrincipalRequest(kind = "USER", name = "bob"))
            val aliceCaller = AccessTokenIdentifier(type = AccessTokenType.PRINCIPAL, value = alice.value.principal.id)

            assertThat(service.permissionCheck(aliceCaller, "bob", "ingot.repository.releases.read", token = null).error.status)
                .describedAs("other principal").isEqualTo(403)
            assertThat(service.permissionCheck(aliceCaller, "alice", "ingot.repository.releases.read", token = null).isOk)
                .describedAs("own principal").isTrue
        }
    }

    /** Creates an instance admin with a usable token and returns the identifier of that principal, for the check. */
    private fun bootstrapAdmin(service: IdentityFacade): AccessTokenIdentifier {
        val admin = service.createPrincipal(CreatePrincipalRequest(kind = "USER", name = "admin")).orNull()!!
        service.createToken("admin", CreateTokenRequest(name = "admin-token"))
        service.grantPrincipalInstanceAdmin("admin", ifMatch = null)
        return AccessTokenIdentifier(type = AccessTokenType.PRINCIPAL, value = admin.value.principal.id)
    }

    private fun withService(block: (IdentityFacade, IdentityStore) -> Unit) {
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
                repositoryNames = { listOf("releases", "snapshots") },
                clock = clock,
            )
            block(service, store)
        }
    }
}
