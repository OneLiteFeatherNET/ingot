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

import org.jline.reader.Completer
import org.jline.reader.LineReader
import org.jline.reader.LineReaderBuilder
import org.jline.reader.impl.history.DefaultHistory
import org.jline.terminal.Terminal
import java.io.IOException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermissions
import java.time.Instant

/** The history file of the REPL: where it lives, its owner-only permissions, and which lines never reach it. */
internal object ReplHistory {

    /**
     * The commands whose input the server marks sensitive (`mayHideInput`): their secret may stand on the line. Version 1
     * of the console socket does not carry that mark, so the CLI keeps the names of the server's own commands.
     */
    private val SENSITIVE_COMMANDS = setOf("token-generate", "token-regenerate")

    private val OWNER_ONLY = PosixFilePermissions.fromString("rw-------")

    private val POSIX = FileSystems.getDefault().supportedFileAttributeViews().contains("posix")

    /** `$XDG_STATE_HOME/ingot/history`, else `~/.local/state/ingot/history`. */
    fun defaultPath(env: Map<String, String>, home: String = System.getProperty("user.home")): Path =
        env["XDG_STATE_HOME"]?.takeIf { it.isNotBlank() }?.let { Paths.get(it, "ingot", "history") }
            ?: Paths.get(home, ".local", "state", "ingot", "history")

    /**
     * Creates [file] and its directory for the owner only, or narrows an existing file to the owner. Returns false when
     * the file cannot be used, and the REPL then keeps its history in memory.
     */
    fun prepare(file: Path): Boolean =
        try {
            val directory = file.toAbsolutePath().parent
            if (!Files.isDirectory(directory)) {
                if (POSIX) Files.createDirectories(directory, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
                else Files.createDirectories(directory)
            }
            when {
                !POSIX -> if (Files.notExists(file)) Files.createFile(file)
                Files.notExists(file) -> Files.createFile(file, PosixFilePermissions.asFileAttribute(OWNER_ONLY))
                else -> Files.setPosixFilePermissions(file, OWNER_ONLY)
            }
            true
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }

    /** A line that may carry a secret: a sensitive command, or a `--secret` or `-s` option of any command. */
    fun isSensitive(line: String): Boolean {
        val words = line.trim().split(Regex("\\s+"))
        return words.first().lowercase() in SENSITIVE_COMMANDS || words.any { it == "-s" || it.startsWith("--secret") }
    }

}

/** The history of the REPL. A sensitive line is never added, so it is neither recalled nor written to the file. */
private class SensitiveLineFilter : DefaultHistory() {

    override fun add(time: Instant, line: String) {
        if (!ReplHistory.isSensitive(line)) super.add(time, line)
    }

}

/** The line reader of the REPL. With [historyFile], the history of earlier sessions is loaded from it and each line saved. */
internal fun buildLineReader(terminal: Terminal, completer: Completer, historyFile: Path?): LineReader {
    val builder = LineReaderBuilder.builder().terminal(terminal).completer(completer).history(SensitiveLineFilter())
    if (historyFile != null) builder.variable(LineReader.HISTORY_FILE, historyFile)
    return builder.build()
}
