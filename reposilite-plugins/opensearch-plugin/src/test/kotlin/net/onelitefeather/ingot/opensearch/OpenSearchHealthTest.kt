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

@file:OptIn(ExperimentalIngotSearchApi::class)

package net.onelitefeather.ingot.opensearch

import net.onelitefeather.ingot.search.backend.BackendHealth
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Health through the circuit breaker, with a clock that does not move and an exchange that never reaches a socket. */
internal class OpenSearchHealthTest {

    private val fixedClock: Clock = Clock.fixed(Instant.parse("2026-11-13T10:00:00Z"), ZoneOffset.UTC)

    private val environment = mapOf(
        "INGOT_OPENSEARCH_URLS" to "https://search.test:9200",
        "INGOT_OPENSEARCH_USERNAME" to "ingot",
        "INGOT_OPENSEARCH_PASSWORD" to "not-in-messages",
    )

    @Test
    fun `health is down with the transport reason after a single failed request`() {
        val exchange = CountingExchange()
        val backend = backend(exchange)

        val health = backend.health()

        assertThat(health).`as`("health after one transport failure").isInstanceOf(BackendHealth.Down::class.java)
        assertThat((health as BackendHealth.Down).message)
            .`as`("reason of the first failure")
            .doesNotContain("not-in-messages")
            .contains("not reachable")
    }

    @Test
    fun `a circuit opened by five failures sends no request and reports the pause`() {
        val exchange = CountingExchange()
        val backend = backend(exchange)
        repeat(FAILURES_TO_OPEN) { backend.health() }
        val requestsBefore = exchange.calls

        val health = backend.health()

        assertThat(exchange.calls).`as`("requests sent while the circuit is open").isEqualTo(requestsBefore)
        assertThat((health as BackendHealth.Down).message).`as`("reason while paused").contains("paused")
    }

    private fun backend(exchange: CountingExchange): OpenSearchSearchBackend =
        OpenSearchSearchBackend(OpenSearchSettings.read(environment), fixedClock, sslContext = null, exchange = exchange)

    private class CountingExchange : HttpExchange {
        var calls: Int = 0
            private set

        override fun exchange(call: HttpCall): HttpAnswer {
            calls += 1
            throw IOException("connection refused")
        }
    }

    private companion object {
        const val FAILURES_TO_OPEN = 5
    }

}
