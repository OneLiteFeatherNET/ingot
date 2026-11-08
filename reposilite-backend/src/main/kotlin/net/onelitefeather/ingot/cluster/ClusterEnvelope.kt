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

import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import java.time.Instant

/**
 * One event on the bus. On the wire it is a single JSON object: `{"v":1,"type":...,"cluster":...,"origin":...,"at":...,"payload":{...}}`.
 */
data class ClusterEnvelope(
    val type: ClusterEventType,
    val cluster: String,
    val origin: String,
    val at: Instant,
    val payload: ObjectNode,
    val version: Int = ClusterEnvelopeCodec.VERSION,
)

sealed interface ClusterDecodeResult {

    data class Accepted(val envelope: ClusterEnvelope) : ClusterDecodeResult

    /** A message of a version or type this instance does not know, or one that is not an envelope at all. */
    data class Ignored(val reason: String) : ClusterDecodeResult
}

object ClusterEnvelopeCodec {

    const val VERSION = 1

    private val mapper = ObjectMapper()

    fun encode(envelope: ClusterEnvelope): String =
        mapper.createObjectNode()
            .put("v", envelope.version)
            .put("type", envelope.type.wireName)
            .put("cluster", envelope.cluster)
            .put("origin", envelope.origin)
            .put("at", envelope.at.toString())
            .set<ObjectNode>("payload", envelope.payload)
            .let { mapper.writeValueAsString(it) }

    /** Never throws: anything this codec cannot read is reported as [ClusterDecodeResult.Ignored]. */
    fun decode(raw: String): ClusterDecodeResult {
        val node = try {
            mapper.readTree(raw)
        } catch (exception: JacksonException) {
            return ClusterDecodeResult.Ignored("not json")
        }
        if (node == null || !node.isObject) {
            return ClusterDecodeResult.Ignored("not an object")
        }

        val version = node.path("v")
        if (!version.isInt || version.asInt() != VERSION) {
            return ClusterDecodeResult.Ignored("unknown envelope version")
        }

        val type = ClusterEventType.fromWireName(node.text("type") ?: "")
            ?: return ClusterDecodeResult.Ignored("unknown event type")
        val cluster = node.text("cluster") ?: return ClusterDecodeResult.Ignored("missing cluster")
        val origin = node.text("origin") ?: return ClusterDecodeResult.Ignored("missing origin")
        val at = node.text("at")?.let { runCatching { Instant.parse(it) }.getOrNull() }
            ?: return ClusterDecodeResult.Ignored("missing or invalid timestamp")
        val payload = node.get("payload")?.takeIf { it.isObject } as? ObjectNode
            ?: return ClusterDecodeResult.Ignored("missing payload")

        return ClusterDecodeResult.Accepted(ClusterEnvelope(type, cluster, origin, at, payload, VERSION))
    }

    private fun JsonNode.text(field: String): String? =
        path(field).takeIf { it.isTextual }?.asText()
}
