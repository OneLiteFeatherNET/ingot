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

package net.onelitefeather.ingot.deploy

import com.reposilite.journalist.Channel
import com.reposilite.journalist.Logger
import com.reposilite.journalist.backend.PrintStreamLogger
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets.UTF_8
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The warning is asserted on the text a real logger writes, captured in memory, so the test reads no console and no file.
 */
internal class DeployTargetWarningTest {

    @Test
    fun `a target all with the legacy switch off is logged as exactly one warning`() {
        // given: the target asks for the dashboard and the legacy switch asks for the server only
        val captured = CapturedLog()
        val resolution = DeployTargetResolver.resolve(
            commandLine = "all",
            properties = emptyMap(),
            environment = emptyMap(),
            legacyDefaultFrontend = false,
        )

        // when: the resolution is logged at start
        resolution.logWarnings(captured.logger)

        // then: the disagreement is written exactly once, and it names both values
        val warnings = captured.text.lines().filter { it.contains("overrides the legacy setting") }
        assertThat(warnings).describedAs("warning lines written at start").hasSize(1)
        assertThat(warnings.single())
            .describedAs("warning text")
            .contains("'all'")
            .contains("defaultFrontend=false")
    }

    @Test
    fun `agreement between the target and the legacy switch writes no warning`() {
        // given: the target is server and the legacy switch is off
        val captured = CapturedLog()
        val resolution = DeployTargetResolver.resolve(
            commandLine = "server",
            properties = emptyMap(),
            environment = emptyMap(),
            legacyDefaultFrontend = false,
        )

        // when: the resolution is logged at start
        resolution.logWarnings(captured.logger)

        // then: nothing is written about the target
        assertThat(captured.text).describedAs("log output").doesNotContain("overrides the legacy setting")
    }

    /** A real logger whose output goes to memory. */
    private class CapturedLog {
        private val buffer = ByteArrayOutputStream()
        private val stream = PrintStream(buffer, true, UTF_8)
        val logger: Logger = PrintStreamLogger(stream, stream, Channel.ALL, true)

        val text: String
            get() {
                stream.flush()
                return buffer.toString(UTF_8)
            }
    }

}
