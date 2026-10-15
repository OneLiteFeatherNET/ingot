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

package net.onelitefeather.ingot.identity.login.storage

import net.onelitefeather.ingot.identity.storage.PrincipalTable
import org.jetbrains.exposed.v1.core.ReferenceOption.CASCADE
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.javatime.timestamp

/** One local password per principal. The hash is argon2id in PHC format, see `PasswordHasher`. */
object LocalCredentialTable : Table("local_credential") {
    val principalId = reference("principal_id", PrincipalTable, onDelete = CASCADE)
    val passwordHash = varchar("password_hash", 512)
    val mustChangePassword = bool("must_change_password").default(false)
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(principalId)
}

/**
 * Refresh tokens of browser sessions. Only a SHA-256 hash of each token is stored. A token and every token issued after it
 * share a [familyId], so that one logout, or the reuse of a spent token, revokes the whole chain. [usedAt] is set when the
 * token is exchanged for the next one, which makes it single use.
 */
object RefreshTokenTable : IntIdTable("refresh_token") {
    val familyId = varchar("family_id", 64).index()
    val principalId = reference("principal_id", PrincipalTable, onDelete = CASCADE)
    val tokenHash = varchar("token_hash", 128).uniqueIndex("uq_refresh_token_hash")
    val createdAt = timestamp("created_at")
    val expiresAt = timestamp("expires_at")
    val usedAt = timestamp("used_at").nullable()
    val revokedAt = timestamp("revoked_at").nullable()
    val userAgent = varchar("user_agent", 512).nullable()
    val ip = varchar("ip", 64).nullable()
}
