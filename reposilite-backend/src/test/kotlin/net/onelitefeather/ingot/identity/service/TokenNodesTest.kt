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
import com.reposilite.token.AccessTokenType
import com.reposilite.token.application.AccessTokenComponents
import net.onelitefeather.ingot.identity.api.CreatePrincipalRequest
import net.onelitefeather.ingot.identity.api.CreateTeamRequest
import net.onelitefeather.ingot.identity.api.CreateTokenRequest
import net.onelitefeather.ingot.identity.api.NodeDto
import net.onelitefeather.ingot.identity.model.NodeRule
import net.onelitefeather.ingot.identity.storage.IdentityStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Tokens that narrow their owner with node lists (identity model, permission nodes, decision D11), and the manager areas
 * of the administration (decision D13). Each test starts from an empty database with a fixed clock.
 */
internal class TokenNodesTest {

    private val journalist = InMemoryLogger()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)

    @TempDir
    lateinit var workingDirectory: Path

    @Test
    fun `a token created with nodes keeps that node list`() {
        withService { service, store, _ ->
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "ci-bot"))

            service.createToken("ci-bot", CreateTokenRequest(name = "ci-read", nodes = listOf(NodeDto("ingot.repository.*.read", true))))

            assertThat(store.findToken("ci-read")!!.nodes).describedAs("node list")
                .containsExactly(NodeRule("ingot.repository.*.read", true))
        }
    }

    @Test
    fun `an empty node list is refused`() {
        withService { service, _, _ ->
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "ci-bot"))

            val result = service.createToken("ci-bot", CreateTokenRequest(name = "ci-read", nodes = emptyList()))

            assertThat(result.error.status).describedAs("status").isEqualTo(400)
        }
    }

    @Test
    fun `an invalid node is refused and the answer names it`() {
        withService { service, store, _ ->
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "ci-bot"))

            val result = service.createToken("ci-bot", CreateTokenRequest(name = "ci-bad", nodes = listOf(NodeDto("ingot.admin.billing", true))))

            assertThat(result.error.status).describedAs("status").isEqualTo(400)
            assertThat(result.error.message).describedAs("names the node").contains("billing")
            assertThat(store.findToken("ci-bad")).describedAs("nothing stored").isNull()
        }
    }

    @Test
    fun `scopes are kept as scopes and their node list is stored with the token`() {
        withService { service, store, _ ->
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "ci-bot"))

            service.createToken("ci-bot", CreateTokenRequest(name = "ci-scoped", scopes = listOf("READ")))

            assertThat(store.findToken("ci-scoped")!!.nodes).describedAs("node list of the scope")
                .containsExactly(NodeRule("ingot.repository.*.read", true))
        }
    }

    @Test
    fun `a token narrowed to reads cannot deploy although its owner may`() {
        withService { service, _, _ ->
            val admin = bootstrapAdmin(service)
            service.createPrincipal(CreatePrincipalRequest(kind = "USER", name = "alice"))
            service.createTeam(CreateTeamRequest(name = "deployers"))
            service.replaceTeamNodes("deployers", listOf(NodeDto("ingot.repository.releases.deploy", true)), ifMatch = null)
            service.replaceMembers("deployers", listOf("alice"), ifMatch = null)
            service.createToken("alice", CreateTokenRequest(name = "alice-read", nodes = listOf(NodeDto("ingot.repository.*.read", true))))

            val owner = service.permissionCheck(admin, "alice", "ingot.repository.releases.deploy", token = null).orNull()!!
            val narrowed = service.permissionCheck(admin, "alice", "ingot.repository.releases.deploy", token = "alice-read").orNull()!!

            assertThat(owner.allowed).describedAs("owner").isTrue
            assertThat(narrowed.allowed).describedAs("narrowed token").isFalse
        }
    }

    @Test
    fun `a principal holding only the users area may administer users and nothing else`() {
        withService { service, _, accessTokens ->
            service.createPrincipal(CreatePrincipalRequest(kind = "USER", name = "alice"))
            service.replacePrincipalNodes("alice", listOf(NodeDto("ingot.admin.users", true)), ifMatch = null)
            val alice = AccessTokenIdentifier(type = AccessTokenType.PRINCIPAL, value = service.getPrincipal("alice").orNull()!!.value.principal.id)

            assertThat(accessTokens.hasAdminArea(alice, "users")).describedAs("users").isTrue
            assertThat(accessTokens.hasAdminArea(alice, "tokens")).describedAs("tokens").isFalse
        }
    }

    @Test
    fun `a narrowed token does not make its owner a usable instance admin`() {
        withService { service, _, _ ->
            // given: admin is a usable admin; alice holds the admin node, but her only token is narrowed to reads
            bootstrapAdmin(service)
            service.createPrincipal(CreatePrincipalRequest(kind = "USER", name = "alice"))
            service.replacePrincipalNodes("alice", listOf(NodeDto("ingot.admin.*", true)), ifMatch = null)
            service.createToken("alice", CreateTokenRequest(name = "alice-narrow", nodes = listOf(NodeDto("ingot.repository.*.read", true))))

            // when: the admin of admin is removed; alice would be the only other admin, but her token cannot act as one
            val result = service.revokePrincipalInstanceAdmin("admin", ifMatch = null)

            // then
            assertThat(result.error.status).describedAs("last usable admin").isEqualTo(409)
        }
    }

    /** Creates an instance admin with a usable token and returns the identifier of that principal. */
    private fun bootstrapAdmin(service: IdentityFacade): AccessTokenIdentifier {
        val admin = service.createPrincipal(CreatePrincipalRequest(kind = "USER", name = "admin")).orNull()!!
        service.createToken("admin", CreateTokenRequest(name = "admin-token"))
        service.grantPrincipalInstanceAdmin("admin", ifMatch = null)
        return AccessTokenIdentifier(type = AccessTokenType.PRINCIPAL, value = admin.value.principal.id)
    }

    private fun withService(block: (IdentityFacade, IdentityStore, AccessTokenFacade) -> Unit) {
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
            block(service, store, accessTokens)
        }
    }
}
