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

internal class ClusterReadOnlyEventTypeTest {

    @Test
    fun `the read-only change is sent under the wire name read-only changed`() {
        // when / then: the wire name resolves to the read-only type
        assertThat(ClusterEventType.fromWireName("read-only.changed"))
            .`as`("type of the wire name read-only.changed")
            .isEqualTo(ClusterEventType.READ_ONLY_CHANGED)
    }

    @Test
    fun `the read-only change carries only the enabled flag`() {
        // when / then: the payload has one field, the flag, and no secret material
        assertThat(ClusterEventType.READ_ONLY_CHANGED.payloadFields)
            .`as`("payload fields of the read-only change")
            .containsExactly("enabled")
    }

}
