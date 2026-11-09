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

import com.fasterxml.jackson.databind.JsonNode
import com.reposilite.plugin.api.ResolutionInvalidatedEvent
import com.reposilite.plugin.api.SharedConfigurationChangedEvent
import com.reposilite.plugin.event
import com.reposilite.web.api.HttpServerStartedEvent

/**
 * Wires the cache events of the bus: a local resolution invalidation and a local configuration change are published, and the
 * received ones are applied through [hooks]. Received events never reach the emitting facade methods, so nothing is echoed.
 */
internal object ClusterCacheEvents {

    fun install(
        plugin: ClusterPlugin,
        hooks: ClusterCacheHooks,
        publish: (ClusterEventType, Map<String, String>) -> Unit,
    ) {
        plugin.onReceived(ClusterEventType.RESOLUTION_INVALIDATED) { envelope ->
            val repository = envelope.payload.text("repository") ?: return@onReceived
            val path = envelope.payload.text("path") ?: return@onReceived
            hooks.resolutionInvalidated(repository, path)
        }
        plugin.onReceived(ClusterEventType.CONFIG_CHANGED) { envelope ->
            val domain = envelope.payload.text("domain") ?: return@onReceived
            hooks.sharedConfigurationChanged(domain)
        }

        plugin.event { event: ResolutionInvalidatedEvent ->
            publish(ClusterEventType.RESOLUTION_INVALIDATED, mapOf("repository" to event.repository, "path" to event.path))
        }
        plugin.event { event: SharedConfigurationChangedEvent ->
            publish(ClusterEventType.CONFIG_CHANGED, mapOf("domain" to event.domain))
        }
        plugin.event { _: HttpServerStartedEvent ->
            hooks.clusterBusOpened()
        }
    }

    private fun JsonNode.text(field: String): String? =
        path(field).takeIf { it.isTextual }?.asText()
}
