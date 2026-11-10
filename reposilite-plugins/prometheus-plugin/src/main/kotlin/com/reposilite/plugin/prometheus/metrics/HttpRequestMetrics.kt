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

import com.reposilite.storage.api.toLocation
import io.javalin.config.RoutesConfig
import io.prometheus.metrics.core.metrics.Counter
import io.prometheus.metrics.core.metrics.Histogram
import io.prometheus.metrics.model.registry.PrometheusRegistry
import java.util.function.LongSupplier
import io.prometheus.metrics.model.snapshots.Unit as MetricsUnit

/**
 * Request duration and request count of the HTTP server, labelled with method, route template and status code.
 *
 * Durations are read from [ticker], a monotonic clock in nanoseconds, so the wall clock is not read on the request path.
 * The route label is the matched route template; a request that matched no route is labelled [UNMATCHED_ROUTE].
 */
internal class HttpRequestMetrics(
    registry: PrometheusRegistry,
    private val ticker: LongSupplier,
) {

    private val durations: Histogram = Histogram.builder()
        .name("ingot_http_server_request_duration_seconds")
        .help("Duration of HTTP requests by method, route template and status code")
        .unit(MetricsUnit.SECONDS)
        .labelNames("method", "route", "code")
        .register(registry)

    private val requests: Counter = Counter.builder()
        .name("ingot_http_server_requests_total")
        .help("HTTP requests by method, route template and status code")
        .labelNames("method", "route", "code")
        .register(registry)

    /** The starting point of a request, in nanoseconds of the monotonic clock. */
    fun start(): Long = ticker.getAsLong()

    /**
     * Records one finished request and returns its duration in seconds.
     */
    fun finish(startedAt: Long, method: String, route: String, code: Int): Double {
        val seconds = (ticker.getAsLong() - startedAt).toDouble() / NANOSECONDS_PER_SECOND
        durations.labelValues(method, route, code.toString()).observe(seconds)
        requests.labelValues(method, route, code.toString()).inc()
        return seconds
    }

    /**
     * Times every request that reaches the server. Requests to [metricsPath] are not observed, so scrapes do not count themselves.
     * [onFinish] receives the status code and the duration in seconds of every observed request, so that the existing series can
     * share the same timer.
     */
    fun install(routes: RoutesConfig, metricsPath: String, onFinish: (code: Int, seconds: Double) -> Unit = { _, _ -> }) {
        val metricsLocation = metricsPath.toLocation()

        routes.before { ctx ->
            ctx.attribute(START_ATTRIBUTE, start())
        }

        routes.after { ctx ->
            if (ctx.path().toLocation() == metricsLocation)
                return@after

            val startedAt = ctx.attribute<Long>(START_ATTRIBUTE) ?: return@after
            val code = ctx.statusCode()
            val seconds = finish(startedAt, ctx.method().name, ctx.endpoints().matchedHttpEndpoint()?.path ?: UNMATCHED_ROUTE, code)
            onFinish(code, seconds)
        }
    }

    companion object {
        const val UNMATCHED_ROUTE: String = "unmatched"

        private const val START_ATTRIBUTE = "ingot.request.started"
        private const val NANOSECONDS_PER_SECOND = 1_000_000_000.0
    }

}
