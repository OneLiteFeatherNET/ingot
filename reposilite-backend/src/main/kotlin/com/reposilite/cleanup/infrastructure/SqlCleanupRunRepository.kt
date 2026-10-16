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

package com.reposilite.cleanup.infrastructure

import com.fasterxml.jackson.module.kotlin.readValue
import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.cleanup.CleanupRun
import com.reposilite.cleanup.CleanupRunRepository
import com.reposilite.cleanup.CleanupRunStatus
import com.reposilite.cleanup.CleanupTrigger
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Instant

private const val POLICY_ID_LENGTH = 64

internal object CleanupRunTable : Table("cleanup_run") {
    val id = varchar("run_id", 36)
    val policyId = varchar("policy_id", POLICY_ID_LENGTH).index("idx_cleanup_run_policy_id")
    val trigger = varchar("run_trigger", 16)
    val status = varchar("status", 16)
    val startedAt = long("started_at")
    val finishedAt = long("finished_at")
    val deletedCount = integer("deleted_count")
    val deletedBytes = long("deleted_bytes")
    val failures = text("failures")

    override val primaryKey = PrimaryKey(id)
}

/**
 * Keeps the run history in the instance database, next to the statistics.
 */
internal class SqlCleanupRunRepository(private val database: Database) : CleanupRunRepository {

    init {
        transaction(database) {
            SchemaUtils.create(CleanupRunTable)
        }
    }

    override fun insert(run: CleanupRun) {
        transaction(database) {
            CleanupRunTable.insert {
                it[id] = run.id
                it[policyId] = run.policyId
                it[trigger] = run.trigger.name
                it[status] = run.status.name
                it[startedAt] = run.startedAt.toEpochMilli()
                it[finishedAt] = run.finishedAt.toEpochMilli()
                it[deletedCount] = run.deletedCount
                it[deletedBytes] = run.deletedBytes
                it[failures] = DEFAULT_OBJECT_MAPPER.writeValueAsString(run.failures)
            }
        }
    }

    override fun find(policyId: String?): List<CleanupRun> =
        transaction(database) {
            CleanupRunTable.selectAll()
                .where { policyId?.let { CleanupRunTable.policyId eq it } ?: Op.TRUE }
                .orderBy(CleanupRunTable.startedAt, SortOrder.DESC)
                .map { it.toRun() }
        }

    override fun keepNewest(policyId: String, count: Int) {
        transaction(database) {
            val stale = CleanupRunTable.selectAll()
                .where { CleanupRunTable.policyId eq policyId }
                .orderBy(CleanupRunTable.startedAt, SortOrder.DESC)
                .map { it[CleanupRunTable.id] }
                .drop(count)

            if (stale.isNotEmpty()) {
                CleanupRunTable.deleteWhere { CleanupRunTable.id inList stale }
            }
        }
    }

    private fun ResultRow.toRun(): CleanupRun =
        CleanupRun(
            id = this[CleanupRunTable.id],
            policyId = this[CleanupRunTable.policyId],
            trigger = CleanupTrigger.valueOf(this[CleanupRunTable.trigger]),
            startedAt = Instant.ofEpochMilli(this[CleanupRunTable.startedAt]),
            finishedAt = Instant.ofEpochMilli(this[CleanupRunTable.finishedAt]),
            status = CleanupRunStatus.valueOf(this[CleanupRunTable.status]),
            deletedCount = this[CleanupRunTable.deletedCount],
            deletedBytes = this[CleanupRunTable.deletedBytes],
            failures = DEFAULT_OBJECT_MAPPER.readValue(this[CleanupRunTable.failures]),
        )

}
