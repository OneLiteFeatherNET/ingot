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

package com.reposilite.audit.decision

/**
 * `off` (the default) records nothing, `async` queues records for a background writer, and `sync` stores each record
 * before the answer is sent. Upgrading from Reposilite starts no audit rows until an operator opts in.
 */
enum class DecisionLogMode { OFF, ASYNC, SYNC }

/**
 * The decision log switch, read from `INGOT_DECISION_LOG`. The `REPOSILITE_DECISION_LOG` name is the fallback when the
 * `INGOT_` name is unset or blank. An unset, blank or unknown value gives `off`.
 */
data class DecisionLogSettings(val mode: DecisionLogMode = DecisionLogMode.OFF) {

    companion object {

        const val SETTING = "INGOT_DECISION_LOG"

        private const val LEGACY_SETTING = "REPOSILITE_DECISION_LOG"

        fun read(environment: Map<String, String> = System.getenv()): DecisionLogSettings {
            val value = listOf(SETTING, LEGACY_SETTING)
                .mapNotNull { name -> environment[name]?.trim() }
                .firstOrNull { it.isNotEmpty() }
                ?: return DecisionLogSettings()

            val mode = DecisionLogMode.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: return DecisionLogSettings()
            return DecisionLogSettings(mode)
        }

    }

}
