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

package com.reposilite.storage.s3

/** The escape hatch of [S3StorageProvider], read once when the S3 storage provider factory is created. */
internal object S3BufferToDiskSetting {

    const val SETTING = "INGOT_S3_BUFFER_TO_DISK"
    private const val LEGACY_SETTING = "REPOSILITE_S3_BUFFER_TO_DISK"

    /**
     * Reads the setting from [environment]: `INGOT_S3_BUFFER_TO_DISK`, then the legacy `REPOSILITE_` spelling. Nothing set, or an empty
     * value, means `false`, which streams uploads. Any value other than `true` or `false`, compared without regard to case, is refused
     * with the name of the variable, so that a typo cannot silently pick a path.
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
