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

package net.onelitefeather.ingot.cluster

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.reposilite.plugin.api.StorageUsageFigure
import com.reposilite.plugin.api.StorageUsageRefreshedEvent
import java.time.Instant

/**
 * The payload of a `storage.usage` message: the time of the measurement and the figures per repository. It carries
 * sizes and counts only.
 */
internal object StorageUsageMessage {

    fun toPayload(event: StorageUsageRefreshedEvent): ObjectNode {
        val payload = JsonNodeFactory.instance.objectNode()
        payload.put("computedAt", event.computedAt.toString())
        val repositories = payload.putArray("repositories")
        event.repositories.forEach { figure ->
            repositories.addObject()
                .put("id", figure.id)
                .put("usedBytes", figure.usedBytes)
                .put("fileCount", figure.fileCount)
                .put("storageType", figure.storageType)
        }
        return payload
    }

    /** @return the snapshot, or `null` when the payload does not have the shape of a usage snapshot */
    fun fromPayload(payload: ObjectNode): StorageUsageRefreshedEvent? {
        val computedAt = payload.path("computedAt").takeIf { it.isTextual }
            ?.let { runCatching { Instant.parse(it.asText()) }.getOrNull() }
            ?: return null
        val repositories = payload.path("repositories").takeIf { it.isArray } ?: return null
        val figures = repositories.map { node ->
            val id = node.path("id").takeIf { it.isTextual }?.asText() ?: return null
            StorageUsageFigure(
                id = id,
                usedBytes = node.path("usedBytes").asLong(),
                fileCount = node.path("fileCount").asLong(),
                storageType = node.path("storageType").takeIf { it.isTextual }?.asText() ?: "unknown",
            )
        }
        return StorageUsageRefreshedEvent(computedAt, figures)
    }
}
