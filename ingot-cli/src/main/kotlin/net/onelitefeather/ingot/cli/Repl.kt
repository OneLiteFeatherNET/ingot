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

import com.fasterxml.jackson.databind.ObjectMapper
import org.jline.reader.Candidate
import org.jline.reader.Completer
import org.jline.reader.EndOfFileException
import org.jline.reader.LineReaderBuilder
import org.jline.reader.UserInterruptException
import org.jline.terminal.Terminal
import java.io.PrintStream

/**
 * The names and aliases of the server's commands, for completion. A server without the listing gives none, and the REPL
 * still runs.
 */
fun commandNames(client: IngotClient): List<String> {
    val reply = try {
        client.listCommands()
    } catch (_: ServerUnreachableException) {
        return emptyList()
    }
    if (reply.status != 200) return emptyList()
    val commands = ObjectMapper().readTree(reply.body).path("commands")
    return commands.flatMap { command ->
        listOf(command.path("name").asText()) + command.path("aliases").map { it.asText() }
    }.sorted()
}

/**
 * The interactive mode. Each line is one command. `:quit` leaves, and Ctrl-D on an empty line leaves with exit code 0.
 * Ctrl-C discards the line. A dangerous command asks `[y/N]` on the terminal and runs only on `y`.
 */
fun runRepl(
    client: IngotClient,
    terminal: Terminal,
    context: String,
    json: Boolean,
    yes: Boolean,
    out: PrintStream,
    err: PrintStream,
): Int {
    val names = commandNames(client)
    if (names.isEmpty()) {
        err.println("The server gives no command listing. Completion is off and dangerous commands run at once.")
    }
    val completer = Completer { _, line, candidates ->
        if (line.wordIndex() == 0) names.forEach { candidates.add(Candidate(it)) }
    }
    val reader = LineReaderBuilder.builder().terminal(terminal).completer(completer).build()
    val exec = Exec(
        client = client,
        out = out,
        err = err,
        options = ExecOptions(
            json = json,
            yes = yes,
            keepGoing = false,
            interactive = true,
            confirm = { command, reason ->
                reader.readLine("Run '$command'${if (reason.isEmpty()) "" else " ($reason)"}? [y/N] ").trim().lowercase().let { it == "y" || it == "yes" }
            },
        ),
    )
    while (true) {
        val line = try {
            reader.readLine("ingot ($context)> ").trim()
        } catch (_: UserInterruptException) {
            continue
        } catch (_: EndOfFileException) {
            return ExitCode.OK
        }
        when {
            line.isEmpty() -> continue
            line == ":quit" -> return ExitCode.OK
            line.startsWith(":") -> err.println("Unknown builtin ${line.substringBefore(' ')}. Builtins: :quit")
            else -> exec.runCommand(line)
        }
    }
}
