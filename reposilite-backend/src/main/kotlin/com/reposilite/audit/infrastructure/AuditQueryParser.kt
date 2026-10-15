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
import com.reposilite.audit.DEFAULT_PAGE_SIZE
import com.reposilite.audit.MAX_PAGE_SIZE
import com.reposilite.audit.api.AuditAction
import panda.std.Result
import panda.std.asError
import panda.std.Result.ok
import java.time.Instant

/**
 * Reads the filters of `GET /api/audit` and `GET /api/audit/export` from the query string. A blank
 * parameter counts as absent. Every rejected value names the parameter it came from, so the
 * error tells the caller what to fix.
 */
internal object AuditQueryParser {

    private class InvalidAuditQuery(message: String) : RuntimeException(message)

    fun parse(param: (String) -> List<String>): Result<AuditQuery, String> =
        try {
            ok(build(param))
        } catch (exception: InvalidAuditQuery) {
            exception.message.orEmpty().asError()
        }

    private fun build(param: (String) -> List<String>): AuditQuery {
        val from = text(param, "from")?.let { instant("from", it) }
        val to = text(param, "to")?.let { instant("to", it) }
        if (from != null && to != null && from >= to) {
            throw InvalidAuditQuery("from must be before to")
        }

        return AuditQuery(
            actor = text(param, "actor"),
            actions = actions(param("action")),
            repository = text(param, "repository"),
            text = text(param, "q"),
            from = from,
            to = to,
            after = text(param, "cursor")?.let { AuditCursor.decode(it) ?: throw InvalidAuditQuery("cursor is not valid") },
            limit = text(param, "limit")?.let { limit(it) } ?: DEFAULT_PAGE_SIZE,
        )
    }

    private fun text(param: (String) -> List<String>, name: String): String? =
        param(name).firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * Every `action` parameter may hold a comma separated list, so `action=A,B` and `action=A&action=B`
     * mean the same. Entries are trimmed, empty entries are dropped and repeats count once. Unknown
     * names are all reported together.
     */
    private fun actions(values: List<String>): Set<AuditAction> {
        val names = values
            .flatMap { it.split(',') }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

        val byName = AuditAction.entries.associateBy { it.name }
        val unknown = names.filter { it !in byName }
        if (unknown.isNotEmpty()) {
            val label = if (unknown.size == 1) "Unknown action" else "Unknown actions"
            throw InvalidAuditQuery("$label: ${unknown.joinToString(", ")}")
        }

        return names.mapTo(linkedSetOf()) { byName.getValue(it) }
    }

    private fun instant(name: String, value: String): Long =
        runCatching { Instant.parse(value).toEpochMilli() }
            .getOrElse { throw InvalidAuditQuery("$name must be an ISO-8601 instant, for example 2026-10-15T00:00:00Z") }

    private fun limit(value: String): Int =
        value.toIntOrNull()
            ?.takeIf { it in 1..MAX_PAGE_SIZE }
            ?: throw InvalidAuditQuery("limit must be a number from 1 to $MAX_PAGE_SIZE")

}
