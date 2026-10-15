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

package com.reposilite.audit.infrastructure

import com.reposilite.audit.AuditCursor
import com.reposilite.audit.AuditQuery
import com.reposilite.audit.AuditRecord
import com.reposilite.audit.AuditRepository
import com.reposilite.audit.api.AuditAction
import org.jetbrains.exposed.v1.core.LikePattern
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

/**
 * The audit log table. It is created with the other tables on every start, so a database that
 * already holds Reposilite or Ingot data gets it without a migration step and nothing else changes.
 */
internal object AuditEventsTable : Table("audit_events") {
    val id = long("id").autoIncrement()
    val timestamp = long("timestamp")
    val actor = varchar("actor", ACTOR_MAX_LENGTH)
    val actorAddress = varchar("actorAddress", ADDRESS_MAX_LENGTH).nullable()
    val action = varchar("action", ACTION_MAX_LENGTH)
    val repository = varchar("repository", REPOSITORY_MAX_LENGTH).nullable()
    val path = varchar("path", PATH_MAX_LENGTH).nullable()
    val details = text("details").nullable()
    val success = bool("success")
    val occurrences = integer("occurrences").default(1)

    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_audit_events_timestamp", false, timestamp)
        index("idx_audit_events_actor", false, actor)
        index("idx_audit_events_action", false, action)
    }

    const val ACTOR_MAX_LENGTH = 255
    const val ADDRESS_MAX_LENGTH = 64
    const val ACTION_MAX_LENGTH = 64
    const val REPOSITORY_MAX_LENGTH = 255
    const val PATH_MAX_LENGTH = 1024

}

internal class SqlAuditRepository(
    private val database: Database
) : AuditRepository {

    init {
        transaction(database) {
            SchemaUtils.create(AuditEventsTable)
            SchemaUtils.createMissingTablesAndColumns(AuditEventsTable)
        }
    }

    override fun insert(record: AuditRecord): Long =
        transaction(database) {
            val statement = AuditEventsTable.insert {
                it[timestamp] = record.timestamp
                it[actor] = record.actor.take(AuditEventsTable.ACTOR_MAX_LENGTH)
                it[actorAddress] = record.actorAddress?.take(AuditEventsTable.ADDRESS_MAX_LENGTH)
                it[action] = record.action.name
                it[repository] = record.repository?.take(AuditEventsTable.REPOSITORY_MAX_LENGTH)
                it[path] = record.path?.take(AuditEventsTable.PATH_MAX_LENGTH)
                it[details] = record.details
                it[success] = record.success
                it[occurrences] = record.occurrences
            }
            statement[AuditEventsTable.id]
        }

    override fun incrementOccurrences(id: Long) {
        transaction(database) {
            val current = AuditEventsTable
                .selectAll()
                .where { AuditEventsTable.id eq id }
                .map { it[AuditEventsTable.occurrences] }
                .firstOrNull()
                ?: return@transaction

            AuditEventsTable.update({ AuditEventsTable.id eq id }) {
                it[AuditEventsTable.occurrences] = current + 1
            }
        }
    }

    override fun find(query: AuditQuery): List<AuditRecord> =
        transaction(database) {
            AuditEventsTable
                .selectAll()
                .where { conditions(query) }
                .orderBy(AuditEventsTable.timestamp to SortOrder.DESC, AuditEventsTable.id to SortOrder.DESC)
                .limit(query.limit)
                .map { it.toRecord() }
        }

    override fun deleteBefore(timestampMillis: Long): Long =
        transaction(database) {
            AuditEventsTable.deleteWhere { AuditEventsTable.timestamp less timestampMillis }.toLong()
        }

    override fun count(): Long =
        transaction(database) {
            AuditEventsTable.selectAll().count()
        }

    private fun conditions(query: AuditQuery): Op<Boolean> {
        val parts = mutableListOf<Op<Boolean>>()

        query.actor?.let { parts += AuditEventsTable.actor eq it }
        query.actions.takeIf { it.isNotEmpty() }?.let { parts += AuditEventsTable.action inList it.map { action -> action.name } }
        query.repository?.let { parts += AuditEventsTable.repository eq it }
        query.from?.let { parts += AuditEventsTable.timestamp greaterEq it }
        query.to?.let { parts += AuditEventsTable.timestamp less it }
        query.text?.let { parts += textMatches(it.lowercase()) }
        query.after?.let { parts += afterCursor(it) }

        return parts.fold(Op.TRUE as Op<Boolean>) { condition, part -> condition and part }
    }

    /**
     * Rows strictly after the cursor in the newest-first order. The id breaks ties between rows that
     * share a timestamp, so a page boundary never skips or repeats a row.
     */
    private fun afterCursor(cursor: AuditCursor): Op<Boolean> =
        (AuditEventsTable.timestamp less cursor.timestamp) or
            ((AuditEventsTable.timestamp eq cursor.timestamp) and (AuditEventsTable.id less cursor.id))

    /**
     * Case-insensitive substring match over the searchable columns. The LIKE wildcards in the text
     * are escaped, so a search for "%" finds a literal percent sign and nothing else.
     */
    private fun textMatches(needle: String): Op<Boolean> {
        val pattern = LikePattern("%" + escapeLike(needle) + "%", LIKE_ESCAPE)
        val columns = listOf(
            AuditEventsTable.actor.lowerCase(),
            AuditEventsTable.repository.lowerCase(),
            AuditEventsTable.path.lowerCase(),
            AuditEventsTable.details.lowerCase(),
        )
        return columns.fold(Op.FALSE as Op<Boolean>) { matches, column -> matches or (column like pattern) }
    }

    private fun escapeLike(value: String): String =
        buildString {
            value.forEach { character ->
                if (character == LIKE_ESCAPE || character == '%' || character == '_') {
                    append(LIKE_ESCAPE)
                }
                append(character)
            }
        }

    private companion object {
        const val LIKE_ESCAPE = '\\'
    }

    private fun ResultRow.toRecord(): AuditRecord =
        AuditRecord(
            id = this[AuditEventsTable.id],
            timestamp = this[AuditEventsTable.timestamp],
            actor = this[AuditEventsTable.actor],
            actorAddress = this[AuditEventsTable.actorAddress],
            action = AuditAction.valueOf(this[AuditEventsTable.action]),
            repository = this[AuditEventsTable.repository],
            path = this[AuditEventsTable.path],
            details = this[AuditEventsTable.details],
            success = this[AuditEventsTable.success],
            occurrences = this[AuditEventsTable.occurrences],
        )

}
