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

import com.reposilite.audit.api.AuditAction
import com.reposilite.audit.api.AuditActor
import com.reposilite.audit.api.AuditEvent
import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.plugin.Extensions
import com.reposilite.shared.EntityTagSigner
import com.reposilite.token.application.AccessTokenComponents
import net.onelitefeather.ingot.identity.api.CreatePrincipalRequest
import net.onelitefeather.ingot.identity.api.CreateTeamRequest
import net.onelitefeather.ingot.identity.api.CreateTokenRequest
import net.onelitefeather.ingot.identity.api.GrantRequest
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
 * The node lists and team parents of the identity administration: validation with every problem listed, entity tags, the
 * cycle and depth rules and the audit events. Each test starts from an empty database with a fixed clock.
 */
internal class PermissionNodeAdministrationTest {

    private val journalist = InMemoryLogger()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)
    private val operator = AuditActor.named("operator", "10.0.0.7")

    @TempDir
    lateinit var workingDirectory: Path

    @Test
    fun `a team keeps the node list it was given`() {
        withService { service, _, _ ->
            // given
            service.createTeam(CreateTeamRequest(name = "deployers"))

            // when
            service.replaceTeamNodes("deployers", listOf(NodeDto("ingot.repository.releases.deploy", true)), ifMatch = null)

            // then
            val nodes = service.teamNodes("deployers").orNull()!!.value
            assertThat(nodes).describedAs("stored nodes").containsExactly(NodeDto("ingot.repository.releases.deploy", true))
        }
    }

    @Test
    fun `a denied node is stored with allowed false`() {
        withService { service, _, _ ->
            service.createTeam(CreateTeamRequest(name = "deployers"))

            service.replaceTeamNodes("deployers", listOf(NodeDto("ingot.repository.releases.delete", false)), ifMatch = null)

            assertThat(service.teamNodes("deployers").orNull()!!.value).containsExactly(NodeDto("ingot.repository.releases.delete", false))
        }
    }

    @Test
    fun `replacing a node list drops the nodes that are no longer listed`() {
        withService { service, _, _ ->
            service.createTeam(CreateTeamRequest(name = "deployers"))
            service.replaceTeamNodes("deployers", listOf(NodeDto("*", true), NodeDto("ingot.admin.users", true)), ifMatch = null)

            service.replaceTeamNodes("deployers", listOf(NodeDto("ingot.admin.users", true)), ifMatch = null)

            assertThat(service.teamNodes("deployers").orNull()!!.value).containsExactly(NodeDto("ingot.admin.users", true))
        }
    }

    @Test
    fun `a principal of kind machine keeps its node list too`() {
        withService { service, _, _ ->
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "ci-bot"))

            service.replacePrincipalNodes("ci-bot", listOf(NodeDto("ingot.repository.*.read", true)), ifMatch = null)

            assertThat(service.principalNodes("ci-bot").orNull()!!.value).containsExactly(NodeDto("ingot.repository.*.read", true))
        }
    }

    @Test
    fun `a user keeps its node list too`() {
        withService { service, _, _ ->
            service.createPrincipal(CreatePrincipalRequest(kind = "USER", name = "alice"))

            service.replacePrincipalNodes("alice", listOf(NodeDto("ingot.admin.teams", true)), ifMatch = null)

            assertThat(service.principalNodes("alice").orNull()!!.value).containsExactly(NodeDto("ingot.admin.teams", true))
        }
    }

    @Test
    fun `refuses a node list and lists every invalid node in the message`() {
        withService { service, _, _ ->
            service.createTeam(CreateTeamRequest(name = "deployers"))

            val result = service.replaceTeamNodes(
                "deployers",
                listOf(NodeDto("ingot.repository.releases", true), NodeDto("ingot.admin.billing", true), NodeDto("ingot.*", true)),
                ifMatch = null,
            )

            assertThat(result.isErr).describedAs("refused").isTrue
            assertThat(result.error.status).describedAs("status").isEqualTo(400)
            assertThat(result.error.message)
                .describedAs("message names every invalid node")
                .contains("ingot.repository.releases")
                .contains("ingot.admin.billing")
                .contains("ingot.*")
        }
    }

    @Test
    fun `answers each invalid node as a problem of its own row`() {
        withService { service, _, _ ->
            service.createTeam(CreateTeamRequest(name = "deployers"))

            val result = service.replaceTeamNodes(
                "deployers",
                listOf(NodeDto("ingot.repository.releases", true), NodeDto("ingot.admin.users", true), NodeDto("ingot.admin.billing", true)),
                ifMatch = null,
            )

            val problems = result.error.problems.orEmpty()
            assertThat(problems.map { it.node }).describedAs("one problem per invalid row, valid rows absent").containsExactly("ingot.repository.releases", "ingot.admin.billing")
            assertThat(problems.single { it.node == "ingot.admin.billing" }.message)
                .describedAs("the row message names the node")
                .contains("billing")
        }
    }

    @Test
    fun `answers a node listed twice as a problem of that row`() {
        withService { service, _, _ ->
            service.createTeam(CreateTeamRequest(name = "deployers"))

            val result = service.replaceTeamNodes("deployers", listOf(NodeDto("*", true), NodeDto("*", false)), ifMatch = null)

            assertThat(result.error.problems.orEmpty().map { it.node }).describedAs("the repeated row").containsExactly("*")
        }
    }

    @Test
    fun `a refused node list stores nothing`() {
        withService { service, _, _ ->
            service.createTeam(CreateTeamRequest(name = "deployers"))
            service.replaceTeamNodes("deployers", listOf(NodeDto("*", true)), ifMatch = null)

            service.replaceTeamNodes("deployers", listOf(NodeDto("ingot.admin.billing", true)), ifMatch = null)

            assertThat(service.teamNodes("deployers").orNull()!!.value).describedAs("previous list kept").containsExactly(NodeDto("*", true))
        }
    }

    @Test
    fun `refuses the same node twice in one list`() {
        withService { service, _, _ ->
            service.createTeam(CreateTeamRequest(name = "deployers"))

            val result = service.replaceTeamNodes("deployers", listOf(NodeDto("*", true), NodeDto("*", false)), ifMatch = null)

            assertThat(result.error.status).describedAs("duplicate node").isEqualTo(400)
        }
    }

    @Test
    fun `a node list with a stale tag is refused`() {
        withService { service, _, _ ->
            val first = service.createTeam(CreateTeamRequest(name = "deployers")).orNull()!!
            service.replaceTeamNodes("deployers", listOf(NodeDto("*", true)), ifMatch = null)

            val result = service.replaceTeamNodes("deployers", listOf(NodeDto("ingot.admin.users", true)), ifMatch = first.etag)

            assertThat(result.error.status).describedAs("precondition").isEqualTo(412)
        }
    }

    @Test
    fun `a node change changes the entity tag of the team`() {
        withService { service, _, _ ->
            val before = service.createTeam(CreateTeamRequest(name = "deployers")).orNull()!!

            val after = service.replaceTeamNodes("deployers", listOf(NodeDto("*", true)), ifMatch = null).orNull()!!

            assertThat(after.etag).describedAs("tag after the node change").isNotEqualTo(before.etag)
        }
    }

    @Test
    fun `a node change is audited once with the added and removed nodes`() {
        withService { service, _, events ->
            service.createTeam(CreateTeamRequest(name = "deployers"))

            service.replaceTeamNodes("deployers", listOf(NodeDto("ingot.repository.releases.read", true), NodeDto("ingot.repository.releases.delete", false)), ifMatch = null, actor = operator)

            val event = events.single { it.action == AuditAction.NODES_CHANGED }
            assertThat(event.actor).isEqualTo(operator)
            assertThat(event.details).containsEntry("team", "deployers")
            assertThat(event.details["added"]).describedAs("added nodes")
                .isEqualTo(listOf("ingot.repository.releases.delete=denied", "ingot.repository.releases.read=allowed"))
            assertThat(event.details["removed"]).describedAs("removed nodes").isEqualTo(emptyList<String>())
        }
    }

    @Test
    fun `an unchanged node list records no event`() {
        withService { service, _, events ->
            service.createTeam(CreateTeamRequest(name = "deployers"))
            service.replaceTeamNodes("deployers", listOf(NodeDto("*", true)), ifMatch = null)
            val recorded = events.count { it.action == AuditAction.NODES_CHANGED }

            service.replaceTeamNodes("deployers", listOf(NodeDto("*", true)), ifMatch = null)

            assertThat(events.count { it.action == AuditAction.NODES_CHANGED }).describedAs("events after the no-op").isEqualTo(recorded)
        }
    }

    @Test
    fun `a refused node change records no event`() {
        withService { service, _, events ->
            service.createTeam(CreateTeamRequest(name = "deployers"))

            service.replaceTeamNodes("deployers", listOf(NodeDto("ingot.admin.billing", true)), ifMatch = null)

            assertThat(events.none { it.action == AuditAction.NODES_CHANGED }).describedAs("no event for a refused change").isTrue
        }
    }

    @Test
    fun `a team parent is stored and read back`() {
        withService { service, _, _ ->
            service.createTeam(CreateTeamRequest(name = "release-managers"))
            service.createTeam(CreateTeamRequest(name = "deployers"))

            service.replaceTeamParents("deployers", listOf("release-managers"), ifMatch = null)

            assertThat(service.teamParents("deployers").orNull()!!.value).containsExactly("release-managers")
        }
    }

    @Test
    fun `a team cannot be its own parent`() {
        withService { service, _, _ ->
            service.createTeam(CreateTeamRequest(name = "deployers"))

            val result = service.replaceTeamParents("deployers", listOf("deployers"), ifMatch = null)

            assertThat(result.error.status).describedAs("self parent").isEqualTo(409)
        }
    }

    @Test
    fun `refuses a parent change that closes a cycle`() {
        withService { service, _, _ ->
            service.createTeam(CreateTeamRequest(name = "a"))
            service.createTeam(CreateTeamRequest(name = "b"))
            service.replaceTeamParents("a", listOf("b"), ifMatch = null)

            val result = service.replaceTeamParents("b", listOf("a"), ifMatch = null)

            assertThat(result.error.status).describedAs("cycle").isEqualTo(409)
            assertThat(result.error.message).describedAs("names the rule").contains("cycle")
            assertThat(service.teamParents("b").orNull()!!.value).describedAs("nothing stored").isEmpty()
        }
    }

    @Test
    fun `accepts an inheritance chain of eight levels`() {
        withService { service, _, _ ->
            val chain = (0..8).map { "c$it" }
            chain.forEach { service.createTeam(CreateTeamRequest(name = it)) }

            (0..7).forEach { index -> service.replaceTeamParents(chain[index], listOf(chain[index + 1]), ifMatch = null) }

            assertThat(service.teamParents("c7").orNull()!!.value).containsExactly("c8")
        }
    }

    @Test
    fun `refuses an inheritance chain of nine levels`() {
        withService { service, _, _ ->
            val chain = (0..9).map { "c$it" }
            chain.forEach { service.createTeam(CreateTeamRequest(name = it)) }
            (0..7).forEach { index -> service.replaceTeamParents(chain[index], listOf(chain[index + 1]), ifMatch = null) }

            val result = service.replaceTeamParents("c8", listOf("c9"), ifMatch = null)

            assertThat(result.error.status).describedAs("depth").isEqualTo(409)
            assertThat(result.error.message).describedAs("names the depth rule").contains("8")
        }
    }

    @Test
    fun `refuses a parent that is not a team`() {
        withService { service, _, _ ->
            service.createTeam(CreateTeamRequest(name = "deployers"))

            val result = service.replaceTeamParents("deployers", listOf("nobody"), ifMatch = null)

            assertThat(result.error.status).describedAs("unknown parent").isEqualTo(400)
        }
    }

    @Test
    fun `answers each unknown parent as a problem of its own row`() {
        withService { service, _, _ ->
            service.createTeam(CreateTeamRequest(name = "deployers"))
            service.createTeam(CreateTeamRequest(name = "release-managers"))

            val result = service.replaceTeamParents("deployers", listOf("release-managers", "nobody", "ghost"), ifMatch = null)

            assertThat(result.error.problems.orEmpty().map { it.node }).describedAs("one problem per unknown parent").containsExactly("nobody", "ghost")
            assertThat(result.error.message).describedAs("joined text").contains("nobody").contains("ghost")
        }
    }

    @Test
    fun `a parent change is audited as a team update`() {
        withService { service, _, events ->
            service.createTeam(CreateTeamRequest(name = "release-managers"))
            service.createTeam(CreateTeamRequest(name = "deployers"))

            service.replaceTeamParents("deployers", listOf("release-managers"), ifMatch = null, actor = operator)

            val event = events.single { it.action == AuditAction.TEAM_UPDATED && it.details["changed"] == listOf("parents") }
            assertThat(event.details).containsEntry("team", "deployers")
        }
    }

    @Test
    fun `refuses a repository-wide grant because nodes are the way to grant it`() {
        withService { service, _, _ ->
            service.createTeam(CreateTeamRequest(name = "deployers"))

            val result = service.replaceGrants("deployers", listOf(GrantRequest(repository = "releases", role = "DEPLOY")), ifMatch = null)

            assertThat(result.error.status).describedAs("status").isEqualTo(400)
            assertThat(result.error.message).describedAs("names the node endpoint").contains("nodes")
        }
    }

    @Test
    fun `accepts a path-scoped deploy grant`() {
        withService { service, _, _ ->
            service.createTeam(CreateTeamRequest(name = "deployers"))

            val result = service.replaceGrants("deployers", listOf(GrantRequest(repository = "releases", pathPrefix = "com/example", role = "DEPLOY")), ifMatch = null)

            assertThat(result.isOk).describedAs("path-scoped grant").isTrue
        }
    }

    @Test
    fun `refuses an admin grant on a path, because admin applies to a whole repository`() {
        withService { service, _, _ ->
            service.createTeam(CreateTeamRequest(name = "deployers"))

            val result = service.replaceGrants("deployers", listOf(GrantRequest(repository = "releases", pathPrefix = "com", role = "ADMIN")), ifMatch = null)

            assertThat(result.error.status).describedAs("status").isEqualTo(400)
        }
    }

    @Test
    fun `refuses to remove the admin node of the last usable admin`() {
        withService { service, _, _ ->
            // given: an admin with a usable token
            service.createPrincipal(CreatePrincipalRequest(kind = "USER", name = "alice"))
            service.createToken("alice", CreateTokenRequest(name = "alice-token"))
            service.grantPrincipalInstanceAdmin("alice", ifMatch = null)

            // when
            val result = service.revokePrincipalInstanceAdmin("alice", ifMatch = null)

            // then
            assertThat(result.error.status).describedAs("last admin").isEqualTo(409)
        }
    }

    private fun withService(block: (IdentityFacade, IdentityStore, MutableList<AuditEvent>) -> Unit) {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            val database = connection.database
            val extensions = Extensions(journalist)
            val events = mutableListOf<AuditEvent>()
            extensions.registerEvent(AuditEvent::class.java) { events += it }
            val accessTokens = AccessTokenComponents(journalist, database).accessTokenFacade(extensions = extensions)
            val store = IdentityStore(database, clock)
            val service = IdentityFacade(
                store = store,
                accessTokens = accessTokens,
                entityTags = EntityTagSigner.ephemeral(),
                extensions = extensions,
                visibilityOf = { repository -> if (repository == "releases") "HIDDEN" else null },
                clock = clock,
            )
            block(service, store, events)
        }
    }
}
