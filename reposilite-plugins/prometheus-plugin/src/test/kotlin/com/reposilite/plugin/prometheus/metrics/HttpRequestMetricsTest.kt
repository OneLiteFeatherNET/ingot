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

package com.reposilite.plugin.prometheus.metrics

import io.prometheus.metrics.model.registry.PrometheusRegistry
import io.prometheus.metrics.model.snapshots.CounterSnapshot
import io.prometheus.metrics.model.snapshots.HistogramSnapshot
import io.prometheus.metrics.model.snapshots.MetricSnapshot
import io.prometheus.metrics.model.snapshots.SummarySnapshot
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.jetty.server.handler.StatisticsHandler
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicLong

internal class HttpRequestMetricsTest {

    private val registry = PrometheusRegistry()
    private val ticker = AtomicLong(1_000_000_000L)

    private fun metrics() = HttpRequestMetrics(registry, ticker::get)

    private fun snapshot(name: String): MetricSnapshot =
        registry.scrape().single { it.metadata.prometheusName == name }

    private fun counterValue(method: String, route: String, code: String): Double =
        (snapshot("ingot_http_server_requests") as CounterSnapshot).dataPoints
            .single { it.labels.get("method") == method && it.labels.get("route") == route && it.labels.get("code") == code }
            .value

    private fun histogramSum(method: String, route: String, code: String): Double =
        (snapshot("ingot_http_server_request_duration_seconds") as HistogramSnapshot).dataPoints
            .single { it.labels.get("method") == method && it.labels.get("route") == route && it.labels.get("code") == code }
            .sum

    @Test
    fun `should register the duration histogram and the request counter under their ingot names`() {
        // given: the request metrics registered in a fresh registry
        metrics()

        // when: the registry is scraped
        val ingotNames = registry.scrape().map { it.metadata.prometheusName }.filter { it.startsWith("ingot_") }

        // then: exactly the histogram and the counter are present
        assertThat(ingotNames)
            .describedAs("the request metrics must expose a histogram and a counter, nothing else")
            .containsExactlyInAnyOrder(
                "ingot_http_server_request_duration_seconds",
                "ingot_http_server_requests",
            )
        assertThat(snapshot("ingot_http_server_request_duration_seconds"))
            .describedAs("the duration metric must be a histogram")
            .isInstanceOf(HistogramSnapshot::class.java)
    }

    @Test
    fun `should record the duration as the advance of the injected ticker`() {
        // given: a request that starts at the current ticker value
        val metrics = metrics()
        val startedAt = metrics.start()

        // when: the ticker advances by 250 milliseconds and the request finishes
        ticker.addAndGet(250_000_000L)
        metrics.finish(startedAt, method = "GET", route = "/releases/{path}", code = 200)

        // then: the observed duration is exactly 0.25 seconds
        assertThat(histogramSum("GET", "/releases/{path}", "200"))
            .describedAs("the duration must come from the injected ticker, not from the wall clock")
            .isEqualTo(0.25)
    }

    @Test
    fun `should increase the request counter once for each finished request`() {
        // given: two requests with the same method, route and status
        val metrics = metrics()

        // when: both finish
        metrics.finish(metrics.start(), method = "GET", route = "/releases/{path}", code = 200)
        metrics.finish(metrics.start(), method = "GET", route = "/releases/{path}", code = 200)

        // then: the counter for that label set reads two
        assertThat(counterValue("GET", "/releases/{path}", "200"))
            .describedAs("two finished requests with the same labels must count twice")
            .isEqualTo(2.0)
    }

    @Test
    fun `should label each observation with method, route template and status code only`() {
        // given: a matched request and a request that matched no route
        val metrics = metrics()

        // when: both finish
        metrics.finish(metrics.start(), method = "PUT", route = "/releases/{path}", code = 201)
        metrics.finish(metrics.start(), method = "GET", route = "unmatched", code = 404)

        // then: each series carries exactly the three labels, and the values are the ones given
        val dataPoints = (snapshot("ingot_http_server_requests") as CounterSnapshot).dataPoints
        assertThat(dataPoints.map { it.labels.size() })
            .describedAs("each series must carry exactly the labels method, route and code")
            .containsOnly(3)
        assertThat(counterValue("PUT", "/releases/{path}", "201")).isEqualTo(1.0)
        assertThat(counterValue("GET", "unmatched", "404")).isEqualTo(1.0)
    }

    @Test
    fun `should keep the jetty response time summary with its code label`() {
        // given: the jetty metrics registered next to the request metrics in the same fresh registry
        metrics()
        JettyMetrics.register(StatisticsHandler(), registry)
        JettyMetrics.responseTimeSummary.labelValues("200").observe(0.1)

        // when: the registry is scraped
        val summary = snapshot("jetty_response_time_seconds") as SummarySnapshot

        // then: the existing series is still a summary that is labelled by code
        assertThat(summary.dataPoints.map { it.labels.get("code") })
            .describedAs("the existing summary must keep its code label")
            .containsExactly("200")
    }

}
