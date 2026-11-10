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

package com.reposilite.plugin.prometheus

/**
 * The settings of the metrics endpoint. Each setting is read from the INGOT environment name first, then from the system
 * property of the legacy name, then from the legacy environment name, so every deployment that works today keeps its values.
 */
internal data class PrometheusSettings(
    val path: String,
    val user: String,
    val password: String,
) {

    companion object {

        /**
         * @param environment the environment to read from; injected so that tests do not depend on the process environment
         * @param property the system property lookup, keyed by the legacy property name
         */
        fun read(environment: Map<String, String>, property: (String) -> String?): PrometheusSettings {
            fun setting(name: String): String? =
                environment["INGOT_PROMETHEUS_$name"]
                    ?: property("reposilite.prometheus.${name.lowercase()}")
                    ?: environment["REPOSILITE_PROMETHEUS_$name"]

            return PrometheusSettings(
                path = setting("PATH") ?: "/metrics",
                user = setting("USER") ?: throw IllegalStateException("Prometheus user is not defined"),
                password = setting("PASSWORD") ?: throw IllegalStateException("Prometheus password is not defined"),
            )
        }

    }

}
