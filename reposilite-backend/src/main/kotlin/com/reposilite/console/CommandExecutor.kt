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
package com.reposilite.console

import com.reposilite.ReposiliteJournalist
import com.reposilite.VERSION
import com.reposilite.console.CommandStatus.FAILED
import com.reposilite.console.api.CommandListing
import com.reposilite.console.api.ExecutionResponse
import com.reposilite.console.api.ReposiliteCommand
import com.reposilite.journalist.Journalist
import com.reposilite.journalist.Logger
import com.reposilite.status.FailureFacade
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.MissingParameterException
import picocli.CommandLine.PicocliException
import picocli.CommandLine.UnmatchedArgumentException
import java.io.InputStream
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Consumer
import kotlin.concurrent.withLock

internal const val REDACTED_COMMAND_ARGUMENTS = "<redacted>"

/** Colour and style sequences a command may put into its output through [CommandContext.effect]. */
private val ANSI_SEQUENCE = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]")

@Command(name = "", version = ["Ingot $VERSION"])
internal class CommandExecutor(
    private val journalist: ReposiliteJournalist,
    failureFacade: FailureFacade,
    source: InputStream
) : Journalist {

    private val consoleThread = ConsoleThread(this, source, failureFacade)
    private val commandLine = CommandLine(this)

    /**
     * The registered commands and [commandLine] keep the parsed arguments in their fields, so a parse and the check or
     * run that follows it must not interleave with another call. Held by [isDangerous], [reasonOf], [prepare],
     * [runCommand] and by [PreparedCommand] for a whole structured answer.
     */
    internal val commandLock = ReentrantLock()

    fun execute(command: String): ExecutionResponse =
        execute(command) { logger.info(it) }

    fun execute(command: String, outputConsumer: Consumer<String>): ExecutionResponse =
        runCommand(command).let { outcome ->
            logLines(outcome.lines, outputConsumer)
            ExecutionResponse(outcome.status, outcome.lines)
        }

    /**
     * Runs the command for a remote caller that reads an exit code. The lines reach the log as they do for a plain
     * execution, so a structured call is not less visible to the console than a plain one.
     */
    internal fun executeStructured(command: String): StructuredExecution {
        val outcome = runCommand(command)
        logLines(outcome.lines) { logger.info(it) }
        val stream = if (outcome.status == FAILED) "stderr" else "stdout"
        return StructuredExecution(
            status = outcome.status,
            exitCode = outcome.exitCode(),
            data = outcome.data,
            output = outcome.lines
                .flatMap { splitLines(it) }
                .map { OutputLine(stream = stream, text = it.replace(ANSI_SEQUENCE, "")) },
        )
    }

    /** Whether this command line names a dangerous invocation. A line that does not parse is not dangerous, and fails on its own. */
    internal fun isDangerous(command: String): Boolean {
        val processedCommand = command.trim()
        if (processedCommand.isEmpty()) {
            return false
        }
        return commandLock.withLock {
            try {
                val reposiliteCommand = parseCommand(processedCommand) ?: return false
                reposiliteCommand.isDangerous(processedCommand.split(" ").drop(1))
            } catch (_: PicocliException) {
                false
            }
        }
    }

    /** The reason a dangerous command line gives for asking, which is the description the command has in the help. */
    internal fun reasonOf(command: String): String =
        commandLock.withLock {
            try {
                commandLine.parseArgs(*command.trim().split(" ").toTypedArray())
                    .subcommand()
                    .commandSpec()
                    .usageMessage()
                    .description()
                    .joinToString(" ")
            } catch (_: PicocliException) {
                ""
            }
        }

    internal fun listCommands(): CommandListing =
        describeCommands(commandLine.subcommands.values)

    fun prepare(command: String): PreparedCommand {
        val processedCommand = command.trim()

        if (processedCommand.isEmpty()) {
            return PreparedCommand(processedCommand, command, this)
        }

        val redactedCommand = commandLock.withLock {
            try {
                val reposiliteCommand = parseCommand(processedCommand)

                when {
                    reposiliteCommand == null -> processedCommand.redacted()
                    reposiliteCommand.sensitiveInput -> processedCommand.redacted()
                    else -> processedCommand
                }
            } catch (_: PicocliException) {
                processedCommand.redacted()
            }
        }

        return PreparedCommand(
            redactedCommand = redactedCommand,
            command = command,
            commandExecutor = this,
        )
    }

    /**
     * What a command line produced: [usageError] is set for a line that named no command, did not parse or named an
     * unknown command, which the structured answer reports with exit code 2 apart from a failed command (exit code 1).
     */
    private class CommandOutcome(val status: CommandStatus, val lines: List<String>, val usageError: Boolean, val data: Any? = null) {

        fun exitCode(): Int =
            when {
                usageError -> 2
                status == FAILED -> 1
                else -> 0
            }

    }

    private fun runCommand(command: String): CommandOutcome = commandLock.withLock {
        val processedCommand = command.trim()

        if (processedCommand.isEmpty()) {
            return CommandOutcome(FAILED, emptyList(), usageError = true)
        }

        val response: MutableList<String> = ArrayList()

        return try {
            val commandObject = parseCommand(processedCommand)

            commandObject
                ?.let {
                    val context = CommandContext(journalist)
                    commandObject.execute(context)
                    CommandOutcome(context.status, context.output(), usageError = false, data = context.data)
                }
                ?: CommandOutcome(FAILED, listOf(commandLine.usageMessage), usageError = true)
        } catch (_: UnmatchedArgumentException) {
            CommandOutcome(FAILED, listOf("Unknown command ${processedCommand.substringBefore(' ')}"), usageError = true)
        } catch (missingParameterException: MissingParameterException) {
            response.add(missingParameterException.message.toString())
            response.add("")
            response.add(missingParameterException.commandLine.usageMessage)
            CommandOutcome(FAILED, response, usageError = true)
        } catch (_: PicocliException) {
            // Other parse errors, e.g. a repeated option, name the offending option or argument, which may be a secret
            CommandOutcome(FAILED, listOf("Invalid arguments for ${processedCommand.substringBefore(' ')}"), usageError = true)
        }
    }

    private fun logLines(messages: List<String>, outputConsumer: Consumer<String>) {
        messages.forEach { message -> splitLines(message).forEach { outputConsumer.accept(it) } }
    }

    private fun splitLines(message: String): List<String> =
        message.replace(System.lineSeparator(), "\n").split("\n")

    private fun parseCommand(command: String): ReposiliteCommand? =
        commandLine
            .parseArgs(*command.split(" ").toTypedArray())
            .subcommand()
            .commandSpec()
            .userObject() as? ReposiliteCommand

    private fun String.redacted(): String =
        "${substringBefore(' ')} $REDACTED_COMMAND_ARGUMENTS"

    fun registerCommand(command: ReposiliteCommand): CommandLine =
        commandLine.addSubcommand(command)

    fun getCommands(): Map<String, CommandLine> =
        commandLine.subcommands

    fun hook() =
        consoleThread.start()

    fun stop() =
        consoleThread.interrupt()

    override fun getLogger(): Logger =
        journalist.logger

}
