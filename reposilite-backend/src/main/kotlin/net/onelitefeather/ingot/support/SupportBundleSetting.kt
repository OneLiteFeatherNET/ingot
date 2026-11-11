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

package net.onelitefeather.ingot.support

/**
 * The opt-out of the system information and the support bundle. On by default, because both are admin-only and read-only. When
 * off, both endpoints answer 404 and the dashboard hides both pages.
 */
data class SupportBundleSetting(val enabled: Boolean) {

    companion object {

        const val SETTING = "INGOT_SUPPORT_BUNDLE_ENABLED"
        const val LEGACY_SETTING = "REPOSILITE_SUPPORT_BUNDLE_ENABLED"
        const val PROPERTY = "ingot.supportBundle.enabled"

        /**
         * Reads the switch. The `INGOT_` name wins, then the `REPOSILITE_` name, then the system property. A blank value counts as
         * unset. A value that is neither `true` nor `false` stops the start, and the message names the variable.
         */
        fun read(
            environment: Map<String, String> = System.getenv(),
            systemProperty: String? = System.getProperty(PROPERTY),
        ): SupportBundleSetting {
            val fromEnvironment = listOf(SETTING, LEGACY_SETTING)
                .map { name -> name to environment[name]?.trim() }
                .firstOrNull { (_, value) -> !value.isNullOrEmpty() }
            val named = fromEnvironment
                ?: systemProperty?.trim()?.takeIf { it.isNotEmpty() }?.let { PROPERTY to it }
                ?: return SupportBundleSetting(enabled = true)

            val (name, value) = named
            require(value.equals("true", ignoreCase = true) || value.equals("false", ignoreCase = true)) {
                "Invalid value for $name, expected true or false"
            }
            return SupportBundleSetting(enabled = value.equals("true", ignoreCase = true))
        }
    }
}
