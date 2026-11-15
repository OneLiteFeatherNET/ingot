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
package com.reposilite.console

import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit.SECONDS

/** A console WebSocket. Messages are queued as they arrive, and a receive waits for them with a timeout. */
internal class ConsoleSocket(uri: URI) {

    private val messages = LinkedBlockingQueue<String>()

    private val socket: WebSocket = HttpClient.newHttpClient()
        .newWebSocketBuilder()
        .buildAsync(uri, object : WebSocket.Listener {
            override fun onOpen(webSocket: WebSocket) {
                webSocket.request(1)
            }

            override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*> {
                messages.add(data.toString())
                webSocket.request(1)
                return CompletableFuture.completedFuture(null)
            }
        })
        .get(TIMEOUT_SECONDS, SECONDS)

    fun send(text: String) {
        socket.sendText(text, true).get(TIMEOUT_SECONDS, SECONDS)
    }

    /** Every message received up to and including the first one that matches [predicate]. */
    fun receiveUntil(predicate: (String) -> Boolean): List<String> {
        val seen = mutableListOf<String>()
        while (true) {
            val message = messages.poll(TIMEOUT_SECONDS, SECONDS) ?: throw AssertionError("no matching message, received: $seen")
            seen += message
            if (predicate(message)) return seen
        }
    }

    fun close() {
        socket.abort()
    }

    private companion object {
        const val TIMEOUT_SECONDS = 10L
    }

}
