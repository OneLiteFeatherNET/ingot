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
import com.reposilite.token.AccessTokenIdentifier
import com.reposilite.token.AccessTokenType.PERSISTENT
import com.reposilite.token.application.AccessTokenComponents
import net.onelitefeather.ingot.identity.api.CreatePrincipalRequest
import net.onelitefeather.ingot.identity.api.CreateTeamRequest
import net.onelitefeather.ingot.identity.api.NodeDto
import net.onelitefeather.ingot.identity.api.UpdateTeamRequest
import net.onelitefeather.ingot.identity.storage.IdentityStore
import net.onelitefeather.ingot.identity.model.NodePattern
import com.reposilite.token.AccessTokenSecurityProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Linking a team to an identity provider group is guarded by the admin area `ingot.admin.idp`. In `enforce` a write that
 * sets, changes or clears the reference without that right is refused; in `warn` it proceeds and is recorded. Existing
 * references are never touched by a guard. Every test builds its own database and its own callers.
 */
internal class ExternalRefGuardTest {

    private val journalist = InMemoryLogger()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)
    private val operator = AuditActor.named("operator", "10.0.0.7")

    @TempDir
    lateinit var workingDirectory: Path

    @Test
    fun `enforce refuses a teams administrator who links a new team to a provider group`() {
        withService(IdpGuardMode.ENFORCE) { service, store, events, _ ->
            // given
            val caller = callerWithNodes(service, store, "teams-admin", "ingot.admin.teams")

            // when
            val refused = service.createTeam(CreateTeamRequest(name = "ops", externalRef = "oidc:corp:ops-admins"), operator, caller)

            // then
            assertThat(refused.error.status).describedAs("status of the refusal").isEqualTo(403)
            assertThat(store.findTeam("ops")).describedAs("the team after the refusal").isNull()
            val event = events.single { it.action == AuditAction.EXTERNAL_REF_UNGUARDED }
            assertThat(event.details).containsEntry("team", "ops").containsEntry("reference", "oidc:corp:ops-admins")
            assertThat(event.details).containsEntry("mode", "enforce").containsEntry("outcome", "refused")
            assertThat(event.details).containsEntry("caller", "teams-admin")
        }
    }

    @Test
    fun `enforce refuses a teams administrator who points an existing team at a provider group`() {
        withService(IdpGuardMode.ENFORCE) { service, store, events, _ ->
            // given: a team without a reference, and a caller who administers teams only
            store.createTeam("ops", "", null)
            val caller = callerWithNodes(service, store, "teams-admin", "ingot.admin.teams")

            // when
            val refused = service.updateTeam("ops", UpdateTeamRequest(externalRef = "oidc:corp:ops-admins"), ifMatch = null, actor = operator, caller = caller)

            // then
            assertThat(refused.error.status).describedAs("status of the refusal").isEqualTo(403)
            assertThat(store.findTeam("ops")?.externalRef).describedAs("the reference after the refusal").isNull()
            assertThat(events.filter { it.action == AuditAction.EXTERNAL_REF_UNGUARDED }).describedAs("guard events").hasSize(1)
        }
    }

    @Test
    fun `enforce refuses clearing the provider reference of a team without the idp right`() {
        withService(IdpGuardMode.ENFORCE) { service, store, _, _ ->
            // given: a linked team and a caller who administers teams only
            store.createTeam("ops", "", "oidc:corp:ops-admins")
            val caller = callerWithNodes(service, store, "teams-admin", "ingot.admin.teams")

            // when: a blank reference clears the link
            val refused = service.updateTeam("ops", UpdateTeamRequest(externalRef = ""), ifMatch = null, actor = operator, caller = caller)

            // then
            assertThat(refused.error.status).describedAs("status of the refusal").isEqualTo(403)
            assertThat(store.findTeam("ops")?.externalRef).describedAs("the reference after the refusal").isEqualTo("oidc:corp:ops-admins")
        }
    }

    @Test
    fun `enforce allows the link when the caller holds the idp right`() {
        withService(IdpGuardMode.ENFORCE) { service, store, events, _ ->
            // given
            val caller = callerWithNodes(service, store, "idp-admin", "ingot.admin.idp", "ingot.admin.teams")

            // when
            val created = service.createTeam(CreateTeamRequest(name = "ops", externalRef = "oidc:corp:ops-admins"), operator, caller)

            // then
            assertThat(created.isOk).describedAs("the creation with the idp right").isTrue
            assertThat(store.findTeam("ops")?.externalRef).isEqualTo("oidc:corp:ops-admins")
            assertThat(events.map { it.action }).describedAs("events").doesNotContain(AuditAction.EXTERNAL_REF_UNGUARDED)
        }
    }

    @Test
    fun `enforce allows the link when the caller is an instance admin`() {
        withService(IdpGuardMode.ENFORCE) { service, store, _, _ ->
            // given
            val caller = callerWithNodes(service, store, "root", "ingot.admin.*")

            // when
            val updated = service.createTeam(CreateTeamRequest(name = "ops", externalRef = "oidc:corp:ops-admins"), operator, caller)

            // then
            assertThat(updated.isOk).describedAs("the creation by an instance admin").isTrue
            assertThat(store.findTeam("ops")?.externalRef).isEqualTo("oidc:corp:ops-admins")
        }
    }

    @Test
    fun `an update that repeats the current reference needs no idp right`() {
        withService(IdpGuardMode.ENFORCE) { service, store, events, _ ->
            // given: a linked team and a caller who administers teams only
            store.createTeam("ops", "", "oidc:corp:ops-admins")
            val caller = callerWithNodes(service, store, "teams-admin", "ingot.admin.teams")

            // when: the same reference is sent again
            val updated = service.updateTeam("ops", UpdateTeamRequest(externalRef = "oidc:corp:ops-admins"), ifMatch = null, actor = operator, caller = caller)

            // then
            assertThat(updated.isOk).describedAs("the unchanged reference").isTrue
            assertThat(events.map { it.action }).describedAs("events").doesNotContain(AuditAction.EXTERNAL_REF_UNGUARDED)
        }
    }

    @Test
    fun `an unrelated update leaves an existing provider reference untouched`() {
        withService(IdpGuardMode.ENFORCE) { service, store, _, _ ->
            // given: a linked team and a caller who administers teams only
            store.createTeam("ops", "", "oidc:corp:ops-admins")
            val caller = callerWithNodes(service, store, "teams-admin", "ingot.admin.teams")

            // when: only the description changes
            service.updateTeam("ops", UpdateTeamRequest(description = "Operations"), ifMatch = null, actor = operator, caller = caller)

            // then
            assertThat(store.findTeam("ops")?.externalRef).isEqualTo("oidc:corp:ops-admins")
        }
    }

    @Test
    fun `warn allows the unguarded link and records it with a warning`() {
        withService(IdpGuardMode.WARN) { service, store, events, warnings ->
            // given
            val caller = callerWithNodes(service, store, "teams-admin", "ingot.admin.teams")

            // when
            val created = service.createTeam(CreateTeamRequest(name = "ops", externalRef = "oidc:corp:ops-admins"), operator, caller)

            // then
            assertThat(created.isOk).describedAs("the creation in warn mode").isTrue
            assertThat(store.findTeam("ops")?.externalRef).isEqualTo("oidc:corp:ops-admins")
            val event = events.single { it.action == AuditAction.EXTERNAL_REF_UNGUARDED }
            assertThat(event.details).containsEntry("mode", "warn").containsEntry("outcome", "allowed")
            assertThat(event.details).containsEntry("caller", "teams-admin").containsEntry("team", "ops")
            assertThat(warnings).describedAs("warning lines").singleElement().asString().contains("ops").contains("oidc:corp:ops-admins")
        }
    }

    @Test
    fun `off neither refuses nor records the unguarded link`() {
        withService(IdpGuardMode.OFF) { service, store, events, warnings ->
            // given
            val caller = callerWithNodes(service, store, "teams-admin", "ingot.admin.teams")

            // when
            service.createTeam(CreateTeamRequest(name = "ops", externalRef = "oidc:corp:ops-admins"), operator, caller)

            // then
            assertThat(store.findTeam("ops")?.externalRef).isEqualTo("oidc:corp:ops-admins")
            assertThat(events.map { it.action }).describedAs("events").doesNotContain(AuditAction.EXTERNAL_REF_UNGUARDED)
            assertThat(warnings).describedAs("warning lines").isEmpty()
        }
    }

    @Test
    fun `the idp area is covered only by the instance admin wildcard`() {
        withService(IdpGuardMode.ENFORCE) { service, store, _, _ ->
            // given: one caller who administers teams, and one who holds the wildcard
            val teamsAdmin = callerWithNodes(service, store, "teams-admin", "ingot.admin.teams")
            val root = callerWithNodes(service, store, "root", "ingot.admin.*")

            // then
            assertThat(service.createTeam(CreateTeamRequest(name = "first", externalRef = "oidc:corp:a"), operator, teamsAdmin).isErr)
                .describedAs("teams administrator").isTrue
            assertThat(service.createTeam(CreateTeamRequest(name = "second", externalRef = "oidc:corp:b"), operator, root).isOk)
                .describedAs("instance admin").isTrue
        }
    }

    @Test
    fun `ingot admin idp is an admin area node`() {
        assertThat(NodePattern.problemOf("ingot.admin.idp")).describedAs("problem of the node").isNull()
        assertThat(NodePattern.ADMIN_AREAS).describedAs("admin areas").contains("idp")
    }

    @Test
    fun `an idp holder who lacks the rights of the team is refused in enforce`() {
        withService(IdpGuardMode.ENFORCE) { service, store, events, _ ->
            // given: a linked team with a repository right, and a caller with the idp right but no repository right
            store.createTeam("ops", "", "oidc:corp:ops-admins")
            service.replaceTeamNodes("ops", listOf(NodeDto("ingot.repository.releases.deploy", allowed = true)), ifMatch = null)
            val caller = callerWithNodes(service, store, "idp-admin", "ingot.admin.idp", "ingot.admin.teams")

            // when: the caller unlinks the team
            val refused = service.updateTeam("ops", UpdateTeamRequest(externalRef = ""), ifMatch = null, actor = operator, caller = caller)

            // then
            assertThat(refused.error.status).describedAs("status of the refusal").isEqualTo(403)
            assertThat(store.findTeam("ops")?.externalRef).describedAs("the reference after the refusal").isEqualTo("oidc:corp:ops-admins")
            assertThat(events.filter { it.action == AuditAction.EXTERNAL_REF_UNGUARDED }.single().details)
                .containsEntry("reason", "rights").containsEntry("outcome", "refused")
        }
    }

    @Test
    fun `warn allows an update of a link without the idp right, records it and logs a warning`() {
        withService(IdpGuardMode.WARN) { service, store, events, warnings ->
            // given
            store.createTeam("ops", "", null)
            val caller = callerWithNodes(service, store, "teams-admin", "ingot.admin.teams")

            // when
            val updated = service.updateTeam("ops", UpdateTeamRequest(externalRef = "oidc:corp:ops-admins"), ifMatch = null, actor = operator, caller = caller)

            // then
            assertThat(updated.isOk).describedAs("the update in warn mode").isTrue
            assertThat(store.findTeam("ops")?.externalRef).isEqualTo("oidc:corp:ops-admins")
            assertThat(events.single { it.action == AuditAction.EXTERNAL_REF_UNGUARDED }.details)
                .containsEntry("outcome", "allowed").containsEntry("mode", "warn")
            assertThat(warnings).describedAs("warning lines").singleElement().asString().contains("oidc:corp:ops-admins")
        }
    }

    @Test
    fun `a repeated reference with surrounding whitespace needs no right and leaves the stored value as it was`() {
        withService(IdpGuardMode.ENFORCE) { service, store, events, _ ->
            // given
            store.createTeam("ops", "", "oidc:corp:ops-admins")
            val caller = callerWithNodes(service, store, "teams-admin", "ingot.admin.teams")

            // when
            val updated = service.updateTeam("ops", UpdateTeamRequest(externalRef = "  oidc:corp:ops-admins "), ifMatch = null, actor = operator, caller = caller)

            // then
            assertThat(updated.isOk).describedAs("the repeated reference").isTrue
            assertThat(store.findTeam("ops")?.externalRef).describedAs("the stored reference").isEqualTo("oidc:corp:ops-admins")
            assertThat(events.map { it.action }).describedAs("events").doesNotContain(AuditAction.EXTERNAL_REF_UNGUARDED)
        }
    }

    @Test
    fun `a reference that differs in letter case is a change and needs the idp right`() {
        withService(IdpGuardMode.ENFORCE) { service, store, _, _ ->
            store.createTeam("ops", "", "oidc:corp:ops-admins")
            val caller = callerWithNodes(service, store, "teams-admin", "ingot.admin.teams")

            val refused = service.updateTeam("ops", UpdateTeamRequest(externalRef = "OIDC:corp:ops-admins"), ifMatch = null, actor = operator, caller = caller)

            assertThat(refused.error.status).describedAs("status of the refusal").isEqualTo(403)
            assertThat(store.findTeam("ops")?.externalRef).isEqualTo("oidc:corp:ops-admins")
        }
    }

    @Test
    fun `an absent reference leaves the link and needs no right`() {
        withService(IdpGuardMode.ENFORCE) { service, store, events, _ ->
            store.createTeam("ops", "", "oidc:corp:ops-admins")
            val caller = callerWithNodes(service, store, "teams-admin", "ingot.admin.teams")

            val updated = service.updateTeam("ops", UpdateTeamRequest(description = "Operations"), ifMatch = null, actor = operator, caller = caller)

            assertThat(updated.isOk).describedAs("the update without a reference").isTrue
            assertThat(store.findTeam("ops")?.externalRef).isEqualTo("oidc:corp:ops-admins")
            assertThat(events.map { it.action }).doesNotContain(AuditAction.EXTERNAL_REF_UNGUARDED)
        }
    }

    @Test
    fun `an empty reference on a team without a link needs no right and writes no link`() {
        withService(IdpGuardMode.ENFORCE) { service, store, events, _ ->
            store.createTeam("ops", "", null)
            val caller = callerWithNodes(service, store, "teams-admin", "ingot.admin.teams")

            val updated = service.updateTeam("ops", UpdateTeamRequest(externalRef = ""), ifMatch = null, actor = operator, caller = caller)

            assertThat(updated.isOk).describedAs("the empty reference on an unlinked team").isTrue
            assertThat(store.findTeam("ops")?.externalRef).describedAs("the reference").isNull()
            assertThat(events.map { it.action }).doesNotContain(AuditAction.EXTERNAL_REF_UNGUARDED)
        }
    }

    @Test
    fun `the refusal body names neither the provider reference nor the caller`() {
        withService(IdpGuardMode.ENFORCE) { service, store, _, _ ->
            val caller = callerWithNodes(service, store, "teams-admin", "ingot.admin.teams")

            val message = service.createTeam(CreateTeamRequest(name = "ops", externalRef = "oidc:corp:ops-admins"), operator, caller)
                .error.message

            assertThat(message).describedAs("generic refusal").doesNotContain("oidc:corp:ops-admins").doesNotContain("teams-admin")
            assertThat(message).contains("ingot.admin.idp")
        }
    }

    private fun callerWithNodes(service: IdentityFacade, store: IdentityStore, name: String, vararg nodes: String): WriteCaller {
        service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = name), operator)
        service.replacePrincipalNodes(name, nodes.map { NodeDto(it, allowed = true) }, ifMatch = null)
        val principal = store.findPrincipal(name)!!
        val record = store.createToken(principal.id, "$name-token", AccessTokenSecurityProvider.encodeSecret("secret"), null, null, LocalDate.of(2026, 10, 1), "")!!
        return WriteCaller.Token(AccessTokenIdentifier(PERSISTENT, record.id))
    }

    private fun withService(
        mode: IdpGuardMode,
        block: (IdentityFacade, IdentityStore, MutableList<AuditEvent>, MutableList<String>) -> Unit,
    ) {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            val database = connection.database
            val extensions = Extensions(journalist)
            val events = mutableListOf<AuditEvent>()
            val warnings = mutableListOf<String>()
            extensions.registerEvent(AuditEvent::class.java) { events += it }
            val accessTokens = AccessTokenComponents(journalist, database).accessTokenFacade(extensions = extensions)
            val store = IdentityStore(database, clock)
            val service = IdentityFacade(
                store = store,
                accessTokens = accessTokens,
                entityTags = EntityTagSigner.ephemeral(),
                extensions = extensions,
                visibilityOf = { null },
                clock = clock,
                idpGuard = mode,
                warnings = { warnings += it },
            )
            block(service, store, events, warnings)
        }
    }
}
