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
import org.jline.reader.LineReader
import org.jline.reader.UserInterruptException
import org.jline.terminal.Terminal
import org.jline.terminal.Terminal.Signal
import java.io.PrintStream
import java.nio.file.Path

/** Opens one session on the console socket. Log lines reach [ConsoleConnector.open]'s callback as they arrive. */
fun interface ConsoleConnector {
    fun open(onLog: (ConsoleLogLine) -> Unit): ConsoleSocket
}

/**
 * The interactive mode, on one console socket session in protocol 1. Each line is one `execute` frame, Tab sends one
 * `complete` frame, and no HTTP request is made while the session runs. `:quit` leaves, and Ctrl-D on an empty line
 * leaves with exit code 0. Ctrl-C discards the line, and while a command runs it stops the wait for its result on the
 * client only and keeps the connection open. A dangerous command asks `[y/N]` on the terminal, and only `y` accepts it.
 * With [historyFile], the lines are kept there for the next session, except a line that may carry a secret.
 */
fun runRepl(
    client: IngotClient,
    connector: ConsoleConnector,
    terminal: Terminal,
    context: String,
    json: Boolean,
    yes: Boolean,
    out: PrintStream,
    err: PrintStream,
    historyFile: Path? = null,
): Int {
    var socket: ConsoleSocket? = null
    val completer = Completer { _, line, candidates ->
        val completion = runCatching { socket?.complete(line.line(), line.cursor()) }.getOrNull()
        completion?.candidates?.forEach { candidates.add(Candidate(it)) }
    }
    val reader = buildLineReader(terminal, completer, historyFile)
    val session = try {
        connector.open { log -> showLog(reader, log) }
    } catch (_: ServerUnreachableException) {
        err.println("The server could not be reached. Check INGOT_URL and the network.")
        return ExitCode.UNREACHABLE
    } catch (refused: ConsoleSocketRefusedException) {
        return refusedHello(client, refused, err)
    } catch (lost: ConsoleSocketLostException) {
        if (listingMissing(client)) return tooOld(err)
        err.println("${lost.message}. Nothing ran.")
        return ExitCode.UNREACHABLE
    }
    socket = session
    session.use {
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
                else -> runLine(session, reader, line, json, yes, out, err)?.let { return it }
            }
        }
    }
}

/** Runs one line. Returns an exit code when the session has to end, and null when the REPL goes on. */
private fun runLine(socket: ConsoleSocket, reader: LineReader, line: String, json: Boolean, yes: Boolean, out: PrintStream, err: PrintStream): Int? {
    // Ctrl-C while the result is awaited. The confirmation prompt is a readLine, which handles Ctrl-C itself meanwhile.
    val previous = reader.terminal.handle(Signal.INT) { socket.stopWaiting() }
    val result = try {
        socket.execute(line) { command, reason -> yes || askYesNo(reader, command, reason) }
    } catch (_: ConsoleWaitStoppedException) {
        err.println("Stopped waiting for '$line'. The server may still complete it, and its result is not shown.")
        return null
    } catch (_: ConsoleSocketLostException) {
        err.println("The connection to the server was lost. '$line' may or may not have run, and it is not sent again.")
        return ExitCode.UNREACHABLE
    } catch (refused: ConsoleSocketRefusedException) {
        err.println(refused.message)
        return if (refused.code == "unauthorized") ExitCode.DENIED else null
    } finally {
        reader.terminal.handle(Signal.INT, previous)
    }
    if (json) {
        val answer = mapOf(
            "command" to line,
            "status" to result.status,
            "exitCode" to result.exitCode,
            "output" to result.output.map { mapOf("stream" to it.stream, "text" to it.text) },
            "data" to result.data,
            "node" to result.node,
            "confirmed" to result.confirmed,
        )
        out.println(MAPPER.writeValueAsString(answer))
    } else {
        result.output.forEach { if (it.stream == "stderr") err.println(it.text) else out.println(it.text) }
    }
    return null
}

private fun askYesNo(reader: LineReader, command: String, reason: String): Boolean =
    try {
        reader.readLine("Run '$command'${if (reason.isEmpty()) "" else " ($reason)"}? [y/N] ").trim().lowercase().let { it == "y" || it == "yes" }
    } catch (_: UserInterruptException) {
        false
    } catch (_: EndOfFileException) {
        false
    }

/** Log lines go to the terminal above the prompt, never into the command output. */
private fun showLog(reader: LineReader, log: ConsoleLogLine) {
    if (log.dropped > 0) reader.printAbove("(${log.dropped} log lines were dropped)")
    reader.printAbove(log.text)
}

/**
 * A refused hello. A version 1 server names the reason. A legacy server refuses a hello the same way it refuses a wrong
 * token, so the command listing tells the two apart: an older server has none.
 */
private fun refusedHello(client: IngotClient, refused: ConsoleSocketRefusedException, err: PrintStream): Int {
    if (refused.code == "unsupported_protocol" || (refused.code == null && listingMissing(client))) return tooOld(err)
    err.println("The token is not accepted, or it has no console area (ingot.admin.console).")
    return ExitCode.DENIED
}

private fun tooOld(err: PrintStream): Int {
    err.println("The server does not speak version 1 of the console protocol. Upgrade the server, or use 'ingot exec'.")
    return ExitCode.SERVER_TOO_OLD
}

private fun listingMissing(client: IngotClient): Boolean =
    try {
        client.listCommands().status == 404
    } catch (_: ServerUnreachableException) {
        false
    }

private val MAPPER = ObjectMapper()
