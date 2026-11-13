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

package net.onelitefeather.ingot.opensearch

import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64
import java.util.Locale
import javax.net.ssl.SSLContext

/** One request as the plugin sends it. Header names are as written here. */
internal data class HttpCall(
    val method: String,
    val uri: URI,
    val headers: Map<String, String>,
    val body: String?,
)

/** One answer of a cluster. Header names are in lower case, so that [header] finds them in any spelling. */
internal data class HttpAnswer(
    val status: Int,
    val headers: Map<String, String>,
    val body: String,
) {

    fun header(name: String): String? = headers[name.lowercase(Locale.ROOT)]

}

/**
 * The seam under [OpenSearchHttp]: one call, one answer. The production implementation is [JdkHttpExchange]; the tests replace it,
 * so that no unit test opens a socket. An I/O failure is thrown as [IOException].
 */
internal fun interface HttpExchange {
    fun exchange(call: HttpCall): HttpAnswer
}

/**
 * The client of the OpenSearch REST API (design D1). It is the only class of the plugin that builds requests: the base URLs are
 * tried in the configured order, and the basic authentication header is built from the user and the password. An answer of any
 * status is returned to the caller, which decides what it means. Only a transport failure of every URL is thrown, as an
 * [IOException], so that the circuit breaker counts it.
 */
internal class OpenSearchHttp(
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
        throw failure ?: IOException("no OpenSearch URL is configured")
    }

    companion object {
        const val JSON: String = "application/json"
        const val NDJSON: String = "application/x-ndjson"
    }

}

/** The production exchange on the JDK client. The default SSL context of the JVM is used, with hostname verification. */
internal class JdkHttpExchange(private val timeout: Duration, sslContext: SSLContext? = null) : HttpExchange {

    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(timeout).apply { sslContext?.let(::sslContext) }.build()

    override fun exchange(call: HttpCall): HttpAnswer {
        val publisher = call.body?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody()
        val request = HttpRequest.newBuilder(call.uri)
            .timeout(timeout)
            .method(call.method, publisher)
        call.headers.forEach { (name, value) -> request.header(name, value) }
        val response = try {
            client.send(request.build(), HttpResponse.BodyHandlers.ofString())
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("the request to the search cluster was interrupted", interrupted)
        }
        val headers = response.headers().map().entries.associate { (name, values) ->
            name.lowercase(Locale.ROOT) to values.firstOrNull().orEmpty()
        }
        return HttpAnswer(response.statusCode(), headers, response.body())
    }

}
