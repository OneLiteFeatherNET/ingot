/*
 * Copyright (c) 2023 dzikoysk
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
package net.onelitefeather.ingot.cli

import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Base64

/** The server could not be reached, or did not answer within the timeout. */
class ServerUnreachableException(cause: Throwable) : RuntimeException("The server could not be reached", cause)

data class Reply(val status: Int, val body: String)

/**
 * Talks to the console API of one server. The token travels in the Authorization header only. The base URL keeps its
 * context path, so `https://host/ingot` is served under `/ingot/api/console`.
 */
class IngotClient(
    baseUrl: String,
    private val token: String,
    private val timeout: Duration,
    private val http: HttpClient,
) {

    private val base = baseUrl.trimEnd('/')
    private val authorization = "Basic " + Base64.getEncoder().encodeToString(token.toByteArray(StandardCharsets.UTF_8))

    /** Runs one command line in structured mode. [confirm] sends `confirm=true`, which a dangerous command needs. */
    fun execute(commandLine: String, confirm: Boolean): Reply {
        val query = if (confirm) "structured=true&confirm=true" else "structured=true"
        return send("/api/console/execute?$query", HttpRequest.BodyPublishers.ofString(commandLine, StandardCharsets.UTF_8), "text/plain")
    }

    /** The command listing, for completion. */
    fun listCommands(): Reply =
        send("/api/console/commands", null, null)

    private fun send(path: String, body: HttpRequest.BodyPublisher?, contentType: String?): Reply {
        val builder = HttpRequest.newBuilder(URI.create(base + path))
            .timeout(timeout)
            .header("Authorization", authorization)
            .header("Accept", "application/json")
        if (contentType != null) builder.header("Content-Type", contentType)
        val request = (if (body == null) builder.GET() else builder.POST(body)).build()
        return try {
            val response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            Reply(response.statusCode(), response.body())
        } catch (failure: IOException) {
            throw ServerUnreachableException(failure)
        } catch (failure: InterruptedException) {
            Thread.currentThread().interrupt()
            throw ServerUnreachableException(failure)
        }
    }

}
