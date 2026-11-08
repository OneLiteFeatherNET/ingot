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
import java.time.Instant

internal class ClusterReceiverTest {

    private val at = Instant.parse("2026-11-08T10:15:30Z")
    private val mapper = ObjectMapper()
    private val counters = ClusterCounters()
    private val receiver = ClusterReceiver(clusterIdentifier = "cluster-1", instanceIdentifier = "ingot-a", counters = counters)

    private fun wire(
        version: Int = 1,
        type: String = "token.changed",
        cluster: String = "cluster-1",
        origin: String = "ingot-b",
    ): String =
        """{"v":$version,"type":"$type","cluster":"$cluster","origin":"$origin","at":"$at","payload":{"name":"ci-bot"}}"""

    @Test
    fun `an event of this cluster from another instance is applied`() {
        // when: a token change from another instance arrives
        val envelope = receiver.accept(wire())

        // then: it is handed on and counted as applied
        assertThat(envelope?.origin).isEqualTo("ingot-b")
        assertThat(counters.applied).isEqualTo(1)
        assertThat(counters.ignored).isZero()
    }

    @Test
    fun `an envelope with an unknown version is ignored and counted`() {
        val envelope = receiver.accept(wire(version = 2))

        assertThat(envelope).isNull()
        assertThat(counters.ignored).isEqualTo(1)
    }

    @Test
    fun `an envelope with an unknown event type is ignored and counted`() {
        val envelope = receiver.accept(wire(type = "status.snapshot"))

        assertThat(envelope).isNull()
        assertThat(counters.ignored).isEqualTo(1)
    }

    @Test
    fun `an envelope of another cluster is ignored`() {
        val envelope = receiver.accept(wire(cluster = "other-database"))

        assertThat(envelope).isNull()
        assertThat(counters.applied).isZero()
        assertThat(counters.ignored).isEqualTo(1)
    }

    @Test
    fun `an envelope published by this instance is ignored as an echo`() {
        val envelope = receiver.accept(wire(origin = "ingot-a"))

        assertThat(envelope).isNull()
        assertThat(counters.ignored).isEqualTo(1)
    }

    @Test
    fun `a message that is not an envelope is ignored and counted`() {
        val envelope = receiver.accept("""{"hello":"world"}""")

        assertThat(envelope).isNull()
        assertThat(counters.ignored).isEqualTo(1)
    }
}
