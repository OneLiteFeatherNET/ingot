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

import io.prometheus.metrics.model.registry.PrometheusRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class JvmMetricsSwitchTest {

    private val credentials = mapOf("INGOT_PROMETHEUS_USER" to "user", "INGOT_PROMETHEUS_PASSWORD" to "password")

    private fun jvmMetrics(
        environment: Map<String, String> = emptyMap(),
        properties: Map<String, String> = emptyMap(),
    ): Boolean = PrometheusSettings.read(credentials + environment, properties::get).jvmMetrics

    private fun registeredNames(jvmMetrics: Boolean): Set<String> {
        val registry = PrometheusRegistry()
        val settings = PrometheusSettings(path = "/metrics", user = "u", password = "p", jvmMetrics = jvmMetrics)
        registerJvmMetrics(settings, registry)
        return registry.scrape().map { it.metadata.name }.toSet()
    }

    @Test
    fun `should keep the jvm series on when nothing is set`() {
        assertThat(jvmMetrics()).describedAs("default must keep today's series").isTrue()
    }

    @Test
    fun `should switch off with the INGOT name`() {
        assertThat(jvmMetrics(environment = mapOf("INGOT_PROMETHEUS_JVM_METRICS" to "false"))).isFalse()
    }

    @Test
    fun `should switch off with the legacy property`() {
        assertThat(jvmMetrics(properties = mapOf("reposilite.prometheus.jvm_metrics" to "false"))).isFalse()
    }

    @Test
    fun `should switch off with the legacy environment name`() {
        assertThat(jvmMetrics(environment = mapOf("REPOSILITE_PROMETHEUS_JVM_METRICS" to "false"))).isFalse()
    }

    @Test
    fun `should prefer the INGOT name over the legacy name`() {
        val environment = mapOf("INGOT_PROMETHEUS_JVM_METRICS" to "true", "REPOSILITE_PROMETHEUS_JVM_METRICS" to "false")
        assertThat(jvmMetrics(environment = environment)).isTrue()
    }

    @Test
    fun `should keep the jvm series on for any value other than false`() {
        val values = listOf("true", "False", "FALSE", "0", "no", "", "flase")
        for (value in values) {
            assertThat(jvmMetrics(environment = mapOf("INGOT_PROMETHEUS_JVM_METRICS" to value)))
                .describedAs("value '%s' must not remove series", value)
                .isTrue()
        }
    }

    @Test
    fun `should register the jvm series when on`() {
        assertThat(registeredNames(jvmMetrics = true)).anyMatch { it.startsWith("jvm_memory") }
    }

    @Test
    fun `should register no jvm series when off`() {
        assertThat(registeredNames(jvmMetrics = false)).isEmpty()
    }

}
