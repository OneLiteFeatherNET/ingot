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

import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A stand-in for the console API, in process on an ephemeral port. It records every request and answers with what the
 * test's responder returns. Nothing here waits by time: each call returns when its response has been read.
 */
internal class StubIngot(private val responder: (Request) -> Pair<Int, String>) : AutoCloseable {

    data class Request(val method: String, val path: String, val query: String?, val authorization: String?, val body: String)

    val requests = CopyOnWriteArrayList<Request>()
    private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)

    init {
        server.createContext("/") { exchange ->
            val request = Request(
                method = exchange.requestMethod,
                path = exchange.requestURI.path,
                query = exchange.requestURI.rawQuery,
                authorization = exchange.requestHeaders.getFirst("Authorization"),
                body = exchange.requestBody.readBytes().toString(StandardCharsets.UTF_8),
            )
            requests.add(request)
            val (status, body) = responder(request)
            val bytes = body.toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
    }

    val url: String
        get() = "http://127.0.0.1:${server.address.port}"

    override fun close() {
        server.stop(0)
    }

}

internal fun answer(exitCode: Int, status: String = "SUCCEEDED", stream: String = "stdout", text: String = "ok"): Pair<Int, String> =
    200 to """{"status":"$status","exitCode":$exitCode,"output":[{"stream":"$stream","text":"$text"}],"data":null,"node":"node-1"}"""
