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
import com.reposilite.audit.api.AuditActor
import com.reposilite.audit.api.AuditEvent
import net.onelitefeather.ingot.identity.model.PrincipalRecord
import net.onelitefeather.ingot.identity.model.TeamMemberSource
import net.onelitefeather.ingot.identity.model.TeamRecord
import net.onelitefeather.ingot.identity.storage.IdentityStore

/**
 * The admin right of a team. It is the only place that decides how an admin group becomes an administrator: a synchronised
 * team holds the right, and a person never receives it directly. Today the right is the instance role of the team; under
 * ADR-INGT-0003 it becomes the permission node `ingot.admin.*` on the same team, and only this object changes.
 */
internal object AdminRights {

    fun grant(identities: IdentityStore, teamId: Int) {
        identities.grantInstanceRole(principalId = null, teamId = teamId)
    }

    fun revoke(identities: IdentityStore, teamId: Int) {
        identities.revokeInstanceRole(principalId = null, teamId = teamId)
    }

    fun holders(identities: IdentityStore): Set<Int> =
        identities.listInstanceRoleTeamIds()

}

/**
 * Keeps the teams of one provider in step with the groups of a login.
 *
 * A team takes part when its external reference starts with the prefix of the provider (`oidc:<id>:` or `ldap:`), and its
 * reference is `<prefix><group>`. At each login the principal gets a `SYNC` membership in every such team whose group it has,
 * and loses the `SYNC` memberships of the teams whose group it no longer has. `MANUAL` memberships and teams of other
 * references are never touched.
 *
 * Admin groups: each listed group gets its team on demand, with the admin right (see [AdminRights]). For a team of this
 * provider the right follows the list: granted while the group is listed, revoked when it is not.
 *
 * Every membership the sync adds or removes is audited as `TEAM_MEMBERSHIP_CHANGED`, once per team, with the actor
 * `system:<provider>`. A login that changes nothing writes nothing.
 */
internal class ExternalGroupSync(
    private val identities: IdentityStore,
    private val log: (String) -> Unit,
    private val audit: (AuditEvent) -> Unit = {},
) {

    fun apply(principal: PrincipalRecord, provider: String, prefix: String, groups: Set<String>, adminGroups: Set<String>) {
        ensureAdminTeams(prefix, adminGroups)
        applyAdminRights(prefix, adminGroups)
        applyMemberships(principal, provider, prefix, groups)
    }

    private fun ensureAdminTeams(prefix: String, adminGroups: Set<String>) {
        adminGroups.forEach { group ->
            val reference = prefix + group
            if (identities.listTeams().any { it.externalRef == reference }) return@forEach
            // A team of the same name that was made by hand is not taken over: it has no reference, so it is not ours.
            val created = identities.createTeam(name = reference, description = "Synchronised from the group $reference", externalRef = reference)
            if (created == null) {
                log("Admin group $reference skipped: a team of that name exists without the external reference")
            } else {
                log("Created the synchronised admin team $reference")
            }
        }
    }

    private fun applyAdminRights(prefix: String, adminGroups: Set<String>) {
        val holders = AdminRights.holders(identities)
        identities.listTeams().forEach { team ->
            val reference = team.externalRef ?: return@forEach
            if (!reference.startsWith(prefix)) return@forEach
            val listed = reference.removePrefix(prefix) in adminGroups
            val holds = team.id in holders
            when {
                listed && !holds -> AdminRights.grant(identities, team.id)
                !listed && holds -> AdminRights.revoke(identities, team.id)
            }
        }
    }

    private fun applyMemberships(principal: PrincipalRecord, provider: String, prefix: String, groups: Set<String>) {
        val teams = identities.listTeams()
        val wanted = teams.filter { team -> team.externalRef?.let { it.startsWith(prefix) && it.removePrefix(prefix) in groups } == true }
        val wantedIds = wanted.map { it.id }.toSet()
        val providerTeamIds = teams.filter { it.externalRef?.startsWith(prefix) == true }.map { it.id }.toSet()

        val memberships = identities.membershipsOf(principal.id)
        val synced = memberships.filter { it.source == TeamMemberSource.SYNC }.map { it.teamId }.toSet()
        val dropped = memberships
            .filter { it.source == TeamMemberSource.SYNC && it.teamId in providerTeamIds && it.teamId !in wantedIds }
            .map { it.teamId }
        dropped.forEach { identities.removeMember(it, principal.id) }

        wantedIds.forEach { identities.addMember(it, principal.id, TeamMemberSource.SYNC) }

        val joined = wantedIds - synced
        audit(provider, principal, teams, joined = joined, left = dropped.toSet())
    }

    private fun audit(provider: String, principal: PrincipalRecord, teams: List<TeamRecord>, joined: Set<Int>, left: Set<Int>) {
        val byId = teams.associateBy { it.id }
        val actor = AuditActor.named("system:$provider", null)
        (joined + left).forEach { teamId ->
            val team = byId[teamId] ?: return@forEach
            val added = if (teamId in joined) listOf(principal.name) else emptyList()
            val removed = if (teamId in left) listOf(principal.name) else emptyList()
            audit(AuditEvent(action = TEAM_MEMBERSHIP_CHANGED, actor = actor, details = mapOf("team" to team.name, "added" to added, "removed" to removed)))
        }
    }

}
