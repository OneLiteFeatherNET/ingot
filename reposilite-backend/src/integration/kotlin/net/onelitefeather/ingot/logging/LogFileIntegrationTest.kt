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
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Each start runs in a child JVM, because tinylog reads its configuration once per JVM. The child works in a temporary
 * directory, so the rolling file under `logs/` is written there and nowhere else.
 */
internal class LogFileIntegrationTest {

    @Test
    fun `should write the rolling log file by default`(@TempDir workingDirectory: Path) {
        // when: the server starts with no logging variable set
        val probe = runProbe(workingDirectory, environment = emptyMap())

        // then: the start succeeded and the rolling file received the startup lines
        assertThat(probe.exitCode).describedAs("exit code of the probe, output: ${probe.output}").isZero()
        assertThat(rollingLogFiles(workingDirectory)).describedAs("rolling log files in the working directory").isNotEmpty()
    }

    @Test
    fun `should not create the rolling log file when INGOT_LOG_FILE is false`(@TempDir workingDirectory: Path) {
        // when: the server starts with the file writer switched off
        val probe = runProbe(workingDirectory, environment = mapOf("INGOT_LOG_FILE" to "false"))

        // then: the start succeeded, the console still shows the startup lines, and no rolling file exists
        assertThat(probe.exitCode).describedAs("exit code of the probe, output: ${probe.output}").isZero()
        assertThat(probe.output).describedAs("console output with INGOT_LOG_FILE=false").contains("--- Environment")
        assertThat(rollingLogFiles(workingDirectory)).describedAs("rolling log files with INGOT_LOG_FILE=false").isEmpty()
    }

    @Test
    fun `should not create the rolling log file when the legacy REPOSILITE_LOG_FILE is false`(@TempDir workingDirectory: Path) {
        // when: only the legacy spelling is set
        val probe = runProbe(workingDirectory, environment = mapOf("REPOSILITE_LOG_FILE" to "false"))

        // then: the legacy spelling is honoured
        assertThat(probe.exitCode).describedAs("exit code of the probe, output: ${probe.output}").isZero()
        assertThat(rollingLogFiles(workingDirectory)).describedAs("rolling log files with REPOSILITE_LOG_FILE=false").isEmpty()
    }

    private fun rollingLogFiles(workingDirectory: Path): List<Path> {
        val logs = workingDirectory.resolve("logs")
        if (!Files.isDirectory(logs)) return emptyList()
        Files.list(logs).use { return it.toList() }
    }

    private fun runProbe(workingDirectory: Path, environment: Map<String, String>): ProbeResult {
        val javaBinary = System.getProperty("java.home") + "/bin/java"
        val command = listOf(
            javaBinary,
            "-cp", System.getProperty("java.class.path"),
            LogFileProbe::class.java.name,
            workingDirectory.toAbsolutePath().toString(),
        )
        val builder = ProcessBuilder(command).redirectErrorStream(true).directory(workingDirectory.toFile())
        // Logging variables of the machine running the tests would make the probe depend on the host.
        LOGGING_VARIABLES.forEach { builder.environment().remove(it) }
        builder.environment().putAll(environment)

        val process = builder.start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        check(process.waitFor(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "probe did not finish within $PROBE_TIMEOUT_SECONDS seconds" }
        return ProbeResult(process.exitValue(), output)
    }

    private data class ProbeResult(val exitCode: Int, val output: String)

    private companion object {
        const val PROBE_TIMEOUT_SECONDS = 120L
        val LOGGING_VARIABLES = listOf(
            "INGOT_LOG_FILE", "REPOSILITE_LOG_FILE",
            "INGOT_LOG_FORMAT", "REPOSILITE_LOG_FORMAT",
            "INGOT_LOG_LEVEL", "REPOSILITE_LOG_LEVEL",
            "INGOT_LOG_LEVELS", "REPOSILITE_LOG_LEVELS",
            "INGOT_ACCESS_LOG", "REPOSILITE_ACCESS_LOG",
        )
    }

}
