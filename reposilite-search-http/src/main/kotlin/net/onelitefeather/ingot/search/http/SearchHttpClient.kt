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

import java.io.IOException
import java.net.URI
import java.time.Duration
import java.util.Base64

/**
 * The JSON-over-HTTP client of a search cluster (design D1). It is the only class of a search plugin that builds requests: the base
 * URLs are tried in the configured order, and the basic authentication header is built from the user and the password. An answer of
 * any status is returned to the caller, which decides what it means. Only a transport failure of every URL is thrown, as an
 * [IOException], so that the circuit breaker counts it.
 */
class SearchHttpClient(
    private val urls: List<URI>,
    username: String?,
    password: String?,
    timeout: Duration,
    private val exchange: HttpExchange = JdkHttpExchange(timeout),
) {

    private val authorization: String? = if (username != null && password != null) {
        "Basic " + Base64.getEncoder().encodeToString("$username:$password".toByteArray(Charsets.UTF_8))
    } else {
        null
    }

    fun send(method: String, path: String, body: String? = null, contentType: String = JSON): HttpAnswer {
        val headers = buildMap {
            put("Accept", JSON)
            put("Content-Type", contentType)
            authorization?.let { put("Authorization", it) }
        }
        var failure: IOException? = null
        for (base in urls) {
            val uri = URI.create(base.toString().trimEnd('/') + path)
            try {
                return exchange.exchange(HttpCall(method, uri, headers, body))
            } catch (transport: IOException) {
                failure?.let { transport.addSuppressed(it) }
                failure = transport
            }
        }
        throw failure ?: IOException("no search cluster URL is configured")
    }

    companion object {
        const val JSON: String = "application/json"
        const val NDJSON: String = "application/x-ndjson"
    }

}
