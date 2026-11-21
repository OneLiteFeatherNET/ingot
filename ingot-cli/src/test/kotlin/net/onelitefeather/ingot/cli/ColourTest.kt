/*
 * Copyright (c) 2023 dzikoysk
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
package net.onelitefeather.ingot.cli

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Path

private const val ESC = "\u001b"

private fun stripped(vararg writes: String): String {
    val sink = ByteArrayOutputStream()
    AnsiStrippingOutputStream(sink).use { stream -> writes.forEach { stream.write(it.toByteArray(StandardCharsets.UTF_8)) } }
    return sink.toString(StandardCharsets.UTF_8)
}

internal class ColourTest {

    @TempDir
    lateinit var dir: Path

    @Test
    fun `should turn colour off when stdout is not a terminal`() {
        assertThat(colourEnabled(stdoutIsTerminal = false, env = emptyMap())).describedAs("colour in a pipe").isFalse()
    }

    @Test
    fun `should turn colour off on a terminal when NO_COLOR is set`() {
        assertThat(colourEnabled(stdoutIsTerminal = true, env = mapOf("NO_COLOR" to "1"))).describedAs("colour with NO_COLOR").isFalse()
    }

    @Test
    fun `should keep colour on a terminal when NO_COLOR is unset or empty`() {
        assertThat(colourEnabled(stdoutIsTerminal = true, env = emptyMap())).describedAs("colour without NO_COLOR").isTrue()
        assertThat(colourEnabled(stdoutIsTerminal = true, env = mapOf("NO_COLOR" to ""))).describedAs("colour with an empty NO_COLOR").isTrue()
    }

    @Test
    fun `should strip colour sequences and keep the text`() {
        assertThat(stripped("$ESC[1;32mok$ESC[0m done")).describedAs("stripped text").isEqualTo("ok done")
    }

    @Test
    fun `should strip a sequence that is split across two writes`() {
        assertThat(stripped("a$ESC[3", "1mb")).describedAs("stripped text").isEqualTo("ab")
    }

    @Test
    fun `should strip a title sequence and keep non ASCII text`() {
        assertThat(stripped("$ESC]0;title\u0007Größe")).describedAs("stripped text").isEqualTo("Größe")
    }

    @Test
    fun `should strip colour from a log line and leave a plain line as it is`() {
        assertThat(stripAnsi("$ESC[33mWARN$ESC[0m disk")).describedAs("coloured line").isEqualTo("WARN disk")
        assertThat(stripAnsi("plain [line]")).describedAs("plain line").isEqualTo("plain [line]")
    }

    @Test
    fun `should print command output without colour sequences when stdout is not a terminal`() {
        StubIngot { answer(0, text = "\\u001b[32mIngot status\\u001b[0m") }.use { stub ->
            val out = ByteArrayOutputStream()
            val exit = runCli(
                args = listOf("--url", stub.url, "exec", "status"),
                env = mapOf("INGOT_CONFIG" to dir.resolve("absent.yaml").toString(), "INGOT_TOKEN" to "ci:ci-secret"),
                stdin = ByteArrayInputStream(ByteArray(0)),
                out = PrintStream(out, true, StandardCharsets.UTF_8),
                err = PrintStream(ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                interactive = false,
            )

            assertThat(exit).describedAs("exit code").isEqualTo(ExitCode.OK)
            assertThat(out.toString(StandardCharsets.UTF_8)).describedAs("stdout").doesNotContain(ESC).contains("Ingot status")
        }
    }

}
