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
package com.reposilite.console

import picocli.CommandLine
import picocli.CommandLine.Model.CommandSpec

/** The answer to a completion: the client replaces the characters from [start] to [end] of its line with one of [candidates]. */
internal data class Completion(val start: Int, val end: Int, val candidates: List<String>)

/**
 * Completes the word before [cursor] in [line] from the registered [commands], as the command listing describes them: the
 * first word from the names and aliases, a word after a dash from the options, and any other word from the fixed values of
 * the option before it or of the positional parameter it stands for. Values that come from data are not completed.
 */
internal fun completeCommand(commands: Collection<CommandLine>, line: String, cursor: Int): Completion {
    val end = cursor.coerceIn(0, line.length)
    val head = line.substring(0, end)
    val start = head.lastIndexOf(' ') + 1
    val word = head.substring(start)
    val previous = head.substring(0, start).split(' ').filter { it.isNotEmpty() }
    val specs = commands.map { it.commandSpec }.distinct()

    val candidates =
        if (previous.isEmpty()) {
            specs.flatMap { listOf(it.name()) + it.aliases() }
        } else {
            specs.firstOrNull { previous.first() == it.name() || previous.first() in it.aliases() }
                ?.let { argumentsOf(it, previous.drop(1), word) }
                .orEmpty()
        }

    return Completion(start, end, candidates.filter { it.startsWith(word) }.distinct().sorted())
}

/** The words that may follow [arguments] of the command [spec]. */
private fun argumentsOf(spec: CommandSpec, arguments: List<String>, word: String): List<String> {
    val option = arguments.lastOrNull()?.let { spec.findOption(it) }
    if (option != null && option.arity().max() > 0) {
        return option.completionCandidates()?.toList().orEmpty()
    }
    if (word.startsWith("-")) {
        return spec.options().flatMap { it.names().toList() }
    }
    val position = positionOf(spec, arguments)
    return spec.subcommands().keys.toList() +
        spec.positionalParameters()
            .filter { it.index().contains(position) }
            .flatMap { it.completionCandidates()?.toList().orEmpty() }
}

/** The index of the positional parameter that follows [arguments]: options and the values they take are not counted. */
private fun positionOf(spec: CommandSpec, arguments: List<String>): Int {
    var position = 0
    var skipValue = false
    for (argument in arguments) {
        when {
            skipValue -> skipValue = false
            argument.startsWith("-") -> skipValue = (spec.findOption(argument)?.arity()?.max() ?: 0) > 0
            else -> position++
        }
    }
    return position
}
