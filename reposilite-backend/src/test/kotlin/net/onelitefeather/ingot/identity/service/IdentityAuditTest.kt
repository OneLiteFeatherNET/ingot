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
import net.onelitefeather.ingot.identity.api.UpdatePrincipalRequest
import net.onelitefeather.ingot.identity.api.UpdateTeamRequest
import net.onelitefeather.ingot.identity.storage.IdentityStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Every administrative change of the identity API leaves one audit event that names what changed and never carries a
 * secret. Refused and no-op changes leave none. Each test starts from an empty database.
 */
internal class IdentityAuditTest {

    private val journalist = InMemoryLogger()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)
    private val operator = AuditActor.named("operator", "10.0.0.7")

    @TempDir
    lateinit var workingDirectory: Path

    @Test
    fun `creating a principal records its name and kind`() {
        withService { service, _, events ->
            // when
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "ci-bot"), operator)

            // then
            val event = events.single { it.action == AuditAction.PRINCIPAL_CREATED }
            assertThat(event.actor).isEqualTo(operator)
            assertThat(event.details).containsEntry("principal", "ci-bot").containsEntry("kind", "MACHINE")
        }
    }

    @Test
    fun `updating a display name records the changed field only`() {
        withService { service, _, events ->
            // given
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "ci-bot"))

            // when
            service.updatePrincipal("ci-bot", UpdatePrincipalRequest(displayName = "CI Bot"), ifMatch = null, actor = operator)

            // then
            val event = events.single { it.action == AuditAction.PRINCIPAL_UPDATED }
            assertThat(event.details["changed"]).isEqualTo(listOf("displayName"))
        }
    }

    @Test
    fun `disabling a principal records a disabled event, not an update`() {
        withService { service, _, events ->
            // given
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "ci-bot"))

            // when
            service.updatePrincipal("ci-bot", UpdatePrincipalRequest(disabled = true), ifMatch = null, actor = operator)

            // then
            assertThat(events.map { it.action }).contains(AuditAction.PRINCIPAL_DISABLED)
            assertThat(events.single { it.action == AuditAction.PRINCIPAL_DISABLED }.details).containsEntry("principal", "ci-bot")
        }
    }

    @Test
    fun `an update that changes nothing records nothing`() {
        withService { service, _, events ->
            // given
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "ci-bot"))
            events.clear()

            // when: the disabled flag is sent with its current value
            service.updatePrincipal("ci-bot", UpdatePrincipalRequest(disabled = false), ifMatch = null, actor = operator)

            // then
            assertThat(events).describedAs("events for a no-op update").isEmpty()
        }
    }

    @Test
    fun `deleting a principal records its name and the names of its tokens, never a secret`() {
        withService { service, _, events ->
            // given
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "ci-bot"))
            service.createToken("ci-bot", CreateTokenRequest(name = "ci-token", secret = "very-secret-value"), operator)

            // when
            service.deletePrincipal("ci-bot", ifMatch = null, actor = operator)

            // then
            val event = events.single { it.action == AuditAction.PRINCIPAL_DELETED }
            assertThat(event.details).containsEntry("principal", "ci-bot")
            assertThat(event.details["tokens"]).isEqualTo(listOf("ci-token"))
            assertThat(events.toString()).doesNotContain("very-secret-value")
        }
    }

    @Test
    fun `creating, updating and deleting a team each record one event`() {
        withService { service, _, events ->
            // when
            service.createTeam(CreateTeamRequest(name = "deployers"), operator)
            service.updateTeam("deployers", UpdateTeamRequest(description = "Releases"), ifMatch = null, actor = operator)
            service.deleteTeam("deployers", ifMatch = null, actor = operator)

            // then
            assertThat(events.map { it.action }).containsExactly(AuditAction.TEAM_CREATED, AuditAction.TEAM_UPDATED, AuditAction.TEAM_DELETED)
            assertThat(events[1].details["changed"]).isEqualTo(listOf("description"))
            assertThat(events.map { it.details["team"] }).containsOnly("deployers")
        }
    }

    @Test
    fun `replacing members records who was added and who was removed`() {
        withService { service, _, events ->
            // given: two members
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "alice"))
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "bob"))
            service.createTeam(CreateTeamRequest(name = "deployers"))
            service.replaceMembers("deployers", listOf("alice"), ifMatch = null)
            events.clear()

            // when: alice leaves and bob joins
            service.replaceMembers("deployers", listOf("bob"), ifMatch = null, actor = operator)

            // then
            val event = events.single { it.action == AuditAction.TEAM_MEMBERSHIP_CHANGED }
            assertThat(event.details["added"]).isEqualTo(listOf("bob"))
            assertThat(event.details["removed"]).isEqualTo(listOf("alice"))
        }
    }

    @Test
    fun `replacing grants records the grants that were added and removed`() {
        withService { service, _, events ->
            // given
            service.createTeam(CreateTeamRequest(name = "deployers"))
            service.replaceGrants("deployers", listOf(GrantRequest(repository = "releases", pathPrefix = "com/example", role = "READ")), ifMatch = null)
            events.clear()

            // when
            service.replaceGrants("deployers", listOf(GrantRequest(repository = "releases", pathPrefix = "com/example", role = "DEPLOY")), ifMatch = null, actor = operator)

            // then
            val event = events.single { it.action == AuditAction.TEAM_GRANTS_CHANGED }
            assertThat(event.details["added"]).isEqualTo(listOf("repository=releases path=com/example role=DEPLOY"))
            assertThat(event.details["removed"]).isEqualTo(listOf("repository=releases path=com/example role=READ"))
        }
    }

    @Test
    fun `granting and revoking the instance role of a principal record the principal`() {
        withService { service, _, events ->
            // given: a principal with a token, so that revoking is allowed while a second admin exists
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "ops"))
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "root"))
            service.createToken("ops", CreateTokenRequest(name = "ops-token"))
            service.createToken("root", CreateTokenRequest(name = "root-token"))
            service.grantPrincipalInstanceAdmin("root", ifMatch = null)
            events.clear()

            // when
            service.grantPrincipalInstanceAdmin("ops", ifMatch = null, actor = operator)
            service.revokePrincipalInstanceAdmin("ops", ifMatch = null, actor = operator)

            // then
            assertThat(events.map { it.action }).containsExactly(AuditAction.INSTANCE_ADMIN_GRANTED, AuditAction.INSTANCE_ADMIN_REVOKED)
            assertThat(events.map { it.details["principal"] }).containsOnly("ops")
            assertThat(events.all { it.actor == operator }).isTrue
        }
    }

    @Test
    fun `granting the instance role twice records one event`() {
        withService { service, _, events ->
            // given
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "ops"))
            val first = service.grantPrincipalInstanceAdmin("ops", ifMatch = null).orNull()!!
            events.clear()

            // when
            service.grantPrincipalInstanceAdmin("ops", ifMatch = first.etag, actor = operator)

            // then
            assertThat(events).describedAs("events for a repeated grant").isEmpty()
        }
    }

    @Test
    fun `granting and revoking the instance role of a team record the team`() {
        withService { service, _, events ->
            // given
            service.createTeam(CreateTeamRequest(name = "operators"))
            events.clear()

            // when
            service.grantTeamInstanceAdmin("operators", ifMatch = null, actor = operator)
            service.revokeTeamInstanceAdmin("operators", ifMatch = null, actor = operator)

            // then
            assertThat(events.map { it.action }).containsExactly(AuditAction.INSTANCE_ADMIN_GRANTED, AuditAction.INSTANCE_ADMIN_REVOKED)
            assertThat(events.map { it.details["team"] }).containsOnly("operators")
        }
    }

    @Test
    fun `a refused change records nothing`() {
        withService { service, _, events ->
            // given: the only admin, whose removal is refused
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "root"))
            service.createToken("root", CreateTokenRequest(name = "root-token"))
            service.grantPrincipalInstanceAdmin("root", ifMatch = null)
            events.clear()

            // when
            val refused = service.revokePrincipalInstanceAdmin("root", ifMatch = null, actor = operator)

            // then
            assertThat(refused.isErr).isTrue
            assertThat(events).describedAs("events for a refused revoke").isEmpty()
        }
    }

    @Test
    fun `creating and deleting a token record the token name, never the secret`() {
        withService { service, _, events ->
            // given
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "ci-bot"))
            events.clear()

            // when
            service.createToken("ci-bot", CreateTokenRequest(name = "ci-token", secret = "very-secret-value"), operator)
            service.deleteToken("ci-bot", "ci-token", operator)

            // then
            assertThat(events.map { it.action }).containsExactly(AuditAction.TOKEN_CREATED, AuditAction.TOKEN_DELETED)
            assertThat(events.map { it.details["token"] }).containsOnly("ci-token")
            assertThat(events.toString()).doesNotContain("very-secret-value")
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
