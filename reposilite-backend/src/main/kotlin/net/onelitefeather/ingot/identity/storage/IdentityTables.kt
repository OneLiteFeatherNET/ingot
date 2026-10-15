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

import org.jetbrains.exposed.v1.core.ReferenceOption.CASCADE
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestamp

/** Names are kept in a case-folded key so that uniqueness is case-insensitive on every database. */
internal const val NAME_LENGTH = 255
internal const val TEAM_NAME_LENGTH = 320

object PrincipalTable : IntIdTable("principal") {
    val kind = varchar("kind", 16)
    val name = varchar("name", NAME_LENGTH)
    val nameKey = varchar("name_key", NAME_LENGTH).uniqueIndex("uq_principal_name_key")
    val displayName = varchar("display_name", NAME_LENGTH)
    val disabled = bool("disabled").default(false)
    val createdAt = timestamp("created_at")
    val lastSeenAt = timestamp("last_seen_at").nullable()
}

object TeamTable : IntIdTable("team") {
    val name = varchar("name", TEAM_NAME_LENGTH)
    val nameKey = varchar("name_key", TEAM_NAME_LENGTH).uniqueIndex("uq_team_name_key")
    val description = text("description")
    val externalRef = varchar("external_ref", 512).nullable()
}

object TeamMemberTable : Table("team_member") {
    val teamId = reference("team_id", TeamTable, onDelete = CASCADE)
    val principalId = reference("principal_id", PrincipalTable, onDelete = CASCADE)
    val memberSource = varchar("source", 16)

    override val primaryKey = PrimaryKey(teamId, principalId)
}

object TeamGrantTable : IntIdTable("team_grant") {
    val teamId = reference("team_id", TeamTable, onDelete = CASCADE).index()
    val repository = varchar("repository", NAME_LENGTH).nullable()
    val pathPrefix = varchar("path_prefix", 1024).nullable()
    val role = varchar("role", 32)
}

object InstanceRoleTable : IntIdTable("instance_role") {
    val principalId = reference("principal_id", PrincipalTable, onDelete = CASCADE).nullable().index()
    val teamId = reference("team_id", TeamTable, onDelete = CASCADE).nullable().index()
    val role = varchar("role", 32)
}

/**
 * Tokens keep the id and the name of the access token they were migrated from, so identifiers stay stable across the
 * upgrade. Only the name is unique; the owner is the principal.
 */
object PrincipalTokenTable : IntIdTable("principal_token") {
    val principalId = reference("principal_id", PrincipalTable, onDelete = CASCADE).index()
    val name = varchar("name", NAME_LENGTH).uniqueIndex("uq_principal_token_name")
    val secretHash = varchar("secret_hash", 512)
    val scopes = text("scopes").nullable()
    val expiresAt = timestamp("expires_at").nullable()
    val lastUsedAt = timestamp("last_used_at").nullable()
    val lastUsedIp = varchar("last_used_ip", 64).nullable()
    val createdAt = date("created_at")
    val description = text("description")
}

object IdentityMigrationTable : Table("identity_migration") {
    val name = varchar("name", 64).uniqueIndex("uq_identity_migration_name")
    val appliedAt = timestamp("applied_at")
}

/** Every identity table, parents before children, in the order they are created. */
internal val IDENTITY_TABLES: Array<Table> = arrayOf(
    PrincipalTable,
    TeamTable,
    TeamMemberTable,
    TeamGrantTable,
    InstanceRoleTable,
    PrincipalTokenTable,
    IdentityMigrationTable,
)
