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

import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteSpecification
import com.reposilite.journalist.Channel
import io.javalin.http.HttpStatus.OK
import kong.unirest.core.Unirest
import net.onelitefeather.ingot.identity.login.LoginFacade
import net.onelitefeather.ingot.identity.session.SessionCookies
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.WebSocket
import java.time.Duration
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.concurrent.thread

/**
 * The remote console accepts a browser session of an instance admin, on the log stream the page reads and on the
 * WebSocket, and keeps accepting the legacy token credentials. A session of a user without the instance admin role is
 * refused. Every wait below is bounded by a poll timeout that only fires when a reply never arrives.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class ConsoleSessionIntegrationTest : ReposiliteSpecification() {

    private val password = "correct horse battery"

    private val loginFacade: LoginFacade
        get() = useFacade()

    private val http = Unirest.spawnInstance().also { it.config().enableCookieManagement(false) }

    private val streams = mutableListOf<EventStream>()
    private val sockets = mutableListOf<ConsoleSocket>()

    /**
     * The test runner shows only warnings, and the stream replays only what is visible. This instance shows info, so the
     * replay of the startup log proves that an accepted connection receives console output.
     */
    @BeforeEach
    fun showConnectionLog() {
        reposilite.journalist.setVisibleThreshold(Channel.INFO)
    }

    @AfterEach
    fun closeConnections() {
        streams.forEach { it.close() }
        sockets.forEach { it.close() }
    }

    @Test
    fun `streams the console log to a session of an instance admin`() {
        // given: a user with the instance admin role who has signed in
        val session = signedInSession("console-admin", instanceAdmin = true)

        // when: the log stream is opened with only the session cookie
        val stream = openLogStream(headers = mapOf("Cookie" to "${SessionCookies.SESSION}=$session"))

        // then: the connection is accepted and the console output is streamed, not a refusal
        assertThat(stream.awaitData { it.contains(INFO_LINE) })
            .describedAs("console output line")
            .contains(INFO_LINE)
    }

    @Test
    fun `refuses the log stream to a session of a user without the instance admin role`() {
        // given: a user without any admin role who has signed in
        val session = signedInSession("console-user", instanceAdmin = false)

        // when: the log stream is opened with only the session cookie
        val stream = openLogStream(headers = mapOf("Cookie" to "${SessionCookies.SESSION}=$session"))

        // then: the first event is the refusal, not the log
        assertThat(stream.awaitData { it.contains("\"status\":401") })
            .describedAs("refusal event")
            .contains("Unauthorized CLI access request")
    }

    @Test
    fun `keeps streaming the log to a legacy token with basic credentials`() {
        // given: the default manager token, the instance admin that has always opened the console
        val (name, secret) = useDefaultManagementToken()
        val basic = Base64.getEncoder().encodeToString("$name:$secret".toByteArray())

        // when: the log stream is opened with basic credentials
        val stream = openLogStream(headers = mapOf("Authorization" to "Basic $basic"))

        // then: the connection is accepted and the console output is streamed
        assertThat(stream.awaitData { it.contains(INFO_LINE) })
            .describedAs("console output line")
            .contains(INFO_LINE)
    }

    @Test
    fun `answers the log stream without any credentials with a 401`() {
        // when: the log stream is opened without credentials and without a session cookie
        val stream = openLogStream(headers = emptyMap())

        // then: the refusal says that the credentials are missing
        assertThat(stream.awaitData { it.contains("\"status\":401") })
            .describedAs("refusal event")
            .contains("Missing authorization credentials")
    }

    @Test
    fun `accepts a session of an instance admin sent as the first WebSocket message`() {
        // given: a user with the instance admin role who has signed in
        val session = signedInSession("console-socket-admin", instanceAdmin = true)

        // when: the session is sent as the first message and a keep-alive follows it
        val socket = openSocket()
        socket.send("Authorization:Session $session")
        socket.send("keep-alive")

        // then: the keep-alive is answered, which only an authenticated connection does, and no refusal arrives first.
        // Log lines of other tests and background jobs may arrive before the answer, so only the refusal is checked.
        val received = socket.receiveUntil { it == "keep-alive" }
        assertThat(received)
            .describedAs("messages received before the keep-alive answer")
            .noneMatch { it.contains("\"status\":401") }
        assertThat(received.last()).isEqualTo("keep-alive")
    }

    @Test
    fun `refuses a WebSocket session of a user without the instance admin role`() {
        // given: a user without any admin role who has signed in
        val session = signedInSession("console-socket-user", instanceAdmin = false)

        // when: the session is sent as the first message
        val socket = openSocket()
        socket.send("Authorization:Session $session")

        // then: the connection is refused with a 401
        assertThat(socket.receiveUntil { it.contains("\"status\":401") })
            .describedAs("refusal message")
            .anyMatch { it.contains("Unauthorized CLI access request") }
    }

    @Test
    fun `keeps accepting a legacy token as the first WebSocket message`() {
        // given: the default manager token
        val (name, secret) = useDefaultManagementToken()

        // when: the credentials are sent as the first message and a keep-alive follows them
        val socket = openSocket()
        socket.send("Authorization:$name:$secret")
        socket.send("keep-alive")

        // then: the keep-alive is answered, so the connection is authenticated
        assertThat(socket.receiveUntil { it == "keep-alive" }).contains("keep-alive")
    }

    /** Creates a user, optionally grants it the instance admin role, and returns the session token of its login. */
    private fun signedInSession(username: String, instanceAdmin: Boolean): String {
        loginFacade.createUser(username, username, password)
        if (instanceAdmin) {
            val (name, secret) = useDefaultManagementToken()
            val granted = Unirest.put("$base/api/identity/principals/$username/instance-admin")
                .basicAuth(name, secret)
                .asString()
            assertThat(granted.status).describedAs("instance admin grant").isEqualTo(OK.code)
        }
        val login = http.post("$base/api/auth/login")
            .header("Content-Type", "application/json")
            .body("""{"provider":"local","username":"$username","password":"$password"}""")
            .asString()
        assertThat(login.status).describedAs("login").isEqualTo(OK.code)
        return login.headers.get("Set-Cookie")
            .single { it.startsWith("${SessionCookies.SESSION}=") }
            .substringAfter("=")
            .substringBefore(";")
    }

    private fun openLogStream(headers: Map<String, String>): EventStream {
        val request = HttpRequest.newBuilder(URI.create("$base/api/console/log"))
            .version(HttpClient.Version.HTTP_1_1)
            .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .header("Accept", "text/event-stream")
            .apply { headers.forEach { (key, value) -> header(key, value) } }
            .GET()
            .build()
        // Bounded on this side as well: a stream that never answers fails the test instead of holding the build.
        val response = HttpClient.newHttpClient().sendAsync(request, BodyHandlers.ofInputStream()).get(TIMEOUT_SECONDS, SECONDS)
        return EventStream(response.body()).also { streams += it }
    }

    private fun openSocket(): ConsoleSocket =
        ConsoleSocket(URI.create("${base.replace("http", "ws")}/api/console/sock")).also { sockets += it }

    /** The `data` lines of a server-sent event stream, read on a thread of their own so that the test can time out. */
    private class EventStream(private val body: InputStream) {

        private val lines = LinkedBlockingQueue<String>()

        init {
            // Comment lines (the pings of the stream) are dropped here, so that they do not keep a wait alive.
            thread(isDaemon = true, name = "console-log-test") {
                runCatching { body.bufferedReader().forEachLine { if (it.startsWith("data:")) lines.add(it) } }
            }
        }

        /**
         * The first data event that matches [predicate], within [MAX_DATA_EVENTS] events. A stream that goes silent fails
         * after the poll timeout, and one that keeps sending events fails after the bound.
         */
        fun awaitData(predicate: (String) -> Boolean): String {
            val seen = mutableListOf<String>()
            repeat(MAX_DATA_EVENTS) {
                val line = lines.poll(TIMEOUT_SECONDS, SECONDS) ?: throw AssertionError("stream silent, received: $seen")
                val data = line.removePrefix("data:").trim()
                if (predicate(data)) return data
                seen += data
            }
            throw AssertionError("no matching event in $MAX_DATA_EVENTS events, received: $seen")
        }

        fun close() {
            body.close()
        }
    }

    /** A console WebSocket. Messages are queued as they arrive, and a receive waits for them with a timeout. */
    private class ConsoleSocket(uri: URI) {

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
    }

    private companion object {
        const val TIMEOUT_SECONDS = 10L
        const val MAX_DATA_EVENTS = 500
        const val INFO_LINE = " INFO | "
    }

}
