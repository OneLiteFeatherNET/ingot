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

package net.onelitefeather.ingot.logging

/** A logging variable that holds a value the server does not accept. The message names the variable. */
class LogSettingsRefusal(message: String) : IllegalArgumentException(message)

/**
 * The logging variables of one start. Each one is read under its `INGOT_` name first and under its `REPOSILITE_` name when
 * the first is unset or blank. With nothing set, the values are the defaults of earlier versions.
 *
 * [file] tells whether the rolling log file is written.
 */
data class LogSettings(
    val file: Boolean = true,
) {

    companion object {

        /**
         * Reads the logging variables from [environment]. A value that is not accepted stops the start with a
         * [LogSettingsRefusal] that names the variable, instead of falling back silently.
         */
        fun read(environment: Map<String, String> = System.getenv()): LogSettings {
            val file = logVariable(environment, "LOG_FILE")?.let { (name, value) ->
                when {
                    value.equals("true", ignoreCase = true) -> true
                    value.equals("false", ignoreCase = true) -> false
                    else -> throw LogSettingsRefusal("Invalid value for $name: $value, expected true or false")
                }
            } ?: true

            return LogSettings(file = file)
        }

        /**
         * The value of `INGOT_<suffix>`, or of `REPOSILITE_<suffix>` when the first is unset or blank, with the name that
         * was read. Blank values count as unset, so the name in a refusal is always the one that held the bad value.
         */
        internal fun logVariable(environment: Map<String, String>, suffix: String): Pair<String, String>? =
            listOf("INGOT_$suffix", "REPOSILITE_$suffix")
                .firstNotNullOfOrNull { name -> environment[name]?.trim()?.takeIf { it.isNotEmpty() }?.let { name to it } }

    }

}