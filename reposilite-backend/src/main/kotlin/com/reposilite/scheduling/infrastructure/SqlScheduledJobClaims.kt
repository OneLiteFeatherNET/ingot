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
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Clock
import java.time.Duration
import java.util.UUID

/**
 * The claims of the scheduled jobs. The primary key on [jobKey] makes the insert of a claim the decision: the instance
 * whose insert succeeds runs the job, every other instance finds the row and skips it.
 */
internal object ScheduledJobClaimTable : Table("scheduled_job_claim") {
    val jobKey = varchar("job_key", JOB_KEY_MAX_LENGTH)
    val claimedBy = varchar("claimed_by", CLAIMANT_MAX_LENGTH)
    val claimedAt = long("claimed_at")

    override val primaryKey = PrimaryKey(jobKey)

    const val JOB_KEY_MAX_LENGTH = 255
    const val CLAIMANT_MAX_LENGTH = 64
}

/**
 * Keeps the claims in the instance database, so that every instance sharing it sees the same claims. A claim older than
 * [CLAIM_RETENTION] is deleted by the instance that makes the next claim. Its key can no longer be claimed by then, since
 * keys are dated.
 */
internal class SqlScheduledJobClaims(
    private val database: Database,
    private val clock: Clock,
    private val instanceId: String = UUID.randomUUID().toString(),
) : ScheduledJobClaims {

    init {
        transaction(database) {
            SchemaUtils.create(ScheduledJobClaimTable)
        }
    }

    override fun claim(key: String): Boolean {
        // A truncated key could collide with another one, so an over-long key is refused rather than shortened.
        require(key.length <= ScheduledJobClaimTable.JOB_KEY_MAX_LENGTH) { "job key is longer than ${ScheduledJobClaimTable.JOB_KEY_MAX_LENGTH} characters" }
        val now = clock.instant()
        return transaction(database) {
            ScheduledJobClaimTable.deleteWhere {
                claimedAt less now.minus(CLAIM_RETENTION).toEpochMilli()
            }

            val inserted = ScheduledJobClaimTable.insertIgnore {
                it[jobKey] = key
                it[claimedBy] = instanceId.take(ScheduledJobClaimTable.CLAIMANT_MAX_LENGTH)
                it[claimedAt] = now.toEpochMilli()
            }
            inserted.insertedCount == 1
        }
    }

    private companion object {
        val CLAIM_RETENTION: Duration = Duration.ofDays(7)
    }

}
