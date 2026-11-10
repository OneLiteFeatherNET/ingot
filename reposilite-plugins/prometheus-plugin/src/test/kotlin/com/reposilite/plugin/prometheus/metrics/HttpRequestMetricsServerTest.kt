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

import io.javalin.Javalin
import io.prometheus.metrics.model.registry.PrometheusRegistry
import io.prometheus.metrics.model.snapshots.CounterSnapshot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.atomic.AtomicLong

/**
 * Serves a real Javalin application on an ephemeral port for each test, so the route label is the one Javalin matched.
 */
internal class HttpRequestMetricsServerTest {

    private val registry = PrometheusRegistry()
    private val ticker = AtomicLong(0L)
    private val client = HttpClient.newHttpClient()

    private fun requestCounters(): List<CounterSnapshot.CounterDataPointSnapshot> =
        registry.scrape().single { it.metadata.prometheusName == "ingot_http_server_requests" }
            .let { (it as CounterSnapshot).dataPoints }

    private fun withServer(block: (baseUrl: String) -> Unit) {
        val app = Javalin.create { config ->
            HttpRequestMetrics(registry, ticker::incrementAndGet).install(config.routes, metricsPath = "/metrics")
            config.routes.get("/releases/{path}") { ctx -> ctx.result("artifact") }
            config.routes.get("/metrics") { ctx -> ctx.result("exposition") }
        }.start(0)
        try {
            block("http://localhost:${app.port()}")
        } finally {
            app.stop()
        }
    }

    private fun get(url: String): Int =
        client.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode()

    @Test
    fun `should label a matched request with its route template and not the raw path`() {
        withServer { baseUrl ->
            // given: a request whose path carries a client-chosen segment
            // when: the route answers it
            val status = get("$baseUrl/releases/com-example-a")

            // then: the label is the template, and the client-chosen segment appears in no label
            assertThat(status).isEqualTo(200)
            assertThat(requestCounters().map { it.labels.get("route") })
                .describedAs("the route label must be the matched route template")
                .containsExactly("/releases/{path}")
            assertThat(requestCounters().map { it.value })
                .describedAs("the request must be counted once")
                .containsExactly(1.0)
        }
    }

    @Test
    fun `should label a request that matches no route as unmatched`() {
        withServer { baseUrl ->
            // given: a path that no route serves
            // when: the request is answered with 404
            val status = get("$baseUrl/nothing/to/see-here")

            // then: the label is unmatched and the raw path is in no label
            assertThat(status).isEqualTo(404)
            assertThat(requestCounters().map { it.labels.get("route") to it.labels.get("code") })
                .describedAs("an unmatched request must be labelled unmatched with its status code")
                .containsExactly("unmatched" to "404")
        }
    }

    @Test
    fun `should not observe requests to the metrics path`() {
        withServer { baseUrl ->
            // given: a request to the metrics path itself
            // when: the endpoint answers it
            val status = get("$baseUrl/metrics")

            // then: no request series is recorded for it
            assertThat(status).isEqualTo(200)
            assertThat(requestCounters())
                .describedAs("requests to the metrics path must not be counted")
                .isEmpty()
        }
    }

}
