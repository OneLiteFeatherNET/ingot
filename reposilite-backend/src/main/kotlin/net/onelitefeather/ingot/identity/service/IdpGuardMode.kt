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
package net.onelitefeather.ingot.identity.service

/**
 * Whether linking a team to an identity provider group needs the admin area `ingot.admin.idp`.
 *
 * - [ENFORCE] (default): a write that sets, changes or clears a team's external reference without the right is refused.
 * - [WARN]: a temporary fallback for operators with automation. The write proceeds and is recorded as unguarded.
 * - [OFF]: no check and no record.
 */
enum class IdpGuardMode {
    OFF,
    WARN,
    ENFORCE;

    companion object {

        const val SETTING = "INGOT_IDENTITY_IDP_GUARD"
        const val LEGACY_SETTING = "REPOSILITE_IDENTITY_IDP_GUARD"

        /**
         * Reads the mode. The `INGOT_` name wins, then the `REPOSILITE_` name. A blank value counts as unset, and the default
         * is [ENFORCE]. A value that is none of the three modes stops the start, and the message names the variable.
         */
        fun read(environment: Map<String, String> = System.getenv()): IdpGuardMode {
            val (name, value) = listOf(SETTING, LEGACY_SETTING)
                .map { name -> name to environment[name]?.trim() }
                .firstOrNull { (_, value) -> !value.isNullOrEmpty() }
                ?: return ENFORCE
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("Invalid value '$value' for $name, expected one of: off, warn, enforce")
        }
    }
}
