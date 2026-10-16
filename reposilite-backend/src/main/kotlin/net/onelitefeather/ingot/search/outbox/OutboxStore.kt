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

package net.onelitefeather.ingot.search.outbox

import net.onelitefeather.ingot.search.SearchColumn
import net.onelitefeather.ingot.search.database.SearchDialect
import net.onelitefeather.ingot.search.database.SearchTables
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Clock
import java.time.Instant

/**
 * The durable queue of index work (artifact search spec, 3.2 to 3.4). Writers insert; workers claim with a lease,
 * finish with a delete and fail with a backoff. Every transition is a short transaction on the shared database.
 *
 * A claim takes an entry only through a conditional update, so two instances that select the same candidates cannot
 * both win it. Leases are an efficiency measure: a takeover after an expired lease repeats idempotent work, it never
 * loses an entry.
 */
internal class OutboxStore(
    private val database: Database,
    private val clock: Clock,
) {

    private val tables = SearchTables(SearchDialect.of(database.vendor))
    private val outbox = tables.outbox

    /** Adds one entry. The paths are checked against the column limits here, so an entry that could not be stored is refused. */
    fun enqueue(repository: String, path: String, op: OutboxOp) {
        require(repository.length <= SearchColumn.REPOSITORY.limit) { "repository is longer than ${SearchColumn.REPOSITORY.limit} characters" }
        require(path.length <= SearchColumn.PATH.limit) { "path is longer than ${SearchColumn.PATH.limit} characters" }
        val now = clock.millis()
        transaction(database) {
            outbox.insert {
                it[outbox.repository] = repository
                it[outbox.path] = path
                it[outbox.op] = op.name
                it[outbox.createdAt] = now
                it[outbox.attempts] = 0
            }
        }
    }

    /**
     * Claims up to [limit] entries for [owner]. Candidates are the claimable entries in id order. Each one is then taken
     * with a conditional update, and only the updates that touch a row count. Every claim counts as an attempt.
     */
    fun claim(owner: String, limit: Int): List<OutboxEntry> {
        val now = clock.instant()
        val candidates = transaction(database) {
            outbox.selectAll()
                .where { (outbox.attempts less OutboxPolicy.MAX_ATTEMPTS) and (outbox.leaseUntil.isNull() or (outbox.leaseUntil less now.toEpochMilli())) }
                .orderBy(outbox.id)
                .limit(limit)
                .map { it[outbox.id] }
        }
        if (candidates.isEmpty()) return emptyList()

        val claimed = transaction(database) {
            candidates.filter { id ->
                outbox.update(where = {
                    (outbox.id eq id) and (outbox.leaseUntil.isNull() or (outbox.leaseUntil less now.toEpochMilli()))
                }, body = {
                    it[outbox.leaseOwner] = owner
                    it[outbox.leaseUntil] = OutboxPolicy.leaseUntil(now).toEpochMilli()
                    it[outbox.attempts] = outbox.attempts + 1
                }) == 1
            }
        }
        if (claimed.isEmpty()) return emptyList()

        return transaction(database) {
            outbox.selectAll()
                .where { (outbox.id inList claimed) and (outbox.leaseOwner eq owner) }
                .orderBy(outbox.id)
                .map { it.toEntry() }
        }
    }

    /**
     * Extends the leases of [ids] that [owner] still holds. Returns the ids that are still held afterwards; the others
     * were taken over and must be dropped from the batch.
     */
    fun extend(owner: String, ids: Collection<Long>): Set<Long> {
        if (ids.isEmpty()) return emptySet()
        val now = clock.instant()
        return transaction(database) {
            outbox.update(where = { (outbox.id inList ids) and (outbox.leaseOwner eq owner) }, body = {
                it[outbox.leaseUntil] = OutboxPolicy.leaseUntil(now).toEpochMilli()
            })
            outbox.selectAll()
                .where { (outbox.id inList ids) and (outbox.leaseOwner eq owner) }
                .map { it[outbox.id] }
                .toSet()
        }
    }

    /** Removes the entries [owner] still holds. Returns how many were removed; an entry taken over meanwhile is left alone. */
    fun complete(owner: String, ids: Collection<Long>): Int {
        if (ids.isEmpty()) return 0
        return transaction(database) {
            outbox.deleteWhere { (outbox.id inList ids) and (outbox.leaseOwner eq owner) }
        }
    }

    /** Releases an entry that failed, with a backoff that grows with its attempts. */
    fun fail(owner: String, id: Long, error: String) {
        val now = clock.instant()
        transaction(database) {
            val attempts = outbox.selectAll().where { outbox.id eq id }.map { it[outbox.attempts] }.firstOrNull() ?: return@transaction
            outbox.update(where = { (outbox.id eq id) and (outbox.leaseOwner eq owner) }, body = {
                it[outbox.leaseUntil] = OutboxPolicy.retryAt(now, attempts).toEpochMilli()
                it[outbox.lastError] = error.take(LAST_ERROR_LENGTH)
            })
        }
    }

    /** Entries that may still be claimed. */
    fun pending(): Long = transaction(database) {
        outbox.selectAll().where { outbox.attempts less OutboxPolicy.MAX_ATTEMPTS }.count()
    }

    /** Entries that used up their attempts and wait for a rebuild or verify to repair their rows. */
    fun deadLetters(): Long = transaction(database) {
        outbox.selectAll().where { outbox.attempts greaterEq OutboxPolicy.MAX_ATTEMPTS }.count()
    }

    private fun ResultRow.toEntry() = OutboxEntry(
        id = this[outbox.id],
        repository = this[outbox.repository],
        path = this[outbox.path],
        op = OutboxOp.valueOf(this[outbox.op]),
        createdAt = Instant.ofEpochMilli(this[outbox.createdAt]),
        leaseOwner = this[outbox.leaseOwner],
        leaseUntil = this[outbox.leaseUntil]?.let { Instant.ofEpochMilli(it) },
        attempts = this[outbox.attempts],
        lastError = this[outbox.lastError],
    )

    private companion object {
        const val LAST_ERROR_LENGTH = 512
    }

}
