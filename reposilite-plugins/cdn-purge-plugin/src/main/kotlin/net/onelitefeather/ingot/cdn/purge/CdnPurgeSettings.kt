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

package net.onelitefeather.ingot.cdn.purge

/**
 * The settings of the purge webhook. [purgeUrl] is `INGOT_CDN_PURGE_URL`: when it is null the plugin sends nothing. [baseUrl] is
 * `INGOT_CDN_PURGE_BASE_URL`, the public address of the instance, and [token] is `INGOT_CDN_PURGE_TOKEN`, sent as a bearer token.
 * Every name has the legacy `REPOSILITE_` spelling as a fallback.
 */
data class CdnPurgeSettings(
    val purgeUrl: String?,
    val baseUrl: String?,
    val token: String?,
) {

    companion object {

        private const val PURGE_URL = "INGOT_CDN_PURGE_URL"
        private const val LEGACY_PURGE_URL = "REPOSILITE_CDN_PURGE_URL"
        private const val BASE_URL = "INGOT_CDN_PURGE_BASE_URL"
        private const val LEGACY_BASE_URL = "REPOSILITE_CDN_PURGE_BASE_URL"
        private const val TOKEN = "INGOT_CDN_PURGE_TOKEN"
        private const val LEGACY_TOKEN = "REPOSILITE_CDN_PURGE_TOKEN"

        /**
         * Reads the settings from [environment]. A purge URL without a base URL stops the start, because the purged URLs could not be built;
         * the message names `INGOT_CDN_PURGE_BASE_URL`.
         */
        fun read(environment: Map<String, String>): CdnPurgeSettings {
            val purgeUrl = firstValue(environment, PURGE_URL, LEGACY_PURGE_URL)
            val baseUrl = firstValue(environment, BASE_URL, LEGACY_BASE_URL)
            if (purgeUrl != null && baseUrl == null) {
                throw IllegalArgumentException("$PURGE_URL is set, so $BASE_URL must be set too, to build the public URLs to purge")
            }
            return CdnPurgeSettings(
                purgeUrl = purgeUrl,
                baseUrl = baseUrl,
                token = firstValue(environment, TOKEN, LEGACY_TOKEN),
            )
        }

        /** The first of [names] that is set to a non-blank value, trimmed. */
        private fun firstValue(environment: Map<String, String>, vararg names: String): String? =
            names.asSequence()
                .mapNotNull { environment[it]?.trim()?.takeIf { value -> value.isNotEmpty() } }
                .firstOrNull()

    }

}
