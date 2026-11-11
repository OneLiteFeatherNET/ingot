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

import net.onelitefeather.ingot.nexusimport.plan.LossRow
import net.onelitefeather.ingot.nexusimport.plan.LossStatus
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
import org.jetbrains.exposed.v1.jdbc.deleteWhere
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
    val pauseRequested: Boolean,
    val cancelRequested: Boolean,
    val lastError: String?,
    val summary: String?,
)

/** One row of `nexus_import_asset`: the result of one file, final once written. [detail] names the checksums of a conflict. */
data class AssetRecord(
    val repository: String,
    val path: String,
    val size: Long,
    val result: AssetResult,
    val detail: String? = null,
)

/** One repository of a job: its Nexus name, the Ingot name its files go to, its type and its state. */
data class RepositoryRow(
    val nexusName: String,
    val targetName: String,
    val type: String,
    val status: RepositoryStatus,
)

/**
 * The job tables and the fenced lease, after `search/runs/RunStore.kt`. A worker takes a job with a conditional update of its
 * owner and lease, and every later write of that worker is conditional on its token and a live lease. A worker that lost its
 * lease therefore commits nothing, even when it wakes up late, and a job that a worker paused for read-only mode is taken by
 * no one until the operator resumes it.
 *
 * Operator requests (pause, cancel) on a running job are flags that the worker reads before each file, so a request never
 * writes over the worker's own commit. A request on a job nobody runs takes effect at once.
 */
internal class JobStore(
    private val database: Database,
    private val clock: Clock,
    val lease: Duration = DEFAULT_LEASE,
) {

    private val jobs = JobTable
    private val assets = AssetTable
    private val repositories = RepositoryTable
    private val reports = ReportTable

    init {
        transaction(database) { SchemaUtils.create(jobs, assets, repositories, reports) }
    }

    fun createJob(kind: String, parameters: String): Long = transaction(database) {
        jobs.insert {
            it[jobs.kind] = kind
            it[jobs.status] = JobStatus.QUEUED.name
            it[jobs.parameters] = parameters
            it[jobs.created] = clock.millis()
        }[jobs.id]
    }

    /**
     * Takes the job for [token] when it is queued, or running with an expired lease. A holder whose lease is still live takes
     * it again, which the planning and the copy step of one run both need. Returns false while another lease is live.
     */
    fun acquire(id: Long, token: String): Boolean {
        val now = clock.millis()
        return transaction(database) {
            jobs.update(where = {
                (jobs.id eq id) and (jobs.status inList listOf(JobStatus.QUEUED.name, JobStatus.RUNNING.name)) and
                    (jobs.owner.isNull() or (jobs.leaseUntil less now) or (jobs.owner eq token))
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
                it[assets.detail] = asset.detail
            })
            if (updated == 0) {
                assets.insert {
                    it[assets.jobId] = id
                    it[assets.repository] = asset.repository
                    it[assets.path] = asset.path
                    it[assets.size] = asset.size
                    it[assets.result] = asset.result.name
                    it[assets.detail] = asset.detail
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
                it[jobs.pauseRequested] = false
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
            it[jobs.pauseRequested] = false
        }) == 1
    }

    /** Operator pause. A queued job pauses at once; a running job pauses after the file in flight. */
    fun requestPause(id: Long): Boolean = transaction(database) {
        val queued = jobs.update(where = {
            (jobs.id eq id) and (jobs.status eq JobStatus.QUEUED.name)
        }, body = {
            it[jobs.status] = JobStatus.PAUSED.name
            it[jobs.pauseReason] = PauseReason.OPERATOR.name
        }) == 1
        queued || jobs.update(where = { (jobs.id eq id) and (jobs.status eq JobStatus.RUNNING.name) }, body = {
            it[jobs.pauseRequested] = true
        }) == 1
    }

    /** Operator cancel. A queued or paused job is cancelled at once; a running job after the file in flight. */
    fun requestCancel(id: Long): Boolean = cancelIdle(id) || transaction(database) {
        jobs.update(where = { (jobs.id eq id) and (jobs.status eq JobStatus.RUNNING.name) }, body = {
            it[jobs.cancelRequested] = true
        }) == 1
    }

    /** Cancels a job that no worker holds. Returns false when the job is running or finished. */
    fun cancelIdle(id: Long): Boolean = transaction(database) {
        jobs.update(where = {
            (jobs.id eq id) and (jobs.status inList listOf(JobStatus.QUEUED.name, JobStatus.PAUSED.name))
        }, body = {
            it[jobs.status] = JobStatus.CANCELLED.name
            it[jobs.pauseReason] = null
            it[jobs.owner] = null
            it[jobs.leaseUntil] = null
        }) == 1
    }

    /**
     * Pauses a running job whose lease ran out and whose password this replica does not hold. The job waits for the
     * operator, because the password cannot be read from storage.
     */
    fun markCredentialsNeeded(id: Long): Boolean {
        val now = clock.millis()
        return transaction(database) {
            jobs.update(where = {
                (jobs.id eq id) and (jobs.status eq JobStatus.RUNNING.name) and (jobs.leaseUntil less now)
            }, body = {
                it[jobs.status] = JobStatus.PAUSED.name
                it[jobs.pauseReason] = PauseReason.CREDENTIALS_NEEDED.name
                it[jobs.owner] = null
                it[jobs.leaseUntil] = null
            }) == 1
        }
    }

    /** Ends the job held by [token]. Returns false when the token no longer holds it. */
    fun finish(id: Long, token: String, status: JobStatus, error: String? = null): Boolean {
        val now = clock.millis()
        return transaction(database) {
            jobs.update(where = { ownedBy(id, token, now) }, body = {
                it[jobs.status] = status.name
                it[jobs.owner] = null
                it[jobs.leaseUntil] = null
                it[jobs.pauseRequested] = false
                it[jobs.lastError] = error
            }) == 1
        }
    }

    /** Stores the summary of a dry run, as JSON, under the lease of [token]. */
    fun commitSummary(id: Long, token: String, summary: String): Boolean {
        val now = clock.millis()
        return transaction(database) {
            jobs.update(where = { ownedBy(id, token, now) }, body = { it[jobs.summary] = summary }) == 1
        }
    }

    /** Stores the Ingot names of the repositories of a job. Rows that exist keep their state, so a planning step may run again. */
    fun planRepositories(id: Long, rows: List<RepositoryRow>) = transaction(database) {
        rows.forEach { row ->
            val exists = repositories.selectAll().where {
                (repositories.jobId eq id) and (repositories.nexusName eq row.nexusName)
            }.any()
            if (!exists) {
                repositories.insert {
                    it[repositories.jobId] = id
                    it[repositories.nexusName] = row.nexusName
                    it[repositories.targetName] = row.targetName
                    it[repositories.type] = row.type
                    it[repositories.status] = row.status.name
                }
            }
        }
    }

    /** Changes the state of one repository. Only the holder of a live lease may change it. */
    fun setRepositoryStatus(id: Long, token: String, nexusName: String, status: RepositoryStatus): Boolean {
        val now = clock.millis()
        return transaction(database) {
            if (jobs.selectAll().where { ownedBy(id, token, now) }.none()) return@transaction false
            repositories.update(where = {
                (repositories.jobId eq id) and (repositories.nexusName eq nexusName)
            }, body = { it[repositories.status] = status.name }) == 1
        }
    }

    /**
     * Records the final state of a repository once its job is complete. No lease is needed, because a completed job has no
     * worker left to race with; the caller must not use it while the job still runs.
     */
    fun settleRepository(id: Long, nexusName: String, status: RepositoryStatus) = transaction(database) {
        repositories.update(where = {
            (repositories.jobId eq id) and (repositories.nexusName eq nexusName)
        }, body = { it[repositories.status] = status.name })
    }

    fun repositories(id: Long): List<RepositoryRow> = transaction(database) {
        repositories.selectAll().where { repositories.jobId eq id }
            .orderBy(repositories.nexusName, SortOrder.ASC)
            .map { row ->
                RepositoryRow(
                    nexusName = row[repositories.nexusName],
                    targetName = row[repositories.targetName],
                    type = row[repositories.type],
                    status = RepositoryStatus.valueOf(row[repositories.status]),
                )
            }
    }

    /** Replaces the report of a job. A dry run writes its report once; a definition step appends its rows through [appendReport]. */
    fun replaceReport(id: Long, rows: List<LossRow>) = transaction(database) {
        reports.deleteWhere { reports.jobId eq id }
        appendReport(id, rows)
    }

    fun appendReport(id: Long, rows: List<LossRow>) = transaction(database) {
        rows.forEach { row ->
            reports.insert {
                it[reports.jobId] = id
                it[reports.objectType] = row.objectType
                it[reports.nexusId] = row.nexusId
                it[reports.target] = row.target
                it[reports.status] = row.status.name
                it[reports.reason] = row.reason
                it[reports.consequence] = row.consequence
                it[reports.action] = row.action
            }
        }
    }

    fun report(id: Long): List<LossRow> = transaction(database) {
        reports.selectAll().where { reports.jobId eq id }
            .orderBy(reports.seq, SortOrder.ASC)
            .map { row ->
                LossRow(
                    objectType = row[reports.objectType],
                    nexusId = row[reports.nexusId],
                    target = row[reports.target],
                    status = LossStatus.valueOf(row[reports.status]),
                    reason = row[reports.reason],
                    consequence = row[reports.consequence],
                    action = row[reports.action],
                )
            }
    }

    fun job(id: Long): JobRow? = transaction(database) {
        jobs.selectAll().where { jobs.id eq id }.map(::toJobRow).firstOrNull()
    }

    fun jobs(): List<JobRow> = transaction(database) {
        jobs.selectAll().orderBy(jobs.id, SortOrder.DESC).map(::toJobRow)
    }

    /** Jobs a worker may take: queued ones, and running ones whose lease has run out. */
    fun runnable(): List<JobRow> {
        val now = clock.millis()
        return transaction(database) {
            jobs.selectAll().where {
                (jobs.status eq JobStatus.QUEUED.name) or
                    ((jobs.status eq JobStatus.RUNNING.name) and (jobs.leaseUntil less now))
            }.orderBy(jobs.id, SortOrder.ASC).map(::toJobRow)
        }
    }

    fun assets(id: Long): List<AssetRecord> = transaction(database) {
        assets.selectAll().where { assets.jobId eq id }
            .orderBy(assets.repository, SortOrder.ASC)
            .orderBy(assets.path, SortOrder.ASC)
            .map(::toAssetRecord)
    }

    fun conflicts(id: Long): List<AssetRecord> = transaction(database) {
        assets.selectAll().where { (assets.jobId eq id) and (assets.result eq AssetResult.CONFLICT.name) }
            .orderBy(assets.repository, SortOrder.ASC)
            .orderBy(assets.path, SortOrder.ASC)
            .map(::toAssetRecord)
    }

    /** Removes a finished job with its rows. A job that is still queued, paused or running is kept. */
    fun deleteJob(id: Long): Boolean = transaction(database) {
        val finished = jobs.selectAll().where {
            (jobs.id eq id) and (jobs.status inList listOf(JobStatus.COMPLETED.name, JobStatus.FAILED.name, JobStatus.CANCELLED.name))
        }.any()
        if (!finished) return@transaction false
        assets.deleteWhere { assets.jobId eq id }
        repositories.deleteWhere { repositories.jobId eq id }
        reports.deleteWhere { reports.jobId eq id }
        jobs.deleteWhere { jobs.id eq id }
        true
    }

    private fun toJobRow(row: org.jetbrains.exposed.v1.core.ResultRow) = JobRow(
        id = row[jobs.id],
        kind = row[jobs.kind],
        status = JobStatus.valueOf(row[jobs.status]),
        pauseReason = row[jobs.pauseReason]?.let { PauseReason.valueOf(it) },
        parameters = row[jobs.parameters],
        owner = row[jobs.owner],
        leaseUntil = row[jobs.leaseUntil],
        cursor = row[jobs.cursor],
        pauseRequested = row[jobs.pauseRequested],
        cancelRequested = row[jobs.cancelRequested],
        lastError = row[jobs.lastError],
        summary = row[jobs.summary],
    )

    private fun toAssetRecord(row: org.jetbrains.exposed.v1.core.ResultRow) = AssetRecord(
        repository = row[assets.repository],
        path = row[assets.path],
        size = row[assets.size],
        result = AssetResult.valueOf(row[assets.result]),
        detail = row[assets.detail],
    )

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
    val pauseRequested = bool("pause_requested").default(false)
    val cancelRequested = bool("cancel_requested").default(false)
    val created = long("created")
    val lastError = varchar("last_error", 512).nullable()
    val summary = text("summary").nullable()

    override val primaryKey = PrimaryKey(id)
}

internal object AssetTable : Table("nexus_import_asset") {
    val jobId = long("job_id")
    val repository = varchar("repository", 255)
    val path = varchar("path", 1024)
    val size = long("size")
    val result = varchar("result", 32)
    val detail = varchar("detail", 512).nullable()

    override val primaryKey = PrimaryKey(jobId, repository, path)
}

internal object RepositoryTable : Table("nexus_import_repository") {
    val jobId = long("job_id")
    val nexusName = varchar("nexus_name", 255)
    val targetName = varchar("target_name", 255)
    val type = varchar("type", 32)
    val status = varchar("status", 32)

    override val primaryKey = PrimaryKey(jobId, nexusName)
}

internal object ReportTable : Table("nexus_import_report") {
    val seq = long("seq").autoIncrement()
    val jobId = long("job_id")
    val objectType = varchar("object_type", 32)
    val nexusId = varchar("nexus_id", 255)
    val target = varchar("target", 255).nullable()
    val status = varchar("status", 32)
    val reason = text("reason")
    val consequence = text("consequence")
    val action = text("action")

    override val primaryKey = PrimaryKey(seq)
}
