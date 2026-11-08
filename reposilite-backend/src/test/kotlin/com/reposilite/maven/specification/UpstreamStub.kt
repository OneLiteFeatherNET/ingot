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

package com.reposilite.maven.specification

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * An in-process upstream on an ephemeral port. The first path segment picks the behaviour, so one stub plays several
 * upstreams: `s404`, `s410`, `s500`, `html`, `slow` (never answers until [close]), `flaky` (answers as [flaky] says)
 * and anything else (answers 200 with a small body). Nothing sleeps: a hanging request waits on a latch that [close]
 * releases.
 */
internal class UpstreamStub : AutoCloseable {

    enum class Flaky { OK, ERROR, HANG }

    val flaky = AtomicReference(Flaky.OK)

    private val release = CountDownLatch(1)

    private val pool = Executors.newCachedThreadPool { runnable -> Thread(runnable).also { it.isDaemon = true } }

    private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
        executor = pool
        createContext("/") { exchange -> exchange.use { handle(it) } }
        start()
    }

    val baseUrl: String = "http://127.0.0.1:${server.address.port}"

    /** A loopback port nothing listens on, so a connection to it is refused. */
    val refusedBaseUrl: String = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { "http://127.0.0.1:${it.localPort}" }

    private fun handle(exchange: HttpExchange) {
        when (exchange.requestURI.path.split('/').getOrNull(1)) {
            "s404" -> empty(exchange, 404)
            "s410" -> empty(exchange, 410)
            "s500" -> empty(exchange, 500)
            "html" -> content(exchange, "text/html", "<html>login</html>")
            "slow" -> hang(exchange)
            "flaky" -> when (flaky.get()!!) {
                Flaky.OK -> content(exchange, "application/octet-stream", "content")
                Flaky.ERROR -> empty(exchange, 500)
                Flaky.HANG -> hang(exchange)
            }
            else -> content(exchange, "application/octet-stream", "content")
        }
    }

    private fun hang(exchange: HttpExchange) {
        release.await()
        empty(exchange, 500)
    }

    private fun empty(exchange: HttpExchange, status: Int) {
        exchange.sendResponseHeaders(status, -1)
    }

    private fun content(exchange: HttpExchange, contentType: String, body: String) {
        exchange.responseHeaders.add("Content-Type", contentType)
        val bytes = body.toByteArray()
        if (exchange.requestMethod == "HEAD") {
            exchange.sendResponseHeaders(200, -1)
        } else {
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.write(bytes)
        }
    }

    override fun close() {
        release.countDown()
        server.stop(0)
        pool.shutdownNow()
    }

}
