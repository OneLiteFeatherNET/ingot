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

import com.reposilite.console.api.CommandDescriptor
import com.reposilite.console.api.CommandListing
import com.reposilite.console.api.OptionDescriptor
import com.reposilite.console.api.ParameterDescriptor
import com.reposilite.console.api.ReposiliteCommand
import picocli.CommandLine

/** The version of the command listing format. A client refuses a version it does not know. */
const val COMMAND_LISTING_VERSION = 1

/** A line of structured output. [stream] is `stdout` for output and `stderr` for the lines of a failed command. */
data class OutputLine(val stream: String, val text: String)

/** The result of a structured execution. The exit code follows the picocli convention: 0, 1 failed, 2 usage or unknown. */
data class StructuredExecution(
    val status: CommandStatus,
    val exitCode: Int,
    val output: List<OutputLine>,
)

/** Answer of a structured execution: either the command ran, or it is held until the caller confirms it. */
sealed interface StructuredAnswer

data class StructuredRun(val execution: StructuredExecution) : StructuredAnswer

data class ConfirmationNeeded(val command: String, val reason: String) : StructuredAnswer

internal fun describeCommands(commands: Collection<CommandLine>): CommandListing =
    CommandListing(
        version = COMMAND_LISTING_VERSION,
        commands = commands
            .distinct()
            .map { describeCommand(it) }
            .sortedBy { it.name }
    )

private fun describeCommand(commandLine: CommandLine): CommandDescriptor {
    val spec = commandLine.commandSpec
    val command = spec.userObject() as? ReposiliteCommand
    return CommandDescriptor(
        name = spec.name(),
        aliases = spec.aliases().toList(),
        description = spec.usageMessage().description().joinToString(" "),
        dangerous = command?.dangerous ?: false,
        sensitive = command?.mayHideInput ?: false,
        parameters = spec.positionalParameters().map {
            ParameterDescriptor(
                label = it.paramLabel(),
                index = it.index().toString(),
                required = it.required(),
                description = it.description().joinToString(" "),
            )
        },
        options = spec.options().map {
            OptionDescriptor(
                names = it.names().toList(),
                description = it.description().joinToString(" "),
                arity = it.arity().toString(),
            )
        },
        subcommands = spec.subcommands().keys.toList(),
    )
}
