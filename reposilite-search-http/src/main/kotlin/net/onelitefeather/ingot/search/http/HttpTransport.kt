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
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Locale
import javax.net.ssl.SSLContext

/** One request as a search plugin sends it. Header names are as written here. */
data class HttpCall(
    val method: String,
    val uri: URI,
    val headers: Map<String, String>,
    val body: String?,
)

/** One answer of a cluster. Header names are in lower case, so that [header] finds them in any spelling. */
data class HttpAnswer(
    val status: Int,
    val headers: Map<String, String>,
    val body: String,
) {

    fun header(name: String): String? = headers[name.lowercase(Locale.ROOT)]

}

/**
 * The seam under [SearchHttpClient]: one call, one answer. The production implementation is [JdkHttpExchange]; the tests replace it,
 * so that no unit test opens a socket. An I/O failure is thrown as [IOException].
 */
fun interface HttpExchange {
    fun exchange(call: HttpCall): HttpAnswer
}

/** The production exchange on the JDK client. The default SSL context of the JVM is used, with hostname verification. */
class JdkHttpExchange(private val timeout: Duration, sslContext: SSLContext? = null) : HttpExchange {

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
