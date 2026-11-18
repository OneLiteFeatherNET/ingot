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

package net.onelitefeather.ingot.plugin

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

// Each test compiles its own samples in process into a temporary directory. The classpath holds this server's classes and the
// Kotlin standard library, so a sample sees the marker the way a plugin does.
class ExperimentalIngotPluginApiTest {

    @TempDir
    lateinit var tempDir: Path

    private val probe = """
        package sample
        import net.onelitefeather.ingot.plugin.ExperimentalIngotPluginApi
        @ExperimentalIngotPluginApi
        class Probe { fun value(): Int = 1 }
    """.trimIndent()

    @Test
    fun useWithoutOptInWarnsWithTheMarkerName() {
        val output = compile(
            probe,
            """
            package sample
            fun read(): Int = Probe().value()
            """.trimIndent(),
        )

        assertThat(output).describedAs("the compiler warns about a use without opt-in").contains("warning:")
        assertThat(output).describedAs("the warning names the marker").contains("ExperimentalIngotPluginApi")
        assertThat(output).describedAs("the warning carries the text of the marker").contains("may change without a major version")
    }

    @Test
    fun useWithOptInCompilesWithoutWarning() {
        val output = compile(
            probe,
            """
            package sample
            import net.onelitefeather.ingot.plugin.ExperimentalIngotPluginApi
            @OptIn(ExperimentalIngotPluginApi::class)
            fun read(): Int = Probe().value()
            """.trimIndent(),
        )

        assertThat(output).describedAs("a use with opt-in compiles without a warning").doesNotContain("warning:")
    }

    // Returns the compiler output. The samples must compile, because the warning is what the test is about.
    private fun compile(vararg sources: String): String {
        val src = tempDir.resolve("src").createDirectories()
        val files = sources.mapIndexed { index, text ->
            src.resolve("Sample$index.kt").apply { writeText(text) }.toString()
        }
        val classpath = listOf(ExperimentalIngotPluginApi::class.java, Unit::class.java)
            .map { File(it.protectionDomain.codeSource.location.toURI()).path }
        val output = ByteArrayOutputStream()

        val status = K2JVMCompiler().exec(
            PrintStream(output, true),
            "-no-stdlib", "-no-reflect",
            "-classpath", classpath.joinToString(File.pathSeparator),
            "-d", tempDir.resolve("classes").toString(),
            *files.toTypedArray(),
        )

        assertThat(status).describedAs("the samples compile, with or without warnings").isEqualTo(ExitCode.OK)
        return output.toString()
    }
}
