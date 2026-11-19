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

package com.reposilite.cleanup

/**
 * Whether a scheduled cleanup run that an instance was too late for is made up, read from the environment. The switch is
 * off by default, so an installation that does not set it keeps skipping a missed run, as before.
 *
 * `INGOT_CLEANUP_CATCHUP=true` turns it on. The `REPOSILITE_` name is the fallback when the `INGOT_` name is unset or blank.
 */
internal object CleanupCatchUp {

    const val SETTING = "INGOT_CLEANUP_CATCHUP"

    private const val LEGACY_SETTING = "REPOSILITE_CLEANUP_CATCHUP"

    fun read(environment: Map<String, String> = System.getenv()): Boolean {
        val value = listOf(SETTING, LEGACY_SETTING)
            .mapNotNull { name -> environment[name]?.trim() }
            .firstOrNull { it.isNotEmpty() }
        return value?.equals("true", ignoreCase = true) == true
    }

}
