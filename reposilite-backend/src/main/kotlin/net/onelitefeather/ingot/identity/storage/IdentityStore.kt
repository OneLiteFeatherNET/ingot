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
package net.onelitefeather.ingot.identity.storage

import com.fasterxml.jackson.core.type.TypeReference
import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import net.onelitefeather.ingot.identity.login.external.ExternalIdentityTable
import net.onelitefeather.ingot.identity.login.storage.LocalCredentialTable
import net.onelitefeather.ingot.identity.login.storage.RefreshTokenTable
import net.onelitefeather.ingot.identity.model.AccessSubject
import net.onelitefeather.ingot.identity.model.Action
import net.onelitefeather.ingot.identity.model.Grant
import net.onelitefeather.ingot.identity.model.GrantRecord
import net.onelitefeather.ingot.identity.model.INSTANCE_ADMIN_ROLE
import net.onelitefeather.ingot.identity.model.MembershipRecord
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.model.PrincipalRecord
import net.onelitefeather.ingot.identity.model.Role
import net.onelitefeather.ingot.identity.model.TeamMemberSource
import net.onelitefeather.ingot.identity.model.TeamRecord
import net.onelitefeather.ingot.identity.model.TokenRecord
import net.onelitefeather.ingot.identity.model.privateTeamName
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

/**
 * Reads and writes the identity tables. Every method runs in its own transaction. Names are unique case-insensitively
 * through their folded keys; a duplicate returns `null` instead of throwing, so callers can answer with a conflict.
 * Children are deleted explicitly with their parents, so the result does not depend on foreign key enforcement.
 */
class IdentityStore(
    private val database: Database,
    private val clock: Clock = Clock.systemUTC(),
) {

    init {
        transaction(database) {
            SchemaUtils.create(*IDENTITY_TABLES)
            SchemaUtils.createMissingTablesAndColumns(*IDENTITY_TABLES)
        }
    }

    fun database(): Database =
        database

    fun now(): Instant =
        Instant.now(clock)

    // principals

    fun createPrincipal(kind: PrincipalKind, name: String, displayName: String, disabled: Boolean, createdAt: Instant = now()): PrincipalRecord? {
        if (findPrincipal(name) != null) return null
        return insertUnique {
            val id = PrincipalTable.insertAndGetId {
                it[PrincipalTable.kind] = kind.name
                it[PrincipalTable.name] = name
                it[PrincipalTable.nameKey] = foldName(name)
                it[PrincipalTable.displayName] = displayName
                it[PrincipalTable.disabled] = disabled
                it[PrincipalTable.createdAt] = createdAt
            }
            PrincipalTable.selectAll().where { PrincipalTable.id eq id }.single().toPrincipal()
        }
    }

    fun findPrincipal(name: String): PrincipalRecord? =
        transaction(database) {
            PrincipalTable.selectAll().where { PrincipalTable.nameKey eq foldName(name) }
                .map { it.toPrincipal() }
                .firstOrNull()
        }

    fun findPrincipalById(id: Int): PrincipalRecord? =
        transaction(database) {
            PrincipalTable.selectAll().where { PrincipalTable.id eq id }
                .map { it.toPrincipal() }
                .firstOrNull()
        }

    fun listPrincipals(kind: PrincipalKind? = null): List<PrincipalRecord> =
        transaction(database) {
            PrincipalTable.selectAll()
                .map { it.toPrincipal() }
                .filter { kind == null || it.kind == kind }
                .sortedBy { it.name.lowercase(Locale.ROOT) }
        }

    fun updatePrincipal(id: Int, displayName: String?, disabled: Boolean?) {
        transaction(database) {
            PrincipalTable.update(where = { PrincipalTable.id eq id }, body = {
                if (displayName != null) it[PrincipalTable.displayName] = displayName
                if (disabled != null) it[PrincipalTable.disabled] = disabled
            })
        }
    }

    /**
     * Stores the time the principal was last seen, unless a later or equal time is stored within the window. Returns
     * whether a row was written. The condition only ever moves the stored time forward.
     */
    fun touchPrincipal(id: Int, at: Instant, notBefore: Instant): Boolean =
        transaction(database) {
            PrincipalTable.update(
                where = { (PrincipalTable.id eq id) and (PrincipalTable.lastSeenAt.isNull() or (PrincipalTable.lastSeenAt lessEq notBefore)) },
                body = { it[PrincipalTable.lastSeenAt] = at },
            ) > 0
        }

    /**
     * Deletes the principal with its tokens, memberships, instance role and private team (including the legacy grants of
     * that team). Nothing else is touched.
     */
    fun deletePrincipal(principal: PrincipalRecord) {
        transaction(database) {
            findTeamRowIdsByKey(foldName(privateTeamName(principal.name))).forEach { deleteTeamRows(it) }
            InstanceRoleTable.deleteWhere { InstanceRoleTable.principalId eq principal.id }
            TeamMemberTable.deleteWhere { TeamMemberTable.principalId eq principal.id }
            PrincipalTokenTable.deleteWhere { PrincipalTokenTable.principalId eq principal.id }
            // The password and the browser sessions of a user go in the same transaction. Deleting the refresh tokens also
            // ends the sessions: a session token is refused as soon as its principal is gone, and no refresh token is left
            // that could renew it. The schema cascades these rows too, but only where foreign keys are enforced.
            LocalCredentialTable.deleteWhere { LocalCredentialTable.principalId eq principal.id }
            ExternalIdentityTable.deleteWhere { ExternalIdentityTable.principalId eq principal.id }
            RefreshTokenTable.deleteWhere { RefreshTokenTable.principalId eq principal.id }
            PrincipalTable.deleteWhere { PrincipalTable.id eq principal.id }
        }
    }

    // teams

    fun createTeam(name: String, description: String, externalRef: String?): TeamRecord? {
        if (findTeam(name) != null) return null
        return insertUnique {
            val id = TeamTable.insertAndGetId {
                it[TeamTable.name] = name
                it[TeamTable.nameKey] = foldName(name)
                it[TeamTable.description] = description
                it[TeamTable.externalRef] = externalRef
            }
            TeamTable.selectAll().where { TeamTable.id eq id }.single().toTeam()
        }
    }

    fun findTeam(name: String): TeamRecord? =
        transaction(database) {
            TeamTable.selectAll().where { TeamTable.nameKey eq foldName(name) }.map { it.toTeam() }.firstOrNull()
        }

    fun findTeamById(id: Int): TeamRecord? =
        transaction(database) {
            TeamTable.selectAll().where { TeamTable.id eq id }.map { it.toTeam() }.firstOrNull()
        }

    /** All teams, private ones included. Callers that present teams to operators filter [TeamRecord.isPrivate]. */
    fun listTeams(): List<TeamRecord> =
        transaction(database) {
            TeamTable.selectAll().map { it.toTeam() }.sortedBy { it.name.lowercase(Locale.ROOT) }
        }

    fun updateTeam(id: Int, description: String?, externalRef: String?, updateExternalRef: Boolean) {
        transaction(database) {
            TeamTable.update(where = { TeamTable.id eq id }, body = {
                if (description != null) it[TeamTable.description] = description
                if (updateExternalRef) it[TeamTable.externalRef] = externalRef
            })
        }
    }

    fun deleteTeam(id: Int) {
        transaction(database) { deleteTeamRows(id) }
    }

    // memberships

    fun listMemberships(teamId: Int): List<MembershipRecord> =
        transaction(database) {
            TeamMemberTable.selectAll().where { TeamMemberTable.teamId eq teamId }.map { it.toMembership() }
        }

    /** Every membership of every team, read in one query. */
    fun listAllMemberships(): List<MembershipRecord> =
        transaction(database) {
            TeamMemberTable.selectAll().map { it.toMembership() }
        }

    fun membershipsOf(principalId: Int): List<MembershipRecord> =
        transaction(database) {
            TeamMemberTable.selectAll().where { TeamMemberTable.principalId eq principalId }.map { it.toMembership() }
        }

    /**
     * Replaces the MANUAL members of the team with the given principals. SYNC members are not touched, so a later
     * synchronisation keeps its own rows.
     */
    fun replaceManualMembers(teamId: Int, principalIds: Set<Int>) {
        transaction(database) {
            val syncMembers = TeamMemberTable.selectAll()
                .where { (TeamMemberTable.teamId eq teamId) and (TeamMemberTable.memberSource eq TeamMemberSource.SYNC.name) }
                .map { it[TeamMemberTable.principalId].value }
                .toSet()
            TeamMemberTable.deleteWhere {
                (TeamMemberTable.teamId eq teamId) and (TeamMemberTable.memberSource eq TeamMemberSource.MANUAL.name)
            }
            (principalIds - syncMembers).forEach { principalId ->
                insertMember(teamId, principalId, TeamMemberSource.MANUAL)
            }
        }
    }

    fun addMember(teamId: Int, principalId: Int, source: TeamMemberSource) {
        transaction(database) {
            val exists = TeamMemberTable.selectAll()
                .where { (TeamMemberTable.teamId eq teamId) and (TeamMemberTable.principalId eq principalId) }
                .any()
            if (!exists) insertMember(teamId, principalId, source)
        }
    }

    /** Removes the membership of one principal in one team, whatever its source. Used by the synchronisation of provider groups. */
    fun removeMember(teamId: Int, principalId: Int) {
        transaction(database) {
            TeamMemberTable.deleteWhere { (TeamMemberTable.teamId eq teamId) and (TeamMemberTable.principalId eq principalId) }
        }
    }

    private fun insertMember(teamId: Int, principalId: Int, source: TeamMemberSource) {
        TeamMemberTable.insert {
            it[TeamMemberTable.teamId] = EntityID(teamId, TeamTable)
            it[TeamMemberTable.principalId] = EntityID(principalId, PrincipalTable)
            it[TeamMemberTable.memberSource] = source.name
        }
    }

    // grants

    fun listGrants(teamId: Int): List<GrantRecord> =
        transaction(database) {
            TeamGrantTable.selectAll().where { TeamGrantTable.teamId eq teamId }.map { it.toGrant() }
        }

    /** The number of grants of every team that has any, read in one query. */
    fun countGrantsByTeam(): Map<Int, Int> =
        transaction(database) {
            TeamGrantTable.select(TeamGrantTable.teamId)
                .groupingBy { it[TeamGrantTable.teamId].value }
                .eachCount()
        }

    fun addGrant(teamId: Int, repository: String?, pathPrefix: String?, role: Role): GrantRecord =
        transaction(database) {
            val id = TeamGrantTable.insertAndGetId {
                it[TeamGrantTable.teamId] = EntityID(teamId, TeamTable)
                it[TeamGrantTable.repository] = repository
                it[TeamGrantTable.pathPrefix] = pathPrefix
                it[TeamGrantTable.role] = role.name
            }
            TeamGrantTable.selectAll().where { TeamGrantTable.id eq id }.single().toGrant()
        }

    fun deleteGrant(id: Int) {
        transaction(database) { TeamGrantTable.deleteWhere { TeamGrantTable.id eq id } }
    }

    /** Replaces every grant of the team in one transaction. */
    fun replaceGrants(teamId: Int, grants: List<Triple<String?, String?, Role>>) {
        transaction(database) {
            TeamGrantTable.deleteWhere { TeamGrantTable.teamId eq teamId }
            grants.forEach { (repository, pathPrefix, role) ->
                TeamGrantTable.insert {
                    it[TeamGrantTable.teamId] = EntityID(teamId, TeamTable)
                    it[TeamGrantTable.repository] = repository
                    it[TeamGrantTable.pathPrefix] = pathPrefix
                    it[TeamGrantTable.role] = role.name
                }
            }
        }
    }

    /**
     * Deletes the legacy grants of a private team. A null [pathPrefix] or [role] matches every value, so the caller can
     * remove one route, all routes of a path, or all legacy grants of the team.
     */
    fun deleteLegacyGrants(teamId: Int, pathPrefix: String?, role: Role?) {
        transaction(database) {
            val legacyRoles = Role.entries.filter { it.legacy }.map { it.name }
            TeamGrantTable.deleteWhere {
                var condition = (TeamGrantTable.teamId eq teamId) and (TeamGrantTable.role inList legacyRoles)
                if (pathPrefix != null) condition = condition and (TeamGrantTable.pathPrefix eq pathPrefix)
                if (role != null) condition = condition and (TeamGrantTable.role eq role.name)
                condition
            }
        }
    }

    // instance roles

    /** Grants the instance role to a principal or to a team, once. Exactly one of the two must be given. */
    fun grantInstanceRole(principalId: Int?, teamId: Int?) {
        require((principalId == null) != (teamId == null)) { "Exactly one of principalId or teamId must be set" }
        transaction(database) {
            if (instanceRoleExists(principalId, teamId)) return@transaction
            InstanceRoleTable.insert {
                it[InstanceRoleTable.principalId] = principalId?.let { value -> EntityID(value, PrincipalTable) }
                it[InstanceRoleTable.teamId] = teamId?.let { value -> EntityID(value, TeamTable) }
                it[InstanceRoleTable.role] = INSTANCE_ADMIN_ROLE
            }
        }
    }

    fun revokeInstanceRole(principalId: Int?, teamId: Int?) {
        transaction(database) {
            val condition = if (principalId != null) {
                InstanceRoleTable.principalId eq principalId
            } else {
                InstanceRoleTable.teamId eq teamId!!
            }
            InstanceRoleTable.deleteWhere { condition }
        }
    }

    fun hasDirectInstanceRole(principalId: Int): Boolean =
        transaction(database) { instanceRoleExists(principalId, null) }

    fun listInstanceRoleTeamIds(): Set<Int> =
        transaction(database) {
            InstanceRoleTable.selectAll()
                .mapNotNull { it[InstanceRoleTable.teamId]?.value }
                .toSet()
        }

    fun listInstanceRolePrincipalIds(): Set<Int> =
        transaction(database) {
            InstanceRoleTable.selectAll()
                .mapNotNull { it[InstanceRoleTable.principalId]?.value }
                .toSet()
        }

    // tokens

    /** The number of tokens of every principal that has any, read in one query. */
    fun countTokensByPrincipal(): Map<Int, Int> =
        transaction(database) {
            PrincipalTokenTable.select(PrincipalTokenTable.principalId)
                .groupingBy { it[PrincipalTokenTable.principalId].value }
                .eachCount()
        }

    fun createToken(
        principalId: Int,
        name: String,
        secretHash: String,
        scopes: Set<Action>?,
        expiresAt: Instant?,
        createdAt: LocalDate,
        description: String,
    ): TokenRecord? {
        if (findToken(name) != null) return null
        return insertUnique {
            val id = PrincipalTokenTable.insertAndGetId {
                it[PrincipalTokenTable.principalId] = EntityID(principalId, PrincipalTable)
                it[PrincipalTokenTable.name] = name
                it[PrincipalTokenTable.secretHash] = secretHash
                it[PrincipalTokenTable.scopes] = encodeScopes(scopes)
                it[PrincipalTokenTable.expiresAt] = expiresAt
                it[PrincipalTokenTable.createdAt] = createdAt
                it[PrincipalTokenTable.description] = description
            }
            PrincipalTokenTable.selectAll().where { PrincipalTokenTable.id eq id }.single().toToken()
        }
    }

    /** Writes every field of the token except its id and owner. The name is compared exactly, as it always was. */
    fun saveToken(token: TokenRecord) {
        transaction(database) {
            PrincipalTokenTable.update(where = { PrincipalTokenTable.id eq token.id }, body = {
                it[PrincipalTokenTable.name] = token.name
                it[PrincipalTokenTable.secretHash] = token.secretHash
                it[PrincipalTokenTable.scopes] = encodeScopes(token.scopes)
                it[PrincipalTokenTable.expiresAt] = token.expiresAt
                it[PrincipalTokenTable.createdAt] = token.createdAt
                it[PrincipalTokenTable.description] = token.description
            })
        }
    }

    fun findToken(name: String): TokenRecord? =
        transaction(database) {
            PrincipalTokenTable.selectAll().where { PrincipalTokenTable.name eq name }.map { it.toToken() }.firstOrNull()
        }

    fun findTokenById(id: Int): TokenRecord? =
        transaction(database) {
            PrincipalTokenTable.selectAll().where { PrincipalTokenTable.id eq id }.map { it.toToken() }.firstOrNull()
        }

    fun tokensOf(principalId: Int): List<TokenRecord> =
        transaction(database) {
            PrincipalTokenTable.selectAll().where { PrincipalTokenTable.principalId eq principalId }
                .map { it.toToken() }
                .sortedBy { it.id }
        }

    fun listTokens(): List<TokenRecord> =
        transaction(database) {
            PrincipalTokenTable.selectAll().map { it.toToken() }.sortedBy { it.id }
        }

    fun deleteToken(id: Int) {
        transaction(database) { PrincipalTokenTable.deleteWhere { PrincipalTokenTable.id eq id } }
    }

    /**
     * Stores the last use of the token and its address, unless a later or equal use is stored within the window. Returns
     * whether a row was written. The condition only ever moves the stored time forward.
     */
    fun touchToken(id: Int, at: Instant, ip: String?, notBefore: Instant): Boolean =
        transaction(database) {
            PrincipalTokenTable.update(
                where = { (PrincipalTokenTable.id eq id) and (PrincipalTokenTable.lastUsedAt.isNull() or (PrincipalTokenTable.lastUsedAt lessEq notBefore)) },
                body = {
                    it[PrincipalTokenTable.lastUsedAt] = at
                    it[PrincipalTokenTable.lastUsedIp] = ip
                },
            ) > 0
        }

    fun countTokens(): Long =
        transaction(database) { PrincipalTokenTable.selectAll().count() }

    // login credentials

    /** The principals that have a local credential (a password), read in one query. */
    fun listPrincipalIdsWithLocalCredential(): Set<Int> =
        transaction(database) {
            LocalCredentialTable.select(LocalCredentialTable.principalId)
                .map { it[LocalCredentialTable.principalId].value }
                .toSet()
        }

    // effective access

    /**
     * Everything the evaluator needs about the principal: the grants of all its teams, whether it is an instance admin
     * (directly or through a team), and the scopes of the token it authenticated with.
     */
    fun loadAccess(principal: PrincipalRecord, scopes: Set<Action>?): AccessSubject =
        transaction(database) {
            val teamIds = TeamMemberTable.selectAll()
                .where { TeamMemberTable.principalId eq principal.id }
                .map { it[TeamMemberTable.teamId].value }
                .toSet()
            val teams = if (teamIds.isEmpty()) emptyMap() else TeamTable.selectAll()
                .where { TeamTable.id inList teamIds.map { EntityID(it, TeamTable) } }
                .associate { it[TeamTable.id].value to it[TeamTable.name] }
            val grants = if (teams.isEmpty()) emptyList() else TeamGrantTable.selectAll()
                .where { TeamGrantTable.teamId inList teams.keys.map { EntityID(it, TeamTable) } }
                .map { row ->
                    Grant(
                        team = teams.getValue(row[TeamGrantTable.teamId].value),
                        role = Role.valueOf(row[TeamGrantTable.role]),
                        repository = row[TeamGrantTable.repository],
                        pathPrefix = row[TeamGrantTable.pathPrefix],
                    )
                }
            val instanceAdmin = instanceRoleExists(principal.id, null) || (teams.isNotEmpty() && InstanceRoleTable.selectAll()
                .where { InstanceRoleTable.teamId inList teams.keys.map { EntityID(it, TeamTable) } }
                .any())
            AccessSubject(
                principalId = principal.id,
                principalName = principal.name,
                kind = principal.kind,
                disabled = principal.disabled,
                instanceAdmin = instanceAdmin,
                grants = grants,
                scopes = scopes,
                teams = teams.values.sorted(),
            )
        }

    // internals

    private fun instanceRoleExists(principalId: Int?, teamId: Int?): Boolean {
        val condition = when {
            principalId != null -> InstanceRoleTable.principalId eq principalId
            else -> InstanceRoleTable.teamId eq teamId!!
        }
        return InstanceRoleTable.selectAll().where { condition }.any()
    }

    private fun findTeamRowIdsByKey(nameKey: String): List<Int> =
        TeamTable.selectAll().where { TeamTable.nameKey eq nameKey }.map { it[TeamTable.id].value }

    private fun deleteTeamRows(teamId: Int) {
        TeamGrantTable.deleteWhere { TeamGrantTable.teamId eq teamId }
        TeamMemberTable.deleteWhere { TeamMemberTable.teamId eq teamId }
        InstanceRoleTable.deleteWhere { InstanceRoleTable.teamId eq teamId }
        TeamTable.deleteWhere { TeamTable.id eq teamId }
    }

    /** Runs the write in its own transaction and reports a unique-key violation as `null`. */
    private fun <T : Any> insertUnique(block: () -> T): T? =
        try {
            transaction(database) { block() }
        } catch (exception: ExposedSQLException) {
            null
        }

    private fun ResultRow.toPrincipal() = PrincipalRecord(
        id = this[PrincipalTable.id].value,
        kind = PrincipalKind.valueOf(this[PrincipalTable.kind]),
        name = this[PrincipalTable.name],
        displayName = this[PrincipalTable.displayName],
        disabled = this[PrincipalTable.disabled],
        createdAt = this[PrincipalTable.createdAt],
        lastSeenAt = this[PrincipalTable.lastSeenAt],
    )

    private fun ResultRow.toTeam() = TeamRecord(
        id = this[TeamTable.id].value,
        name = this[TeamTable.name],
        description = this[TeamTable.description],
        externalRef = this[TeamTable.externalRef],
    )

    private fun ResultRow.toMembership() = MembershipRecord(
        teamId = this[TeamMemberTable.teamId].value,
        principalId = this[TeamMemberTable.principalId].value,
        source = TeamMemberSource.valueOf(this[TeamMemberTable.memberSource]),
    )

    private fun ResultRow.toGrant() = GrantRecord(
        id = this[TeamGrantTable.id].value,
        teamId = this[TeamGrantTable.teamId].value,
        repository = this[TeamGrantTable.repository],
        pathPrefix = this[TeamGrantTable.pathPrefix],
        role = Role.valueOf(this[TeamGrantTable.role]),
    )

    private fun ResultRow.toToken() = TokenRecord(
        id = this[PrincipalTokenTable.id].value,
        principalId = this[PrincipalTokenTable.principalId].value,
        name = this[PrincipalTokenTable.name],
        secretHash = this[PrincipalTokenTable.secretHash],
        scopes = decodeScopes(this[PrincipalTokenTable.scopes]),
        expiresAt = this[PrincipalTokenTable.expiresAt],
        lastUsedAt = this[PrincipalTokenTable.lastUsedAt],
        lastUsedIp = this[PrincipalTokenTable.lastUsedIp],
        createdAt = this[PrincipalTokenTable.createdAt],
        description = this[PrincipalTokenTable.description],
    )

    companion object {

        /** The key under which names are unique. Locale-independent, so Turkish dotted i does not split a name. */
        fun foldName(name: String): String =
            name.lowercase(Locale.ROOT)

        /** Scopes are stored as a JSON array of action names, or `null` when the token is not narrowed. */
        internal fun encodeScopes(scopes: Set<Action>?): String? =
            scopes?.map { it.name }?.sorted()?.let { DEFAULT_OBJECT_MAPPER.writeValueAsString(it) }

        internal fun decodeScopes(encoded: String?): Set<Action>? =
            encoded?.let {
                DEFAULT_OBJECT_MAPPER.readValue(it, object : TypeReference<List<String>>() {})
                    .map { name -> Action.valueOf(name) }
                    .toSet()
            }
    }

}
