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

import java.time.Clock
import java.time.Instant
import net.onelitefeather.ingot.identity.storage.PrincipalTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

/** A second factor as stored. [secret] is the sealed seed of an authenticator. [confirmedAt] is null while enrolling. */
data class FactorRow(
    val id: Int,
    val principalId: Int,
    val kind: String,
    val name: String,
    val factorKey: String,
    val secret: String?,
    val confirmedAt: Instant?,
    val lastUsedStep: Long?,
    val createdAt: Instant,
    val lastUsedAt: Instant?,
)

/** A sign-in challenge as stored. Only its hash is kept. */
data class ChallengeRow(
    val idHash: String,
    val principalId: Int,
    val expiresAt: Instant,
    val usedAt: Instant?,
)

/**
 * Reads and writes the second factor tables. Every conditional write is a single update whose condition is part of the
 * statement, so that of several instances on one database only one of them wins a code, a recovery code or a challenge.
 * Every method runs in its own transaction.
 */
class SecondFactorStore(
    private val database: Database,
    private val clock: Clock = Clock.systemUTC(),
) {

    init {
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(MfaFactorTable, MfaRecoveryCodeTable, AuthChallengeTable)
        }
    }

    /**
     * Turns a pending enrolment into a factor and, when [recoveryHashes] is given, stores the first set of recovery codes, all
     * in one transaction. The update is guarded by the pending row, so of two confirmations of one enrolment only one writes.
     */
    fun confirmWithRecoveryCodes(id: Int, at: Instant, step: Long, recoveryHashes: List<String>?): Boolean =
        transaction(database) {
            val confirmed = MfaFactorTable.update(
                where = { (MfaFactorTable.id eq id) and MfaFactorTable.confirmedAt.isNull() },
                body = {
                    it[MfaFactorTable.confirmedAt] = at
                    it[MfaFactorTable.lastUsedStep] = step
                    it[MfaFactorTable.lastUsedAt] = at
                },
            ) > 0
            if (confirmed && recoveryHashes != null) {
                val principalId = MfaFactorTable.selectAll().where { MfaFactorTable.id eq id }.single()[MfaFactorTable.principalId].value
                MfaRecoveryCodeTable.deleteWhere { MfaRecoveryCodeTable.principalId eq principalId }
                recoveryHashes.forEach { hash ->
                    MfaRecoveryCodeTable.insert {
                        it[MfaRecoveryCodeTable.principalId] = EntityID(principalId, PrincipalTable)
                        it[MfaRecoveryCodeTable.codeHash] = hash
                        it[MfaRecoveryCodeTable.createdAt] = at
                    }
                }
            }
            confirmed
        }

    fun insertPendingTotp(principalId: Int, factorKey: String, name: String, secret: String, at: Instant) {
        transaction(database) {
            MfaFactorTable.insert {
                it[MfaFactorTable.principalId] = EntityID(principalId, PrincipalTable)
                it[MfaFactorTable.kind] = KIND_TOTP
                it[MfaFactorTable.name] = name
                it[MfaFactorTable.factorKey] = factorKey
                it[MfaFactorTable.secret] = secret
                it[MfaFactorTable.createdAt] = at
            }
        }
    }

    /** The enrolment of a principal that waits for its first code, if there is one. */
    fun findPendingTotp(principalId: Int): FactorRow? =
        transaction(database) {
            MfaFactorTable.selectAll()
                .where { (MfaFactorTable.principalId eq principalId) and (MfaFactorTable.kind eq KIND_TOTP) and MfaFactorTable.confirmedAt.isNull() }
                .map { it.toFactor() }
                .firstOrNull()
        }

    fun deletePendingTotp(principalId: Int) {
        transaction(database) {
            MfaFactorTable.deleteWhere {
                (MfaFactorTable.principalId eq principalId) and (MfaFactorTable.kind eq KIND_TOTP) and MfaFactorTable.confirmedAt.isNull()
            }
        }
    }

    /** Turns an enrolment into a factor. The step of the confirming code is recorded, so that the code cannot be used again. */
    fun confirm(id: Int, at: Instant, step: Long) {
        transaction(database) {
            MfaFactorTable.update(where = { MfaFactorTable.id eq id }, body = {
                it[MfaFactorTable.confirmedAt] = at
                it[MfaFactorTable.lastUsedStep] = step
                it[MfaFactorTable.lastUsedAt] = at
            })
        }
    }

    /** The confirmed factors of a principal, oldest first. */
    fun findFactors(principalId: Int): List<FactorRow> =
        transaction(database) {
            MfaFactorTable.selectAll()
                .where { (MfaFactorTable.principalId eq principalId) and MfaFactorTable.confirmedAt.isNotNull() }
                .orderBy(MfaFactorTable.id, SortOrder.ASC)
                .map { it.toFactor() }
        }

    /** A confirmed factor of a principal, `null` when the id names none of its factors. */
    fun findFactor(principalId: Int, id: Int): FactorRow? =
        findFactors(principalId).firstOrNull { it.id == id }

    /**
     * Records the step of an accepted authenticator code. Only a step later than the last accepted one is written, so of two
     * attempts with the same code, exactly one gets `true`.
     */
    fun claimTotpStep(id: Int, step: Long, at: Instant): Boolean =
        transaction(database) {
            MfaFactorTable.update(
                where = {
                    (MfaFactorTable.id eq id) and (MfaFactorTable.lastUsedStep.isNull() or (MfaFactorTable.lastUsedStep less step))
                },
                body = {
                    it[MfaFactorTable.lastUsedStep] = step
                    it[MfaFactorTable.lastUsedAt] = at
                },
            ) > 0
        }

    /** Removes one factor of a principal. `true` when it existed. */
    fun deleteFactor(principalId: Int, id: Int): Boolean =
        transaction(database) {
            MfaFactorTable.deleteWhere { (MfaFactorTable.principalId eq principalId) and (MfaFactorTable.id eq id) } > 0
        }

    /** Removes every factor of a principal, pending enrolments included. Returns how many confirmed factors were removed. */
    fun deleteAllFactors(principalId: Int): Int =
        transaction(database) {
            val confirmed = MfaFactorTable.selectAll()
                .where { (MfaFactorTable.principalId eq principalId) and MfaFactorTable.confirmedAt.isNotNull() }
                .count()
                .toInt()
            MfaFactorTable.deleteWhere { MfaFactorTable.principalId eq principalId }
            confirmed
        }

    fun hasRecoveryCodes(principalId: Int): Boolean =
        transaction(database) {
            MfaRecoveryCodeTable.selectAll().where { MfaRecoveryCodeTable.principalId eq principalId }.any()
        }

    /** Replaces the whole set of recovery codes of a principal. Codes of an earlier set stop working at once. */
    fun replaceRecoveryCodes(principalId: Int, hashes: List<String>, at: Instant) {
        transaction(database) {
            MfaRecoveryCodeTable.deleteWhere { MfaRecoveryCodeTable.principalId eq principalId }
            hashes.forEach { hash ->
                MfaRecoveryCodeTable.insert {
                    it[MfaRecoveryCodeTable.principalId] = EntityID(principalId, PrincipalTable)
                    it[MfaRecoveryCodeTable.codeHash] = hash
                    it[MfaRecoveryCodeTable.createdAt] = at
                }
            }
        }
    }

    fun deleteRecoveryCodes(principalId: Int) {
        transaction(database) { MfaRecoveryCodeTable.deleteWhere { MfaRecoveryCodeTable.principalId eq principalId } }
    }

    /** Spends a recovery code of a principal. `true` only for the first use of an unused code of that principal. */
    fun claimRecoveryCode(principalId: Int, codeHash: String, at: Instant): Boolean =
        transaction(database) {
            MfaRecoveryCodeTable.update(
                where = {
                    (MfaRecoveryCodeTable.principalId eq principalId) and (MfaRecoveryCodeTable.codeHash eq codeHash) and MfaRecoveryCodeTable.usedAt.isNull()
                },
                body = { it[MfaRecoveryCodeTable.usedAt] = at },
            ) > 0
        }

    fun insertChallenge(idHash: String, principalId: Int, createdAt: Instant, expiresAt: Instant) {
        transaction(database) {
            AuthChallengeTable.insert {
                it[AuthChallengeTable.idHash] = idHash
                it[AuthChallengeTable.kind] = KIND_MFA_LOGIN
                it[AuthChallengeTable.principalId] = EntityID(principalId, PrincipalTable)
                it[AuthChallengeTable.createdAt] = createdAt
                it[AuthChallengeTable.expiresAt] = expiresAt
            }
            // Challenges that can no longer be spent are removed, so the table does not grow with abandoned sign-ins.
            AuthChallengeTable.deleteWhere { AuthChallengeTable.expiresAt less createdAt }
        }
    }

    fun findChallenge(idHash: String): ChallengeRow? =
        transaction(database) {
            AuthChallengeTable.selectAll()
                .where { AuthChallengeTable.idHash eq idHash }
                .map { row ->
                    ChallengeRow(
                        idHash = row[AuthChallengeTable.idHash],
                        principalId = row[AuthChallengeTable.principalId].value,
                        expiresAt = row[AuthChallengeTable.expiresAt],
                        usedAt = row[AuthChallengeTable.usedAt],
                    )
                }
                .firstOrNull()
        }

    /** Spends a challenge that is unused and not yet expired at [at]. Of several attempts, exactly one gets `true`. */
    fun claimChallenge(idHash: String, at: Instant): Boolean =
        transaction(database) {
            AuthChallengeTable.update(
                where = {
                    (AuthChallengeTable.idHash eq idHash) and AuthChallengeTable.usedAt.isNull() and AuthChallengeTable.expiresAt.greater(at)
                },
                body = { it[AuthChallengeTable.usedAt] = at },
            ) > 0
        }

    private fun ResultRow.toFactor() = FactorRow(
        id = this[MfaFactorTable.id].value,
        principalId = this[MfaFactorTable.principalId].value,
        kind = this[MfaFactorTable.kind],
        name = this[MfaFactorTable.name],
        factorKey = this[MfaFactorTable.factorKey],
        secret = this[MfaFactorTable.secret],
        confirmedAt = this[MfaFactorTable.confirmedAt],
        lastUsedStep = this[MfaFactorTable.lastUsedStep],
        createdAt = this[MfaFactorTable.createdAt],
        lastUsedAt = this[MfaFactorTable.lastUsedAt],
    )

    companion object {
        const val KIND_TOTP = "TOTP"
        const val KIND_MFA_LOGIN = "MFA_LOGIN"
    }

}
