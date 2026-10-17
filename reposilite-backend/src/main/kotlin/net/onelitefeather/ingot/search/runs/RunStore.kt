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

package net.onelitefeather.ingot.search.runs

import com.fasterxml.jackson.databind.ObjectMapper
import net.onelitefeather.ingot.search.database.SearchDialect
import net.onelitefeather.ingot.search.database.SearchStateKey
import net.onelitefeather.ingot.search.database.SearchTables
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Clock
import java.time.Instant

/**
 * The run rows of `search_rebuild_run` and the single-run locks of `search_state` (artifact search spec, 2.6, 2.7 and 3.6).
 *
 * A lock is the row `rebuild.active` or `verify.active`. A run starts by a conditional update of that row, which is atomic on
 * every dialect, so exactly one instance wins. The lease of the lock and of the run row are extended together, in the
 * transaction that commits a batch. Every write of a run is conditional on the token of the instance that holds it, so an
 * instance that lost its lease cannot write again.
 *
 * A rebuild and a verify run exclude each other: neither starts while the lock of the other is live.
 */
internal class RunStore(
    private val database: Database,
    private val clock: Clock,
) {

    private val tables = SearchTables(SearchDialect.of(database.vendor))
    private val runs = tables.rebuildRun
    private val state = tables.state

    /** Runs [block] in one transaction. Calls of this store made inside it join that transaction. */
    fun <T> atomically(block: () -> T): T = transaction(database) { block() }

    /**
     * Starts a run if no run of either kind holds its lease, and returns its id, or null when the lock is taken. Runs of the
     * same kind that are still `RUNNING` but whose lease expired are failed: their instance is gone, and a new run
     * replaces them. [repositories] null means all repositories.
     */
    fun start(kind: RunKind, trigger: RunTrigger, repositories: List<String>?, token: String): Long? {
        val names = encode(repositories)
        val now = clock.millis()
        return transaction(database) {
            ensureLockRow(kind)
            if (isLive(other(kind), now)) return@transaction null
            if (!acquire(kind, PENDING, now)) return@transaction null

            runs.update(where = { (runs.kind eq kind.name) and (runs.status eq RunStatus.RUNNING.name) }, body = {
                it[runs.status] = RunStatus.FAILED.name
                it[runs.finishedAt] = now
                it[runs.leaseUntil] = null
                it[runs.lastError] = ABANDONED
            })
            val id = runs.insert {
                it[runs.kind] = kind.name
                it[runs.trigger] = trigger.name
                it[runs.status] = RunStatus.RUNNING.name
                it[runs.startedAt] = now
                it[runs.leaseOwner] = token
                it[runs.leaseUntil] = leaseEnd(now)
                it[runs.runStartedAt] = now
                it[runs.repositories] = names
                it[runs.processed] = 0
                it[runs.upserted] = 0
                it[runs.deleted] = 0
                it[runs.unparsed] = 0
                it[runs.skippedTooLong] = 0
                it[runs.errors] = 0
            }[runs.id]
            state.update(where = { state.key eq lockKey(kind) }, body = { it[state.value] = id.toString() })
            id
        }
    }

    /**
     * Takes over the newest run of [kind] that is `RUNNING` with an expired lease, and returns it, or returns null when there
     * is none or another instance wins the lock first. The run keeps its cursor and counters.
     */
    fun takeOver(kind: RunKind, token: String): RunSummary? {
        val now = clock.millis()
        return transaction(database) {
            val dead = runs.selectAll()
                .where {
                    (runs.kind eq kind.name) and (runs.status eq RunStatus.RUNNING.name) and
                        (runs.leaseUntil.isNull() or (runs.leaseUntil less now))
                }
                .orderBy(runs.id, SortOrder.DESC)
                .limit(1)
                .firstOrNull() ?: return@transaction null
            val id = dead[runs.id]

            ensureLockRow(kind)
            if (isLive(other(kind), now)) return@transaction null
            if (!acquire(kind, id.toString(), now)) return@transaction null

            runs.update(where = { runs.id eq id }, body = {
                it[runs.leaseOwner] = token
                it[runs.leaseUntil] = leaseEnd(now)
            })
            runs.update(where = { (runs.kind eq kind.name) and (runs.status eq RunStatus.RUNNING.name) and (runs.id neq id) }, body = {
                it[runs.status] = RunStatus.FAILED.name
                it[runs.finishedAt] = now
                it[runs.leaseUntil] = null
                it[runs.lastError] = ABANDONED
            })
            runs.selectAll().where { runs.id eq id }.map { it.toSummary() }.single()
        }
    }

    /**
     * Commits the position and counters of a run and extends its lease and the lock, all or nothing. Returns false when the
     * run is no longer `RUNNING` under [handle]'s token (cancelled, or taken over) or the lock moved on; the caller then stops.
     * Call it inside the transaction that writes the batch, and roll that transaction back on false.
     */
    fun progress(handle: RunHandle, cursorRepository: String?, cursorPath: String?, counters: RunCounters, lastError: String?): Boolean {
        val now = clock.millis()
        return transaction(database) {
            val updated = runs.update(where = {
                (runs.id eq handle.runId) and (runs.status eq RunStatus.RUNNING.name) and (runs.leaseOwner eq handle.token)
            }, body = {
                it[runs.cursorRepository] = cursorRepository
                it[runs.cursorPath] = cursorPath
                it[runs.processed] = counters.processed
                it[runs.upserted] = counters.upserted
                it[runs.deleted] = counters.deleted
                it[runs.unparsed] = counters.unparsed
                it[runs.skippedTooLong] = counters.skippedTooLong
                it[runs.errors] = counters.errors
                it[runs.lastError] = lastError?.take(LAST_ERROR_LENGTH)
                it[runs.leaseUntil] = leaseEnd(now)
            })
            if (updated != 1) return@transaction false
            state.update(where = { (state.key eq lockKey(handle.kind)) and (state.value eq handle.runId.toString()) }, body = {
                it[state.leaseUntil] = leaseEnd(now)
                it[state.updatedAt] = now
            }) == 1
        }
    }

    /** Ends the run [handle] holds with [status] and releases the lock. A run that was cancelled meanwhile stays cancelled. */
    fun finish(handle: RunHandle, status: RunStatus, counters: RunCounters, lastError: String?) {
        val now = clock.millis()
        transaction(database) {
            runs.update(where = {
                (runs.id eq handle.runId) and (runs.status eq RunStatus.RUNNING.name) and (runs.leaseOwner eq handle.token)
            }, body = {
                it[runs.status] = status.name
                it[runs.finishedAt] = now
                it[runs.leaseUntil] = null
                it[runs.processed] = counters.processed
                it[runs.upserted] = counters.upserted
                it[runs.deleted] = counters.deleted
                it[runs.unparsed] = counters.unparsed
                it[runs.skippedTooLong] = counters.skippedTooLong
                it[runs.errors] = counters.errors
                it[runs.lastError] = lastError?.take(LAST_ERROR_LENGTH)
            })
            release(handle.kind, handle.runId, now)
        }
    }

    /** Cancels a live run and releases its lock. The instance that runs it notices at its next batch. Returns false when the run is not live. */
    fun cancel(runId: Long): Boolean {
        val now = clock.millis()
        return transaction(database) {
            val run = runs.selectAll().where { runs.id eq runId }.map { it.toSummary() }.firstOrNull() ?: return@transaction false
            val cancelled = runs.update(where = { (runs.id eq runId) and (runs.status eq RunStatus.RUNNING.name) }, body = {
                it[runs.status] = RunStatus.CANCELLED.name
                it[runs.finishedAt] = now
                it[runs.leaseUntil] = null
            })
            if (cancelled == 1) release(run.kind, runId, now)
            cancelled == 1
        }
    }

    fun get(runId: Long): RunSummary? = transaction(database) {
        runs.selectAll().where { runs.id eq runId }.map { it.toSummary() }.firstOrNull()
    }

    /** The newest [limit] runs of either kind, newest first. */
    fun recent(limit: Int): List<RunSummary> = transaction(database) {
        runs.selectAll().orderBy(runs.id, SortOrder.DESC).limit(limit).map { it.toSummary() }
    }

    /** The `RUNNING` run whose lease is live, if any. */
    fun active(): RunSummary? {
        val now = clock.millis()
        return transaction(database) {
            runs.selectAll()
                .where { (runs.status eq RunStatus.RUNNING.name) and (runs.leaseUntil greaterEq now) }
                .orderBy(runs.id, SortOrder.DESC)
                .limit(1)
                .map { it.toSummary() }
                .firstOrNull()
        }
    }

    fun status(): IndexRunStatus {
        val lastFull = transaction(database) {
            state.selectAll().where { state.key eq SearchStateKey.LAST_FULL_REBUILD }.map { it[state.value] }.firstOrNull()
        }
        return IndexRunStatus(
            activeRun = active(),
            runs = recent(RunPolicy.HISTORY),
            lastFullRebuild = lastFull?.toLongOrNull()?.let { Instant.ofEpochMilli(it) },
        )
    }

    /** Whether the lock of [kind] is held by a live lease at the current instant. */
    fun isLive(kind: RunKind): Boolean = transaction(database) { isLive(kind, clock.millis()) }

    private fun ensureLockRow(kind: RunKind) {
        state.insertIgnore {
            it[state.key] = lockKey(kind)
            it[state.updatedAt] = clock.millis()
        }
    }

    private fun isLive(kind: RunKind, now: Long): Boolean =
        state.selectAll()
            .where { (state.key eq lockKey(kind)) and (state.leaseUntil greaterEq now) }
            .any()

    /** The conditional update that decides who runs: it only touches a lock that is free or whose lease ended. */
    private fun acquire(kind: RunKind, value: String, now: Long): Boolean =
        state.update(where = {
            (state.key eq lockKey(kind)) and (state.leaseUntil.isNull() or (state.leaseUntil less now))
        }, body = {
            it[state.value] = value
            it[state.leaseUntil] = leaseEnd(now)
            it[state.updatedAt] = now
        }) == 1

    private fun release(kind: RunKind, runId: Long, now: Long) {
        state.update(where = { (state.key eq lockKey(kind)) and (state.value eq runId.toString()) }, body = {
            it[state.value] = null
            it[state.leaseUntil] = null
            it[state.updatedAt] = now
        })
    }

    private fun leaseEnd(now: Long): Long = now + RunPolicy.LEASE.toMillis()

    private fun other(kind: RunKind): RunKind = if (kind == RunKind.REBUILD) RunKind.VERIFY else RunKind.REBUILD

    private fun lockKey(kind: RunKind): String = when (kind) {
        RunKind.REBUILD -> SearchStateKey.REBUILD_ACTIVE
        RunKind.VERIFY -> SearchStateKey.VERIFY_ACTIVE
    }

    private fun encode(repositories: List<String>?): String {
        if (repositories == null) return ALL
        val json = MAPPER.writeValueAsString(repositories)
        require(json.length <= REPOSITORIES_LIMIT) { "the repository list is too long for a run" }
        return json
    }

    private fun decode(value: String): List<String>? =
        if (value == ALL) null else MAPPER.readValue(value, Array<String>::class.java).toList()

    private fun ResultRow.toSummary() = RunSummary(
        id = this[runs.id],
        kind = RunKind.valueOf(this[runs.kind]),
        trigger = RunTrigger.valueOf(this[runs.trigger]),
        status = RunStatus.valueOf(this[runs.status]),
        startedAt = Instant.ofEpochMilli(this[runs.startedAt]),
        finishedAt = this[runs.finishedAt]?.let { Instant.ofEpochMilli(it) },
        leaseOwner = this[runs.leaseOwner],
        leaseUntil = this[runs.leaseUntil]?.let { Instant.ofEpochMilli(it) },
        runStartedAt = Instant.ofEpochMilli(this[runs.runStartedAt]),
        repositories = decode(this[runs.repositories]),
        cursorRepository = this[runs.cursorRepository],
        cursorPath = this[runs.cursorPath],
        counters = RunCounters(
            processed = this[runs.processed],
            upserted = this[runs.upserted],
            deleted = this[runs.deleted],
            unparsed = this[runs.unparsed],
            skippedTooLong = this[runs.skippedTooLong],
            errors = this[runs.errors],
        ),
        lastError = this[runs.lastError],
    )

    private companion object {
        const val ALL = "*"
        const val PENDING = "pending"
        const val ABANDONED = "abandoned: the lease expired and a newer run replaced it"
        const val LAST_ERROR_LENGTH = 512
        const val REPOSITORIES_LIMIT = 2048
        val MAPPER = ObjectMapper()
    }

}
