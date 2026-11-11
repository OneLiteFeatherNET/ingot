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

package net.onelitefeather.ingot.nexusimport.fake

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * An in-process stand-in for a Nexus server. It binds an ephemeral loopback port, replays the fixtures of the test
 * resources (`{BASE}` in a fixture is replaced by this server's base URL) and records every request. Behaviour that a
 * real Nexus cannot be told to show, such as a `429` burst or a changed file, is scripted per path with [script].
 *
 * One instance per test. Close it at the end of the test.
 */
class FakeNexus : AutoCloseable {

    private val server: HttpServer = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
    private val handlers = ConcurrentHashMap<String, (RecordedRequest) -> FakeReply>()
    private val scripts = ConcurrentHashMap<String, ConcurrentLinkedQueue<FakeReply>>()
    private val recorded = CopyOnWriteArrayList<RecordedRequest>()

    /** The base URL of this server, for example `http://127.0.0.1:41234`. */
    val baseUri: URI = URI.create("http://127.0.0.1:${server.address.port}")

    /** Requests are answered on a pool, so two downloads can be in flight at once, as they are with a real Nexus. */
    private val pool: ExecutorService = Executors.newFixedThreadPool(FAKE_THREADS)

    init {
        server.executor = pool
        server.createContext("/") { exchange -> dispatch(exchange) }
        server.start()
    }

    /** Answers requests for [path] (the path without query) with [handler]. */
    fun on(path: String, handler: (RecordedRequest) -> FakeReply) {
        handlers[path] = handler
    }

    /** Answers the next requests for [path] with [replies] in order; afterwards the [on] handler, if any, answers. */
    fun script(path: String, vararg replies: FakeReply) {
        scripts.computeIfAbsent(path) { ConcurrentLinkedQueue() }.addAll(replies.toList())
    }

    /** Serves the fixture [name] of `src/test/resources/nexus/` as JSON with status 200 for [path]. */
    fun serveFixture(path: String, name: String, status: Int = 200) {
        on(path) { FakeReply.json(fixture(name), status) }
    }

    /** Every request the server received, in arrival order. */
    fun requests(): List<RecordedRequest> = recorded.toList()

    /** Asserts that no request used a method other than GET or HEAD, which is the read-only promise of the importer. */
    fun assertOnlyReadMethods() {
        val writes = recorded.filter { it.method != "GET" && it.method != "HEAD" }
        check(writes.isEmpty()) { "Nexus received write methods: ${writes.map { "${it.method} ${it.path}" }}" }
    }

    /** The text of a fixture, with `{BASE}` replaced by [baseUri]. */
    fun fixture(name: String): String {
        val stream = requireNotNull(javaClass.getResourceAsStream("/nexus/$name")) { "Missing fixture nexus/$name" }
        return stream.use { String(it.readBytes(), StandardCharsets.UTF_8) }.replace("{BASE}", baseUri.toString())
    }

    private fun dispatch(exchange: HttpExchange) {
        val request = RecordedRequest(
            method = exchange.requestMethod,
            path = exchange.requestURI.path,
            query = parseQuery(exchange.requestURI.rawQuery),
            headers = exchange.requestHeaders.entries.associate { it.key.lowercase() to it.value.joinToString(", ") },
        )
        recorded.add(request)
        val reply = scripts[request.path]?.poll()
            ?: handlers[request.path]?.invoke(request)
            ?: FakeReply.status(404)
        try {
            reply.headers.forEach { (name, value) -> exchange.responseHeaders.add(name, value) }
            val bytes = reply.body
            exchange.sendResponseHeaders(reply.status, if (bytes.isEmpty() || exchange.requestMethod == "HEAD") -1 else bytes.size.toLong())
            if (bytes.isNotEmpty() && exchange.requestMethod != "HEAD") exchange.responseBody.use { it.write(bytes) }
        } finally {
            exchange.close()
        }
    }

    override fun close() {
        server.stop(0)
        pool.shutdownNow()
    }

    private fun parseQuery(raw: String?): Map<String, String> =
        raw.orEmpty().split('&').filter { it.isNotEmpty() }.associate {
            val (key, value) = it.split('=', limit = 2).let { parts -> parts[0] to parts.getOrElse(1) { "" } }
            URLDecoder.decode(key, StandardCharsets.UTF_8) to URLDecoder.decode(value, StandardCharsets.UTF_8)
        }
}

/** A request as the fake received it. Header names are lower case. */
data class RecordedRequest(
    val method: String,
    val path: String,
    val query: Map<String, String>,
    val headers: Map<String, String>,
)

/** A scripted or fixed answer. */
class FakeReply(
    val status: Int,
    val body: ByteArray = ByteArray(0),
    val headers: Map<String, String> = emptyMap(),
) {
    companion object {
        fun json(text: String, status: Int = 200): FakeReply =
            FakeReply(status, text.toByteArray(StandardCharsets.UTF_8), mapOf("Content-Type" to "application/json"))

        fun status(status: Int, headers: Map<String, String> = emptyMap()): FakeReply = FakeReply(status, headers = headers)

        fun bytes(content: ByteArray, headers: Map<String, String> = emptyMap()): FakeReply = FakeReply(200, content, headers)

        fun redirect(location: String): FakeReply = FakeReply(302, headers = mapOf("Location" to location))
    }
}

private const val FAKE_THREADS = 16
