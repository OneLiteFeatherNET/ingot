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

import com.reposilite.journalist.Journalist
import com.reposilite.token.infrastructure.AccessTokenTable
import com.reposilite.token.infrastructure.PermissionToAccessTokenTable
import com.reposilite.token.infrastructure.PermissionToRouteTable
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.model.Role
import net.onelitefeather.ingot.identity.model.TeamMemberSource
import net.onelitefeather.ingot.identity.model.INSTANCE_ADMIN_ROLE
import net.onelitefeather.ingot.identity.model.privateTeamName
import net.onelitefeather.ingot.identity.storage.IdentityStore.Companion.foldName
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

/** Outcome of [LegacyAccessTokenMigration.migrate]. */
enum class MigrationOutcome { MIGRATED, ALREADY_APPLIED }

/**
 * Copies the persistent access tokens of the legacy tables into the identity tables. The legacy tables are only read.
 *
 * The whole run is one transaction and starts by claiming the migration marker with an insert-ignore. When several
 * instances start at once they serialize on that row, and every instance but the first inserts nothing and returns
 * [MigrationOutcome.ALREADY_APPLIED]. A failed check rolls the marker back too, so the next start retries from scratch.
 */
internal class LegacyAccessTokenMigration(
    private val database: Database,
    private val journalist: Journalist,
) {

    companion object {
        const val MIGRATION_ACCESS_TOKENS: String = "001-access-tokens"
        private const val MANAGER_PERMISSION = "access-token:manager"
        private const val ROUTE_READ = "route:read"
        private const val ROUTE_WRITE = "route:write"
    }

    fun migrate(): MigrationOutcome =
        transaction(database) {
            SchemaUtils.create(*IDENTITY_TABLES)
            val claimed = IdentityMigrationTable.insertIgnore {
                it[IdentityMigrationTable.name] = MIGRATION_ACCESS_TOKENS
                it[IdentityMigrationTable.appliedAt] = Instant.now()
            }.insertedCount

            if (claimed == 0) {
                return@transaction MigrationOutcome.ALREADY_APPLIED
            }

            val legacyTokens = AccessTokenTable.selectAll().map { it[AccessTokenTable.id].value }.sorted()
            val legacyManagers = PermissionToAccessTokenTable.selectAll()
                .filter { it[PermissionToAccessTokenTable.permission] == MANAGER_PERMISSION }
                .map { it[PermissionToAccessTokenTable.accessTokenId].value }
                .toSet()
            val legacyRoutes = PermissionToRouteTable.selectAll()
                .map { Triple(it[PermissionToRouteTable.accessTokenId].value, it[PermissionToRouteTable.route], it[PermissionToRouteTable.permission]) }

            legacyTokens.forEach { tokenId ->
                migrateToken(tokenId, hasManager = tokenId in legacyManagers, routes = legacyRoutes.filter { it.first == tokenId })
            }

            resetTokenSequence()
            verify(legacyTokens.size, legacyManagers.size, legacyRoutes)

            journalist.logger.info("Identity | migrated ${legacyTokens.size} access token(s) to machine principals")
            MigrationOutcome.MIGRATED
        }

    private fun migrateToken(tokenId: Int, hasManager: Boolean, routes: List<Triple<Int, String, String>>) {
        val row = AccessTokenTable.selectAll().where { AccessTokenTable.id eq tokenId }.single()
        val tokenName = row[AccessTokenTable.name]
        val principalName = resolvePrincipalName(tokenId, tokenName)
        val createdAt = row[AccessTokenTable.createdAt].atStartOfDay(ZoneOffset.UTC).toInstant()

        val principalId = PrincipalTable.insertAndGetId {
            it[PrincipalTable.kind] = PrincipalKind.MACHINE.name
            it[PrincipalTable.name] = principalName
            it[PrincipalTable.nameKey] = foldName(principalName)
            it[PrincipalTable.displayName] = principalName
            it[PrincipalTable.disabled] = false
            it[PrincipalTable.createdAt] = createdAt
        }

        PrincipalTokenTable.insert {
            it[PrincipalTokenTable.id] = EntityID(tokenId, PrincipalTokenTable)
            it[PrincipalTokenTable.principalId] = principalId
            it[PrincipalTokenTable.name] = tokenName
            it[PrincipalTokenTable.secretHash] = row[AccessTokenTable.secret]
            it[PrincipalTokenTable.scopes] = null
            it[PrincipalTokenTable.expiresAt] = row[AccessTokenTable.expiresAt]
            it[PrincipalTokenTable.createdAt] = row[AccessTokenTable.createdAt]
            it[PrincipalTokenTable.description] = row[AccessTokenTable.description]
        }

        val teamName = privateTeamName(principalName)
        val teamId = TeamTable.insertAndGetId {
            it[TeamTable.name] = teamName
            it[TeamTable.nameKey] = foldName(teamName)
            it[TeamTable.description] = "Private team of machine principal $principalName"
            it[TeamTable.externalRef] = null
        }

        TeamMemberTable.insert {
            it[TeamMemberTable.teamId] = teamId
            it[TeamMemberTable.principalId] = principalId
            it[TeamMemberTable.memberSource] = TeamMemberSource.MANUAL.name
        }

        if (hasManager) {
            InstanceRoleTable.insert {
                it[InstanceRoleTable.principalId] = principalId
                it[InstanceRoleTable.teamId] = null
                it[InstanceRoleTable.role] = INSTANCE_ADMIN_ROLE
            }
        }

        routes.forEach { (_, _, permission) ->
            if (legacyRole(permission) == null) {
                journalist.logger.warn("Identity | skipping legacy route permission '$permission'")
            }
        }

        legacyGrants(routes).forEach { (path, role) ->
            TeamGrantTable.insert {
                it[TeamGrantTable.teamId] = teamId
                it[TeamGrantTable.repository] = null
                it[TeamGrantTable.pathPrefix] = path
                it[TeamGrantTable.role] = role.name
            }
        }
    }

    /**
     * The name of the migrated principal. A legacy name is unique case-sensitively only, so on a case-sensitive
     * database two tokens can differ in case alone. The second one gets its token id appended to its principal name.
     * Its token keeps the exact name, so authentication is unchanged.
     */
    private fun resolvePrincipalName(tokenId: Int, tokenName: String): String {
        val taken = PrincipalTable.selectAll().where { PrincipalTable.nameKey eq foldName(tokenName) }.any()
        if (!taken) return tokenName

        val fallback = "$tokenName-$tokenId"
        journalist.logger.warn("Identity | token '$tokenName' differs from another token only in case, its principal is named '$fallback'")
        return fallback
    }

    private fun legacyRole(permission: String): Role? =
        when (permission) {
            ROUTE_READ -> Role.LEGACY_READ
            ROUTE_WRITE -> Role.LEGACY_WRITE
            else -> null
        }

    /** Distinct (path, role) pairs of the legacy routes of one token. Unknown permissions produce nothing. */
    private fun legacyGrants(routes: List<Triple<Int, String, String>>): List<Pair<String, Role>> =
        routes.mapNotNull { (_, path, permission) -> legacyRole(permission)?.let { path to it } }.distinct()

    /**
     * Migrated rows keep their legacy identifiers. PostgreSQL and H2 do not advance their identity sequence for explicit
     * ids, so the sequence is moved past the largest migrated id. Other databases do this on their own.
     */
    private fun resetTokenSequence() {
        val largest = PrincipalTokenTable.selectAll().maxOfOrNull { it[PrincipalTokenTable.id].value } ?: return
        when (database.vendor.lowercase(Locale.ROOT)) {
            "postgresql" -> TransactionManager.current().exec(
                "SELECT setval(pg_get_serial_sequence('principal_token', 'id'), $largest)"
            )
            "h2" -> TransactionManager.current().exec(
                "ALTER TABLE principal_token ALTER COLUMN id RESTART WITH ${largest + 1}"
            )
        }
    }

    /**
     * Compares the copied rows with the legacy rows. A mismatch throws, which rolls back the whole migration, including
     * the marker, and stops the start instead of running with partial identities.
     */
    private fun verify(legacyTokenCount: Int, legacyManagerCount: Int, legacyRoutes: List<Triple<Int, String, String>>) {
        val tokens = PrincipalTokenTable.selectAll().count()
        val principals = PrincipalTable.selectAll().where { PrincipalTable.kind eq PrincipalKind.MACHINE.name }.count()
        val managers = InstanceRoleTable.selectAll().count()
        val expectedGrants = legacyRoutes.groupBy { it.first }.values.sumOf { legacyGrants(it).size }
        val grants = TeamGrantTable.selectAll().count()

        check(tokens == legacyTokenCount.toLong()) { "Identity migration copied $tokens token(s), expected $legacyTokenCount" }
        check(principals == legacyTokenCount.toLong()) { "Identity migration created $principals machine principal(s), expected $legacyTokenCount" }
        check(managers == legacyManagerCount.toLong()) { "Identity migration created $managers instance role(s), expected $legacyManagerCount" }
        check(grants == expectedGrants.toLong()) { "Identity migration created $grants grant(s), expected $expectedGrants" }
    }

}
