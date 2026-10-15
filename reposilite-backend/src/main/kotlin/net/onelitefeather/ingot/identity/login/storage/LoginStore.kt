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

import net.onelitefeather.ingot.identity.login.AttemptState
import net.onelitefeather.ingot.identity.storage.PrincipalTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
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

/** A row of the attempt table with the version it was read at, for the conditional update. */
internal data class AttemptRecord(val state: AttemptState, val version: Int)

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
private const val FIRST_ADMINISTRATOR = "first-administrator"

/** Reads and writes the login tables. Every method runs in its own transaction. */
class LoginStore(
    private val database: Database,
    private val clock: Clock = Clock.systemUTC(),
) {

    init {
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(LocalCredentialTable, RefreshTokenTable, LoginAttemptTable, LoginSetupMarkerTable)
        }
    }

    /**
     * Claims the first administrator for the whole database. Of several instances that set up the same empty database,
     * only one gets `true`; the others must not create an administrator.
     */
    internal fun claimFirstAdministrator(): Boolean =
        transaction(database) {
            LoginSetupMarkerTable.insertIgnore {
                it[LoginSetupMarkerTable.name] = FIRST_ADMINISTRATOR
                it[LoginSetupMarkerTable.claimedAt] = Instant.now(clock)
            }.insertedCount > 0
        }

    /** Gives the claim back when the administrator could not be created after all, so that a valid setup can follow. */
    internal fun releaseFirstAdministrator() {
        transaction(database) { LoginSetupMarkerTable.deleteWhere { LoginSetupMarkerTable.name eq FIRST_ADMINISTRATOR } }
    }

    internal fun isFirstAdministratorClaimed(): Boolean =
        transaction(database) {
            LoginSetupMarkerTable.selectAll().where { LoginSetupMarkerTable.name eq FIRST_ADMINISTRATOR }.any()
        }

    internal fun findAttempt(attemptKey: String): AttemptRecord? =
        transaction(database) {
            LoginAttemptTable.selectAll()
                .where { LoginAttemptTable.attemptKey eq attemptKey }
                .map { row ->
                    AttemptRecord(
                        state = AttemptState(
                            failures = row[LoginAttemptTable.failures],
                            lockouts = row[LoginAttemptTable.lockouts],
                            lastFailureAt = row[LoginAttemptTable.lastFailureAt],
                            lockedUntil = row[LoginAttemptTable.lockedUntil],
                        ),
                        version = row[LoginAttemptTable.version],
                    )
                }
                .firstOrNull()
        }

    /** Creates the row of a key that has none. `false` when another instance created it first. */
    internal fun insertAttempt(attemptKey: String, state: AttemptState): Boolean =
        transaction(database) {
            LoginAttemptTable.insertIgnore {
                it[LoginAttemptTable.attemptKey] = attemptKey
                it[LoginAttemptTable.failures] = state.failures
                it[LoginAttemptTable.lockouts] = state.lockouts
                it[LoginAttemptTable.lastFailureAt] = state.lastFailureAt ?: Instant.now(clock)
                it[LoginAttemptTable.lockedUntil] = state.lockedUntil
                it[LoginAttemptTable.version] = 1
            }.insertedCount > 0
        }

    /** Writes [state] only if the row is still at [expectedVersion]. `false` when another write came first. */
    internal fun updateAttempt(attemptKey: String, expectedVersion: Int, state: AttemptState): Boolean =
        transaction(database) {
            LoginAttemptTable.update(
                where = { (LoginAttemptTable.attemptKey eq attemptKey) and (LoginAttemptTable.version eq expectedVersion) },
                body = {
                    it[LoginAttemptTable.failures] = state.failures
                    it[LoginAttemptTable.lockouts] = state.lockouts
                    it[LoginAttemptTable.lastFailureAt] = state.lastFailureAt ?: Instant.now(clock)
                    it[LoginAttemptTable.lockedUntil] = state.lockedUntil
                    it[LoginAttemptTable.version] = expectedVersion + 1
                },
            ) > 0
        }

    internal fun deleteAttempt(attemptKey: String) {
        transaction(database) { LoginAttemptTable.deleteWhere { LoginAttemptTable.attemptKey eq attemptKey } }
    }

    /** Removes the rows whose last failure is older than [cutoff]. Such a row can no longer hold a lock. */
    internal fun deleteAttemptsBefore(cutoff: Instant): Int =
        transaction(database) { LoginAttemptTable.deleteWhere { LoginAttemptTable.lastFailureAt less cutoff } }

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

    /** Revokes every token of every family of a principal, for example after its password changed. */
    fun revokeAllOfPrincipal(principalId: Int, at: Instant) {
        transaction(database) {
            RefreshTokenTable.update(
                where = { (RefreshTokenTable.principalId eq principalId) and RefreshTokenTable.revokedAt.isNull() },
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
