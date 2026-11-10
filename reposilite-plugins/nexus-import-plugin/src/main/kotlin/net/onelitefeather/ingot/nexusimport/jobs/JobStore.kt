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

package net.onelitefeather.ingot.nexusimport.jobs

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Clock
import java.time.Duration

/** The job row of `nexus_import_job`. [owner] and [leaseUntil] are set while a worker holds the job. */
data class JobRow(
    val id: Long,
    val kind: String,
    val status: JobStatus,
    val pauseReason: PauseReason?,
    val parameters: String,
    val owner: String?,
    val leaseUntil: Long?,
    val cursor: String?,
)

/** One row of `nexus_import_asset`: the result of one file, final once written. */
data class AssetRecord(
    val repository: String,
    val path: String,
    val size: Long,
    val result: AssetResult,
)

/**
 * The job tables and the fenced lease, after `search/runs/RunStore.kt`. A worker takes a job with a conditional update of its
 * owner and lease, and every later write of that worker is conditional on its token and a live lease. A worker that lost its
 * lease therefore commits nothing, even when it wakes up late, and a job that a worker paused for read-only mode is taken by
 * no one until the operator resumes it.
 */
internal class JobStore(
    private val database: Database,
    private val clock: Clock,
    private val lease: Duration = DEFAULT_LEASE,
) {

    private val jobs = JobTable
    private val assets = AssetTable

    init {
        transaction(database) { SchemaUtils.create(jobs, assets) }
    }

    fun createJob(kind: String, parameters: String): Long = transaction(database) {
        jobs.insert {
            it[jobs.kind] = kind
            it[jobs.status] = JobStatus.QUEUED.name
            it[jobs.parameters] = parameters
            it[jobs.created] = clock.millis()
        }[jobs.id]
    }

    /** Takes the job for [token] when it is queued, or running with an expired lease. Returns false while another lease is live. */
    fun acquire(id: Long, token: String): Boolean {
        val now = clock.millis()
        return transaction(database) {
            jobs.update(where = {
                (jobs.id eq id) and (jobs.status inList listOf(JobStatus.QUEUED.name, JobStatus.RUNNING.name)) and
                    (jobs.owner.isNull() or (jobs.leaseUntil less now))
            }, body = {
                it[jobs.owner] = token
                it[jobs.status] = JobStatus.RUNNING.name
                it[jobs.leaseUntil] = now + lease.toMillis()
            }) == 1
        }
    }

    /** Commits one finished file and extends the lease, all or nothing. Returns false when [token] no longer holds a live lease. */
    fun commitAsset(id: Long, token: String, asset: AssetRecord, cursor: String?): Boolean {
        val now = clock.millis()
        return transaction(database) {
            val held = jobs.update(where = { ownedBy(id, token, now) }, body = {
                it[jobs.leaseUntil] = now + lease.toMillis()
                it[jobs.cursor] = cursor
            })
            if (held != 1) return@transaction false
            val key = (assets.jobId eq id) and (assets.repository eq asset.repository) and (assets.path eq asset.path)
            val updated = assets.update(where = { key }, body = {
                it[assets.size] = asset.size
                it[assets.result] = asset.result.name
            })
            if (updated == 0) {
                assets.insert {
                    it[assets.jobId] = id
                    it[assets.repository] = asset.repository
                    it[assets.path] = asset.path
                    it[assets.size] = asset.size
                    it[assets.result] = asset.result.name
                }
            }
            true
        }
    }

    /** Pauses the job held by [token]. The lease is released, so no worker takes it until [resume]. */
    fun pause(id: Long, token: String, reason: PauseReason): Boolean {
        val now = clock.millis()
        return transaction(database) {
            jobs.update(where = { ownedBy(id, token, now) }, body = {
                it[jobs.status] = JobStatus.PAUSED.name
                it[jobs.pauseReason] = reason.name
                it[jobs.owner] = null
                it[jobs.leaseUntil] = null
            }) == 1
        }
    }

    /** Puts a job paused for [reason] back in the queue. Returns false when it is not paused for that reason. */
    fun resume(id: Long, reason: PauseReason): Boolean = transaction(database) {
        jobs.update(where = {
            (jobs.id eq id) and (jobs.status eq JobStatus.PAUSED.name) and (jobs.pauseReason eq reason.name)
        }, body = {
            it[jobs.status] = JobStatus.QUEUED.name
            it[jobs.pauseReason] = null
        }) == 1
    }

    /** Ends the job held by [token]. Returns false when the token no longer holds it. */
    fun finish(id: Long, token: String, status: JobStatus): Boolean {
        val now = clock.millis()
        return transaction(database) {
            jobs.update(where = { ownedBy(id, token, now) }, body = {
                it[jobs.status] = status.name
                it[jobs.owner] = null
                it[jobs.leaseUntil] = null
            }) == 1
        }
    }

    fun job(id: Long): JobRow? = transaction(database) {
        jobs.selectAll().where { jobs.id eq id }.map { row ->
            JobRow(
                id = row[jobs.id],
                kind = row[jobs.kind],
                status = JobStatus.valueOf(row[jobs.status]),
                pauseReason = row[jobs.pauseReason]?.let { PauseReason.valueOf(it) },
                parameters = row[jobs.parameters],
                owner = row[jobs.owner],
                leaseUntil = row[jobs.leaseUntil],
                cursor = row[jobs.cursor],
            )
        }.firstOrNull()
    }

    fun assets(id: Long): List<AssetRecord> = transaction(database) {
        assets.selectAll().where { assets.jobId eq id }
            .orderBy(assets.repository, SortOrder.ASC)
            .orderBy(assets.path, SortOrder.ASC)
            .map { row ->
                AssetRecord(row[assets.repository], row[assets.path], row[assets.size], AssetResult.valueOf(row[assets.result]))
            }
    }

    private fun ownedBy(id: Long, token: String, now: Long) =
        (jobs.id eq id) and (jobs.owner eq token) and (jobs.status eq JobStatus.RUNNING.name) and (jobs.leaseUntil greaterEq now)

    companion object {
        val DEFAULT_LEASE: Duration = Duration.ofSeconds(60)
    }
}

internal object JobTable : Table("nexus_import_job") {
    val id = long("id").autoIncrement()
    val kind = varchar("kind", 32)
    val status = varchar("status", 32)
    val pauseReason = varchar("pause_reason", 32).nullable()
    val parameters = text("parameters")
    val owner = varchar("owner", 64).nullable()
    val leaseUntil = long("lease_until").nullable()
    val cursor = varchar("cursor", 1024).nullable()
    val created = long("created")
    val lastError = varchar("last_error", 512).nullable()

    override val primaryKey = PrimaryKey(id)
}

internal object AssetTable : Table("nexus_import_asset") {
    val jobId = long("job_id")
    val repository = varchar("repository", 255)
    val path = varchar("path", 1024)
    val size = long("size")
    val result = varchar("result", 32)

    override val primaryKey = PrimaryKey(jobId, repository, path)
}
