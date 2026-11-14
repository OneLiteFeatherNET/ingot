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
import java.nio.file.Files
import java.nio.file.Path

/**
 * The JSON console format of a real start, in a child JVM. The probe writes its lines through the same routes as the server.
 */
internal class LogFormatIntegrationTest {

    private val mapper = ObjectMapper()

    @Test
    fun `should write each console line of the probe as a JSON object with the logged message`(@TempDir workingDirectory: Path) {
        // when: the server starts with the JSON console format
        val probe = LoggingProbeProcess.run(LogLevelProbe::class.java, workingDirectory, environment = mapOf("INGOT_LOG_FORMAT" to "json"))

        // then: the lines of the probe are objects that carry the message and the level of each call
        assertThat(probe.exitCode).describedAs("exit code of the probe, output: ${probe.output}").isZero()
        val objects = probe.output.lines().filter { it.startsWith("{") }.map { mapper.readTree(it) }
        assertThat(objects.filter { it["message"].asText() == "probe info line" }.map { it["level"].asText() })
            .describedAs("levels of the objects that carry the info message, output: ${probe.output}")
            .containsExactly("INFO")
        assertThat(objects.map { it["message"].asText() })
            .describedAs("messages of the JSON console lines")
            .contains("probe slf4j info line")
    }

    @Test
    fun `should keep the rolling file in text form with the JSON console format`(@TempDir workingDirectory: Path) {
        // when: the server starts with the JSON console format and the file writer on
        val probe = LoggingProbeProcess.run(LogLevelProbe::class.java, workingDirectory, environment = mapOf("INGOT_LOG_FORMAT" to "json"))

        // then: the rolling file holds the text lines and no JSON object
        assertThat(probe.exitCode).describedAs("exit code of the probe, output: ${probe.output}").isZero()
        val lines = Files.list(workingDirectory.resolve("logs")).use { files ->
            files.toList().flatMap { Files.readAllLines(it) }
        }
        assertThat(lines).describedAs("lines of the rolling file").anyMatch { it.contains("probe info line") }
        assertThat(lines).describedAs("JSON objects in the rolling file").noneMatch { it.startsWith("{") }
    }

}
