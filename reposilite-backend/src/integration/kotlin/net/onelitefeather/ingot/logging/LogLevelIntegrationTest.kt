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
import java.nio.file.Path

/**
 * Each start runs in a child JVM, because the console threshold and tinylog's package levels are read once per JVM. The
 * probe writes one line per level, and the test reads the console of the child.
 */
internal class LogLevelIntegrationTest {

    @Test
    fun `should show info lines and hide debug lines by default`(@TempDir workingDirectory: Path) {
        val probe = runProbe(workingDirectory, environment = emptyMap())

        assertThat(probe.exitCode).describedAs("exit code of the probe, output: ${probe.output}").isZero()
        assertThat(probe.output).describedAs("console with the default threshold").contains("probe info line").doesNotContain("probe debug line")
    }

    @Test
    fun `should show debug lines when INGOT_LOG_LEVEL names DEBUG`(@TempDir workingDirectory: Path) {
        val probe = runProbe(workingDirectory, environment = mapOf("INGOT_LOG_LEVEL" to "DEBUG"))

        assertThat(probe.exitCode).describedAs("exit code of the probe, output: ${probe.output}").isZero()
        assertThat(probe.output).describedAs("console with INGOT_LOG_LEVEL=DEBUG").contains("probe debug line")
    }

    @Test
    fun `should hide lines below the level of the command line flag over INGOT_LOG_LEVEL`(@TempDir workingDirectory: Path) {
        // when: the variable names DEBUG and the flag names WARN
        val probe = runProbe(workingDirectory, environment = mapOf("INGOT_LOG_LEVEL" to "DEBUG"), arguments = listOf("--level", "WARN"))

        // then: the flag decides, so neither the debug nor the info line reaches the console
        assertThat(probe.exitCode).describedAs("exit code of the probe, output: ${probe.output}").isZero()
        assertThat(probe.output).describedAs("console with --level=WARN and INGOT_LOG_LEVEL=DEBUG")
            .doesNotContain("probe debug line")
            .doesNotContain("probe info line")
    }

    @Test
    fun `should refuse an unknown INGOT_LOG_LEVEL and name the variable`(@TempDir workingDirectory: Path) {
        val probe = runProbe(workingDirectory, environment = mapOf("INGOT_LOG_LEVEL" to "verbose"))

        assertThat(probe.exitCode).describedAs("exit code with INGOT_LOG_LEVEL=verbose").isNotZero()
        assertThat(probe.output).describedAs("output with INGOT_LOG_LEVEL=verbose").contains("INGOT_LOG_LEVEL")
    }

    @Test
    fun `should hide info lines of a package that INGOT_LOG_LEVELS names as warn`(@TempDir workingDirectory: Path) {
        val probe = runProbe(workingDirectory, environment = mapOf("INGOT_LOG_LEVELS" to "com.reposilite.web=warn"))

        assertThat(probe.exitCode).describedAs("exit code of the probe, output: ${probe.output}").isZero()
        assertThat(probe.output).describedAs("console with com.reposilite.web=warn")
            .doesNotContain("probe web info line")
            .contains("probe slf4j info line")
    }

    @Test
    fun `should refuse an unknown package level and name the variable and the entry`(@TempDir workingDirectory: Path) {
        val probe = runProbe(workingDirectory, environment = mapOf("INGOT_LOG_LEVELS" to "com.reposilite.web=loud"))

        assertThat(probe.exitCode).describedAs("exit code with an unknown package level").isNotZero()
        assertThat(probe.output).describedAs("output with an unknown package level")
            .contains("INGOT_LOG_LEVELS")
            .contains("com.reposilite.web=loud")
    }

    private fun runProbe(workingDirectory: Path, environment: Map<String, String>, arguments: List<String> = emptyList()): LoggingProbeProcess.Result =
        LoggingProbeProcess.run(LogLevelProbe::class.java, workingDirectory, environment, arguments)

}
