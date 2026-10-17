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
import net.onelitefeather.ingot.identity.api.GrantRequest
import net.onelitefeather.ingot.identity.api.NodeDto
import net.onelitefeather.ingot.identity.api.ReplaceGrantsRequest
import net.onelitefeather.ingot.identity.model.NodePattern
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
    fun `the templates are the five repository bundles followed by the artifact bundles`() {
        withService { service, _ ->
            val templates = service.nodeTemplates().orNull()!!.templates

            assertThat(templates.map { it.id }).containsExactly(
                "read", "deploy", "maintain", "admin", "instance-admin",
                "artifact-read", "artifact-deploy", "repository-artifact-deploy",
            )
        }
    }

    @Test
    fun `the artifact templates are valid nodes once their placeholders are filled`() {
        withService { service, _ ->
            val templates = service.nodeTemplates().orNull()!!.templates.filter { it.id.contains("artifact") }

            val filled = templates.flatMap { it.nodes }.map {
                it.replace("{repository}", "releases").replace("{groupId}", "net.example").replace("{artifactId}", "lib")
            }
            assertThat(filled).describedAs("filled artifact nodes").isNotEmpty()
            filled.forEach { node -> assertThat(NodePattern.problemOf(node)).describedAs("problem of $node").isNull() }
        }
    }

    @Test
    fun `the catalog lists the artifact actions over every artifact`() {
        withService { service, _ ->
            val nodes = service.nodeCatalog(null).orNull()!!.nodes.filter { it.kind == "ARTIFACT" }.map { it.node }

            assertThat(nodes).describedAs("artifact nodes")
                .containsExactlyInAnyOrder("ingot.artifact.*.read", "ingot.artifact.*.deploy", "ingot.artifact.*.delete", "ingot.artifact.*.*")
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
    fun `the check names the artifact node that decides an artifact request`() {
        withService { service, _ ->
            // given: alice may deploy to releases, except below net.onelitefeather
            val admin = bootstrapAdmin(service)
            service.createPrincipal(CreatePrincipalRequest(kind = "USER", name = "alice"))
            service.createTeam(CreateTeamRequest(name = "deployers"))
            service.replaceTeamNodes(
                "deployers",
                listOf(NodeDto("ingot.repository.releases.deploy", true), NodeDto("ingot.artifact.net.onelitefeather.deploy", false)),
                ifMatch = null,
            )
            service.replaceMembers("deployers", listOf("alice"), ifMatch = null)

            // when
            val check = service.permissionCheck(admin, "alice", "ingot.artifact.net.onelitefeather:mycelium-bom.deploy", token = null).orNull()!!

            // then
            assertThat(check.allowed).describedAs("allowed").isFalse
            assertThat(check.decision.decidingNode).describedAs("deciding node").isEqualTo("ingot.artifact.net.onelitefeather.deploy")
            assertThat(check.decision.decidingKind).describedAs("deciding kind").isEqualTo("ARTIFACT")
            assertThat(check.decision.explanation).describedAs("explanation").contains("Denied").contains("ingot.artifact.net.onelitefeather.deploy")
            assertThat(check.trace.map { it.kind }).describedAs("trace kinds").containsOnly("REPOSITORY", "ARTIFACT")
        }
    }

    @Test
    fun `the check names the repository node that allows an artifact request in a repository`() {
        withService { service, _ ->
            val admin = bootstrapAdmin(service)
            service.createPrincipal(CreatePrincipalRequest(kind = "USER", name = "alice"))
            service.replacePrincipalNodes("alice", listOf(NodeDto("ingot.repository.releases.deploy", true)), ifMatch = null)

            val check = service.permissionCheck(admin, "alice", "ingot.repository.releases.artifact.org.other:lib.deploy", token = null).orNull()!!

            assertThat(check.allowed).describedAs("allowed").isTrue
            assertThat(check.decision.decidingNode).describedAs("deciding node").isEqualTo("ingot.repository.releases.deploy")
            assertThat(check.decision.decidingKind).describedAs("deciding kind").isEqualTo("REPOSITORY")
        }
    }

    @Test
    fun `the check refuses an artifact node with a wildcard group`() {
        withService { service, _ ->
            val admin = bootstrapAdmin(service)

            val result = service.permissionCheck(admin, "admin", "ingot.artifact.*.deploy", token = null)

            assertThat(result.error.status).describedAs("status").isEqualTo(400)
            assertThat(result.error.message).describedAs("names the wildcard rule").contains("concrete")
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

    // path checks: the check answers for a repository path the way a request for that path is decided

    private val deployedFile = "net/onelitefeather/mycelium-bom/1.8.9/mycelium-bom-1.8.9.pom"

    /** Alice is in deployers, which may deploy to releases, and the artifact group below is denied to her. */
    private fun aliceInDeployersWithDeniedGroup(service: IdentityFacade) {
        service.createPrincipal(CreatePrincipalRequest(kind = "USER", name = "alice"))
        service.createTeam(CreateTeamRequest(name = "deployers"))
        service.replaceTeamNodes(
            "deployers",
            listOf(NodeDto("ingot.repository.releases.deploy", true), NodeDto("ingot.artifact.net.onelitefeather.deploy", false)),
            ifMatch = null,
        )
        service.replaceMembers("deployers", listOf("alice"), ifMatch = null)
    }

    @Test
    fun `the path check decides a full file path by the artifact node that covers its directory`() {
        withService { service, _ ->
            val admin = bootstrapAdmin(service)
            aliceInDeployersWithDeniedGroup(service)

            val check = service.permissionCheck(admin, "alice", null, null, repository = "releases", path = deployedFile, action = "deploy").orNull()!!

            assertThat(check.allowed).describedAs("allowed").isFalse
            assertThat(check.decision.decidingNode).describedAs("deciding node").isEqualTo("ingot.artifact.net.onelitefeather.deploy")
            assertThat(check.decision.decidingKind).describedAs("deciding kind").isEqualTo("ARTIFACT")
            assertThat(check.node).describedAs("a path check names no node").isNull()
            assertThat(check.repository).describedAs("repository echoed").isEqualTo("releases")
            assertThat(check.path).describedAs("path echoed").isEqualTo(deployedFile)
            assertThat(check.action).describedAs("action echoed").isEqualTo("deploy")
        }
    }

    @Test
    fun `the path check allows a path outside the denied group by the repository node`() {
        withService { service, _ ->
            val admin = bootstrapAdmin(service)
            aliceInDeployersWithDeniedGroup(service)

            val check = service.permissionCheck(admin, "alice", null, null, repository = "releases", path = "org/other/lib/1.0/lib-1.0.jar", action = "deploy").orNull()!!

            assertThat(check.allowed).describedAs("allowed").isTrue
            assertThat(check.decision.decidingNode).describedAs("deciding node").isEqualTo("ingot.repository.releases.deploy")
            assertThat(check.decision.decidingKind).describedAs("deciding kind").isEqualTo("REPOSITORY")
        }
    }

    @Test
    fun `the path check counts a path-scoped grant that covers the path, which the node check does not`() {
        withService { service, _ ->
            val admin = bootstrapAdmin(service)
            service.createPrincipal(CreatePrincipalRequest(kind = "USER", name = "alice"))
            service.createTeam(CreateTeamRequest(name = "deployers"))
            service.replaceGrants(
                "deployers",
                ReplaceGrantsRequest(listOf(GrantRequest(repository = "releases", pathPrefix = "net/onelitefeather", role = "DEPLOY"))).grants,
                ifMatch = null,
            )
            service.replaceMembers("deployers", listOf("alice"), ifMatch = null)

            val path = service.permissionCheck(admin, "alice", null, null, repository = "releases", path = "net/onelitefeather/lib/1.0/lib-1.0.jar", action = "deploy").orNull()!!
            val node = service.permissionCheck(admin, "alice", "ingot.repository.releases.deploy", token = null).orNull()!!

            assertThat(path.allowed).describedAs("path check").isTrue
            assertThat(path.decision.decidingKind).describedAs("deciding kind").isEqualTo("GRANT")
            assertThat(node.allowed).describedAs("node check ignores path-scoped grants").isFalse
        }
    }

    @Test
    fun `the path check takes the action from the node when no action is given`() {
        withService { service, _ ->
            val admin = bootstrapAdmin(service)
            aliceInDeployersWithDeniedGroup(service)

            val check = service.permissionCheck(admin, "alice", "ingot.repository.releases.deploy", null, repository = "releases", path = "org/other/lib/1.0/lib-1.0.jar").orNull()!!

            assertThat(check.allowed).describedAs("allowed").isTrue
            assertThat(check.action).describedAs("action taken from the node").isEqualTo("deploy")
        }
    }

    @Test
    fun `the path check applies the token narrowing to the path`() {
        withService { service, _ ->
            val admin = bootstrapAdmin(service)
            aliceInDeployersWithDeniedGroup(service)
            service.createToken("alice", CreateTokenRequest(name = "ci", nodes = listOf(NodeDto("ingot.artifact.org.other.deploy", true))))

            val listed = service.permissionCheck(admin, "alice", null, "ci", repository = "releases", path = "org/other/lib/1.0/lib-1.0.jar", action = "deploy").orNull()!!
            // The owner may deploy this path; only the token's node list is what refuses it.
            val unlisted = service.permissionCheck(admin, "alice", null, "ci", repository = "releases", path = "org/third/lib/1.0/lib-1.0.jar", action = "deploy").orNull()!!

            assertThat(listed.allowed).describedAs("the listed artifact").isTrue
            assertThat(unlisted.allowed).describedAs("an unlisted artifact").isFalse
            assertThat(unlisted.decision.source).describedAs("source").isEqualTo("TOKEN")
        }
    }

    @Test
    fun `the path check refuses a request without an action and without a node`() {
        withService { service, _ ->
            val admin = bootstrapAdmin(service)
            aliceInDeployersWithDeniedGroup(service)

            assertThat(pathCheck(service, admin, path = deployedFile).error.status).describedAs("status").isEqualTo(400)
        }
    }

    @Test
    fun `the path check refuses an action that is not read, deploy or delete`() {
        withService { service, _ ->
            val admin = bootstrapAdmin(service)
            aliceInDeployersWithDeniedGroup(service)

            assertThat(pathCheck(service, admin, path = deployedFile, action = "manage").error.status).describedAs("status").isEqualTo(400)
        }
    }

    @Test
    fun `the path check refuses an action that differs from the action of the node`() {
        withService { service, _ ->
            val admin = bootstrapAdmin(service)
            aliceInDeployersWithDeniedGroup(service)

            assertThat(pathCheck(service, admin, node = "ingot.repository.releases.read", path = deployedFile, action = "deploy").error.status)
                .describedAs("status").isEqualTo(400)
        }
    }

    @Test
    fun `the path check refuses a wildcard repository`() {
        withService { service, _ ->
            val admin = bootstrapAdmin(service)
            aliceInDeployersWithDeniedGroup(service)

            assertThat(pathCheck(service, admin, repository = "*", path = deployedFile, action = "deploy").error.status).describedAs("status").isEqualTo(400)
        }
    }

    @Test
    fun `the path check refuses a request without a path`() {
        withService { service, _ ->
            val admin = bootstrapAdmin(service)
            aliceInDeployersWithDeniedGroup(service)

            assertThat(pathCheck(service, admin, path = null, action = "deploy").error.status).describedAs("status").isEqualTo(400)
        }
    }

    @Test
    fun `the path check refuses a path that names no directory`() {
        withService { service, _ ->
            val admin = bootstrapAdmin(service)
            aliceInDeployersWithDeniedGroup(service)

            assertThat(pathCheck(service, admin, path = " ", action = "deploy").error.status).describedAs("status").isEqualTo(400)
        }
    }

    private fun pathCheck(
        service: IdentityFacade,
        admin: AccessTokenIdentifier,
        repository: String? = "releases",
        path: String?,
        action: String? = null,
        node: String? = null,
    ) = service.permissionCheck(admin, "alice", node, null, repository = repository, path = path, action = action)

    @Test
    fun `the check names the instance admin shortcut as the deciding kind, as a real request is allowed by it`() {
        withService { service, _ ->
            val admin = bootstrapAdmin(service)

            val check = service.permissionCheck(admin, "admin", "ingot.repository.releases.deploy", token = null).orNull()!!

            assertThat(check.allowed).describedAs("allowed").isTrue
            assertThat(check.decision.decidingKind).describedAs("deciding kind").isEqualTo("INSTANCE_ADMIN")
            assertThat(check.decision.decidingNode).describedAs("deciding node").isEqualTo("ingot.admin.*")
            assertThat(check.decision.explanation).describedAs("explanation").contains("instance administrator").contains("admin")
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
