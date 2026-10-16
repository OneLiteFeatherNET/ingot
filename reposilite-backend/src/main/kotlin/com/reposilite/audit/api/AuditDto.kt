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

package com.reposilite.audit.api

import com.fasterxml.jackson.databind.JsonNode

/**
 * One audited event as the API returns it. [timestamp] is ISO-8601 in UTC, [details] is the JSON
 * object recorded with the event (or null), and [occurrences] is above one only for folded failed logins.
 */
data class AuditEventDto(
    val id: Long,
    val timestamp: String,
    val actor: String,
    val actorAddress: String?,
    val action: AuditAction,
    val repository: String?,
    val path: String?,
    val details: JsonNode?,
    val success: Boolean,
    val occurrences: Int,
)

data class AuditPageDto(
    val events: List<AuditEventDto>,
    val nextCursor: String?,
)
