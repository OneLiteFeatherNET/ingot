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

package com.reposilite.status

/**
 * Whether the hourly check for a newer Ingot version is on.
 *
 * The first source that is set wins: system property `ingot.status.remote-version-check`, variable
 * `INGOT_STATUS_REMOTE_VERSION_CHECK`, system property `reposilite.status.remote-version-check`, variable
 * `REPOSILITE_STATUS_REMOTE_VERSION_CHECK`. Nothing set means on.
 *
 * Properties keep their meaning: `true` is on and every other value is off. Variables accept `true` and `false`
 * without regard to case. An empty variable counts as not set, and any other value is ignored with one warning,
 * so a typo falls back to the next source instead of changing the behaviour silently.
 */
object RemoteVersionCheckSetting {

    const val PROPERTY = "ingot.status.remote-version-check"
    const val VARIABLE = "INGOT_STATUS_REMOTE_VERSION_CHECK"
    const val LEGACY_PROPERTY = "reposilite.status.remote-version-check"
    const val LEGACY_VARIABLE = "REPOSILITE_STATUS_REMOTE_VERSION_CHECK"

    fun read(
        properties: Map<String, String> = System.getProperties().stringPropertyNames().associateWith { System.getProperty(it) },
        environment: Map<String, String> = System.getenv(),
        warn: (String) -> Unit = {},
    ): Boolean {
        properties[PROPERTY]?.let { return it == "true" }
        variable(environment, VARIABLE, warn)?.let { return it }
        properties[LEGACY_PROPERTY]?.let { return it == "true" }
        variable(environment, LEGACY_VARIABLE, warn)?.let { return it }
        return true
    }

    private fun variable(environment: Map<String, String>, name: String, warn: (String) -> Unit): Boolean? {
        val value = environment[name]?.trim()?.takeIf { it.isNotEmpty() } ?: return null

        return when {
            value.equals("true", ignoreCase = true) -> true
            value.equals("false", ignoreCase = true) -> false
            else -> {
                warn("$name has the value '$value', expected 'true' or 'false'. The value is ignored and the next setting applies.")
                null
            }
        }
    }

}
