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
import net.onelitefeather.ingot.search.SearchUnavailableException
import net.onelitefeather.ingot.search.backend.BackendBusyException
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import java.io.IOException

/**
 * The requests of the backend that read an answer, and the breaker and the redactor around them. A transport failure, a busy
 * breaker and an error status become a [SearchUnavailableException] with a redacted message, so a read never shows a cluster
 * detail that carries a secret. [send] returns the answer as it came, for the callers that decide what a status means.
 */
internal class OpenSearchClient(
    private val http: OpenSearchHttp,
    private val breaker: OpenSearchCircuitBreaker,
    private val redactor: OpenSearchRedactor,
) {

    private val mapper = ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER

    /** One request through the breaker. Only a transport failure or a busy breaker is thrown. */
    fun send(method: String, path: String, body: String? = null, contentType: String = OpenSearchHttp.JSON): HttpAnswer =
        breaker.call { http.send(method, path, body, contentType) }

    /** One request whose answer must be a success, parsed as JSON. [body] is serialised as JSON when it is not null. */
    fun read(method: String, path: String, body: Any? = null): JsonNode {
        val answer = try {
            send(method, path, body?.let(mapper::writeValueAsString))
        } catch (failure: IOException) {
            throw SearchUnavailableException(UNREACHABLE)
        } catch (busy: BackendBusyException) {
            throw SearchUnavailableException(UNREACHABLE)
        }
        if (answer.status !in 200..299) {
            throw SearchUnavailableException(redactor.redact("the cluster answered ${answer.status} to $method $path: ${answer.body.take(MAX_TEXT)}"))
        }
        return mapper.readTree(answer.body)
    }

    /** Parses [text] as JSON, for the callers that serialise a body themselves. */
    fun parse(text: String): JsonNode = mapper.readTree(text)

    companion object {
        const val UNREACHABLE: String = "the search cluster is not reachable, the search is unavailable"
        const val MAX_TEXT: Int = 500
    }

}
