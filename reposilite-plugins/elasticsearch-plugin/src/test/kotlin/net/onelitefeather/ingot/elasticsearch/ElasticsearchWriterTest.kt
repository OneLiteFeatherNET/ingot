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

package net.onelitefeather.ingot.elasticsearch

import net.onelitefeather.ingot.search.Provenance
import net.onelitefeather.ingot.search.backend.BackendBusyException
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.backend.IndexAction
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.URI
import java.time.Duration
import java.time.Instant

/** The write path on a fake transport: the status mapping, the rejected documents and the requests of the removals. */
internal class ElasticsearchWriterTest {

    private val index = "ingot-artifacts"
    private val cluster = URI.create("https://search.example.test:9200")

    private fun writer(
        exchange: FakeExchange,
        clock: ManualClock = ManualClock(),
        secrets: List<String> = emptyList(),
    ): ElasticsearchWriter = ElasticsearchWriter(
        http = ElasticsearchHttp(listOf(cluster), null, Duration.ofSeconds(30), exchange),
        breaker = ElasticsearchCircuitBreaker(clock),
        index = index,
        bulkSize = 500,
        redactor = ElasticsearchRedactor(secrets),
    )

    private val oneUpsert = listOf<IndexAction>(IndexAction.Upsert(testRow()))

    private fun answer(status: Int, body: String = "{}", headers: Map<String, String> = emptyMap()) =
        HttpAnswer(status, headers, body)

    private fun rejectedItem(reason: String) = """
        {"errors":true,"items":[{"index":{"_id":"x","status":400,
        "error":{"type":"mapper_parsing_exception","reason":"$reason"}}}]}
    """.trimIndent()

    @Test
    fun `should send the bulk requests to the bulk endpoint as newline delimited json`() {
        // given: a transport that accepts everything
        val exchange = FakeExchange { answer(200) }

        // when: one upsert is applied
        writer(exchange).apply(oneUpsert)

        // then: one POST to the bulk endpoint carries the action lines
        val call = exchange.calls.single()
        assertThat(call.method).`as`("method").isEqualTo("POST")
        assertThat(call.uri.path).`as`("path").isEqualTo("/_bulk")
        assertThat(call.headers["Content-Type"]).`as`("content type").isEqualTo("application/x-ndjson")
    }

    @Test
    fun `should raise a busy exception with the retry delay of the header when the cluster answers 429`() {
        // given: a cluster that answers 429 with a retry delay of twelve seconds
        val exchange = FakeExchange { answer(429, headers = mapOf("retry-after" to "12")) }

        // when: a batch is applied
        val busy = runCatching { writer(exchange).apply(oneUpsert) }.exceptionOrNull()

        // then: the batch is released with that delay
        assertThat(busy).`as`("exception").isInstanceOf(BackendBusyException::class.java)
        assertThat((busy as BackendBusyException).retryAfter).`as`("retry delay").isEqualTo(Duration.ofSeconds(12))
    }

    @Test
    fun `should raise a busy exception with five seconds when a 503 has no retry header`() {
        // given: a cluster that answers 503 without a retry header
        val exchange = FakeExchange { answer(503) }

        // when: a batch is applied
        val busy = runCatching { writer(exchange).apply(oneUpsert) }.exceptionOrNull()

        // then: the batch is released with the default of five seconds
        assertThat(busy).`as`("exception").isInstanceOf(BackendBusyException::class.java)
        assertThat((busy as BackendBusyException).retryAfter).`as`("retry delay").isEqualTo(Duration.ofSeconds(5))
    }

    @Test
    fun `should raise a busy exception when an item is rejected as too many requests`() {
        // given: a bulk answer whose only item is throttled
        val throttled = """
            {"errors":true,"items":[{"index":{"_id":"x","status":429,
            "error":{"type":"es_rejected_execution_exception","reason":"too many requests"}}}]}
        """.trimIndent()
        val exchange = FakeExchange { answer(200, throttled) }

        // when: a batch is applied
        val busy = runCatching { writer(exchange).apply(oneUpsert) }.exceptionOrNull()

        // then: the batch is released without counting an attempt
        assertThat(busy).`as`("exception").isInstanceOf(BackendBusyException::class.java)
    }

    @Test
    fun `should fail the batch with the error text of a rejected document`() {
        // given: a bulk answer that rejects the document
        val exchange = FakeExchange { answer(200, rejectedItem("failed to parse field [size_bytes]")) }

        // when: a batch is applied
        val failure = runCatching { writer(exchange).apply(oneUpsert) }.exceptionOrNull()

        // then: the batch fails with the error text and is not a busy release
        assertThat(failure).`as`("exception").isInstanceOf(ElasticsearchRejectedException::class.java)
        assertThat(failure?.message).`as`("error text").contains("failed to parse field [size_bytes]")
    }

    @Test
    fun `should redact a password that the cluster echoes in the error of a rejected document`() {
        // given: a bulk answer that echoes the password in its reason
        val exchange = FakeExchange { answer(200, rejectedItem("the value s3cret is not a size")) }

        // when: a batch is applied with the password as a secret
        val failure = runCatching { writer(exchange, secrets = listOf("s3cret")).apply(oneUpsert) }.exceptionOrNull()

        // then: the error text is shown without the password
        assertThat(failure?.message).`as`("error text").doesNotContain("s3cret").contains("***")
    }

    @Test
    fun `should count a removal of a document that does not exist as applied`() {
        // given: a bulk answer that reports a delete of a missing document as not found
        val answerBody = """{"errors":true,"items":[{"delete":{"_id":"x","status":404,
            "error":{"type":"not_found","reason":"document missing"}}}]}"""
        val exchange = FakeExchange { answer(200, answerBody) }

        // when: a removal is applied
        val failure = runCatching { writer(exchange).apply(listOf(IndexAction.RemovePath("releases", "a/b.jar"))) }.exceptionOrNull()

        // then: the removal is idempotent and does not fail the batch
        assertThat(failure).`as`("exception").isNull()
    }

    @Test
    fun `should send the prefix removal to the index with conflicts proceed`() {
        // given: a transport that accepts the removal
        val exchange = FakeExchange { answer(200) }
        val removal = IndexAction.RemovePrefix("releases", "org/example/old", Instant.parse("2026-01-01T00:00:00Z"))

        // when: the removal is applied
        writer(exchange).apply(listOf(removal))

        // then: one request to the delete by query endpoint proceeds on version conflicts
        val call = exchange.calls.single()
        assertThat(call.method).`as`("method").isEqualTo("POST")
        assertThat(call.uri.path).`as`("path").isEqualTo("/$index/_delete_by_query")
        assertThat(call.uri.query).`as`("query").isEqualTo("conflicts=proceed")
    }

    @Test
    fun `should send the reorigin to the index with conflicts proceed`() {
        // given: a transport that accepts the reorigin
        val exchange = FakeExchange { answer(200) }
        val reorigin = IndexAction.Reorigin("releases", "org/example", Provenance.HOSTED)

        // when: the reorigin is applied
        writer(exchange).apply(listOf(reorigin))

        // then: one request to the update by query endpoint proceeds on version conflicts
        val call = exchange.calls.single()
        assertThat(call.uri.path).`as`("path").isEqualTo("/$index/_update_by_query")
        assertThat(call.uri.query).`as`("query").isEqualTo("conflicts=proceed")
    }

    @Test
    fun `should send no request while the circuit is open`() {
        // given: five batches that fail with a transport error, which opens the circuit
        val exchange = FakeExchange { throw IOException("connection refused") }
        val clock = ManualClock()
        val writer = writer(exchange, clock)
        repeat(5) { runCatching { writer.apply(oneUpsert) } }
        val sentBefore = exchange.calls.size

        // when: a batch is applied while the circuit is open
        val busy = runCatching { writer.apply(oneUpsert) }.exceptionOrNull()

        // then: no request is sent and the batch is released with the remaining time
        assertThat(exchange.calls.size).`as`("requests sent while open").isEqualTo(sentBefore)
        assertThat(busy).`as`("exception").isInstanceOf(BackendBusyException::class.java)
    }

}
