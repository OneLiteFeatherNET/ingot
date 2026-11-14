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

package net.onelitefeather.ingot.plugin.store

import java.io.IOException
import java.io.InputStream
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** The answer of a source to one request. */
sealed interface FetchResult {

    data class Found(val bytes: ByteArray) : FetchResult

    /** The source answers, and it does not hold the file. */
    data object NotFound : FetchResult

    /** The source cannot be reached or does not answer in time, so the entry may still be available from the cache. */
    data class Unreachable(val reason: String) : FetchResult

}

/** Reads one file of a source. The store depends on this port, so tests answer from memory. */
interface PluginSourceTransport {

    /** Fetches [url], and refuses a body larger than [maxBytes] as unreachable. */
    fun fetch(url: String, maxBytes: Long): FetchResult

}

/**
 * The transport of a production start. Redirects are followed only while they stay on the scheme of the request, so an
 * `https` source never redirects to plain HTTP. The JVM proxy properties apply through the default proxy selector.
 */
class HttpPluginSourceTransport(
    private val timeout: Duration = Duration.ofSeconds(30),
) : PluginSourceTransport {

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(timeout)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .apply { ProxySelector.getDefault()?.let { proxy(it) } }
        .build()

    override fun fetch(url: String, maxBytes: Long): FetchResult =
        try {
            val request = HttpRequest.newBuilder(URI.create(url)).timeout(timeout).GET().build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
            response.body().use { body ->
                when (val status = response.statusCode()) {
                    404 -> FetchResult.NotFound
                    in 200..299 -> readLimited(body, maxBytes)?.let { FetchResult.Found(it) }
                        ?: FetchResult.Unreachable("the file is larger than $maxBytes bytes")
                    else -> FetchResult.Unreachable("HTTP $status")
                }
            }
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            FetchResult.Unreachable("the request was interrupted")
        } catch (exception: IOException) {
            FetchResult.Unreachable(exception.message ?: "the connection failed")
        } catch (exception: IllegalArgumentException) {
            FetchResult.Unreachable("the address is not valid")
        }

    /** Reads at most [maxBytes] bytes, or returns null when the body is larger. */
    private fun readLimited(body: InputStream, maxBytes: Long): ByteArray? {
        val bytes = body.readNBytes((maxBytes + 1).toInt())
        return if (bytes.size > maxBytes) null else bytes
    }

}
