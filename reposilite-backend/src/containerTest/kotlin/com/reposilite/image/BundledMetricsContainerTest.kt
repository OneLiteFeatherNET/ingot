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

package com.reposilite.image

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.testcontainers.containers.wait.strategy.Wait

/**
 * The metrics plugin ships in the image behind a switch. Without the switch the image starts as it did before, and with the
 * switch and credentials the metrics endpoint asks for them.
 */
class BundledMetricsContainerTest {

    @Test
    @DisplayName("without the metrics switch the image starts without the metrics plugin and serves no metrics")
    fun `image without the switch serves no metrics`() {
        serverContainer(image = ImageUnderTest.server)
            .waitingFor(Wait.forHttp(HEALTH_PATH).forPort(DEFAULT_SERVER_PORT).forStatusCode(200))
            .startOrExplain()
            .use { server ->
                val metrics = Http.get(server.baseUrl() + METRICS_PATH)

                assertThat(metrics.status)
                    .describedAs("GET %s must not answer 200 without the metrics switch", METRICS_PATH)
                    .isNotEqualTo(200)
                assertThat(server.logs)
                    .describedAs("the start-up log must not mention the metrics plugin without the switch")
                    .doesNotContain(PLUGIN_LOG_MARKER)
            }
    }

    @Test
    @DisplayName("with the metrics switch the endpoint refuses requests without credentials and accepts the configured ones")
    fun `image with the switch asks for the configured credentials`() {
        serverContainer(
            image = ImageUnderTest.server,
            environment = mapOf(
                "INGOT_METRICS_ENABLED" to "true",
                "INGOT_PROMETHEUS_USER" to "scraper",
                "INGOT_PROMETHEUS_PASSWORD" to "scrape-secret",
            ),
        )
            .waitingFor(Wait.forHttp(HEALTH_PATH).forPort(DEFAULT_SERVER_PORT).forStatusCode(200))
            .startOrExplain()
            .use { server ->
                val anonymous = Http.get(server.baseUrl() + METRICS_PATH)
                val scraper = Http.get(server.baseUrl() + METRICS_PATH, "scraper" to "scrape-secret")

                assertThat(anonymous.status)
                    .describedAs("GET %s without credentials must answer 401", METRICS_PATH)
                    .isEqualTo(401)
                assertThat(scraper.status)
                    .describedAs("GET %s with the configured credentials must answer 200", METRICS_PATH)
                    .isEqualTo(200)
            }
    }

    private companion object {
        const val HEALTH_PATH = "/api/status/health"
        const val METRICS_PATH = "/metrics"
        const val PLUGIN_LOG_MARKER = "--- Prometheus"
    }

}
