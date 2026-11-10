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

package net.onelitefeather.ingot.readonly

/**
 * The read-only switch as the environment sets it. [forced] is true only when `INGOT_READ_ONLY` (or its legacy name, or the
 * system property) says `true`. Anything else lets the stored state decide.
 */
data class ReadOnlySetting(val forced: Boolean) {

    companion object {

        const val SETTING = "INGOT_READ_ONLY"
        const val LEGACY_SETTING = "REPOSILITE_READ_ONLY"
        const val PROPERTY = "ingot.readOnly"

        /**
         * Reads the switch. The `INGOT_` name wins, then the `REPOSILITE_` name, then the system property. A blank value counts
         * as unset. A value that is neither `true` nor `false` stops the start, and the message names the variable.
         */
        fun read(
            environment: Map<String, String> = System.getenv(),
            systemProperty: String? = System.getProperty(PROPERTY),
        ): ReadOnlySetting {
            val fromEnvironment = listOf(SETTING, LEGACY_SETTING)
                .map { name -> name to environment[name]?.trim() }
                .firstOrNull { (_, value) -> !value.isNullOrEmpty() }
            val named = fromEnvironment
                ?: systemProperty?.trim()?.takeIf { it.isNotEmpty() }?.let { PROPERTY to it }
                ?: return ReadOnlySetting(forced = false)

            val (name, value) = named
            require(value.equals("true", ignoreCase = true) || value.equals("false", ignoreCase = true)) {
                "Invalid value for $name, expected true or false"
            }
            return ReadOnlySetting(forced = value.equals("true", ignoreCase = true))
        }
    }
}

/** Where the effective read-only state comes from. */
enum class ReadOnlySource {
    /** The environment forces the state on, whatever is stored. */
    ENVIRONMENT,

    /** The stored state decides, and it is off when nothing is stored. */
    STORED,
}

/** The effective read-only state of one instance. */
data class ReadOnlyState(val enabled: Boolean, val source: ReadOnlySource) {

    companion object {

        /** The environment wins when it forces the state, otherwise the stored state decides. */
        fun resolve(setting: ReadOnlySetting, storedEnabled: Boolean): ReadOnlyState =
            if (setting.forced) ReadOnlyState(enabled = true, source = ReadOnlySource.ENVIRONMENT)
            else ReadOnlyState(enabled = storedEnabled, source = ReadOnlySource.STORED)
    }
}
