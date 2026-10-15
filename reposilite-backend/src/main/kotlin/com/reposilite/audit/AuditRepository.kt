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

package com.reposilite.audit

import com.reposilite.audit.api.AuditAction
import java.util.Base64

const val DEFAULT_PAGE_SIZE = 50
const val MAX_PAGE_SIZE = 200

/**
 * One row of the audit log. [timestamp] is epoch milliseconds, [occurrences] counts how many
 * identical failed logins were folded into this row, and [details] is a JSON object as text.
 */
data class AuditRecord(
    val id: Long,
    val timestamp: Long,
    val actor: String,
    val actorAddress: String?,
    val action: AuditAction,
    val repository: String?,
    val path: String?,
    val details: String?,
    val success: Boolean,
    val occurrences: Int,
)

/**
 * Position of the last row of a page. Rows are ordered newest first by (timestamp, id), so a
 * cursor names the rows that come after it. It is opaque to clients.
 */
data class AuditCursor(
    val timestamp: Long,
    val id: Long,
) {

    fun encode(): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString("$timestamp:$id".toByteArray(Charsets.UTF_8))

    companion object {

        fun decode(value: String): AuditCursor? {
            val text = runCatching { String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8) }.getOrNull() ?: return null
            val parts = text.split(':')
            if (parts.size != 2) return null
            val timestamp = parts[0].toLongOrNull() ?: return null
            val id = parts[1].toLongOrNull() ?: return null
            return AuditCursor(timestamp, id)
        }

    }

}

/**
 * Filters for the audit log. [from] is inclusive and [to] is exclusive, both in epoch milliseconds.
 * [text] is a case-insensitive substring matched against the actor, the repository, the path and
 * the details. The action has its own filter and is not part of the text search.
 */
data class AuditQuery(
    val actor: String? = null,
    val action: AuditAction? = null,
    val repository: String? = null,
    val text: String? = null,
    val from: Long? = null,
    val to: Long? = null,
    val after: AuditCursor? = null,
    val limit: Int = DEFAULT_PAGE_SIZE,
)

data class AuditPage(
    val records: List<AuditRecord>,
    val nextCursor: String?,
)

interface AuditRepository {

    /** Stores the record and returns its generated id. The id of [record] is ignored. */
    fun insert(record: AuditRecord): Long

    fun incrementOccurrences(id: Long)

    /** Returns at most [AuditQuery.limit] matching rows, newest first, strictly after [AuditQuery.after]. */
    fun find(query: AuditQuery): List<AuditRecord>

    /** Deletes every row older than [timestampMillis] and returns how many were removed. */
    fun deleteBefore(timestampMillis: Long): Long

    fun count(): Long

}
