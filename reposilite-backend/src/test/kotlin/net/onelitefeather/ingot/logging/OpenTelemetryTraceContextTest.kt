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

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The real OpenTelemetry API, with no agent and no exporter. A span is made current by wrapping a span context, which is a
 * value and needs no SDK. The scope is closed inside each test, so the thread holds no span after it.
 */
internal class OpenTelemetryTraceContextTest {

    @Test
    fun `should report the ids of the span that is current`() {
        // given: a span context made current on this thread
        val spanContext = SpanContext.create(
            "4bf92f3577b34da6a3ce929d0e0e4736",
            "00f067aa0ba902b7",
            TraceFlags.getSampled(),
            TraceState.getDefault(),
        )

        // when: the active span is asked for while it is current
        val ids = Span.wrap(spanContext).makeCurrent().use { OpenTelemetryTraceContext.current() }

        // then: the ids of that span are returned in lowercase hexadecimal
        assertThat(ids).describedAs("ids of the current span")
            .isEqualTo(TraceIds(traceId = "4bf92f3577b34da6a3ce929d0e0e4736", spanId = "00f067aa0ba902b7"))
    }

    @Test
    fun `should report no span when none is current`() {
        val ids = OpenTelemetryTraceContext.current()

        assertThat(ids).describedAs("ids without a current span").isNull()
    }

}
