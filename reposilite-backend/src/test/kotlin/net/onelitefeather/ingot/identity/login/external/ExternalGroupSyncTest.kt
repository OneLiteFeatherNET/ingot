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

package net.onelitefeather.ingot.identity.login.external

import com.reposilite.audit.api.AuditAction.TEAM_MEMBERSHIP_CHANGED
import com.reposilite.audit.api.AuditEvent
import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import net.onelitefeather.ingot.identity.login.MutableClock
import net.onelitefeather.ingot.identity.login.external.ExternalOutcome.Succeeded
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.model.PrincipalRecord
import net.onelitefeather.ingot.identity.model.TeamMemberSource
import net.onelitefeather.ingot.identity.model.TeamRecord
import net.onelitefeather.ingot.identity.storage.IdentityStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant

internal class ExternalGroupSyncTest {

    private val clock = MutableClock(Instant.parse("2026-10-15T10:00:00Z"))

    @TempDir
    lateinit var workingDirectory: Path

    private class Fixture(
        val identities: IdentityStore,
        val accounts: ExternalAccountService,
        val alice: PrincipalRecord,
        val log: MutableList<String>,
        /** Every audit event of the fixture, the creation of alice included. */
        val audit: MutableList<AuditEvent>,
    )

    private fun withFixture(block: (Fixture) -> Unit) {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            val identities = IdentityStore(connection.database, clock)
            identities.createPrincipal(PrincipalKind.USER, "admin", "Admin", false, clock.instant())
            val log = mutableListOf<String>()
            val audit = mutableListOf<AuditEvent>()
            val accounts = ExternalAccountService(identities, ExternalIdentityStore(connection.database), audit = { audit.add(it) }, log = { log.add(it) })
            // Alice is linked by her first login, so the group tests start from a known user.
            val first = accounts.resolve(identity(groups = emptyList())) as Succeeded
            block(Fixture(identities, accounts, first.principal, log, audit))
        }
    }

    private fun identity(
        groups: List<String> = emptyList(),
        adminGroups: List<String> = emptyList(),
        provider: String = "oidc:corp",
        groupPrefix: String = "oidc:corp:",
    ) = ExternalIdentity(
        provider = provider,
        subject = "subject-1",
        username = "alice",
        displayName = null,
        email = null,
        autoCreateUsers = true,
        allowedEmailDomains = emptyList(),
        groups = groups,
        adminGroups = adminGroups,
        groupPrefix = groupPrefix,
    )

    private fun Fixture.memberships(team: TeamRecord) =
        identities.listMemberships(team.id).filter { it.principalId == alice.id }

    @Test
    fun `a group with a team becomes a synchronised membership at login`() {
        withFixture { fixture ->
            val devs = fixture.identities.createTeam("oidc:corp:devs", "", "oidc:corp:devs")!!

            fixture.accounts.resolve(identity(groups = listOf("devs")))

            assertThat(fixture.memberships(devs).map { it.source }).containsExactly(TeamMemberSource.SYNC)
        }
    }

    @Test
    fun `a membership is removed at the next login when the group is gone`() {
        withFixture { fixture ->
            val devs = fixture.identities.createTeam("oidc:corp:devs", "", "oidc:corp:devs")!!
            fixture.accounts.resolve(identity(groups = listOf("devs")))

            fixture.accounts.resolve(identity(groups = emptyList()))

            assertThat(fixture.memberships(devs)).describedAs("memberships after the group left").isEmpty()
        }
    }

    @Test
    fun `a manual membership is kept when the group is gone`() {
        withFixture { fixture ->
            val devs = fixture.identities.createTeam("oidc:corp:devs", "", "oidc:corp:devs")!!
            fixture.identities.addMember(devs.id, fixture.alice.id, TeamMemberSource.MANUAL)

            fixture.accounts.resolve(identity(groups = emptyList()))

            assertThat(fixture.memberships(devs).map { it.source }).containsExactly(TeamMemberSource.MANUAL)
        }
    }

    @Test
    fun `teams of another provider are never changed by this provider`() {
        withFixture { fixture ->
            val other = fixture.identities.createTeam("oidc:other:devs", "", "oidc:other:devs")!!
            fixture.identities.addMember(other.id, fixture.alice.id, TeamMemberSource.SYNC)

            fixture.accounts.resolve(identity(groups = emptyList()))

            assertThat(fixture.memberships(other).map { it.source }).describedAs("the other provider's membership").containsExactly(TeamMemberSource.SYNC)
        }
    }

    @Test
    fun `a group without a team creates nothing`() {
        withFixture { fixture ->
            val before = fixture.identities.listTeams().size

            fixture.accounts.resolve(identity(groups = listOf("unknown")))

            assertThat(fixture.identities.listTeams()).hasSize(before)
        }
    }

    @Test
    fun `members of an admin group become instance administrators through a synchronised team`() {
        withFixture { fixture ->
            // when: alice logs in with a group that is listed as an admin group
            fixture.accounts.resolve(identity(groups = listOf("ops"), adminGroups = listOf("ops")))

            // then: the team exists with its reference, and alice is an instance administrator through it
            val team = fixture.identities.findTeam("oidc:corp:ops")!!
            assertThat(team.externalRef).isEqualTo("oidc:corp:ops")
            assertThat(fixture.memberships(team).map { it.source }).containsExactly(TeamMemberSource.SYNC)
            assertThat(fixture.identities.loadAccess(fixture.alice, scopes = null).instanceAdmin).isTrue
        }
    }

    @Test
    fun `no admin right is written to the person directly`() {
        withFixture { fixture ->
            fixture.accounts.resolve(identity(groups = listOf("ops"), adminGroups = listOf("ops")))

            assertThat(fixture.identities.hasDirectInstanceRole(fixture.alice.id)).isFalse
        }
    }

    @Test
    fun `the admin right ends at the next login once the group is no longer an admin group`() {
        withFixture { fixture ->
            fixture.accounts.resolve(identity(groups = listOf("ops"), adminGroups = listOf("ops")))

            fixture.accounts.resolve(identity(groups = listOf("ops"), adminGroups = emptyList()))

            assertThat(fixture.identities.loadAccess(fixture.alice, scopes = null).instanceAdmin).isFalse
        }
    }

    @Test
    fun `a team of the same name without the reference is not taken over`() {
        withFixture { fixture ->
            val manual = fixture.identities.createTeam("oidc:corp:ops", "made by hand", null)!!

            fixture.accounts.resolve(identity(groups = listOf("ops"), adminGroups = listOf("ops")))

            assertThat(fixture.identities.findTeamById(manual.id)?.externalRef).describedAs("reference of the hand-made team").isNull()
            assertThat(fixture.identities.loadAccess(fixture.alice, scopes = null).instanceAdmin).isFalse
            assertThat(fixture.log.single()).contains("skipped")
        }
    }

    @Test
    fun `the group names of two providers do not mix`() {
        withFixture { fixture ->
            val otherOps = fixture.identities.createTeam("oidc:other:ops", "", "oidc:other:ops")!!

            fixture.accounts.resolve(identity(groups = listOf("ops"), adminGroups = listOf("ops")))

            assertThat(fixture.memberships(otherOps)).isEmpty()
        }
    }

    private fun Fixture.teamChanges() = audit.filter { it.action == TEAM_MEMBERSHIP_CHANGED }

    @Test
    fun `a membership added by the sync is audited as a team change by the provider`() {
        withFixture { fixture ->
            fixture.identities.createTeam("oidc:corp:devs", "", "oidc:corp:devs")!!

            fixture.accounts.resolve(identity(groups = listOf("devs")))

            val event = fixture.teamChanges().single()
            assertThat(event.actor.name).isEqualTo("system:oidc:corp")
            assertThat(event.details).containsEntry("team", "oidc:corp:devs")
            assertThat(event.details).containsEntry("added", listOf("alice"))
            assertThat(event.details).containsEntry("removed", emptyList<String>())
        }
    }

    @Test
    fun `a membership removed by the sync is audited with the name in removed`() {
        withFixture { fixture ->
            fixture.identities.createTeam("oidc:corp:devs", "", "oidc:corp:devs")!!
            fixture.accounts.resolve(identity(groups = listOf("devs")))

            fixture.accounts.resolve(identity(groups = emptyList()))

            val event = fixture.teamChanges().last()
            assertThat(event.details).containsEntry("added", emptyList<String>())
            assertThat(event.details).containsEntry("removed", listOf("alice"))
        }
    }

    @Test
    fun `a login that changes no membership writes no team change`() {
        withFixture { fixture ->
            fixture.identities.createTeam("oidc:corp:devs", "", "oidc:corp:devs")!!
            fixture.accounts.resolve(identity(groups = listOf("devs")))

            fixture.accounts.resolve(identity(groups = listOf("devs")))

            assertThat(fixture.teamChanges()).hasSize(1)
        }
    }

    @Test
    fun `a manual membership that the group does not touch is not audited`() {
        withFixture { fixture ->
            val devs = fixture.identities.createTeam("oidc:corp:devs", "", "oidc:corp:devs")!!
            fixture.identities.addMember(devs.id, fixture.alice.id, TeamMemberSource.MANUAL)

            fixture.accounts.resolve(identity(groups = emptyList()))

            assertThat(fixture.teamChanges()).isEmpty()
        }
    }

    @Test
    fun `an ldap sync names the ldap provider as its actor`() {
        withFixture { fixture ->
            fixture.identities.createTeam("ldap:staff", "", "ldap:staff")!!

            // The sync is called directly: an LDAP login would need a second subject, and alice already holds the name.
            ExternalGroupSync(fixture.identities, log = {}, audit = { fixture.audit.add(it) })
                .apply(fixture.alice, provider = "ldap:ldap.example.org", prefix = "ldap:", groups = setOf("staff"), adminGroups = emptySet())

            assertThat(fixture.teamChanges().single().actor.name).isEqualTo("system:ldap:ldap.example.org")
        }
    }
}
