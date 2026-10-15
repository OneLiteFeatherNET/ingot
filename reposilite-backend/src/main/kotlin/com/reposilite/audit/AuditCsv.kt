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

import java.time.Instant

/**
 * RFC 4180 rows for the audit export. Actor names and paths come from clients, so a cell that a
 * spreadsheet would read as a formula gets a leading apostrophe.
 */
internal object AuditCsv {

    private val HEADER = listOf("id", "timestamp", "actor", "actorAddress", "action", "repository", "path", "success", "occurrences", "details")

    private val FORMULA_PREFIXES = setOf('=', '+', '-', '@', '\t', '\r')

    val HEADER_LINE: String = line(HEADER)

    fun line(record: AuditRecord): String =
        line(
            listOf(
                record.id.toString(),
                Instant.ofEpochMilli(record.timestamp).toString(),
                record.actor,
                record.actorAddress,
                record.action.name,
                record.repository,
                record.path,
                record.success.toString(),
                record.occurrences.toString(),
                record.details,
            )
        )

    private fun line(cells: List<String?>): String =
        cells.joinToString(separator = ",", postfix = "\n") { cell(it) }

    internal fun cell(value: String?): String {
        val text = value.orEmpty()
        val guarded = if (text.firstOrNull() in FORMULA_PREFIXES) "'$text" else text
        return if (guarded.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + guarded.replace("\"", "\"\"") + "\""
        } else {
            guarded
        }
    }

}
