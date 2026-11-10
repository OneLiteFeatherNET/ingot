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

/**
 * The switch that turns a mounted shared settings file into a watched, read-only source. It is off by default, so an
 * existing installation keeps today's behaviour. It has no effect when the shared settings come from the database.
 */
object SharedConfigurationReload {

    const val SETTING = "INGOT_SHARED_CONFIGURATION_RELOAD"
    private const val LEGACY_SETTING = "REPOSILITE_SHARED_CONFIGURATION_RELOAD"

    /**
     * Reads the switch from [environment]: `INGOT_SHARED_CONFIGURATION_RELOAD`, then the legacy `REPOSILITE_` spelling. Nothing
     * set, or an empty value, means `false`. Any value other than `true` or `false`, compared without regard to case, is
     * refused, so a typo cannot silently leave a file writable or unwatched.
     */
    fun read(environment: Map<String, String> = System.getenv()): Boolean {
        val (name, value) = sequenceOf(SETTING, LEGACY_SETTING)
            .mapNotNull { name -> environment[name]?.trim()?.takeIf { it.isNotEmpty() }?.let { name to it } }
            .firstOrNull()
            ?: return false

        return when (value.lowercase()) {
            "true" -> true
            "false" -> false
            else -> throw IllegalArgumentException("Unknown value '$value' for $name, expected 'true' or 'false'")
        }
    }

}
