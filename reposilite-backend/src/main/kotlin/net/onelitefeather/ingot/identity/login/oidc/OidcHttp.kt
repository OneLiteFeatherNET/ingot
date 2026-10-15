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

package net.onelitefeather.ingot.identity.login.oidc

import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

/** The status and the body of an answer from an identity provider. */
data class HttpReply(val status: Int, val body: String)

/** The outbound calls of the external login flows. Tests and the connection check use the same calls as production. */
interface HttpFetcher {

    /** A GET with the given headers. Throws [java.io.IOException] when the provider cannot be reached. */
    fun get(uri: URI, headers: Map<String, String> = emptyMap()): HttpReply

    /** A form POST with the given headers. Throws [java.io.IOException] when the provider cannot be reached. */
    fun postForm(uri: URI, form: Map<String, String>, headers: Map<String, String> = emptyMap()): HttpReply

}

/**
 * The JDK HTTP client with a five second connect and read timeout. Redirects are never followed, so a provider cannot send
 * the client to another host with the credentials attached.
 */
class JdkHttpFetcher : HttpFetcher {

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(TIMEOUT)
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    override fun get(uri: URI, headers: Map<String, String>): HttpReply =
        send(HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().also { builder -> headers.forEach { builder.header(it.key, it.value) } }.build())

    override fun postForm(uri: URI, form: Map<String, String>, headers: Map<String, String>): HttpReply {
        val body = form.entries.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }
        val builder = HttpRequest.newBuilder(uri)
            .timeout(TIMEOUT)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
        headers.forEach { builder.header(it.key, it.value) }
        return send(builder.build())
    }

    private fun send(request: HttpRequest): HttpReply {
        val response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        return HttpReply(response.statusCode(), response.body())
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)

    private companion object {
        val TIMEOUT: Duration = Duration.ofSeconds(5)
    }

}
