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


package com.reposilite.configuration.shared

import java.nio.file.Paths

/**
 * Reads the switch and the allowlists of settings secret references from the environment. The switch is off by default, and the
 * allowlists are empty, so an instance resolves nothing until the operator names what a reference may read. The stored settings
 * document cannot set any of them.
 */
object SettingsSecretReferences {

    const val SWITCH = "INGOT_SETTINGS_SECRET_REFERENCES"
    const val ENVIRONMENT_LIST = "INGOT_SETTINGS_SECRET_ENV"
    const val FILE_LIST = "INGOT_SETTINGS_SECRET_FILES"

    /**
     * @return the resolver when the switch is on, null when it is off
     * @throws IllegalArgumentException when the switch holds a value other than `true` or `false`, or a file directory is not absolute
     */
    fun read(environment: Map<String, String> = System.getenv()): SecretReferenceResolver? {
        if (!isEnabled(environment)) {
            return null
        }

        val variables = list(environment, ENVIRONMENT_LIST).toSet()
        val directories = list(environment, FILE_LIST).map { directory ->
            val path = Paths.get(directory)
            require(path.isAbsolute) { "Entry '$directory' of $FILE_LIST is not an absolute directory" }
            path
        }
        return SecretReferenceResolver(environment = environment, variables = variables, directories = directories)
    }

    private fun isEnabled(environment: Map<String, String>): Boolean {
        val (name, value) = setting(environment, SWITCH) ?: return false
        return when (value.lowercase()) {
            "true" -> true
            "false" -> false
            else -> throw IllegalArgumentException("Unknown value '$value' for $name, expected 'true' or 'false'")
        }
    }

    private fun list(environment: Map<String, String>, name: String): List<String> =
        setting(environment, name)
            ?.second
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

    /**
     * The INGOT spelling of a setting, or the legacy REPOSILITE spelling when the INGOT one is unset or empty.
     */
    private fun setting(environment: Map<String, String>, name: String): Pair<String, String>? =
        sequenceOf(name, name.replaceFirst("INGOT_", "REPOSILITE_"))
            .mapNotNull { key -> environment[key]?.trim()?.takeIf { it.isNotEmpty() }?.let { key to it } }
            .firstOrNull()

}
