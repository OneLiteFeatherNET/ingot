/*
 * Copyright (c) 2023 dzikoysk
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

package com.reposilite.image

import com.github.dockerjava.api.model.Capability
import com.github.dockerjava.api.model.HostConfig
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.Wait

/** The token the backend of these tests is started with, and the credentials the tests sign in with. */
internal const val DASHBOARD_TEST_TOKEN_NAME = "dashboard-tester"
internal const val DASHBOARD_TEST_TOKEN_SECRET = "dashboard-tester-secret"

/** The dashboard's own addresses, as the route table lists them. A reload of each must answer the document. */
internal val DASHBOARD_SEGMENTS = listOf(
    "browse", "search", "account", "dashboard", "analytics", "access",
    "tokens", "console", "maintenance", "audit", "settings",
)

/**
 * The server the dashboard proxies to. It is reached by the alias `server` on [network], the way the compose file names it,
 * and it carries a token so that the tests can sign in to the API and the console.
 */
internal fun startBackend(network: Network): GenericContainer<*> =
    serverContainer(
        image = ImageUnderTest.server,
        environment = mapOf("REPOSILITE_OPTS" to "--token $DASHBOARD_TEST_TOKEN_NAME:$DASHBOARD_TEST_TOKEN_SECRET"),
        network = network,
    )
        .withNetworkAliases("server")
        .startOrExplain()

/**
 * The dashboard image, run the way the compose file runs it: read only, with every capability dropped and
 * `no-new-privileges`. [environment] replaces the settings the image defaults, and [tmpfs] mounts what a compose file
 * from before this image still declares.
 */
internal fun dashboardContainer(
    network: Network?,
    environment: Map<String, String> = mapOf("INGOT_BACKEND" to "http://server:8080"),
    tmpfs: Map<String, String> = emptyMap(),
): GenericContainer<*> =
    GenericContainer(ImageUnderTest.dashboard)
        .withExposedPorts(DEFAULT_SERVER_PORT)
        .withEnv(environment)
        .apply { network?.let { withNetwork(it) } }
        .withCreateContainerCmdModifier { cmd ->
            cmd.hostConfig
                ?.withReadonlyRootfs(true)
                ?.withCapDrop(Capability.ALL)
                ?.withSecurityOpts(listOf("no-new-privileges:true"))
        }
        .apply { if (tmpfs.isNotEmpty()) withTmpFs(tmpfs) }
        .waitingFor(Wait.forHttp("/healthz").forPort(DEFAULT_SERVER_PORT).forStatusCode(200))
        .withStartupTimeout(Duration.ofMinutes(2))

/** The mounts a compose file written for the nginx image declares: owned by its user 101, which this image does not use. */
internal val LEGACY_NGINX_TMPFS: Map<String, String> = mapOf(
    "/tmp" to "uid=101,gid=101",
    "/var/cache/nginx" to "uid=101,gid=101",
    "/var/run" to "uid=101,gid=101",
    "/etc/nginx/conf.d" to "uid=101,gid=101",
)

/** Plain HTTP for these tests, with the response headers that the default helper leaves out. */
internal object DashboardHttp {
    // HTTP/1.1 on purpose: the default client offers an h2c upgrade on plain http, which the dashboard
    // server takes for a WebSocket upgrade. Browsers and curl do not send it.
    private val client: HttpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    internal data class Reply(val status: Int, val headers: HttpHeaders, val body: String) {
        fun header(name: String): String? = headers.firstValue(name).orElse(null)
    }

    fun send(
        url: String,
        method: String = "GET",
        body: ByteArray? = null,
        accept: String = "text/html",
        authorization: String? = null,
    ): Reply {
        val builder = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(60))
            .header("Accept", accept)
        authorization?.let { builder.header("Authorization", it) }
        val publisher = if (body == null) HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofByteArray(body)
        val request = builder.method(method, publisher).build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        return Reply(response.statusCode(), response.headers(), response.body())
    }

    fun basic(name: String, secret: String): String =
        "Basic " + Base64.getEncoder().encodeToString("$name:$secret".toByteArray())
}

/** The dashboard's own document, with the headers and the base that a browser would see. */
internal fun GenericContainer<*>.dashboardUrl(path: String): String = baseUrl() + path

/**
 * The dashboard image, with its server behind it. One container of each per class: the tests only read from them, and
 * the console tests sign in with a token of their own.
 */
class DashboardImageTest {

    companion object {
        private val PAIR = Regex("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"")

        private lateinit var network: Network
        private lateinit var backend: GenericContainer<*>
        private lateinit var dashboard: GenericContainer<*>

        @JvmStatic
        @BeforeAll
        fun start() {
            network = Network.newNetwork()
            backend = startBackend(network)
            dashboard = dashboardContainer(network).startOrExplain()
        }

        @JvmStatic
        @AfterAll
        fun stop() {
            dashboard.stop()
            backend.stop()
            network.close()
        }
    }

    @Test
    @DisplayName("it answers every dashboard segment and a nested reload with the document")
    fun `answers every segment and a nested reload with the document`() {
        for (segment in DASHBOARD_SEGMENTS) {
            val reply = DashboardHttp.send(dashboard.dashboardUrl("/$segment"))
            assertThat(reply.status).describedAs("/$segment must be a dashboard route").isEqualTo(200)
            assertThat(reply.header("Content-Type")).describedAs("content type of /$segment").startsWith("text/html")
            assertThat(reply.body).describedAs("/$segment must return the document").contains("<base href=")
        }

        val nested = DashboardHttp.send(dashboard.dashboardUrl("/settings/maven/repositories/releases"))
        assertThat(nested.status).describedAs("a reload of a nested address").isEqualTo(200)
        assertThat(nested.body).describedAs("a reload of a nested address returns the document").contains("<base href=")
    }

    @Test
    @DisplayName("it answers the health endpoint with the server running")
    fun `answers the health endpoint`() {
        val reply = DashboardHttp.send(dashboard.dashboardUrl("/healthz"), accept = "*/*")

        assertThat(reply.status).describedAs("health status").isEqualTo(200)
        assertThat(reply.body.trim()).describedAs("health body").isEqualTo("ok")
    }

    @Test
    @DisplayName("it proxies the api and a repository path to the server unchanged")
    fun `proxies the api and repository paths unchanged`() {
        for (path in listOf("/api/status/health", "/releases/com/example/app/1.0/app-1.0.jar")) {
            val direct = DashboardHttp.send(backend.baseUrl() + path, accept = "*/*")
            val proxied = DashboardHttp.send(dashboard.dashboardUrl(path), accept = "*/*")

            assertThat(proxied.status).describedAs("status of $path through the dashboard").isEqualTo(direct.status)
            assertThat(proxied.body).describedAs("body of $path through the dashboard").isEqualTo(direct.body)
        }
    }

    @Test
    @DisplayName("it sends the security headers of the previous configuration on every response")
    fun `sends the security headers of the previous configuration`() {
        val expected = securityHeadersFixture()

        val document = DashboardHttp.send(dashboard.dashboardUrl("/"))
        val proxied = DashboardHttp.send(dashboard.dashboardUrl("/api/status/health"), accept = "*/*")

        for ((name, value) in expected) {
            assertThat(document.header(name)).describedAs("$name on the document").isEqualTo(value)
            assertThat(proxied.header(name)).describedAs("$name on a proxied response").isEqualTo(value)
        }
    }

    @Test
    @DisplayName("it upgrades the console socket and relays the server's messages")
    fun `relays the console socket`() {
        val received = LinkedBlockingQueue<String>()
        val listener = object : WebSocket.Listener {
            override fun onOpen(webSocket: WebSocket) {
                webSocket.request(1)
            }

            override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
                received.add(data.toString())
                webSocket.request(1)
                return null
            }
        }

        val socket = HttpClient.newHttpClient().newWebSocketBuilder()
            .buildAsync(URI.create("ws://${dashboard.host}:${dashboard.getMappedPort(DEFAULT_SERVER_PORT)}/api/console/sock"), listener)
            .get(30, TimeUnit.SECONDS)
        try {
            socket.sendText("Authorization:$DASHBOARD_TEST_TOKEN_NAME:$DASHBOARD_TEST_TOKEN_SECRET", true).get(10, TimeUnit.SECONDS)

            val message = received.poll(60, TimeUnit.SECONDS)
            assertThat(message).describedAs("a message from the server through the console socket").isNotNull()
        } finally {
            socket.abort()
        }
    }

    @Test
    @DisplayName("it streams the first event of the console log before the stream ends")
    fun `streams the console log event by event`() {
        val request = HttpRequest.newBuilder(URI.create(dashboard.dashboardUrl("/api/console/log")))
            .header("Accept", "text/event-stream")
            .header("Authorization", DashboardHttp.basic(DASHBOARD_TEST_TOKEN_NAME, DASHBOARD_TEST_TOKEN_SECRET))
            .timeout(Duration.ofSeconds(90))
            .build()

        val response = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()
            .send(request, HttpResponse.BodyHandlers.ofInputStream())
        response.body().use { stream ->
            assertThat(response.statusCode()).describedAs("status of the event stream").isEqualTo(200)
            assertThat(response.headers().firstValue("Content-Type").orElse("")).describedAs("content type").startsWith("text/event-stream")

            // The stream never ends while the server is up, so a buffered proxy would hold the first line back for good.
            val firstLine = CompletableFuture.supplyAsync { BufferedReader(InputStreamReader(stream)).readLine() }
                .get(60, TimeUnit.SECONDS)
            assertThat(firstLine).describedAs("the first line of the event stream, before the stream ends").isNotNull()
        }
    }

    @Test
    @DisplayName("it runs as uid 65532 with a read only root filesystem and no capabilities")
    fun `runs unprivileged, read only and with no capabilities`() {
        val docker = DockerClientFactory.instance().client()
        val inspected = docker.inspectContainerCmd(dashboard.containerId).exec()

        assertThat(inspected.config.user).describedAs("the image user").isEqualTo("65532")

        val hostConfig: HostConfig = inspected.hostConfig
        assertThat(hostConfig.readonlyRootfs).describedAs("read only root filesystem").isTrue()
        assertThat(hostConfig.capDrop.map { it.toString() }).describedAs("dropped capabilities").contains("ALL")
        assertThat(hostConfig.securityOpts).describedAs("security options").contains("no-new-privileges:true")

        val top = docker.topContainerCmd(dashboard.containerId).exec()
        val uidColumn = top.titles.indexOf("UID")
        assertThat(uidColumn).describedAs("the process list has a UID column").isGreaterThanOrEqualTo(0)
        val uids = top.processes.map { it[uidColumn] }
        assertThat(uids).describedAs("the UIDs of the running processes").contains("65532").doesNotContain("0")
    }

    private fun securityHeadersFixture(): Map<String, String> {
        // Gradle runs container tests in the Gradle project directory, one level below the repository root.
        val fixture = Path.of("..", "reposilite-frontend", "server", "test", "fixtures", "security-headers.json").toAbsolutePath().normalize()
        assertThat(Files.exists(fixture)).describedAs("security header fixture at $fixture").isTrue()
        return PAIR.findAll(Files.readString(fixture)).associate { it.groupValues[1] to it.groupValues[2] }
    }
}
