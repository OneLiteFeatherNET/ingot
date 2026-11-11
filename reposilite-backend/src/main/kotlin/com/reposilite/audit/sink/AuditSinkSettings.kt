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

package com.reposilite.audit.sink

import java.nio.file.Path

/**
 * Where the JSON lines audit sink writes, read from the environment. Both targets are off by default, and then no sink
 * thread is started.
 *
 * - [stdout] is switched on by `INGOT_AUDIT_SINK_STDOUT=true`.
 * - [file] is the path given by `INGOT_AUDIT_SINK_FILE`. A blank value means no file.
 *
 * Each variable takes the `INGOT_` name, and the `REPOSILITE_` name is the fallback when the `INGOT_` name is unset or blank.
 */
data class AuditSinkSettings(
    val stdout: Boolean = false,
    val file: Path? = null,
) {

    val enabled: Boolean
        get() = stdout || file != null

    companion object {

        const val STDOUT_SETTING = "INGOT_AUDIT_SINK_STDOUT"
        const val FILE_SETTING = "INGOT_AUDIT_SINK_FILE"

        private const val LEGACY_PREFIX = "REPOSILITE_"

        fun read(environment: Map<String, String> = System.getenv()): AuditSinkSettings =
            AuditSinkSettings(
                stdout = firstNonBlank(environment, STDOUT_SETTING)?.equals("true", ignoreCase = true) == true,
                file = firstNonBlank(environment, FILE_SETTING)?.let { Path.of(it) },
            )

        private fun firstNonBlank(environment: Map<String, String>, setting: String): String? =
            listOf(setting, LEGACY_PREFIX + setting.removePrefix("INGOT_"))
                .mapNotNull { name -> environment[name]?.trim() }
                .firstOrNull { it.isNotEmpty() }

    }

}
