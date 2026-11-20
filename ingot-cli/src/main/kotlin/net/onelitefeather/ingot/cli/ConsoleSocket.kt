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

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.NullNode
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutionException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** The server refused the hello or a request with an `error` frame. [code] is null for a legacy server's plain refusal. */
class ConsoleSocketRefusedException(val code: String?, message: String) : RuntimeException(message)

/** The connection ended before the answer arrived. A command that was sent may or may not have run. */
class ConsoleSocketLostException(message: String) : RuntimeException(message)

data class ConsoleWelcome(val protocol: Int, val boot: String?, val seq: Long?, val replay: String?)

data class ConsoleLogLine(val seq: Long, val node: String, val level: String, val text: String, val dropped: Int)

data class ConsoleOutputLine(val stream: String, val text: String)

data class ConsoleResult(
    val status: String,
    val exitCode: Int,
    val output: List<ConsoleOutputLine>,
    val data: JsonNode?,
    val node: String,
    val confirmed: Boolean,
)

data class ConsoleCompletion(val start: Int, val end: Int, val candidates: List<String>)

/**
 * One connection to the console socket in protocol version 1: a hello with the token, then JSON text frames. Log frames go
 * to the callback as they arrive, and every other frame is an answer that a caller waits for by its `id`. One caller at a
 * time: the REPL runs one command or one completion after the other.
 */
class ConsoleSocket private constructor(
    private val socket: WebSocket,
    private val answers: LinkedBlockingQueue<JsonNode>,
    private val timeout: Duration,
    val welcome: ConsoleWelcome,
) : AutoCloseable {

    private val ids = AtomicLong()

    /** Runs [command]. A dangerous command asks [confirm] with the command and the reason, and runs only on true. */
    fun execute(command: String, confirm: (command: String, reason: String) -> Boolean): ConsoleResult {
        val id = "e" + ids.incrementAndGet()
        send(mapOf("type" to "execute", "id" to id, "command" to command))
        while (true) {
            val answer = awaitAnswer(id, null)
            when (answer.text("type")) {
                "confirm" -> send(mapOf("type" to "confirm", "id" to id, "accept" to confirm(answer.text("command").orEmpty(), answer.text("reason").orEmpty())))
                "result" -> return ConsoleResult(
                    status = answer.text("status").orEmpty(),
                    exitCode = answer.path("exitCode").asInt(),
                    output = answer.path("output").map { ConsoleOutputLine(it.path("stream").asText(), it.path("text").asText()) },
                    data = answer.get("data")?.takeUnless { it.isNull },
                    node = answer.text("node").orEmpty(),
                    confirmed = answer.path("confirmed").asBoolean(),
                )
                else -> throw refusal(answer)
            }
        }
    }

    /** Asks the server for the words that may stand at [cursor] of [line]. */
    fun complete(line: String, cursor: Int): ConsoleCompletion {
        val id = "c" + ids.incrementAndGet()
        send(mapOf("type" to "complete", "id" to id, "line" to line, "cursor" to cursor))
        val answer = awaitAnswer(id, timeout)
        if (answer.text("type") != "complete") throw refusal(answer)
        val replace = answer.path("replace")
        return ConsoleCompletion(replace.path("start").asInt(), replace.path("end").asInt(), answer.path("candidates").map { it.asText() })
    }

    override fun close() {
        runCatching { socket.sendClose(WebSocket.NORMAL_CLOSURE, "").get(timeout.toMillis(), TimeUnit.MILLISECONDS) }
        socket.abort()
    }

    private fun send(frame: Map<String, Any>) {
        try {
            socket.sendText(MAPPER.writeValueAsString(frame), true).get()
        } catch (failure: ExecutionException) {
            throw ConsoleSocketLostException("The connection to the server was lost")
        }
    }

    /** The next answer for [id]. Answers for other ids, left over from an earlier wait, are skipped. */
    private fun awaitAnswer(id: String, limit: Duration?): JsonNode {
        while (true) {
            val answer = (if (limit == null) answers.take() else answers.poll(limit.toMillis(), TimeUnit.MILLISECONDS))
                ?: throw ConsoleSocketLostException("The server did not answer in time")
            if (answer === CLOSED) {
                answers.add(CLOSED)
                throw ConsoleSocketLostException("The connection to the server was lost")
            }
            val answerId = answer.text("id")
            if (answerId == null || answerId == id) return answer
        }
    }

    companion object {

        private val MAPPER = ObjectMapper()

        /** Stands in the answer queue once the connection has ended, so that every wait ends too. */
        private val CLOSED: JsonNode = NullNode.instance

        /**
         * Connects to `<baseUrl>/api/console/sock`, sends the hello and waits for the welcome. A refusal throws
         * [ConsoleSocketRefusedException]. The token is sent in the hello only and appears in no message.
         */
        fun open(
            baseUrl: String,
            token: String,
            client: String,
            http: HttpClient,
            timeout: Duration,
            onLog: (ConsoleLogLine) -> Unit,
        ): ConsoleSocket {
            val answers = LinkedBlockingQueue<JsonNode>()
            val listener = Listener(answers, onLog)
            val uri = URI.create(baseUrl.trimEnd('/').replaceFirst(Regex("^http"), "ws") + "/api/console/sock")
            val socket = try {
                http.newWebSocketBuilder().connectTimeout(timeout).buildAsync(uri, listener).get(timeout.toMillis(), TimeUnit.MILLISECONDS)
            } catch (failure: Exception) {
                throw ServerUnreachableException(failure)
            }
            val hello = mapOf("type" to "hello", "protocols" to listOf(1), "client" to client, "token" to token)
            socket.sendText(MAPPER.writeValueAsString(hello), true)
            val first = answers.poll(timeout.toMillis(), TimeUnit.MILLISECONDS)
            if (first == null || first === CLOSED || first.text("type") != "welcome") {
                socket.abort()
                throw when {
                    first == null -> ConsoleSocketLostException("The server did not answer the hello in time")
                    first === CLOSED -> ConsoleSocketLostException("The server closed the connection before the welcome")
                    else -> refusal(first)
                }
            }
            val welcome = ConsoleWelcome(
                protocol = first.path("protocol").asInt(),
                boot = first.text("boot"),
                seq = first.get("seq")?.takeIf { it.isIntegralNumber }?.asLong(),
                replay = first.text("replay"),
            )
            return ConsoleSocket(socket, answers, timeout, welcome)
        }

        private fun refusal(frame: JsonNode): ConsoleSocketRefusedException =
            if (frame.text("type") == "error") {
                ConsoleSocketRefusedException(frame.text("code"), frame.text("message") ?: "The server refused the request")
            } else {
                ConsoleSocketRefusedException(null, frame.text("message") ?: "The server refused the request")
            }

        private fun JsonNode.text(field: String): String? =
            get(field)?.takeIf { it.isTextual }?.asText()

    }

    /** Joins the parts of each text frame, hands log frames to [onLog] and queues every other frame as an answer. */
    private class Listener(private val answers: LinkedBlockingQueue<JsonNode>, private val onLog: (ConsoleLogLine) -> Unit) : WebSocket.Listener {

        private val buffer = StringBuilder()

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            buffer.append(data)
            if (last) {
                val frame = runCatching { MAPPER.readTree(buffer.toString()) }.getOrNull()
                buffer.setLength(0)
                if (frame != null && frame.isObject) {
                    if (frame.path("type").asText() == "log") {
                        onLog(
                            ConsoleLogLine(
                                seq = frame.path("seq").asLong(),
                                node = frame.path("node").asText(),
                                level = frame.path("level").asText(),
                                text = frame.path("text").asText(),
                                dropped = frame.path("dropped").asInt(),
                            )
                        )
                    } else {
                        answers.add(frame)
                    }
                }
            }
            webSocket.request(1)
            return null
        }

        override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
            answers.add(CLOSED)
            return CompletableFuture.completedFuture(null)
        }

        override fun onError(webSocket: WebSocket, error: Throwable) {
            answers.add(CLOSED)
        }

    }

}
