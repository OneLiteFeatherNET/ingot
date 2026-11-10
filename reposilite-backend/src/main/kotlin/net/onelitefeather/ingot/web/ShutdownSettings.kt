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

import java.time.Duration

/**
 * How a replica leaves the rotation on shutdown. Both values are optional and default to zero, which keeps the stop
 * sequence of earlier versions.
 *
 * - [timeout] is how long the server waits for requests in flight before it stops. New requests get `503` meanwhile.
 * - [delay] is how long the instance keeps serving after it reports draining, so that load balancers see the `503`
 *   on readiness before the wait starts.
 */
data class ShutdownSettings(
    val timeout: Duration = Duration.ZERO,
    val delay: Duration = Duration.ZERO,
) {

    companion object {

        const val TIMEOUT_SETTING = "INGOT_SHUTDOWN_TIMEOUT"
        const val DELAY_SETTING = "INGOT_SHUTDOWN_DELAY"

        private const val LEGACY_PREFIX = "REPOSILITE_"

        private val WHOLE_NUMBER = Regex("[0-9]+")

        /**
         * Reads both settings from the environment. Each one takes the `INGOT_` name, and the `REPOSILITE_` name is the
         * fallback when the `INGOT_` name is unset or blank. A value that is not a non-negative whole number of seconds
         * stops the start, and the message names the variable that holds it.
         */
        fun read(environment: Map<String, String> = System.getenv()): ShutdownSettings =
            ShutdownSettings(
                timeout = readSeconds(environment, TIMEOUT_SETTING),
                delay = readSeconds(environment, DELAY_SETTING),
            )

        private fun readSeconds(environment: Map<String, String>, setting: String): Duration {
            val legacySetting = LEGACY_PREFIX + setting.removePrefix("INGOT_")
            val named = listOf(setting, legacySetting)
                .map { name -> name to environment[name]?.trim() }
                .firstOrNull { (_, value) -> !value.isNullOrEmpty() }
                ?: return Duration.ZERO

            val (name, value) = named
            require(value != null && WHOLE_NUMBER.matches(value) && value.toLongOrNull() != null) {
                "Invalid value for $name, expected a whole number of seconds from 0 upwards"
            }

            return Duration.ofSeconds(value.toLong())
        }

    }

}
