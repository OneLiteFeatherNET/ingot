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

import com.fasterxml.jackson.databind.JsonNode
import com.reposilite.ReposiliteObjectMapper
import net.onelitefeather.ingot.search.backend.BackendBusyException
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.backend.IndexAction
import java.time.Duration

/** The delay of a busy answer that gives no `Retry-After` (design D4). */
internal val DEFAULT_RETRY_AFTER: Duration = Duration.ofSeconds(5)

/**
 * A batch that the cluster refused for good, for example a document that does not match the mapping. The outbox counts it as a
 * failed attempt. Its message is redacted before it is thrown.
 */
internal class OpenSearchRejectedException(message: String) : RuntimeException(message)

/**
 * Applies the actions of a batch to the index alias (design D4). Every request goes through the circuit breaker. A cluster that is
 * busy (`429`, `503`, or an item rejected as too many requests) raises a [BackendBusyException] with the `Retry-After` delay, and
 * the outbox releases the batch without an attempt. A rejected document raises an [OpenSearchRejectedException] with its error text.
 * A removal of a document that does not exist counts as applied, so a replay changes nothing.
 */
internal class OpenSearchWriter(
    private val http: OpenSearchHttp,
    private val breaker: OpenSearchCircuitBreaker,
    private val index: String,
    private val bulkSize: Int,
    private val redactor: OpenSearchRedactor,
) {

    private val mapper = ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER

    fun apply(actions: List<IndexAction>) {
        OpenSearchBulk.plan(index, actions, bulkSize).forEach { step ->
            when (step) {
                is WriteStep.Bulk -> bulk(step)
                is WriteStep.Single -> query(step.action)
            }
        }
    }

    private fun bulk(step: WriteStep.Bulk) {
        val answer = breaker.call { http.send("POST", "/_bulk", step.body, OpenSearchHttp.NDJSON) }
        checkStatus(answer, "the bulk request")
        val report = mapper.readTree(answer.body)
        if (!report.path("errors").asBoolean()) return

        val rejections = mutableListOf<String>()
        var throttled = false
        report.path("items").forEach { item ->
            val operation = item.fieldNames().next()
            val result = item.path(operation)
            val status = result.path("status").asInt()
            val error = result.path("error")
            when {
                status in 200..299 -> Unit
                operation == "delete" && status == 404 -> Unit
                isThrottled(status, error) -> throttled = true
                else -> rejections += "${error.path("type").asText()}: ${error.path("reason").asText("status $status")}"
            }
        }
        if (rejections.isNotEmpty()) {
            throw OpenSearchRejectedException(redactor.redact("the cluster rejected a document: ${rejections.first()}"))
        }
        if (throttled) {
            throw BackendBusyException("the cluster throttled part of the bulk request", DEFAULT_RETRY_AFTER)
        }
    }

    private fun query(action: IndexAction) {
        val (path, body) = when (action) {
            is IndexAction.RemovePrefix -> "/$index/_delete_by_query?conflicts=proceed" to OpenSearchRequests.deleteByPrefix(action)
            is IndexAction.Reorigin -> "/$index/_update_by_query?conflicts=proceed" to OpenSearchRequests.reorigin(action)
            is IndexAction.Upsert, is IndexAction.RemovePath -> error("a bulk action is not sent as a query request")
        }
        val answer = breaker.call { http.send("POST", path, mapper.writeValueAsString(body)) }
        checkStatus(answer, "the query request")
    }

    /** Maps the status of a request: a success returns, a busy cluster throws the busy exception, anything else is a rejection. */
    private fun checkStatus(answer: HttpAnswer, request: String) {
        when (answer.status) {
            in 200..299 -> Unit
            429, 503 -> throw BackendBusyException("the cluster answered ${answer.status} to $request", retryAfter(answer))
            else -> throw OpenSearchRejectedException(
                redactor.redact("the cluster answered ${answer.status} to $request: ${answer.body.take(MAX_ERROR_TEXT)}"),
            )
        }
    }

    private fun isThrottled(status: Int, error: JsonNode): Boolean =
        status == 429 ||
            error.path("type").asText() == "es_rejected_execution_exception" ||
            error.path("reason").asText().contains("too many requests", ignoreCase = true)

    /** The delay of the `Retry-After` header in seconds, or five seconds when the header is missing or not a number of seconds. */
    private fun retryAfter(answer: HttpAnswer): Duration =
        answer.header("Retry-After")?.trim()?.toLongOrNull()?.takeIf { it >= 0 }?.let { Duration.ofSeconds(it) }
            ?: DEFAULT_RETRY_AFTER

    private companion object {
        const val MAX_ERROR_TEXT = 500
    }

}
