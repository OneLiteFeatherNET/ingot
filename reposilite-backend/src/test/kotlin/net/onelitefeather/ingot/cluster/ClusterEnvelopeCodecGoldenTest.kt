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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The version 1 wire format, pinned as fixed strings. A release that is still running next to a newer one reads these
 * exact bytes, so a change to any of them is a change to the mixed-version contract and has to fail here first.
 *
 * Nothing here reads a clock or the network: every message is a literal and the codec is called directly.
 */
internal class ClusterEnvelopeCodecGoldenTest {

    private val mapper = ObjectMapper()

    /**
     * One fixed version 1 message per event type. The wire name is written out as a literal, not read from the enum, so a
     * renamed entry changes the decoded type and fails here instead of rewriting the golden bytes along with it.
     */
    private data class Golden(val wireName: String, val type: ClusterEventType, val payload: String)

    private val goldens = listOf(
        Golden("resolution.invalidated", ClusterEventType.RESOLUTION_INVALIDATED, """{"repository":"releases","path":"com/example/demo/1.0.0"}"""),
        Golden("token.changed", ClusterEventType.TOKEN_CHANGED, """{"name":"ci-bot"}"""),
        Golden("session.revoked", ClusterEventType.SESSION_REVOKED, """{"family":"6f1c2a9e-0d3b-4c57-9a8e-2b4d6e8f1a20"}"""),
        Golden("config.changed", ClusterEventType.CONFIG_CHANGED, """{"domain":"maven"}"""),
        Golden("javadoc.invalidated", ClusterEventType.JAVADOC_INVALIDATED, """{"repository":"releases","path":"com/example/demo"}"""),
        Golden(
            "storage.usage",
            ClusterEventType.STORAGE_USAGE,
            """{"computedAt":"2026-11-08T10:15:00Z","repositories":[{"id":"releases","usedBytes":1024,"fileCount":3}]}""",
        ),
        Golden(
            "console.line",
            ClusterEventType.CONSOLE_LINE,
            """{"channel":"INFO","message":"server started","timestamp":"2026-11-08T10:15:30Z"}""",
        ),
        Golden("failure.recorded", ClusterEventType.FAILURE_RECORDED, """{"source":"maven","type":"IOException","message":"disk full"}"""),
        Golden(
            "status.snapshot",
            ClusterEventType.STATUS_SNAPSHOT,
            """{"version":"1.1.1","uptimeStart":1762596000000,"usedMemory":512,"maxMemory":1024,"usedThreads":40,"maxThreads":200,""" +
                """"failuresCount":0,"health":"OK","interval":60,"sample":{"at":1762596030000,"memory":512,"threads":40}}""",
        ),
        Golden("read-only.changed", ClusterEventType.READ_ONLY_CHANGED, """{"enabled":true}"""),
    )

    /** The message an earlier build put on the wire for [golden], with the fixed origin and cluster of this test. */
    private fun wire(golden: Golden, extraPayloadField: String? = null): String {
        val payload = if (extraPayloadField == null) golden.payload else golden.payload.dropLast(1) + ",$extraPayloadField}"
        return """{"v":1,"type":"${golden.wireName}","cluster":"cluster-1","origin":"ingot-b","at":"2026-11-08T10:15:30Z","payload":$payload}"""
    }

    @Test
    fun `the golden messages cover every event type`() {
        assertThat(goldens.map { it.type })
            .describedAs("event types with a golden version 1 message")
            .containsExactlyInAnyOrderElementsOf(ClusterEventType.entries)
    }

    @Test
    fun `every golden payload carries exactly the fields its type declares`() {
        goldens.forEach { golden ->
            val fields = mapper.readTree(golden.payload).fieldNames().asSequence().toSet()
            assertThat(fields)
                .describedAs("payload fields of ${golden.wireName}")
                .containsExactlyInAnyOrderElementsOf(golden.type.payloadFields)
        }
    }

    @Test
    fun `every golden version 1 message decodes to its stored type, origin and payload`() {
        goldens.forEach { golden ->
            val result = ClusterEnvelopeCodec.decode(wire(golden))

            assertThat(result)
                .describedAs("golden message of ${golden.wireName}")
                .isInstanceOf(ClusterDecodeResult.Accepted::class.java)
            val envelope = (result as ClusterDecodeResult.Accepted).envelope
            assertThat(envelope.type).describedAs("type of ${golden.wireName}").isEqualTo(golden.type)
            assertThat(envelope.origin).describedAs("origin of ${golden.wireName}").isEqualTo("ingot-b")
            assertThat(envelope.payload)
                .describedAs("payload of ${golden.wireName}")
                .isEqualTo(mapper.readTree(golden.payload))
        }
    }

    @Test
    fun `a version 1 message with an extra payload field is accepted and keeps the field`() {
        val golden = goldens.first { it.type == ClusterEventType.TOKEN_CHANGED }

        val result = ClusterEnvelopeCodec.decode(wire(golden, extraPayloadField = "\"scope\":\"future\""))

        assertThat(result).describedAs("message with an unknown payload field").isInstanceOf(ClusterDecodeResult.Accepted::class.java)
        val payload = (result as ClusterDecodeResult.Accepted).envelope.payload
        assertThat(payload.path("name").asText()).describedAs("known field survives").isEqualTo("ci-bot")
        assertThat(payload.path("scope").asText()).describedAs("unknown field is carried along").isEqualTo("future")
    }

    @Test
    fun `a message with envelope version 2 is ignored`() {
        val golden = goldens.first { it.type == ClusterEventType.TOKEN_CHANGED }
        val version2 = wire(golden).replaceFirst("\"v\":1", "\"v\":2")

        assertThat(ClusterEnvelopeCodec.decode(version2))
            .describedAs("message of envelope version 2")
            .isEqualTo(ClusterDecodeResult.Ignored("unknown envelope version"))
    }

    @Test
    fun `a version 1 message of an unknown event type is ignored`() {
        val golden = goldens.first { it.type == ClusterEventType.TOKEN_CHANGED }
        val unknownType = wire(golden).replace("\"type\":\"token.changed\"", "\"type\":\"token.renamed\"")

        assertThat(ClusterEnvelopeCodec.decode(unknownType))
            .describedAs("version 1 message of an unknown type")
            .isEqualTo(ClusterDecodeResult.Ignored("unknown event type"))
    }
}
