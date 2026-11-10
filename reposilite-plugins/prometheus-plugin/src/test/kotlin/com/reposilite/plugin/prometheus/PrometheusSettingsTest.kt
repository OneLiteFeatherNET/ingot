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

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

internal class PrometheusSettingsTest {

    private fun read(
        environment: Map<String, String> = emptyMap(),
        properties: Map<String, String> = emptyMap(),
    ): PrometheusSettings = PrometheusSettings.read(environment = environment, property = properties::get)

    @Test
    fun `should prefer the INGOT user name over the legacy environment name`() {
        // given: both spellings of the user are set
        val settings = read(
            environment = mapOf("INGOT_PROMETHEUS_USER" to "ingot-user", "REPOSILITE_PROMETHEUS_USER" to "legacy-user", "INGOT_PROMETHEUS_PASSWORD" to "secret"),
        )

        // when / then: the INGOT spelling decides
        assertThat(settings.user)
            .describedAs("INGOT_PROMETHEUS_USER must take precedence over REPOSILITE_PROMETHEUS_USER")
            .isEqualTo("ingot-user")
    }

    @Test
    fun `should read the legacy environment names when the INGOT names are unset`() {
        // given: only the legacy spellings are set, as in existing deployments
        val settings = read(
            environment = mapOf("REPOSILITE_PROMETHEUS_USER" to "user", "REPOSILITE_PROMETHEUS_PASSWORD" to "password"),
        )

        // then: the endpoint is configured as before
        assertThat(settings.user).isEqualTo("user")
        assertThat(settings.password).isEqualTo("password")
    }

    @Test
    fun `should keep the system property before the legacy environment name`() {
        // given: a system property and the legacy environment name for the same setting
        val settings = read(
            environment = mapOf("REPOSILITE_PROMETHEUS_USER" to "env-user", "REPOSILITE_PROMETHEUS_PASSWORD" to "password"),
            properties = mapOf("reposilite.prometheus.user" to "property-user"),
        )

        // then: the property wins, as it did before the INGOT names existed
        assertThat(settings.user).isEqualTo("property-user")
    }

    @Test
    fun `should read the path from the INGOT name before the legacy name`() {
        // given: both spellings of the path
        val settings = read(
            environment = mapOf(
                "INGOT_PROMETHEUS_PATH" to "/ingot-metrics",
                "REPOSILITE_PROMETHEUS_PATH" to "/legacy-metrics",
                "INGOT_PROMETHEUS_USER" to "user",
                "INGOT_PROMETHEUS_PASSWORD" to "password",
            ),
        )

        // then: the INGOT path is used
        assertThat(settings.path).isEqualTo("/ingot-metrics")
    }

    @Test
    fun `should default the path to metrics`() {
        // given: no path in any spelling
        val settings = read(environment = mapOf("INGOT_PROMETHEUS_USER" to "user", "INGOT_PROMETHEUS_PASSWORD" to "password"))

        // then: the endpoint answers at /metrics
        assertThat(settings.path).isEqualTo("/metrics")
    }

    @Test
    fun `should fail with the existing message when no user is set in any spelling`() {
        // given: a password but no user
        // when / then: start-up fails with the message the plugin has always used
        assertThatThrownBy { read(environment = mapOf("INGOT_PROMETHEUS_PASSWORD" to "password")) }
            .describedAs("a missing user must stop start-up with the existing message")
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("Prometheus user is not defined")
    }

    @Test
    fun `should fail with the existing message when no password is set in any spelling`() {
        // given: a user but no password
        // when / then: start-up fails with the message the plugin has always used
        assertThatThrownBy { read(environment = mapOf("INGOT_PROMETHEUS_USER" to "user")) }
            .describedAs("a missing password must stop start-up with the existing message")
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("Prometheus password is not defined")
    }

}
