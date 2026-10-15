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
import com.reposilite.token.application.AccessTokenComponents
import net.onelitefeather.ingot.identity.api.CreatePrincipalRequest
import net.onelitefeather.ingot.identity.api.CreateTeamRequest
import net.onelitefeather.ingot.identity.api.CreateTokenRequest
import net.onelitefeather.ingot.identity.api.GrantRequest
import net.onelitefeather.ingot.identity.api.UpdatePrincipalRequest
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
 * The instance admin role: who may hold it, when it may be removed, and how the entity tags guard the change. Every
 * test starts from an empty database and a fixed clock.
 */
internal class InstanceAdminTest {

    private val journalist = InMemoryLogger()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)

    @TempDir
    lateinit var workingDirectory: Path

    @Test
    fun `granting the instance role to a principal shows it in the principal detail`() {
        withService { service, _ ->
            // given: a machine principal with a token
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "ops"))
            service.createToken("ops", CreateTokenRequest(name = "ops-token"))

            // when
            val granted = service.grantPrincipalInstanceAdmin("ops", ifMatch = null)

            // then
            assertThat(granted.isOk).describedAs("grant result").isTrue
            assertThat(service.getPrincipal("ops").orNull()?.value?.instanceRole).describedAs("instance role after grant").isTrue
        }
    }

    @Test
    fun `granting the instance role twice keeps one role and changes nothing`() {
        withService { service, store ->
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "ops"))

            // when: the same grant is sent twice
            val first = service.grantPrincipalInstanceAdmin("ops", ifMatch = null).orNull()!!
            val second = service.grantPrincipalInstanceAdmin("ops", ifMatch = first.etag)

            // then: the second request is an accepted no-op, and the store holds one role row
            assertThat(second.isOk).describedAs("repeated grant").isTrue
            assertThat(store.hasDirectInstanceRole(store.findPrincipal("ops")!!.id)).isTrue
            assertThat(second.orNull()?.etag).describedAs("tag after no-op grant").isEqualTo(first.etag)
        }
    }

    @Test
    fun `revoking the role of the only admin is refused with a conflict that explains why`() {
        withService { service, _ ->
            // given: one admin with a token
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "root"))
            service.createToken("root", CreateTokenRequest(name = "root-token"))
            service.grantPrincipalInstanceAdmin("root", ifMatch = null)

            // when: the role is removed
            val result = service.revokePrincipalInstanceAdmin("root", ifMatch = null)

            // then: refused, and the role is still there
            assertThat(result.error.status).describedAs("status for the last admin").isEqualTo(409)
            assertThat(result.error.message).describedAs("message names the rule").contains("last instance admin")
            assertThat(service.getPrincipal("root").orNull()?.value?.instanceRole).describedAs("role after refusal").isTrue
        }
    }

    @Test
    fun `revoking the role of one of two admins is allowed`() {
        withService { service, _ ->
            // given: two admins with tokens
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "first"))
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "second"))
            service.createToken("first", CreateTokenRequest(name = "first-token"))
            service.createToken("second", CreateTokenRequest(name = "second-token"))
            service.grantPrincipalInstanceAdmin("first", ifMatch = null)
            service.grantPrincipalInstanceAdmin("second", ifMatch = null)

            // when
            val result = service.revokePrincipalInstanceAdmin("first", ifMatch = null)

            // then
            assertThat(result.isOk).describedAs("revoke with another admin left").isTrue
            assertThat(service.getPrincipal("first").orNull()?.value?.instanceRole).isFalse
            assertThat(service.getPrincipal("second").orNull()?.value?.instanceRole).isTrue
        }
    }

    @Test
    fun `an admin whose only token has expired does not count as a remaining admin`() {
        withService { service, _ ->
            // given: a second admin whose token expired before the clock, and a first admin with a live token
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "first"))
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "expired"))
            service.createToken("first", CreateTokenRequest(name = "first-token"))
            service.createToken("expired", CreateTokenRequest(name = "expired-token", expiresAt = Instant.parse("2026-01-01T00:00:00Z")))
            service.grantPrincipalInstanceAdmin("first", ifMatch = null)
            service.grantPrincipalInstanceAdmin("expired", ifMatch = null)

            // when: the admin with the live token loses the role
            val result = service.revokePrincipalInstanceAdmin("first", ifMatch = null)

            // then: refused, because the expired admin cannot authenticate
            assertThat(result.error.status).describedAs("status when only an expired admin remains").isEqualTo(409)
        }
    }

    @Test
    fun `an admin whose principal is disabled does not count as a remaining admin`() {
        withService { service, _ ->
            // given: a second admin that is disabled
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "first"))
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "parked", disabled = true))
            service.createToken("first", CreateTokenRequest(name = "first-token"))
            service.createToken("parked", CreateTokenRequest(name = "parked-token"))
            service.grantPrincipalInstanceAdmin("first", ifMatch = null)
            service.grantPrincipalInstanceAdmin("parked", ifMatch = null)

            // when
            val result = service.revokePrincipalInstanceAdmin("first", ifMatch = null)

            // then
            assertThat(result.error.status).describedAs("status when only a disabled admin remains").isEqualTo(409)
        }
    }

    @Test
    fun `a team with the instance role keeps its members as admins`() {
        withService { service, _ ->
            // given: a team with the instance role and a member that has a token
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "ci-bot"))
            service.createToken("ci-bot", CreateTokenRequest(name = "ci-token"))
            service.createTeam(CreateTeamRequest(name = "operators"))
            service.replaceMembers("operators", listOf("ci-bot"), ifMatch = null)
            service.grantTeamInstanceAdmin("operators", ifMatch = null)

            // when: a direct role on another principal is granted and then removed again
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "helper"))
            service.createToken("helper", CreateTokenRequest(name = "helper-token"))
            service.grantPrincipalInstanceAdmin("helper", ifMatch = null)
            val result = service.revokePrincipalInstanceAdmin("helper", ifMatch = null)

            // then: the team member is still an admin, so the revoke is allowed
            assertThat(result.isOk).describedAs("revoke while the team admin remains").isTrue
        }
    }

    @Test
    fun `removing the team instance role is refused when the team holds the last admin`() {
        withService { service, _ ->
            // given: a team whose member is the only admin
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "ci-bot"))
            service.createToken("ci-bot", CreateTokenRequest(name = "ci-token"))
            service.createTeam(CreateTeamRequest(name = "operators"))
            service.replaceMembers("operators", listOf("ci-bot"), ifMatch = null)
            service.grantTeamInstanceAdmin("operators", ifMatch = null)

            // when
            val result = service.revokeTeamInstanceAdmin("operators", ifMatch = null)

            // then
            assertThat(result.error.status).describedAs("status for the last team admin").isEqualTo(409)
        }
    }

    @Test
    fun `removing the last admin from a team by replacing its members is refused`() {
        withService { service, _ ->
            // given: a team whose member is the only admin
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "ci-bot"))
            service.createToken("ci-bot", CreateTokenRequest(name = "ci-token"))
            service.createTeam(CreateTeamRequest(name = "operators"))
            service.replaceMembers("operators", listOf("ci-bot"), ifMatch = null)
            service.grantTeamInstanceAdmin("operators", ifMatch = null)

            // when: the member list is replaced by an empty one
            val result = service.replaceMembers("operators", emptyList(), ifMatch = null)

            // then: refused, and the member is still there
            assertThat(result.error.status).describedAs("status for dropping the last team admin").isEqualTo(409)
            assertThat(service.getTeam("operators").orNull()?.value?.members?.map { it.principal }).containsExactly("ci-bot")
        }
    }

    @Test
    fun `replacing team members refuses to remove the only usable admin held through the team and names the rule`() {
        withService { service, _ ->
            // given: a team with the instance role whose only member is the only usable admin
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "ci-bot"))
            service.createToken("ci-bot", CreateTokenRequest(name = "ci-token"))
            service.createTeam(CreateTeamRequest(name = "operators"))
            service.replaceMembers("operators", listOf("ci-bot"), ifMatch = null)
            service.grantTeamInstanceAdmin("operators", ifMatch = null)

            // when
            val result = service.replaceMembers("operators", emptyList(), ifMatch = null)

            // then
            assertThat(result.error.status).describedAs("status for dropping the only team admin").isEqualTo(409)
            assertThat(result.error.message).describedAs("message names the rule").contains("last instance admin")
        }
    }

    @Test
    fun `replacing team members is allowed while another usable admin remains`() {
        withService { service, _ ->
            // given: a team admin and a separate direct admin, both with tokens
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "ci-bot"))
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "root"))
            service.createToken("ci-bot", CreateTokenRequest(name = "ci-token"))
            service.createToken("root", CreateTokenRequest(name = "root-token"))
            service.createTeam(CreateTeamRequest(name = "operators"))
            service.replaceMembers("operators", listOf("ci-bot"), ifMatch = null)
            service.grantTeamInstanceAdmin("operators", ifMatch = null)
            service.grantPrincipalInstanceAdmin("root", ifMatch = null)

            // when: the team admin is dropped from the team
            val result = service.replaceMembers("operators", emptyList(), ifMatch = null)

            // then
            assertThat(result.isOk).describedAs("replace with another admin left").isTrue
            assertThat(service.getTeam("operators").orNull()?.value?.members).describedAs("members after replace").isEmpty()
        }
    }

    @Test
    fun `replacing team members keeps an admin team whose admin membership is kept unaffected`() {
        withService { service, _ ->
            // given: an admin team with an admin member and a plain member
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "ci-bot"))
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "helper"))
            service.createToken("ci-bot", CreateTokenRequest(name = "ci-token"))
            service.createTeam(CreateTeamRequest(name = "operators"))
            service.replaceMembers("operators", listOf("ci-bot", "helper"), ifMatch = null)
            service.grantTeamInstanceAdmin("operators", ifMatch = null)

            // when: only the member without a token leaves
            val result = service.replaceMembers("operators", listOf("ci-bot"), ifMatch = null)

            // then
            assertThat(result.isOk).describedAs("replace that keeps the admin member").isTrue
            assertThat(service.getTeam("operators").orNull()?.value?.members?.map { it.principal }).containsExactly("ci-bot")
        }
    }

    @Test
    fun `replacing team members does not count a disabled admin as a remaining admin`() {
        withService { service, _ ->
            // given: an admin team with a usable member and a disabled member
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "ci-bot"))
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "parked", disabled = true))
            service.createToken("ci-bot", CreateTokenRequest(name = "ci-token"))
            service.createToken("parked", CreateTokenRequest(name = "parked-token"))
            service.createTeam(CreateTeamRequest(name = "operators"))
            service.replaceMembers("operators", listOf("ci-bot", "parked"), ifMatch = null)
            service.grantTeamInstanceAdmin("operators", ifMatch = null)

            // when: the usable member is dropped
            val result = service.replaceMembers("operators", listOf("parked"), ifMatch = null)

            // then
            assertThat(result.error.status).describedAs("status when only a disabled admin would remain").isEqualTo(409)
        }
    }

    @Test
    fun `replacing team members does not count an admin with an expired token as a remaining admin`() {
        withService { service, _ ->
            // given: an admin team with a usable member and a member whose only token has expired
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "ci-bot"))
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "expired"))
            service.createToken("ci-bot", CreateTokenRequest(name = "ci-token"))
            service.createToken("expired", CreateTokenRequest(name = "expired-token", expiresAt = Instant.parse("2026-01-01T00:00:00Z")))
            service.createTeam(CreateTeamRequest(name = "operators"))
            service.replaceMembers("operators", listOf("ci-bot", "expired"), ifMatch = null)
            service.grantTeamInstanceAdmin("operators", ifMatch = null)

            // when: the usable member is dropped
            val result = service.replaceMembers("operators", listOf("expired"), ifMatch = null)

            // then
            assertThat(result.error.status).describedAs("status when only an expired admin would remain").isEqualTo(409)
        }
    }

    @Test
    fun `replacing manual team members keeps a synchronised admin member, so the change is allowed`() {
        withService { service, store ->
            // given: an admin team with a SYNC member that has a token, and a MANUAL member
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "synced"))
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "manual"))
            service.createToken("synced", CreateTokenRequest(name = "synced-token"))
            service.createToken("manual", CreateTokenRequest(name = "manual-token"))
            service.createTeam(CreateTeamRequest(name = "operators"))
            store.addMember(store.findTeam("operators")!!.id, store.findPrincipal("synced")!!.id, TeamMemberSource.SYNC)
            service.replaceMembers("operators", listOf("manual"), ifMatch = null)
            service.grantTeamInstanceAdmin("operators", ifMatch = null)

            // when: all manual members are removed
            val result = service.replaceMembers("operators", emptyList(), ifMatch = null)

            // then: the SYNC member is untouched and still an admin
            assertThat(result.isOk).describedAs("replace while a SYNC admin remains").isTrue
            assertThat(service.getTeam("operators").orNull()?.value?.members?.map { it.principal }).containsExactly("synced")
        }
    }

    @Test
    fun `deleting the last admin principal is refused`() {
        withService { service, _ ->
            // given: the only admin
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "root"))
            service.createToken("root", CreateTokenRequest(name = "root-token"))
            service.grantPrincipalInstanceAdmin("root", ifMatch = null)

            // when
            val result = service.deletePrincipal("root", ifMatch = null)

            // then: refused, the principal still exists
            assertThat(result.error.status).describedAs("status for deleting the last admin").isEqualTo(409)
            assertThat(service.getPrincipal("root").isOk).describedAs("principal after refusal").isTrue
        }
    }

    @Test
    fun `disabling the last admin principal is refused`() {
        withService { service, _ ->
            // given: the only admin
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "root"))
            service.createToken("root", CreateTokenRequest(name = "root-token"))
            service.grantPrincipalInstanceAdmin("root", ifMatch = null)

            // when
            val result = service.updatePrincipal("root", UpdatePrincipalRequest(disabled = true), ifMatch = null)

            // then
            assertThat(result.error.status).describedAs("status for disabling the last admin").isEqualTo(409)
        }
    }

    @Test
    fun `deleting the last token of the only admin is refused`() {
        withService { service, _ ->
            // given: the only admin with a single token
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "root"))
            service.createToken("root", CreateTokenRequest(name = "root-token"))
            service.grantPrincipalInstanceAdmin("root", ifMatch = null)

            // when
            val result = service.deleteToken("root", "root-token", AuditActor.SYSTEM_ACTOR)

            // then
            assertThat(result.error.status).describedAs("status for deleting the last usable token").isEqualTo(409)
        }
    }

    @Test
    fun `a stale entity tag refuses the instance role grant`() {
        withService { service, _ ->
            // given: a principal read once, then changed by someone else
            val first = service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "ops")).orNull()!!
            service.updatePrincipal("ops", UpdatePrincipalRequest(displayName = "Operations"), ifMatch = null)

            // when
            val result = service.grantPrincipalInstanceAdmin("ops", ifMatch = first.etag)

            // then
            assertThat(result.error.status).describedAs("status for a stale tag").isEqualTo(412)
            assertThat(service.getPrincipal("ops").orNull()?.value?.instanceRole).isFalse
        }
    }

    @Test
    fun `the instance role of an unknown team is not found`() {
        withService { service, _ ->
            assertThat(service.grantTeamInstanceAdmin("missing", ifMatch = null).error.status).isEqualTo(404)
        }
    }

    @Test
    fun `the instance role of a private team is not found`() {
        withService { service, _ ->
            // given: a machine principal, which owns a private team
            service.createPrincipal(CreatePrincipalRequest(kind = "MACHINE", name = "ci-bot"))

            // when / then: private teams are not part of the administration
            assertThat(service.grantTeamInstanceAdmin("machine:ci-bot", ifMatch = null).error.status).isEqualTo(404)
        }
    }

    @Test
    fun `a team grant request is unaffected by the instance role of the team`() {
        withService { service, _ ->
            // given: a team with the instance role
            service.createTeam(CreateTeamRequest(name = "operators"))
            service.grantTeamInstanceAdmin("operators", ifMatch = null)

            // when: the grants are replaced
            val result = service.replaceGrants("operators", listOf(GrantRequest(repository = "releases", pathPrefix = "com", role = "READ")), ifMatch = null)

            // then: the instance role stays and is reported
            assertThat(result.orNull()?.value?.instanceRole).isTrue
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
