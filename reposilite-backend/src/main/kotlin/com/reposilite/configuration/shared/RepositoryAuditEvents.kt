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

package com.reposilite.configuration.shared

import com.fasterxml.jackson.databind.JsonNode
import com.reposilite.audit.api.AuditAction
import com.reposilite.audit.api.AuditActor
import com.reposilite.audit.api.AuditEvent

/** The name of the settings domain whose `repositories` list the repository events describe. */
internal const val MAVEN_DOMAIN = "maven"

/**
 * The repository events of a save of the `maven` settings domain. A repository is keyed by its id, so an id that appears
 * only after the save is a creation and an id that disappears is a removal. A rename is therefore a removal and a creation.
 *
 * A change of any other field of a repository, such as its visibility, yields no event here: the `SETTINGS_CHANGED` event
 * of the save already names it. Only the id, the visibility and the declared type are read, never the rest of the entry.
 */
fun repositoryAuditEvents(before: JsonNode, after: JsonNode, actor: AuditActor): List<AuditEvent> {
    val previous = repositoriesById(before)
    val current = repositoriesById(after)

    val removed = previous.filterKeys { it !in current }.map { (id, entry) ->
        AuditEvent(AuditAction.REPOSITORY_DELETED, actor, repository = id, details = repositoryDetails(entry))
    }
    val created = current.filterKeys { it !in previous }.map { (id, entry) ->
        AuditEvent(AuditAction.REPOSITORY_CREATED, actor, repository = id, details = repositoryDetails(entry))
    }
    return removed + created
}

private fun repositoriesById(settings: JsonNode): Map<String, JsonNode> {
    val repositories = settings.get("repositories")?.takeIf { it.isArray } ?: return emptyMap()
    return repositories
        .filter { it.get("id")?.isTextual == true }
        .associateBy({ it.get("id").asText() }, { it })
}

private fun repositoryDetails(entry: JsonNode): Map<String, Any?> =
    mapOf(
        "visibility" to entry.textOrNull("visibility"),
        "type" to entry.textOrNull("type"),
    )

private fun JsonNode.textOrNull(field: String): String? =
    get(field)?.takeIf { !it.isNull }?.asText()
