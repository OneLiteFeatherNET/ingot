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

package net.onelitefeather.ingot.meilisearch

import com.fasterxml.jackson.databind.JsonNode
import com.reposilite.ReposiliteObjectMapper
import net.onelitefeather.ingot.search.SearchUnavailableException
import net.onelitefeather.ingot.search.backend.BackendBusyException
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.http.CredentialRedactor
import net.onelitefeather.ingot.search.http.HttpAnswer
import net.onelitefeather.ingot.search.http.HttpCall
import net.onelitefeather.ingot.search.http.HttpExchange
import net.onelitefeather.ingot.search.http.SearchCircuitBreaker
import net.onelitefeather.ingot.search.http.SearchHttpClient
import java.io.IOException
import java.time.Clock
import java.time.Duration

/** A request the server refused for good, for example a document without an id. The message is redacted before it is thrown. */
internal class MeilisearchRejectedException(message: String) : RuntimeException(message)

/** The pause between two polls of a task. Production sleeps; the tests move a manual clock. */
internal fun interface MeilisearchWaiter {
    fun pause(duration: Duration)
}

/** The waiter of production: a real pause between two polls. */
internal object SleepingWaiter : MeilisearchWaiter {
    override fun pause(duration: Duration) {
        Thread.sleep(duration.toMillis())
    }
}

/**
 * The JSON calls of the Meilisearch backend, through the shared transport, the circuit breaker and the redactor. The API key goes
 * only in the `Authorization` header, which [bearer] adds to every call; it is never part of a URL or of a message.
 */
internal class MeilisearchClient(
    private val url: java.net.URI,
    private val apiKey: String?,
    private val timeout: Duration,
    clock: Clock,
    exchange: HttpExchange,
) {

    private val mapper = ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
    private val redactor = CredentialRedactor(listOfNotNull(apiKey))
    private val breaker = SearchCircuitBreaker(clock)
    private val http = SearchHttpClient(listOf(url), null, null, timeout, bearer(apiKey, exchange))

    /** Whether the circuit is open, and the reason of a pause is the same text the health reports. */
    fun pausedFor(): Duration? = breaker.openRemaining()

    /** Sends one request and returns its answer, whatever the status. A transport failure is reported as unreachable. */
    fun send(method: String, path: String, body: Any? = null): HttpAnswer {
        val text = body?.let(mapper::writeValueAsString)
        return try {
            breaker.call { http.send(method, path, text, SearchHttpClient.JSON) }
        } catch (failure: IOException) {
            throw SearchUnavailableException(redactor.redact(UNREACHABLE))
        }
    }

    /**
     * Sends one request and returns the JSON of a success. A busy server (429 or 503) raises [BackendBusyException]; any other
     * refusal raises [MeilisearchRejectedException] with the error code of the server.
     */
    fun json(method: String, path: String, body: Any? = null): JsonNode {
        val answer = send(method, path, body)
        return when (answer.status) {
            in 200..299 -> if (answer.body.isBlank()) mapper.createObjectNode() else mapper.readTree(answer.body)
            429, 503 -> throw BackendBusyException("the server answered ${answer.status} to $method $path", BUSY_RETRY_AFTER)
            else -> throw MeilisearchRejectedException(redactor.redact(describe(answer.status, method, path, answer.body)))
        }
    }

    /** Redacts a text that is about to leave the plugin, such as a message of a task or of a refused setting. */
    fun redact(text: String): String = redactor.redact(text)

    private fun describe(status: Int, method: String, path: String, body: String): String {
        val error = runCatching { mapper.readTree(body) }.getOrNull()
        val code = error?.path("code")?.asText()?.takeIf { it.isNotBlank() } ?: "no code"
        return "the server answered $status with $code to $method ${path.substringBefore('?')}"
    }

    companion object {
        const val UNREACHABLE: String = "the search server is not reachable, requests are paused"
        val BUSY_RETRY_AFTER: Duration = Duration.ofSeconds(10)

        /** Adds the bearer header to every call of [exchange] when a key is configured. */
        fun bearer(apiKey: String?, exchange: HttpExchange): HttpExchange {
            if (apiKey == null) return exchange
            return HttpExchange { call: HttpCall -> exchange.exchange(call.copy(headers = call.headers + ("Authorization" to "Bearer $apiKey"))) }
        }
    }

}
