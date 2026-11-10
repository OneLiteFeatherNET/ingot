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

package net.onelitefeather.ingot.nexusimport.nexus

import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import javax.net.ssl.SSLContext

/** One answer of Nexus. The body is open until the caller closes it. */
class HttpReply(
    val status: Int,
    /** Header names in lower case. */
    val headers: Map<String, String>,
    val body: InputStream,
)

/**
 * The only way the importer talks to Nexus. It sends GET requests and nothing else: the methods of the Nexus client are the
 * read surface of the REST API, and the test fake records any other method as a failure.
 */
fun interface NexusTransport {
    fun get(uri: URI, headers: Map<String, String>): HttpReply
}

/**
 * The JDK HTTP client. Redirects are never followed by the client itself, because the Nexus client has to decide whether
 * the Authorization header may go to the next origin. TLS uses the given [SSLContext], which defaults to the JVM trust store.
 */
class JdkNexusTransport(
    private val sslContext: SSLContext? = null,
    private val connectTimeout: Duration = Duration.ofSeconds(10),
    private val requestTimeout: Duration = Duration.ofMinutes(10),
) : NexusTransport {

    private val client: HttpClient by lazy {
        val builder = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(connectTimeout)
        sslContext?.let { builder.sslContext(it) }
        builder.build()
    }

    override fun get(uri: URI, headers: Map<String, String>): HttpReply {
        val builder = HttpRequest.newBuilder(uri).timeout(requestTimeout).GET()
        headers.forEach { (name, value) -> builder.header(name, value) }
        val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream())
        return HttpReply(
            status = response.statusCode(),
            headers = response.headers().map().mapKeys { it.key.lowercase() }.mapValues { it.value.joinToString(", ") },
            body = response.body(),
        )
    }
}
