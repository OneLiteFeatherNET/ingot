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
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Clock
import java.time.Instant

data class LocalCredential(
    val principalId: Int,
    val passwordHash: String,
    val mustChangePassword: Boolean,
    val updatedAt: Instant,
)

data class RefreshTokenRecord(
    val id: Int,
    val familyId: String,
    val principalId: Int,
    val createdAt: Instant,
    val expiresAt: Instant,
    val usedAt: Instant?,
    val revokedAt: Instant?,
)

private const val MAX_USER_AGENT_LENGTH = 512
private const val MAX_IP_LENGTH = 64

/** Reads and writes the login tables. Every method runs in its own transaction. */
class LoginStore(
    private val database: Database,
    private val clock: Clock = Clock.systemUTC(),
) {

    init {
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(LocalCredentialTable, RefreshTokenTable)
        }
    }

    fun findCredential(principalId: Int): LocalCredential? =
        transaction(database) {
            LocalCredentialTable.selectAll()
                .where { LocalCredentialTable.principalId eq principalId }
                .map { it.toCredential() }
                .firstOrNull()
        }

    /** Sets the password hash of a principal, creating its credential when it has none yet. */
    fun saveCredential(principalId: Int, passwordHash: String, mustChangePassword: Boolean) {
        transaction(database) {
            val updatedAt = Instant.now(clock)
            val exists = LocalCredentialTable.selectAll().where { LocalCredentialTable.principalId eq principalId }.any()
            if (exists) {
                LocalCredentialTable.update(where = { LocalCredentialTable.principalId eq principalId }, body = {
                    it[LocalCredentialTable.passwordHash] = passwordHash
                    it[LocalCredentialTable.mustChangePassword] = mustChangePassword
                    it[LocalCredentialTable.updatedAt] = updatedAt
                })
            } else {
                LocalCredentialTable.insert {
                    it[LocalCredentialTable.principalId] = EntityID(principalId, PrincipalTable)
                    it[LocalCredentialTable.passwordHash] = passwordHash
                    it[LocalCredentialTable.mustChangePassword] = mustChangePassword
                    it[LocalCredentialTable.updatedAt] = updatedAt
                }
            }
        }
    }

    fun insertRefreshToken(
        familyId: String,
        principalId: Int,
        tokenHash: String,
        createdAt: Instant,
        expiresAt: Instant,
        userAgent: String?,
        ip: String?,
    ) {
        transaction(database) {
            RefreshTokenTable.insert {
                it[RefreshTokenTable.familyId] = familyId
                it[RefreshTokenTable.principalId] = EntityID(principalId, PrincipalTable)
                it[RefreshTokenTable.tokenHash] = tokenHash
                it[RefreshTokenTable.createdAt] = createdAt
                it[RefreshTokenTable.expiresAt] = expiresAt
                it[RefreshTokenTable.userAgent] = userAgent?.take(MAX_USER_AGENT_LENGTH)
                it[RefreshTokenTable.ip] = ip?.take(MAX_IP_LENGTH)
            }
        }
    }

    fun findRefreshToken(tokenHash: String): RefreshTokenRecord? =
        transaction(database) {
            RefreshTokenTable.selectAll()
                .where { RefreshTokenTable.tokenHash eq tokenHash }
                .map { it.toRefreshToken() }
                .firstOrNull()
        }

    /**
     * Marks a token as spent, but only if it has not been spent or revoked yet. The condition is part of the update, so
     * of two concurrent exchanges of one token, on any instance, exactly one gets `true`.
     */
    fun claimRefreshToken(id: Int, at: Instant): Boolean =
        transaction(database) {
            RefreshTokenTable.update(
                where = { (RefreshTokenTable.id eq id) and RefreshTokenTable.usedAt.isNull() and RefreshTokenTable.revokedAt.isNull() },
                body = { it[RefreshTokenTable.usedAt] = at },
            ) > 0
        }

    /** Revokes every token of a family that is not revoked yet. Revoking twice changes nothing. */
    fun revokeFamily(familyId: String, at: Instant) {
        transaction(database) {
            RefreshTokenTable.update(
                where = { (RefreshTokenTable.familyId eq familyId) and RefreshTokenTable.revokedAt.isNull() },
                body = { it[RefreshTokenTable.revokedAt] = at },
            )
        }
    }

    private fun ResultRow.toRefreshToken() = RefreshTokenRecord(
        id = this[RefreshTokenTable.id].value,
        familyId = this[RefreshTokenTable.familyId],
        principalId = this[RefreshTokenTable.principalId].value,
        createdAt = this[RefreshTokenTable.createdAt],
        expiresAt = this[RefreshTokenTable.expiresAt],
        usedAt = this[RefreshTokenTable.usedAt],
        revokedAt = this[RefreshTokenTable.revokedAt],
    )

    private fun ResultRow.toCredential() = LocalCredential(
        principalId = this[LocalCredentialTable.principalId].value,
        passwordHash = this[LocalCredentialTable.passwordHash],
        mustChangePassword = this[LocalCredentialTable.mustChangePassword],
        updatedAt = this[LocalCredentialTable.updatedAt],
    )

}
