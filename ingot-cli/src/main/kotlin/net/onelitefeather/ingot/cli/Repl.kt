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
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

/**
 * Opens one session on the console socket. Log lines reach [ConsoleConnector.open]'s callback as they arrive. With a
 * resume, the server replays the log after the point where the earlier session stopped.
 */
fun interface ConsoleConnector {
    fun open(resume: ConsoleResume?, onLog: (ConsoleLogLine) -> Unit): ConsoleSocket
}

/**
 * The interactive mode, on one console socket session in protocol 1. Each line is one `execute` frame, Tab sends one
 * `complete` frame, and no HTTP request is made while the session runs. `:quit` leaves, and Ctrl-D on an empty line
 * leaves with exit code 0. Ctrl-C discards the line, and while a command runs it stops the wait for its result on the
 * client only and keeps the connection open. A dangerous command asks `[y/N]` on the terminal, and only `y` accepts it.
 * With [historyFile], the lines are kept there for the next session, except a line that may carry a secret.
 * When the connection drops, the REPL reconnects with a backoff ([backoff], waited through [pause]) and resumes the log
 * after the last line it showed. A command that had no result is reported as unknown and never sent again, and a line
 * typed while the connection is down is not sent. A session the server ends with 1008 is not reopened.
 * `:output` switches between text and JSON. `:context <name>` asks [switchContext], which answers null when it accepts
 * the switch, and then the session ends with exit code 0, or the reason it refuses.
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
    backoff: Backoff = Backoff(),
    pause: (Duration) -> Unit = { Thread.sleep(it.toMillis()) },
    switchContext: (name: String) -> String? = { "This session cannot switch contexts." },
): Int {
    var output = json
    lateinit var session: ConsoleSocket
    val completer = Completer { _, line, candidates ->
        val completion = runCatching { session.complete(line.line(), line.cursor()) }.getOrNull()
        completion?.candidates?.forEach { candidates.add(Candidate(it)) }
    }
    val reader = buildLineReader(terminal, completer, historyFile)
    // The last log number of the current session, or -1 before its first log line. Each session counts its own.
    var lastSeq = AtomicLong(-1)
    val open = { resume: ConsoleResume? ->
        val seen = AtomicLong(-1)
        connector.open(resume) { log -> seen.set(log.seq); showLog(reader, log) }.also { lastSeq = seen }
    }
    session = try {
        open(null)
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
    val reconnector = Reconnector(backoff, pause, err, open)
    val recover = { old: ConsoleSocket ->
        val resume = old.welcome.boot?.let { ConsoleResume(it, lastSeq.get().takeIf { seq -> seq >= 0 } ?: old.welcome.seq ?: 0) }
        reconnect(reconnector, old, resume, err)
    }
    try {
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
                line.startsWith(":") -> when (val builtin = parseBuiltin(line)) {
                    Builtin.Quit -> return ExitCode.OK
                    is Builtin.Output -> output = builtin.json
                    is Builtin.Context -> when (val name = builtin.name) {
                        null -> err.println("Context: $context")
                        // An accepted switch ends this session, and the caller opens the next one.
                        else -> switchContext(name)?.let { err.println(it) } ?: return ExitCode.OK
                    }
                    is Builtin.Invalid -> err.println(builtin.message)
                }
                !session.isOpen -> {
                    err.println("The connection to the server was lost, so '$line' was not sent.")
                    session = recover(session)
                }
                else -> {
                    runLine(session, reader, line, output, yes, out, err)?.let { return it }
                    if (!session.isOpen) session = recover(session)
                }
            }
        }
    } catch (exit: ReplExit) {
        return exit.code
    } finally {
        session.close()
    }
}

/** Ends the REPL with [code] from inside a reconnect. */
private class ReplExit(val code: Int) : RuntimeException(null, null, false, false)

/** Closes [old] and opens a new session that resumes at [resume], or ends the REPL when the server will not have it. */
private fun reconnect(reconnector: Reconnector, old: ConsoleSocket, resume: ConsoleResume?, err: PrintStream): ConsoleSocket {
    old.close()
    if (old.closeCode == CLOSE_POLICY_VIOLATION) {
        err.println("The server ended the session: the token is no longer accepted, or the session expired.")
        throw ReplExit(ExitCode.DENIED)
    }
    val fresh = try {
        reconnector.reconnect(resume)
    } catch (refused: ConsoleSocketRefusedException) {
        if (refused.code == "unsupported_protocol") throw ReplExit(tooOld(err))
        err.println("The server refused the new connection. The token is not accepted, or it has no console area (ingot.admin.console).")
        throw ReplExit(ExitCode.DENIED)
    }
    err.println(
        when (fresh.welcome.replay) {
            "resumed" -> "Reconnected. The log continues where it stopped."
            "full" -> "Reconnected to a restarted server or another replica. Its cached log is shown again."
            else -> "Reconnected."
        }
    )
    return fresh
}

/** The close code of a session the server refuses: a revoked token, an expired session. It is not reopened. */
private const val CLOSE_POLICY_VIOLATION = 1008

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
        return null
    } catch (refused: ConsoleSocketRefusedException) {
        if (refused.code == "shutdown") {
            // The server is stopping and closes the connection next, so the REPL reconnects.
            err.println("The server is stopping. '$line' may or may not have run, and it is not sent again.")
            socket.close()
            return null
        }
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
