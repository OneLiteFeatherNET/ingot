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

/** The builtins of the REPL. They start with `:`, so they never collide with a server command. */
internal const val BUILTINS = ":context [name], :output text|json, :quit"

/** What a line that starts with `:` asks the REPL to do. */
internal sealed interface Builtin {
    data object Quit : Builtin

    /** Switch the output format; [json] is false for text. */
    data class Output(val json: Boolean) : Builtin

    /** Switch to the context [name] of the config file, or name the current context when [name] is null. */
    data class Context(val name: String?) : Builtin

    /** A builtin that does not exist or has a wrong argument. [message] says what is accepted. */
    data class Invalid(val message: String) : Builtin
}

internal fun parseBuiltin(line: String): Builtin {
    val words = line.trim().split(Regex("\\s+"))
    val argument = words.getOrNull(1)
    return when (words.first()) {
        ":quit" -> if (words.size == 1) Builtin.Quit else Builtin.Invalid("Usage: :quit")
        ":output" -> when {
            words.size == 2 && argument == "text" -> Builtin.Output(json = false)
            words.size == 2 && argument == "json" -> Builtin.Output(json = true)
            else -> Builtin.Invalid("Usage: :output text|json")
        }
        ":context" -> if (words.size <= 2) Builtin.Context(argument) else Builtin.Invalid("Usage: :context [name]")
        else -> Builtin.Invalid("Unknown builtin ${words.first()}. Builtins: $BUILTINS")
    }
}
