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

import com.fasterxml.jackson.databind.ObjectMapper
import com.reposilite.journalist.Channel
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The JSON console journalist. Every test owns its streams and its clock, and the trace source is a double, so no test
 * reads the system time or the state of another test.
 */
internal class JsonLoggerTest {

    private val mapper = ObjectMapper()

    private val fixedClock = Clock.fixed(Instant.parse("2026-11-14T10:15:30Z"), ZoneOffset.UTC)

    @Test
    fun `should write one object with the time, level and message fields`() {
        // given: a logger that writes to a captured stream
        val console = Console()

        // when: one line is logged
        console.logger().log(Channel.INFO, "server started")

        // then: the line is one object with exactly the three fields
        val line = console.lines().single()
        assertThat(mapper.readTree(line).fieldNames().asSequence().toList())
            .describedAs("field names of the JSON line")
            .containsExactly("time", "level", "message")
        assertThat(mapper.readTree(line)["message"].asText()).describedAs("message of the JSON line").isEqualTo("server started")
    }

    @Test
    fun `should write the time from the injected clock in UTC`() {
        val console = Console()

        console.logger().log(Channel.INFO, "tick")

        assertThat(mapper.readTree(console.lines().single())["time"].asText())
            .describedAs("time of the JSON line")
            .isEqualTo("2026-11-14T10:15:30Z")
    }

    @Test
    fun `should write the level name of the channel`() {
        val console = Console()

        console.logger().log(Channel.DEBUG, "details")

        assertThat(mapper.readTree(console.lines().single())["level"].asText())
            .describedAs("level of the JSON line")
            .isEqualTo("DEBUG")
    }

    @Test
    fun `should write warnings to the error stream as the text console does`() {
        val console = Console()

        console.logger().log(Channel.WARN, "disk is almost full")

        assertThat(console.lines()).describedAs("lines on the standard output").isEmpty()
        assertThat(console.errorLines()).describedAs("lines on the error stream").hasSize(1)
    }

    @Test
    fun `should keep quotes, backslashes and newlines so that the line parses back to the message`() {
        val console = Console()
        val message = "say \"hi\" from C:\\logs\nsecond line"

        console.logger().log(Channel.INFO, message)

        val line = console.lines().single()
        assertThat(line).describedAs("JSON line of a multi-line message").doesNotContain("\n")
        assertThat(mapper.readTree(line)["message"].asText()).describedAs("message after parsing the line").isEqualTo(message)
    }

    @Test
    fun `should escape control characters so that the line parses back to the message`() {
        val console = Console()
        val message = "bell\u0007 tab\t nul\u0000 unit-separator\u001F"

        console.logger().log(Channel.INFO, message)

        val line = console.lines().single()
        assertThat(mapper.readTree(line)["message"].asText()).describedAs("message after parsing the line").isEqualTo(message)
    }

    @Test
    fun `should write no ANSI escape sequence and keep the visible text`() {
        val console = Console()

        console.logger().log(Channel.INFO, "\u001B[35;1mIngot 1.0\u001B[0m")

        val line = console.lines().single()
        assertThat(line).describedAs("JSON line with colour codes in the message").doesNotContain("\u001B")
        assertThat(mapper.readTree(line)["message"].asText()).describedAs("message without colour codes").isEqualTo("Ingot 1.0")
    }

    @Test
    fun `should not carry the line separator that the console writer appends`() {
        val console = Console()

        console.logger().log(Channel.INFO, "Ingot 1.0" + System.lineSeparator())

        assertThat(mapper.readTree(console.lines().single())["message"].asText())
            .describedAs("message without the appended line separator")
            .isEqualTo("Ingot 1.0")
    }

    @Test
    fun `should write one line per logged message`() {
        val console = Console()

        console.logger().log(Channel.INFO, "first").log(Channel.INFO, "second")

        assertThat(console.lines()).describedAs("lines written for two messages").hasSize(2)
    }

    @Test
    fun `should not write messages below the threshold`() {
        val console = Console(threshold = Channel.WARN)

        console.logger().log(Channel.INFO, "routine")

        assertThat(console.lines()).describedAs("lines below the WARN threshold").isEmpty()
    }

    @Test
    fun `should write trace_id and span_id when a span is active`() {
        val console = Console(trace = TraceContext { TraceIds(traceId = "4bf92f3577b34da6a3ce929d0e0e4736", spanId = "00f067aa0ba902b7") })

        console.logger().log(Channel.INFO, "inside a span")

        val node = mapper.readTree(console.lines().single())
        assertThat(node["trace_id"].asText()).describedAs("trace_id of the JSON line").isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736")
        assertThat(node["span_id"].asText()).describedAs("span_id of the JSON line").isEqualTo("00f067aa0ba902b7")
    }

    @Test
    fun `should omit trace_id and span_id when no span is visible`() {
        val console = Console(trace = TraceContext { null })

        console.logger().log(Channel.INFO, "outside a span")

        val node = mapper.readTree(console.lines().single())
        assertThat(node.has("trace_id")).describedAs("trace_id without a span").isFalse()
        assertThat(node.has("span_id")).describedAs("span_id without a span").isFalse()
    }

    @Test
    fun `should omit trace fields when no span is current in the OpenTelemetry API`() {
        // given: the console reads the active span through the real API, and no span is current on this thread
        val console = Console(trace = OpenTelemetryTraceContext)

        // when: a line is logged
        console.logger().log(Channel.INFO, "outside any span")

        // then: neither field is written, not even as null
        val node = mapper.readTree(console.lines().single())
        assertThat(node.has("trace_id")).describedAs("trace_id without a span").isFalse()
        assertThat(node.has("span_id")).describedAs("span_id without a span").isFalse()
    }

    @Test
    fun `should write the ids of the span that the OpenTelemetry API reports as current`() {
        val console = Console(trace = OpenTelemetryTraceContext)
        val spanContext = SpanContext.create("4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7", TraceFlags.getSampled(), TraceState.getDefault())

        Span.wrap(spanContext).makeCurrent().use { console.logger().log(Channel.INFO, "inside a span") }

        val node = mapper.readTree(console.lines().single())
        assertThat(node["trace_id"].asText()).describedAs("trace_id of the current span").isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736")
        assertThat(node["span_id"].asText()).describedAs("span_id of the current span").isEqualTo("00f067aa0ba902b7")
    }

    private inner class Console(
        threshold: Channel = Channel.ALL,
        trace: TraceContext = TraceContext { null },
    ) {
        private val out = ByteArrayOutputStream()
        private val err = ByteArrayOutputStream()
        private val logger = JsonLogger(
            out = PrintStream(out, true, StandardCharsets.UTF_8),
            err = PrintStream(err, true, StandardCharsets.UTF_8),
            threshold = threshold,
            clock = fixedClock,
            traceContext = trace,
        )

        fun logger(): JsonLogger = logger

        fun lines(): List<String> = out.toString(StandardCharsets.UTF_8).lines().filter { it.isNotEmpty() }

        fun errorLines(): List<String> = err.toString(StandardCharsets.UTF_8).lines().filter { it.isNotEmpty() }
    }

}
