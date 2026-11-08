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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

internal class ClusterEnvelopeCodecTest {

    private val at = Instant.parse("2026-11-08T10:15:30Z")
    private val mapper = ObjectMapper()

    private fun envelope(type: ClusterEventType = ClusterEventType.TOKEN_CHANGED, payload: ObjectNode = mapper.createObjectNode().put("name", "ci-bot")) =
        ClusterEnvelope(type = type, cluster = "cluster-1", origin = "ingot-b", at = at, payload = payload)

    @Test
    fun `an envelope survives a round trip through the codec`() {
        // given: an envelope of a known type
        val original = envelope()

        // when: it is encoded and decoded again
        val result = ClusterEnvelopeCodec.decode(ClusterEnvelopeCodec.encode(original))

        // then: every field is restored
        assertThat(result).isEqualTo(ClusterDecodeResult.Accepted(original))
    }

    @Test
    fun `the payload of every event type carries no secret-shaped field`() {
        val secretShaped = Regex("(?i)secret|password|refresh|setup|jwt|credential|session.?token")

        ClusterEventType.entries.forEach { type ->
            // given: a sample payload that carries every field the type declares
            val payload = mapper.createObjectNode()
            type.payloadFields.forEach { payload.put(it, "sample") }

            // when: the envelope is serialised
            val encoded = mapper.readTree(ClusterEnvelopeCodec.encode(envelope(type, payload)))

            // then: no field name of the payload looks like a secret
            val fields = encoded.get("payload").fieldNames().asSequence().toList()
            assertThat(fields)
                .describedAs("payload fields of ${type.wireName}")
                .noneMatch { secretShaped.containsMatchIn(it) }
        }
    }
}
