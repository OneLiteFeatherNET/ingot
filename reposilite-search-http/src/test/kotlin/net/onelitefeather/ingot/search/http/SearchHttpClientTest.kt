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

package net.onelitefeather.ingot.search.http

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.URI
import java.time.Duration
import java.util.Base64

/** The only class that speaks HTTP: its requests, its credentials and its failover between URLs, on a fake transport. */
internal class SearchHttpClientTest {

    private val first = URI.create("https://first.example.test:9200")
    private val second = URI.create("https://second.example.test:9200")

    @Test
    fun `should send the basic authorization header built from the user and the password`() {
        // given: a client with credentials and a transport that answers
        val exchange = FakeExchange()
        val http = SearchHttpClient(listOf(first), username = "indexer", password = "s3cret", timeout = Duration.ofSeconds(30), exchange = exchange)

        // when: a request is sent
        http.send("GET", "/_cluster/health")

        // then: the header carries the base64 of user and password
        val expected = "Basic " + Base64.getEncoder().encodeToString("indexer:s3cret".toByteArray())
        assertThat(exchange.calls.single().headers["Authorization"]).`as`("authorization header").isEqualTo(expected)
    }

    @Test
    fun `should send the api key authorization header as it was given`() {
        // given: a client with an encoded API key
        val exchange = FakeExchange()
        val http = SearchHttpClient(listOf(first), null, null, Duration.ofSeconds(30), exchange, apiKey = "QXBpS2V5SWQ6c2VjcmV0")

        // when: a request is sent
        http.send("GET", "/_cluster/health")

        // then: the header is the ApiKey scheme with the key, and no basic header is added
        assertThat(exchange.calls.single().headers["Authorization"])
            .`as`("authorization header")
            .isEqualTo("ApiKey QXBpS2V5SWQ6c2VjcmV0")
    }

    @Test
    fun `should send the api key and no basic header when a key and a user are both configured`() {
        // given: a client with an API key and a user (the settings refuse this pair, the client still picks the key)
        val exchange = FakeExchange()
        val http = SearchHttpClient(listOf(first), "indexer", "s3cret", Duration.ofSeconds(30), exchange, apiKey = "QXBpS2V5SWQ6c2VjcmV0")

        // when: a request is sent
        http.send("GET", "/_cluster/health")

        // then: the ApiKey scheme is the only authorization header
        assertThat(exchange.calls.single().headers["Authorization"])
            .`as`("authorization header")
            .isEqualTo("ApiKey QXBpS2V5SWQ6c2VjcmV0")
    }

    @Test
    fun `should send no authorization header when no user is configured`() {
        // given: a client without credentials
        val exchange = FakeExchange()
        val http = SearchHttpClient(listOf(first), username = null, password = null, timeout = Duration.ofSeconds(30), exchange = exchange)

        // when: a request is sent
        http.send("GET", "/_cluster/health")

        // then: no authorization header is sent
        assertThat(exchange.calls.single().headers).`as`("headers").doesNotContainKey("Authorization")
    }

    @Test
    fun `should send the request to the base URL with the path and the query`() {
        // given: a client with a base URL that ends in a slash
        val exchange = FakeExchange()
        val http = SearchHttpClient(listOf(URI.create("https://first.example.test:9200/")), null, null, Duration.ofSeconds(30), exchange)

        // when: a request with a query is sent
        http.send("POST", "/ingot-artifacts/_delete_by_query?conflicts=proceed", body = "{}")

        // then: the URI joins base and path without a doubled slash
        assertThat(exchange.calls.single().uri.toString())
            .`as`("uri")
            .isEqualTo("https://first.example.test:9200/ingot-artifacts/_delete_by_query?conflicts=proceed")
    }

    @Test
    fun `should try the next URL after a transport failure`() {
        // given: a first cluster that refuses the connection and a second one that answers
        val exchange = FakeExchange { call ->
            if (call.uri.host == first.host) throw IOException("connection refused")
            HttpAnswer(200, emptyMap(), "{}")
        }
        val http = SearchHttpClient(listOf(first, second), null, null, Duration.ofSeconds(30), exchange)

        // when: a request is sent
        val answer = http.send("GET", "/_cluster/health")

        // then: the second cluster answered
        assertThat(answer.status).`as`("status of the answer").isEqualTo(200)
        assertThat(exchange.calls.last().uri.host).`as`("host of the answering request").isEqualTo(second.host)
    }

    @Test
    fun `should report a transport failure when every URL is unreachable`() {
        // given: two clusters that both refuse the connection
        val exchange = FakeExchange { throw IOException("connection refused") }
        val http = SearchHttpClient(listOf(first, second), null, null, Duration.ofSeconds(30), exchange)

        // when: a request is sent
        // then: the failure reaches the caller as a transport failure, which the circuit breaker counts
        assertThatThrownBy { http.send("GET", "/_cluster/health") }
            .`as`("transport failure")
            .isInstanceOf(IOException::class.java)
    }

}
