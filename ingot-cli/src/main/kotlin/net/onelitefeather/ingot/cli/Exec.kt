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
import com.fasterxml.jackson.databind.node.ObjectNode
import java.io.PrintStream

/** The exit codes of the CLI, as the specification names them. */
object ExitCode {
    const val OK = 0
    const val FAILED = 1
    const val USAGE = 2
    const val CONFIRMATION_REQUIRED = 3
    const val DENIED = 4
    const val UNREACHABLE = 5
    const val SERVER_TOO_OLD = 6
}

class ExecOptions(
    val json: Boolean,
    /** Confirms dangerous commands without asking. */
    val yes: Boolean,
    val keepGoing: Boolean,
    /** True only when standard input and output are a terminal. A pipe never gets a prompt. */
    val interactive: Boolean,
    /** Asks the user about a dangerous command. Only called when [interactive] is set. */
    val confirm: (command: String, reason: String) -> Boolean,
)

/** Runs command lines against one server and turns each answer into output and an exit code. */
class Exec(
    private val client: IngotClient,
    private val out: PrintStream,
    private val err: PrintStream,
    private val options: ExecOptions,
) {

    private val mapper = ObjectMapper()

    /** Runs the lines in order. Stops at the first non-zero exit code unless keep-going is set; returns the highest code. */
    fun runAll(lines: List<String>): Int {
        var worst = ExitCode.OK
        for (line in lines) {
            val code = runCommand(line)
            worst = maxOf(worst, code)
            if (code != ExitCode.OK && !options.keepGoing) return worst
        }
        return worst
    }

    fun runCommand(commandLine: String): Int {
        val reply = try {
            client.execute(commandLine, confirm = options.yes)
        } catch (_: ServerUnreachableException) {
            err.println("The server could not be reached. Check INGOT_URL and the network.")
            return ExitCode.UNREACHABLE
        }
        return when (reply.status) {
            200 -> printAnswer(commandLine, reply.body)
            409 -> confirmAndRetry(commandLine, reply.body)
            401, 403 -> {
                err.println("The token is not accepted, or it has no console area (ingot.admin.console).")
                ExitCode.DENIED
            }
            404 -> {
                err.println("The server has no structured console API. Upgrade the server to use this client.")
                ExitCode.SERVER_TOO_OLD
            }
            else -> {
                err.println("The server answered with status ${reply.status}.")
                ExitCode.FAILED
            }
        }
    }

    private fun confirmAndRetry(commandLine: String, body: String): Int {
        val answer = runCatching { mapper.readTree(body) }.getOrNull()
        val command = answer?.path("command")?.asText().orEmpty().ifEmpty { commandLine.substringBefore(' ') }
        val reason = answer?.path("reason")?.asText().orEmpty()
        if (!options.interactive || !options.confirm(command, reason)) {
            err.println("Confirmation required for '$command'${if (reason.isEmpty()) "" else " ($reason)"}. Nothing ran. Pass --yes to confirm.")
            return ExitCode.CONFIRMATION_REQUIRED
        }
        val confirmed = try {
            client.execute(commandLine, confirm = true)
        } catch (_: ServerUnreachableException) {
            err.println("The server could not be reached. Check INGOT_URL and the network.")
            return ExitCode.UNREACHABLE
        }
        return if (confirmed.status == 200) printAnswer(commandLine, confirmed.body) else ExitCode.FAILED
    }

    private fun printAnswer(commandLine: String, body: String): Int {
        val answer = try {
            mapper.readTree(body) as? ObjectNode
        } catch (_: Exception) {
            null
        }
        if (answer == null) {
            err.println("The server sent an answer this client cannot read.")
            return ExitCode.FAILED
        }
        val exitCode = answer.path("exitCode").asInt(ExitCode.FAILED)
        if (options.json) {
            val line = answer.deepCopy().put("command", commandLine)
            out.println(mapper.writeValueAsString(line))
            return exitCode
        }
        answer.path("output").forEach { line ->
            val text = line.path("text").asText()
            if (line.path("stream").asText() == "stderr") err.println(text) else out.println(text)
        }
        return exitCode
    }

}

/** The lines of a script: one command per line, with blank lines and lines that start with `#` skipped. */
fun scriptLines(text: String): List<String> =
    text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
