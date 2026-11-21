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
import org.jline.reader.Completer
import org.jline.reader.EndOfFileException
import org.jline.terminal.impl.DumbTerminal
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermissions

/**
 * The history file of the REPL, through the line reader the REPL builds. Every test types its lines on a dumb terminal and
 * reads the file in its own temporary directory.
 */
internal class ReplHistoryTest {

    @TempDir
    lateinit var home: Path

    private val posix = FileSystems.getDefault().supportedFileAttributeViews().contains("posix")

    /** Types [lines] into the reader the REPL builds with [file] as its history, until the input ends. */
    private fun type(file: Path?, vararg lines: String): List<String> {
        val keys = ByteArrayInputStream(lines.joinToString("") { "$it\n" }.toByteArray(StandardCharsets.UTF_8))
        val typed = mutableListOf<String>()
        DumbTerminal("scripted", "dumb", keys, ByteArrayOutputStream(), StandardCharsets.UTF_8).use { terminal ->
            val reader = buildLineReader(terminal, Completer { _, _, _ -> }, file)
            while (true) {
                try {
                    typed.add(reader.readLine("> "))
                } catch (_: EndOfFileException) {
                    return typed
                }
            }
        }
    }

    private fun mode(path: Path): String = PosixFilePermissions.toString(Files.getPosixFilePermissions(path))

    @Test
    fun `should keep the history under XDG_STATE_HOME and else under the local state directory of the home`() {
        assertThat(ReplHistory.defaultPath(mapOf("XDG_STATE_HOME" to "/state"), "/home/op"))
            .describedAs("with XDG_STATE_HOME").isEqualTo(Paths.get("/state", "ingot", "history"))
        assertThat(ReplHistory.defaultPath(mapOf("XDG_STATE_HOME" to " "), "/home/op"))
            .describedAs("with a blank XDG_STATE_HOME").isEqualTo(Paths.get("/home/op", ".local", "state", "ingot", "history"))
        assertThat(ReplHistory.defaultPath(emptyMap(), "/home/op"))
            .describedAs("without XDG_STATE_HOME").isEqualTo(Paths.get("/home/op", ".local", "state", "ingot", "history"))
    }

    @Test
    fun `should write typed lines to a history file that only its owner may read`() {
        assumeTrue(posix, "owner-only permissions need a POSIX file system")
        val file = home.resolve("state").resolve("ingot").resolve("history")

        assertThat(ReplHistory.prepare(file)).describedAs("history file usable").isTrue()
        type(file, "status", "failures")

        assertThat(Files.readString(file)).describedAs("history").contains("status").contains("failures")
        assertThat(mode(file)).describedAs("file mode").isEqualTo("rw-------")
        assertThat(mode(file.parent)).describedAs("directory mode").isEqualTo("rwx------")
    }

    @Test
    fun `should narrow an existing history file to owner-only permissions`() {
        assumeTrue(posix, "owner-only permissions need a POSIX file system")
        val file = home.resolve("history")
        Files.writeString(file, "")
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"))

        assertThat(ReplHistory.prepare(file)).describedAs("history file usable").isTrue()

        assertThat(mode(file)).describedAs("file mode").isEqualTo("rw-------")
    }

    @Test
    fun `should not write a sensitive line to the history file`() {
        val file = home.resolve("history")
        ReplHistory.prepare(file)

        val typed = type(
            file,
            "token-generate ci m",
            "token-regenerate ci",
            "token-modify ci --secret=hunter2",
            "token-modify ci -s hunter3",
            "status",
        )

        assertThat(typed).describedAs("every line still reaches the REPL").hasSize(5)
        assertThat(Files.readString(file)).describedAs("history")
            .contains("status")
            .doesNotContain("token-generate")
            .doesNotContain("token-regenerate")
            .doesNotContain("hunter2")
            .doesNotContain("hunter3")
    }

    @Test
    fun `should load the lines of an earlier session from the history file`() {
        val file = home.resolve("history")
        ReplHistory.prepare(file)
        type(file, "status")

        val keys = ByteArrayInputStream("\u0010\n".toByteArray(StandardCharsets.UTF_8))
        val recalled = DumbTerminal("scripted", "xterm-256color", keys, ByteArrayOutputStream(), StandardCharsets.UTF_8).use { terminal ->
            buildLineReader(terminal, Completer { _, _, _ -> }, file).readLine("> ")
        }

        assertThat(recalled).describedAs("Ctrl-P recalls the last line of the earlier session").isEqualTo("status")
    }

    @Test
    fun `should run without a history file when its directory cannot be created`() {
        val blocker = home.resolve("not-a-directory")
        Files.writeString(blocker, "")
        val file = blocker.resolve("ingot").resolve("history")

        assertThat(ReplHistory.prepare(file)).describedAs("history file usable").isFalse()
        assertThat(type(null, "status")).describedAs("lines without a history file").containsExactly("status")
    }

}
