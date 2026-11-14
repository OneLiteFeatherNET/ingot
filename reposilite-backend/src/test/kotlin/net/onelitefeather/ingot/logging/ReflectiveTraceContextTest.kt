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

package net.onelitefeather.ingot.logging

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.URLClassLoader

internal class ReflectiveTraceContextTest {

    @Test
    fun `should report the ids of the active span when the API is visible`() {
        // given: the test class path holds a stand-in with the shape of the OpenTelemetry API
        val trace = ReflectiveTraceContext.create(ReflectiveTraceContextTest::class.java.classLoader)

        // when: the active span is asked for
        val ids = trace.current()

        // then: the ids of that span are returned in lowercase hexadecimal
        assertThat(ids).describedAs("ids of the active span")
            .isEqualTo(TraceIds(traceId = "4bf92f3577b34da6a3ce929d0e0e4736", spanId = "00f067aa0ba902b7"))
    }

    @Test
    fun `should report no span when the API is not visible`() {
        // given: a class loader that cannot see any OpenTelemetry class
        URLClassLoader(emptyArray(), null).use { withoutApi ->
            // when: the trace source is created and asked for the active span
            val ids = ReflectiveTraceContext.create(withoutApi).current()

            // then: there is no span, and the probe does not fail the start
            assertThat(ids).describedAs("ids without the API").isNull()
        }
    }

}
