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

package net.onelitefeather.ingot.web

/**
 * The opt-in limit on the requests that the server works on at the same time.
 *
 * Unset means no limit, and then the server answers every request as it did before the limit existed. A value must be a
 * positive whole number. Anything else stops the start, so that a typo never turns the limit off silently.
 */
object RequestLimitSettings {

    const val SETTING = "INGOT_HTTP_MAXCONCURRENT"
    const val PROPERTY = "ingot.http.maxConcurrent"

    private const val LEGACY_SETTING = "REPOSILITE_HTTP_MAXCONCURRENT"

    private val WHOLE_NUMBER = Regex("[0-9]+")

    /**
     * Reads the limit. The system property wins over the `INGOT_` variable, and the `REPOSILITE_` variable is the
     * fallback. A blank value counts as unset. Returns null when no name is set.
     */
    fun read(
        environment: Map<String, String> = System.getenv(),
        properties: Map<String, String> = systemProperties(),
    ): Int? {
        val named = listOf(PROPERTY, SETTING, LEGACY_SETTING)
            .map { name -> name to (if (name == PROPERTY) properties[name] else environment[name])?.trim() }
            .firstOrNull { (_, value) -> !value.isNullOrEmpty() }
            ?: return null

        val (name, value) = named
        require(value != null && WHOLE_NUMBER.matches(value) && value.toIntOrNull()?.let { it > 0 } == true) {
            "Invalid value '$value' for $name, expected a positive whole number of concurrent requests"
        }

        return value.toInt()
    }

    private fun systemProperties(): Map<String, String> =
        System.getProperties().stringPropertyNames().associateWith { System.getProperty(it) }

}
