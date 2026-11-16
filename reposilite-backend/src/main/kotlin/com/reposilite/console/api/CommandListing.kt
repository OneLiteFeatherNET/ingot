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
package com.reposilite.console.api

/** The command tree a remote client needs for help and completion. */
data class CommandListing(
    val version: Int,
    val commands: List<CommandDescriptor>
)

data class CommandDescriptor(
    val name: String,
    val aliases: List<String>,
    val description: String,
    val dangerous: Boolean,
    val sensitive: Boolean,
    val parameters: List<ParameterDescriptor>,
    val options: List<OptionDescriptor>,
    val subcommands: List<String>,
)

data class ParameterDescriptor(
    val label: String,
    val index: String,
    val required: Boolean,
    val description: String,
)

data class OptionDescriptor(
    val names: List<String>,
    val description: String,
    val arity: String,
)

/** Answer of a structured execution. [data] is null until a command provides structured data. */
data class StructuredExecutionResponse(
    val status: com.reposilite.console.CommandStatus,
    val exitCode: Int,
    val output: List<com.reposilite.console.OutputLine>,
    val data: Any?,
    val node: String,
)

/** Answer of a structured execution held for confirmation. Sent with status 409, and the command did not run. */
data class ConfirmationRequiredResponse(
    val error: String,
    val command: String,
    val reason: String,
)
