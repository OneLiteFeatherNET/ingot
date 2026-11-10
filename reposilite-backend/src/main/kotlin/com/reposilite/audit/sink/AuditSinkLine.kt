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

package com.reposilite.audit.sink

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.reposilite.audit.api.AuditAction
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * One audit event as the JSON lines sink writes it. The field names and their order are the consumer contract: adding a
 * field or an action name keeps [VERSION], while renaming, removing or retyping a field raises it.
 *
 * [details] is written as a JSON object. [occurrences] is always `1`, because a line is an event and not a folded row.
 */
data class AuditSinkLine(
    val time: Instant,
    val node: String,
    val action: AuditAction,
    val actor: String,
    val actorAddress: String?,
    val repository: String?,
    val path: String?,
    val details: Map<String, Any?>?,
    val success: Boolean,
) {

    /** The line without its terminating newline. Every value is escaped, so the line never holds a raw line break. */
    fun encode(): String {
        val line = MAPPER.createObjectNode()
        line.put("schema", SCHEMA)
        line.put("version", VERSION)
        line.put("time", time.truncatedTo(ChronoUnit.MILLIS).toString())
        line.put("node", node)
        line.put("action", action.name)
        line.put("actor", actor)
        line.putNullable("actorAddress", actorAddress)
        line.putNullable("repository", repository)
        line.putNullable("path", path)
        line.set<JsonNode>("details", details?.let { MAPPER.valueToTree<JsonNode>(it) } ?: MAPPER.nullNode())
        line.put("success", success)
        line.put("occurrences", OCCURRENCES)
        return MAPPER.writeValueAsString(line)
    }

    private fun ObjectNode.putNullable(name: String, value: String?) {
        if (value == null) putNull(name) else put(name, value)
    }

    companion object {

        const val SCHEMA = "ingot.audit"
        const val VERSION = 1

        private const val OCCURRENCES = 1

        /** Its own mapper: the shared one leaves out null values, and every field of a line is always present. */
        private val MAPPER = JsonMapper.builder().build()

    }

}
