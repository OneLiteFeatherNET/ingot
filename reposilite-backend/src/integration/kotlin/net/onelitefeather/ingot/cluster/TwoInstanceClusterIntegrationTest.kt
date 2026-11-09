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

package net.onelitefeather.ingot.cluster

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.reposilite.IsolatedResources
import com.reposilite.Reposilite
import com.reposilite.ReposiliteFactory
import com.reposilite.ReposiliteParameters
import com.reposilite.SharedContainers
import com.reposilite.configuration.local.LocalConfiguration
import com.reposilite.configuration.local.infrastructure.LOCAL_CONFIGURATION_FILE
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.databaseToken
import com.reposilite.journalist.Channel
import com.reposilite.journalist.backend.PrintStreamLogger
import com.reposilite.maven.application.MavenSettings
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.natsUrl
import com.reposilite.schemaToken
import com.reposilite.token.AccessTokenSecurityProvider
import kong.unirest.core.Unirest
import kong.unirest.core.UnirestInstance
import net.dzikoysk.cdn.KCdnFactory
import net.dzikoysk.cdn.source.Source
import net.onelitefeather.ingot.identity.login.LoginFacade
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.ResultAssertions.assertOk
import panda.std.reactive.ReferenceUtils
import java.io.File
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.concurrent.thread

/**
 * Two complete instances in one JVM on one database and one bus (cluster bus, task 11.1). What happens on instance A has to
 * show on instance B through the bus alone: the shared configuration polls only every ten seconds, and the credential
 * cache of B lives for a minute, so neither can explain a change that is seen within the bounded waits below.
 *
 * Console fan-out is on here, as it is by default on NATS: the unit tests of the cluster turn it off, so these tests are
 * its real coverage. Every test makes a database or schema of its own, and every wait is bounded by [BOUND].
 */
internal class TwoInstanceClusterIntegrationTest {

    @TempDir
    lateinit var workingDirectories: File

    private val instances = mutableListOf<Node>()
    private val streams = mutableListOf<EventStream>()
    private val cleanups = mutableListOf<() -> Unit>()
    private val http: UnirestInstance = Unirest.spawnInstance().also { it.config().enableCookieManagement(false) }
    private val json = ObjectMapper()

    @AfterEach
    fun shutdown() {
        streams.forEach { it.close() }
        instances.forEach { runCatching { it.reposilite.shutdown() } }
        cleanups.forEach { runCatching { it() } }
        http.close()
    }

    @Test
    fun `a token disabled on one instance is refused on the other`() {
        val (first, second) = startPair { bus -> bus }
        // given: a machine principal with a token that the second instance has already accepted (and so cached)
        val secret = createMachineToken(first, "ci-bot")
        assertThat(status(second, "/api/auth/me", "Bearer $secret")).describedAs("status before the token is disabled").isEqualTo(200)

        // when: the first instance disables the principal
        val patched = http.patch("${first.base}/api/identity/principals/ci-bot")
            .basicAuth(MANAGER.first, MANAGER.second)
            .header("Content-Type", "application/json")
            .body("""{"disabled":true}""")
            .asString()
        assertThat(patched.status).describedAs("status of disabling the principal").isEqualTo(200)

        // then: the second instance refuses the token within the bound, long before its cache would expire
        awaitTrue("the second instance refuses the disabled token") {
            status(second, "/api/auth/me", "Bearer $secret") != 200
        }
    }

    @Test
    fun `a token disabled on one instance is refused on the other over the database transport`() {
        val (first, second) = startPair(postgres = true) { _ -> "database:" }
        val secret = createMachineToken(first, "db-bot")
        assertThat(status(second, "/api/auth/me", "Bearer $secret")).describedAs("status before the token is disabled").isEqualTo(200)

        val patched = http.patch("${first.base}/api/identity/principals/db-bot")
            .basicAuth(MANAGER.first, MANAGER.second)
            .header("Content-Type", "application/json")
            .body("""{"disabled":true}""")
            .asString()
        assertThat(patched.status).describedAs("status of disabling the principal").isEqualTo(200)

        awaitTrue("the second instance refuses the disabled token") {
            status(second, "/api/auth/me", "Bearer $secret") != 200
        }
    }

    @Test
    fun `a logout on one instance ends the session on the other`() {
        val (first, second) = startPair { bus -> bus }
        // given: a user who signed in on the first instance and whose session the second instance accepts
        first.reposilite.extensions.facade<LoginFacade>().createUser("session-user", "session-user", PASSWORD)
        val login = http.post("${first.base}/api/auth/login")
            .header("Content-Type", "application/json")
            .body("""{"provider":"local","username":"session-user","password":"$PASSWORD"}""")
            .asString()
        assertThat(login.status).describedAs("status of the login").isEqualTo(200)
        val cookies = login.headers.get("Set-Cookie")
        val session = cookieValue(cookies, "ingot_session")
        val refresh = cookieValue(cookies, "ingot_refresh")
        val csrf = cookieValue(cookies, "ingot_csrf")
        assertThat(status(second, "/api/auth/me", cookie = "ingot_session=$session")).describedAs("status before the logout").isEqualTo(200)

        // when: the user logs out on the first instance
        val logout = http.post("${first.base}/api/auth/logout")
            .header("Cookie", "ingot_refresh=$refresh; ingot_csrf=$csrf")
            .header("X-CSRF-Token", csrf)
            .asString()
        assertThat(logout.status).describedAs("status of the logout").isEqualTo(200)

        // then: the second instance stops accepting the session within the bound
        awaitTrue("the second instance refuses the ended session") {
            status(second, "/api/auth/me", cookie = "ingot_session=$session") != 200
        }
    }

    @Test
    fun `a shared configuration saved on one instance applies on the other before its next poll`() {
        val (first, second) = startPair { bus -> bus }
        val appliedBefore = busApplied(second)
        val repository = "bus-probe-${UUID.randomUUID().toString().take(8)}"

        // when: the first instance saves a repository into the maven settings
        val configuration = first.reposilite.extensions.facade<SharedConfigurationFacade>()
        val current = configuration.getDomainSettings<MavenSettings>().get()
        val stored = configuration.updateSharedSettings("maven", current.copy(repositories = current.repositories + RepositorySettings(id = repository)))
        assertThat(stored?.isOk).describedAs("the maven settings were stored").isTrue()

        // then: the second instance has the repository within the bound, which is shorter than its ten second poll,
        // and it counts the event it applied from the bus
        awaitTrue("the second instance has the saved repository") {
            second.reposilite.extensions.facade<SharedConfigurationFacade>().getDomainSettings<MavenSettings>().get()
                .repositories.any { it.id == repository }
        }
        assertThat(busApplied(second)).describedAs("events the second instance applied from the bus").isGreaterThan(appliedBefore)
    }

    @Test
    fun `a console line logged on one instance appears once in a console session on the other`() {
        val (first, second) = startPair { bus -> bus }
        // given: an open console session on the first instance, whose visible threshold shows warnings
        val stream = openLogStream(first)
        // the stream is subscribed once it shows a line this instance logs itself, so nothing below can be sent too early
        val ready = "cluster-ready-${UUID.randomUUID()}"
        awaitTrue("the console session shows a line of its own instance") {
            first.reposilite.journalist.visibleLogger.warn(ready)
            stream.contains(ready)
        }
        val marker = "cluster-marker-${UUID.randomUUID()}"
        val end = "cluster-end-${UUID.randomUUID()}"

        // when: the second instance logs the marker and then an end line, both at its visible threshold
        second.reposilite.journalist.visibleLogger.warn(marker)
        second.reposilite.journalist.visibleLogger.warn(end)

        // then: the session shows the marker exactly once, labelled with the instance that logged it, before the end line
        val received = stream.collectUntil { it.contains(end) }
        val markerLines = received.filter { it.contains(marker) }
        assertThat(markerLines).describedAs("lines with the marker, received: $received").hasSize(1)
        assertThat(markerLines.single()).describedAs("origin label of the line").contains(second.instanceId)
    }

    // ---- fixture

    private class Node(val instanceId: String, val reposilite: Reposilite) {
        val base: String
            get() = "http://localhost:${reposilite.parameters.port}"
    }

    /**
     * Starts two instances on one database of a test of its own, with the bus the [busUrl] function chooses from the NATS
     * URL, and waits until both report the bus as connected. Instances start one after the other, because the cluster
     * settings are read from system properties, which are shared by the whole JVM.
     */
    private fun startPair(postgres: Boolean = false, busUrl: (natsUrl: String) -> String): Pair<Node, Node> {
        val database = if (postgres) {
            val schema = IsolatedResources.createPostgresSchema(SharedContainers.postgres)
            cleanups += { IsolatedResources.dropPostgresSchema(SharedContainers.postgres, schema) }
            SharedContainers.postgres.schemaToken(schema)
        } else {
            val name = IsolatedResources.createMariaDbDatabase(SharedContainers.mariaDb)
            cleanups += { IsolatedResources.dropMariaDbDatabase(SharedContainers.mariaDb, name) }
            SharedContainers.mariaDb.databaseToken(name)
        }
        val url = busUrl(SharedContainers.nats.natsUrl())
        val first = start("ingot-a", database, url)
        val second = start("ingot-b", database, url)
        listOf(first, second).forEach { node ->
            awaitTrue("${node.instanceId} reports the bus as connected") { busState(node) == "connected" }
        }
        return first to second
    }

    private fun start(instanceId: String, database: String, url: String): Node {
        val directory = File(workingDirectories, instanceId).also { it.mkdirs() }
        val parameters = ReposiliteParameters()
        parameters.tokenEntries = arrayOf("${MANAGER.first}:${MANAGER.second}")
        parameters.workingDirectoryName = directory.absolutePath
        parameters.testEnv = true
        parameters.port = 0
        parameters.database = database
        parameters.tokenHashStrength = AccessTokenSecurityProvider.MIN_BCRYPT_STRENGTH
        parameters.run()

        val localConfiguration = LocalConfiguration().also {
            ReferenceUtils.setValue(it.webThreadPool, 6)
            ReferenceUtils.setValue(it.ioThreadPool, 4)
        }
        KCdnFactory.createStandard().render(localConfiguration, Source.of(directory.resolve(LOCAL_CONFIGURATION_FILE)))

        val logger = PrintStreamLogger(System.out, System.err, Channel.ALL, false)
        val reposilite = synchronized(PROPERTY_LOCK) {
            System.setProperty("ingot.cluster.url", url)
            System.setProperty("ingot.cluster.instance", instanceId)
            try {
                ReposiliteFactory.createReposilite(parameters, logger)
            } finally {
                System.clearProperty("ingot.cluster.url")
                System.clearProperty("ingot.cluster.instance")
            }
        }
        reposilite.journalist.setVisibleThreshold(Channel.WARN)
        val node = Node(instanceId, reposilite).also { instances += it }
        assertOk(reposilite.launch())
        return node
    }

    private fun createMachineToken(node: Node, principal: String): String {
        val created = http.post("${node.base}/api/identity/principals")
            .basicAuth(MANAGER.first, MANAGER.second)
            .header("Content-Type", "application/json")
            .body("""{"kind":"MACHINE","name":"$principal"}""")
            .asString()
        assertThat(created.status).describedAs("status of creating $principal").isEqualTo(200)
        val token = http.post("${node.base}/api/identity/principals/$principal/tokens")
            .basicAuth(MANAGER.first, MANAGER.second)
            .header("Content-Type", "application/json")
            .body("""{"name":"$principal-token"}""")
            .asString()
        assertThat(token.status).describedAs("status of the token of $principal").isEqualTo(200)
        return json.readTree(token.body).get("secret").asText()
    }

    private fun status(node: Node, path: String, authorization: String? = null, cookie: String? = null): Int =
        http.get("${node.base}$path")
            .apply { authorization?.let { header("Authorization", it) } }
            .apply { cookie?.let { header("Cookie", it) } }
            .asString()
            .status

    private fun instanceStatus(node: Node): JsonNode =
        json.readTree(http.get("${node.base}/api/status/instance").basicAuth(MANAGER.first, MANAGER.second).asString().body)

    private fun busState(node: Node): String? =
        runCatching { instanceStatus(node).path("cluster").path("bus").path("state").asText() }.getOrNull()

    private fun busApplied(node: Node): Long =
        instanceStatus(node).path("cluster").path("bus").path("applied").asLong()

    private fun cookieValue(headers: List<String>, name: String): String =
        headers.single { it.startsWith("$name=") }.substringAfter("=").substringBefore(";")

    /** Re-checks [condition] until it holds or [BOUND] has passed. The pause between checks is a bounded latch wait. */
    private fun awaitTrue(description: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + BOUND.toNanos()
        val pause = CountDownLatch(1)
        while (!condition()) {
            if (System.nanoTime() > deadline) {
                throw AssertionError("not within ${BOUND.seconds} seconds: $description")
            }
            pause.await(POLL_MILLIS, TimeUnit.MILLISECONDS)
        }
    }

    private fun openLogStream(node: Node): EventStream {
        val basic = Base64.getEncoder().encodeToString("${MANAGER.first}:${MANAGER.second}".toByteArray())
        val request = HttpRequest.newBuilder(URI.create("${node.base}/api/console/log"))
            .version(HttpClient.Version.HTTP_1_1)
            .timeout(BOUND)
            .header("Accept", "text/event-stream")
            .header("Authorization", "Basic $basic")
            .GET()
            .build()
        val response = HttpClient.newHttpClient().sendAsync(request, BodyHandlers.ofInputStream()).get(BOUND.seconds, SECONDS)
        return EventStream(response.body()).also { streams += it }
    }

    /** The `data` lines of a server-sent event stream, read on a thread of their own so that the test can time out. */
    private class EventStream(private val body: InputStream) {

        private val lines = LinkedBlockingQueue<String>()

        init {
            thread(isDaemon = true, name = "cluster-console-test") {
                runCatching { body.bufferedReader().forEachLine { if (it.startsWith("data:")) lines.add(it.removePrefix("data:").trim()) } }
            }
        }

        /** Every event up to and including the first that matches [predicate]. A silent stream fails after [BOUND]. */
        fun collectUntil(predicate: (String) -> Boolean): List<String> {
            val seen = mutableListOf<String>()
            val deadline = System.nanoTime() + BOUND.toNanos()
            while (true) {
                val remaining = deadline - System.nanoTime()
                val line = (if (remaining > 0) lines.poll(remaining, TimeUnit.NANOSECONDS) else null)
                    ?: throw AssertionError("no matching event within ${BOUND.seconds} seconds, received: $seen")
                seen += line
                if (predicate(line)) return seen
            }
        }

        private val history = java.util.concurrent.CopyOnWriteArrayList<String>()

        /** Drains what has arrived into the history and tells whether a line with [text] is in it. */
        fun contains(text: String): Boolean {
            generateSequence { lines.poll() }.forEach { history += it }
            return history.any { it.contains(text) }
        }

        fun close() {
            runCatching { body.close() }
        }
    }

    private companion object {
        val MANAGER = "manager" to "manager-secret"
        const val PASSWORD = "correct horse battery"
        val BOUND: Duration = Duration.ofSeconds(20)
        const val POLL_MILLIS = 100L
        val PROPERTY_LOCK = Any()
    }

}
