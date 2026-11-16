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

import java.time.Duration

/**
 * The redirect of cacheable S3 downloads to presigned URLs, read once at start. [enabled] is `INGOT_S3_PRESIGNED_REDIRECTS` and is
 * off by default. [ttl] is `INGOT_S3_PRESIGN_TTL_SECONDS`, 300 by default and 60 to 3600 seconds. [endpoint] is
 * `INGOT_S3_PRESIGN_ENDPOINT`, or null when it is empty, which means the configured S3 endpoint. Every name has the legacy
 * `REPOSILITE_` spelling as a fallback.
 */
data class S3PresignConfig(
    val enabled: Boolean,
    val ttl: Duration,
    val endpoint: String?,
) {

    companion object {

        private const val SWITCH = "INGOT_S3_PRESIGNED_REDIRECTS"
        private const val LEGACY_SWITCH = "REPOSILITE_S3_PRESIGNED_REDIRECTS"
        private const val TTL = "INGOT_S3_PRESIGN_TTL_SECONDS"
        private const val LEGACY_TTL = "REPOSILITE_S3_PRESIGN_TTL_SECONDS"
        private const val ENDPOINT = "INGOT_S3_PRESIGN_ENDPOINT"
        private const val LEGACY_ENDPOINT = "REPOSILITE_S3_PRESIGN_ENDPOINT"

        private const val DEFAULT_TTL_SECONDS = 300L
        private const val MIN_TTL_SECONDS = 60L
        private const val MAX_TTL_SECONDS = 3600L

        /**
         * Reads the three settings from [environment]. A value that is set but not valid stops the start with a message that names
         * the variable: a switch other than `true` or `false` (compared without regard to case), and a lifetime that is not a whole
         * number of seconds from 60 to 3600.
         */
        fun read(environment: Map<String, String> = System.getenv()): S3PresignConfig {
            val enabled = when (val value = firstValue(environment, SWITCH, LEGACY_SWITCH)) {
                null -> false
                else -> when (value.second.lowercase()) {
                    "true" -> true
                    "false" -> false
                    else -> throw IllegalArgumentException("Unknown value '${value.second}' for ${value.first}, expected 'true' or 'false'")
                }
            }

            val ttl = when (val value = firstValue(environment, TTL, LEGACY_TTL)) {
                null -> Duration.ofSeconds(DEFAULT_TTL_SECONDS)
                else -> value.second.toLongOrNull()
                    ?.takeIf { it in MIN_TTL_SECONDS..MAX_TTL_SECONDS }
                    ?.let { Duration.ofSeconds(it) }
                    ?: throw IllegalArgumentException(
                        "Invalid value '${value.second}' for ${value.first}, expected a whole number of seconds from $MIN_TTL_SECONDS to $MAX_TTL_SECONDS"
                    )
            }

            val endpoint = firstValue(environment, ENDPOINT, LEGACY_ENDPOINT)?.second

            return S3PresignConfig(enabled = enabled, ttl = ttl, endpoint = endpoint)
        }

        /** The first of [names] that is set to a non-blank value, with its name and its trimmed value. */
        private fun firstValue(environment: Map<String, String>, vararg names: String): Pair<String, String>? =
            names.asSequence()
                .mapNotNull { name -> environment[name]?.trim()?.takeIf { it.isNotEmpty() }?.let { name to it } }
                .firstOrNull()

    }

}
