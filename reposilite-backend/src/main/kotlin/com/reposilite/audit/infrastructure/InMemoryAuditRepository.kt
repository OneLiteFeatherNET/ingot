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

import com.reposilite.audit.AuditQuery
import com.reposilite.audit.AuditRecord
import com.reposilite.audit.AuditRepository
import com.reposilite.audit.AuditCursor

/**
 * Keeps the audit log in memory. Used when no database is configured, and by the fast unit tests.
 * It follows the same ordering, cursor and filter rules as the SQL repository.
 */
internal class InMemoryAuditRepository : AuditRepository {

    private val records = mutableListOf<AuditRecord>()
    private var lastId = 0L

    @Synchronized
    override fun insert(record: AuditRecord): Long {
        lastId++
        records += record.copy(id = lastId)
        return lastId
    }

    @Synchronized
    override fun incrementOccurrences(id: Long) {
        val index = records.indexOfFirst { it.id == id }
        if (index >= 0) {
            records[index] = records[index].copy(occurrences = records[index].occurrences + 1)
        }
    }

    @Synchronized
    override fun find(query: AuditQuery): List<AuditRecord> =
        records
            .asSequence()
            .filter { matches(it, query) }
            .sortedWith(compareByDescending<AuditRecord> { it.timestamp }.thenByDescending { it.id })
            .filter { isAfter(it, query.after) }
            .take(query.limit)
            .toList()

    @Synchronized
    override fun deleteBefore(timestampMillis: Long): Long {
        val before = records.size
        records.removeIf { it.timestamp < timestampMillis }
        return (before - records.size).toLong()
    }

    @Synchronized
    override fun count(): Long =
        records.size.toLong()

    private fun matches(record: AuditRecord, query: AuditQuery): Boolean {
        if (query.actor != null && record.actor != query.actor) return false
        if (query.action != null && record.action != query.action) return false
        if (query.repository != null && record.repository != query.repository) return false
        if (query.from != null && record.timestamp < query.from) return false
        if (query.to != null && record.timestamp >= query.to) return false
        if (query.text != null && !containsText(record, query.text.lowercase())) return false
        return true
    }

    private fun containsText(record: AuditRecord, needle: String): Boolean =
        listOfNotNull(record.actor, record.repository, record.path, record.details)
            .any { it.lowercase().contains(needle) }

    private fun isAfter(record: AuditRecord, cursor: AuditCursor?): Boolean =
        cursor == null || record.timestamp < cursor.timestamp || (record.timestamp == cursor.timestamp && record.id < cursor.id)

}
