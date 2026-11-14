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

package com.reposilite.scheduling.infrastructure

import com.reposilite.scheduling.ScheduledJobClaims
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Clock
import java.time.Duration
import java.util.UUID

/**
 * The claims of the scheduled jobs. The primary key on [jobKey] makes the insert of a claim the decision: the instance
 * whose insert succeeds runs the job, every other instance finds the row and skips it.
 *
 * The lease columns are nullable, so rows written by a release without them stay valid. A row with no [leaseUntil] was
 * written by a claim and is never granted as a lease. [RELEASED] marks a lease that was released, or a key that was
 * never leased.
 */
internal object ScheduledJobClaimTable : Table("scheduled_job_claim") {
    val jobKey = varchar("job_key", JOB_KEY_MAX_LENGTH)
    val claimedBy = varchar("claimed_by", CLAIMANT_MAX_LENGTH)
    val claimedAt = long("claimed_at")
    val leaseOwner = varchar("lease_owner", CLAIMANT_MAX_LENGTH).nullable()
    val leaseUntil = long("lease_until").nullable()
    val doneAt = long("done_at").nullable()

    /**
     * The fencing token of the current lease. It is incremented by every grant and is never reset, so a holder that
     * kept an older epoch cannot renew or release a newer grant. A row written by a previous release gets 0 from the
     * database default, which is the epoch of a row that was never granted.
     */
    val leaseEpoch = long("lease_epoch").default(0L)

    override val primaryKey = PrimaryKey(jobKey)

    const val JOB_KEY_MAX_LENGTH = 255
    const val CLAIMANT_MAX_LENGTH = 64
    const val RELEASED = 0L
}

/**
 * Keeps the claims in the instance database, so that every instance sharing it sees the same claims. A claim older than
 * [CLAIM_RETENTION] is deleted by the instance that makes the next claim, unless its lease is still live. Its key can no
 * longer be claimed by then, since keys are dated.
 */
internal class SqlScheduledJobClaims(
    private val database: Database,
    private val clock: Clock,
    private val instanceId: String = UUID.randomUUID().toString(),
) : ScheduledJobClaims {

    private val owner = instanceId.take(ScheduledJobClaimTable.CLAIMANT_MAX_LENGTH)

    init {
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(ScheduledJobClaimTable)
        }
    }

    override fun claim(key: String): Boolean {
        checkKey(key)
        val now = clock.instant()
        return transaction(database) {
            deleteExpiredRows(now.toEpochMilli())

            val inserted = ScheduledJobClaimTable.insertIgnore {
                it[jobKey] = key
                it[claimedBy] = owner
                it[claimedAt] = now.toEpochMilli()
            }
            inserted.insertedCount == 1
        }
    }

    override fun lease(key: String, ttl: Duration): Boolean {
        checkKey(key)
        require(!ttl.isNegative && !ttl.isZero) { "lease duration must be positive" }
        val now = clock.instant().toEpochMilli()
        return transaction(database) {
            deleteExpiredRows(now)

            ScheduledJobClaimTable.insertIgnore {
                it[jobKey] = key
                it[claimedBy] = owner
                it[claimedAt] = now
                it[leaseUntil] = ScheduledJobClaimTable.RELEASED
            }
            grantExpired(key, ttl, now)
        }
    }

    override fun takeover(key: String, ttl: Duration): Boolean {
        checkKey(key)
        require(!ttl.isNegative && !ttl.isZero) { "lease duration must be positive" }
        val now = clock.instant().toEpochMilli()
        return transaction(database) {
            deleteExpiredRows(now)
            grantExpired(key, ttl, now)
        }
    }

    /** Grants the lease of a row that is not done and whose lease has expired or was released, in the current transaction. */
    private fun grantExpired(key: String, ttl: Duration, now: Long): Boolean {
        val expiry = clock.instant().plus(ttl).toEpochMilli()
        return ScheduledJobClaimTable.update({
            (ScheduledJobClaimTable.jobKey eq key) and
                ScheduledJobClaimTable.doneAt.isNull() and
                ScheduledJobClaimTable.leaseUntil.isNotNull() and
                (ScheduledJobClaimTable.leaseUntil lessEq now)
        }) {
            it[leaseOwner] = owner
            it[leaseUntil] = expiry
            it[leaseEpoch] = ScheduledJobClaimTable.leaseEpoch + 1
        } == 1
    }

    override fun acquire(key: String, ttl: Duration): ScheduledJobClaims.Lease? {
        checkKey(key)
        require(!ttl.isNegative && !ttl.isZero) { "lease duration must be positive" }
        val now = clock.instant().toEpochMilli()
        return transaction(database) {
            deleteExpiredRows(now)

            ScheduledJobClaimTable.insertIgnore {
                it[jobKey] = key
                it[claimedBy] = owner
                it[claimedAt] = now
                it[leaseUntil] = ScheduledJobClaimTable.RELEASED
            }
            if (!grantExpired(key, ttl, now)) return@transaction null
            ScheduledJobClaims.Lease(key, epochOf(key))
        }
    }

    override fun acquireExpired(key: String, ttl: Duration): ScheduledJobClaims.Lease? {
        checkKey(key)
        require(!ttl.isNegative && !ttl.isZero) { "lease duration must be positive" }
        val now = clock.instant().toEpochMilli()
        return transaction(database) {
            deleteExpiredRows(now)
            if (!grantExpired(key, ttl, now)) return@transaction null
            ScheduledJobClaims.Lease(key, epochOf(key))
        }
    }

    override fun renew(lease: ScheduledJobClaims.Lease, ttl: Duration): Boolean {
        checkKey(lease.key)
        require(!ttl.isNegative && !ttl.isZero) { "lease duration must be positive" }
        val now = clock.instant().toEpochMilli()
        val expiry = clock.instant().plus(ttl).toEpochMilli()
        return transaction(database) {
            ScheduledJobClaimTable.update({ holds(lease) and (ScheduledJobClaimTable.leaseUntil greater now) }) {
                it[leaseUntil] = expiry
            } == 1
        }
    }

    override fun release(lease: ScheduledJobClaims.Lease, done: Boolean) {
        checkKey(lease.key)
        val now = clock.instant().toEpochMilli()
        transaction(database) {
            ScheduledJobClaimTable.update({ holds(lease) }) {
                it[leaseOwner] = null
                it[leaseUntil] = ScheduledJobClaimTable.RELEASED
                if (done) it[doneAt] = now
            }
        }
    }

    /** Whether this instance holds the grant [lease] names: same key, same owner and the current epoch. */
    private fun holds(lease: ScheduledJobClaims.Lease): Op<Boolean> =
        (ScheduledJobClaimTable.jobKey eq lease.key) and
            (ScheduledJobClaimTable.leaseOwner eq owner) and
            (ScheduledJobClaimTable.leaseEpoch eq lease.epoch)

    private fun epochOf(key: String): Long =
        ScheduledJobClaimTable.selectAll()
            .where { ScheduledJobClaimTable.jobKey eq key }
            .single()[ScheduledJobClaimTable.leaseEpoch]

    override fun extend(key: String, ttl: Duration): Boolean {
        checkKey(key)
        require(!ttl.isNegative && !ttl.isZero) { "lease duration must be positive" }
        val now = clock.instant().toEpochMilli()
        val expiry = clock.instant().plus(ttl).toEpochMilli()
        return transaction(database) {
            ScheduledJobClaimTable.update({
                (ScheduledJobClaimTable.jobKey eq key) and
                    (ScheduledJobClaimTable.leaseOwner eq owner) and
                    (ScheduledJobClaimTable.leaseUntil greater now)
            }) {
                it[leaseUntil] = expiry
            } == 1
        }
    }

    override fun complete(key: String, done: Boolean) {
        checkKey(key)
        val now = clock.instant().toEpochMilli()
        transaction(database) {
            ScheduledJobClaimTable.update({
                (ScheduledJobClaimTable.jobKey eq key) and (ScheduledJobClaimTable.leaseOwner eq owner)
            }) {
                it[leaseOwner] = null
                it[leaseUntil] = ScheduledJobClaimTable.RELEASED
                if (done) it[doneAt] = now
            }
        }
    }

    private fun deleteExpiredRows(now: Long) {
        ScheduledJobClaimTable.deleteWhere {
            (claimedAt less now - CLAIM_RETENTION.toMillis()) and
                (leaseUntil.isNull() or (leaseUntil lessEq now))
        }
    }

    private fun checkKey(key: String) {
        // A truncated key could collide with another one, so an over-long key is refused rather than shortened.
        require(key.length <= ScheduledJobClaimTable.JOB_KEY_MAX_LENGTH) { "job key is longer than ${ScheduledJobClaimTable.JOB_KEY_MAX_LENGTH} characters" }
    }

    private companion object {
        val CLAIM_RETENTION: Duration = Duration.ofDays(7)
    }

}
