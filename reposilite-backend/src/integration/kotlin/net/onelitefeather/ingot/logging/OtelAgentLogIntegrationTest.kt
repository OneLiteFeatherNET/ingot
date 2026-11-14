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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.ServerSocket
import java.nio.file.Path

/**
 * The trace fields of a JSON console line under the OpenTelemetry Java agent. The agent is attached to a child JVM with its
 * exporters off, so no span leaves the machine. The agent jar is resolved by Gradle into the `otelAgent` configuration and
 * passed in the system property `ingot.otelAgent`, so the test runs only through the Gradle task that sets it.
 */
internal class OtelAgentLogIntegrationTest {

    private val mapper = ObjectMapper()

    @Test
    fun `should write a 32 hex trace id on a JSON line that is logged inside a traced request`(@TempDir workingDirectory: Path) {
        // given: the agent jar that Gradle resolved for this test run
        val agent = File(requireNotNull(System.getProperty("ingot.otelAgent")) { "ingot.otelAgent is set by the integration task" })
        assertThat(agent).describedAs("OpenTelemetry Java agent jar").isFile()

        // when: the server starts with the agent, serves one request, and the request logs a line
        val probe = LoggingProbeProcess.run(
            probe = OtelAgentProbe::class.java,
            workingDirectory = workingDirectory,
            environment = mapOf("INGOT_LOG_FORMAT" to "json"),
            arguments = listOf(freePort().toString()),
            jvmArguments = listOf(
                "-javaagent:${agent.absolutePath}",
                "-Dotel.traces.exporter=none",
                "-Dotel.metrics.exporter=none",
                "-Dotel.logs.exporter=none",
            ),
        )

        // then: the probe finished, and the line of the request carries a trace id of 32 lowercase hexadecimal digits
        assertThat(probe.exitCode).describedAs("exit code of the probe, output: ${probe.output}").isZero()
        val line = probe.output.lines()
            .filter { it.startsWith("{") }
            .map { mapper.readTree(it) }
            .single { it["message"].asText() == OtelAgentProbe.MESSAGE }
        assertThat(line["trace_id"].asText())
            .describedAs("trace_id of the line logged inside the request, output: ${probe.output}")
            .matches("[0-9a-f]{32}")
        assertThat(line["span_id"].asText()).describedAs("span_id of the line").matches("[0-9a-f]{16}")
    }

    /** A port that was free a moment ago. The probe binds it right after, so the race is limited to this one start. */
    private fun freePort(): Int =
        ServerSocket(0).use { it.localPort }

}
