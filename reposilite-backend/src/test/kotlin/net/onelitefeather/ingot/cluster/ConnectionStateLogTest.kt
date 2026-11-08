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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class ConnectionStateLogTest {

    @Test
    fun `a state is logged once per transition, however often it is reported`() {
        // given: a log that records its lines
        val lines = mutableListOf<String>()
        val log = ConnectionStateLog { lines.add(it) }

        // when: the connection is reported as connected several times, then drops and recovers
        log.transition(ConnectionState.CONNECTED)
        log.transition(ConnectionState.CONNECTED)
        log.transition(ConnectionState.RECONNECTING)
        log.transition(ConnectionState.RECONNECTING)
        log.transition(ConnectionState.CONNECTED)

        // then: one line for each change of state
        assertThat(lines).hasSize(3)
        assertThat(lines[1]).contains("reconnecting")
    }
}
