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

package com.reposilite.shared.http

/**
 * How a proxy repository reports a failing upstream.
 *
 * - [LEGACY] keeps the statuses Reposilite always answered, which Maven and Gradle clients of existing installations rely on.
 * - [STRICT] answers 504 for a timeout, 502 for an unreachable or failing upstream and 404 for an upstream miss.
 */
enum class UpstreamStatusMode {
    LEGACY,
    STRICT;

    companion object {

        const val SETTING = "INGOT_PROXY_UPSTREAM_STATUS"

        /**
         * Reads the setting: system property `ingot.proxy.upstreamStatus`, then the environment variable
         * `INGOT_PROXY_UPSTREAM_STATUS`, then the legacy spellings `reposilite.proxy.upstreamStatus` and
         * `REPOSILITE_PROXY_UPSTREAM_STATUS`. Nothing set means [LEGACY]; an unknown value is refused.
         */
        fun read(
            properties: Map<String, String> = System.getProperties().stringPropertyNames().associateWith { System.getProperty(it) },
            environment: Map<String, String> = System.getenv(),
        ): UpstreamStatusMode {
            val value = sequenceOf(
                properties["ingot.proxy.upstreamStatus"],
                environment[SETTING],
                properties["reposilite.proxy.upstreamStatus"],
                environment["REPOSILITE_PROXY_UPSTREAM_STATUS"],
            ).firstNotNullOfOrNull { it?.trim()?.takeIf { text -> text.isNotEmpty() } } ?: return LEGACY

            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("Unknown value '$value' for $SETTING, expected 'legacy' or 'strict'")
        }

    }
}
