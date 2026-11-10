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

package com.reposilite.image

import java.net.Socket
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName

/**
 * The previous release and the current build beside each other on one MariaDB database and one repository directory,
 * which is the state of a rolling upgrade while the replicas are replaced one after the other.
 *
 * The order is the one a rolling upgrade produces: the previous release is already running when the current build joins
 * the database, and the previous release is the one that was started first. Every case gets its own database, its own
 * directory and its own replicas, so no case can see what another one wrote.
 *
 * Requests authenticate with the bootstrap token that both releases accept from `--token`, so no case depends on a
 * persistent token that only one release could have written.
 */
class MixedVersionClusterTest {

    @Test
    @DisplayName("deploys cross between the releases")
    fun `deploys cross between the releases`(@TempDir directory: Path) {
        withSharedState(directory) { state ->
            sharedRepositoryReplica(ImageUnderTest.previous, state).use { previous ->
                previous.startOrExplain()
                sharedRepositoryReplica(ImageUnderTest.server, state).use { current ->
                    current.startOrExplain()
                    val oldUrl = previous.baseUrl()
                    val newUrl = current.baseUrl()

                    // One artifact in each direction, one after the other.
                    deploy(oldUrl, FROM_OLD_TO_NEW, FROM_OLD_TO_NEW)
                    assertServes(newUrl, FROM_OLD_TO_NEW, reason = "the current build reads what the previous release deployed")

                    deploy(newUrl, FROM_NEW_TO_OLD, FROM_NEW_TO_OLD)
                    assertServes(oldUrl, FROM_NEW_TO_OLD, reason = "the previous release reads what the current build deployed")

                    // Two different artifacts at the same moment, one through each replica.
                    deployConcurrently(
                        oldUrl to CONCURRENT_FROM_OLD,
                        newUrl to CONCURRENT_FROM_NEW,
                    )
                    listOf(CONCURRENT_FROM_OLD, CONCURRENT_FROM_NEW).forEach { path ->
                        assertServes(oldUrl, path, reason = "the previous release reads a concurrent deploy")
                        assertServes(newUrl, path, reason = "the current build reads a concurrent deploy")
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("the previous release restarts and serves once the current build has changed the schema")
    fun `the old replica restarts after the schema changed`(@TempDir directory: Path) {
        withSharedState(directory) { state ->
            // The current build keeps running for the whole case, so the restart happens beside a changed schema.
            sharedRepositoryReplica(ImageUnderTest.server, state).use { current ->
                sharedRepositoryReplica(ImageUnderTest.previous, state).use { previous ->
                    previous.startOrExplain()
                    deploy(previous.baseUrl(), BEFORE_RESTART, BEFORE_RESTART)

                    current.startOrExplain()
                    assertServes(current.baseUrl(), BEFORE_RESTART, reason = "the current build reads the old deploy")
                }

                // The old replica is replaced by a new process on the same database and directory.
                sharedRepositoryReplica(ImageUnderTest.previous, state).use { restarted ->
                    restarted.startOrExplain()
                    val url = restarted.baseUrl()

                    val tokens = Http.get("$url/api/tokens", MIXED_BOOTSTRAP)
                    assertThat(tokens.isSuccessful)
                        .describedAs("the restarted previous release answers an authenticated request: status %s, body %s", tokens.status, tokens.body)
                        .isTrue()
                    assertServes(url, BEFORE_RESTART, reason = "the restarted previous release reads the old deploy")
                    deploy(url, AFTER_RESTART, AFTER_RESTART)
                    assertServes(current.baseUrl(), AFTER_RESTART, reason = "the current build reads a deploy of the restarted release")
                }
            }
        }
    }

    @Test
    @DisplayName("the columns the current build adds can be written by the previous release")
    fun `columns the current build adds stay writable by the previous release`(@TempDir directory: Path) {
        withSharedState(directory) { state ->
            val afterPrevious: List<SchemaColumn>
            val afterCurrent: List<SchemaColumn>
            sharedRepositoryReplica(ImageUnderTest.previous, state).use { previous ->
                previous.startOrExplain()
                afterPrevious = state.database.columns()

                sharedRepositoryReplica(ImageUnderTest.server, state).use { current ->
                    current.startOrExplain()
                    afterCurrent = state.database.columns()
                }
            }
            assertNoIncompatibleChange(afterPrevious, afterCurrent, "the current build started on the previous release's schema")

            // The previous release is restarted on the schema the current build left behind.
            sharedRepositoryReplica(ImageUnderTest.previous, state).use { restarted ->
                restarted.startOrExplain()
                assertNoIncompatibleChange(afterCurrent, state.database.columns(), "the previous release restarted")
            }
        }
    }

    /**
     * What the previous release can still work with, checked on two listings of the database. A column the first listing
     * had is not removed, does not change its type, and does not become required. A column the second listing added to a
     * table the first listing had must not be required either, because the previous release inserts into that table
     * without it.
     *
     * A table the first listing did not have is not checked. The previous release does not know it and never writes to
     * it, and the design allows new tables with any columns. Its columns are the current build's own business.
     */
    private fun assertNoIncompatibleChange(before: List<SchemaColumn>, after: List<SchemaColumn>, transition: String) {
        val known = before.associateBy { it.key }
        val knownTables = before.map { it.table }.toSet()
        val current = after.associateBy { it.key }

        assertThat(known.keys - current.keys)
            .describedAs("%s: columns that were removed", transition)
            .isEmpty()
        assertThat(known.values.filter { current.getValue(it.key).type != it.type }.map { it.key })
            .describedAs("%s: columns whose type changed", transition)
            .isEmpty()
        assertThat(known.values.filter { !it.requiresValue && current.getValue(it.key).requiresValue }.map { it.key })
            .describedAs("%s: columns that became NOT NULL without a default", transition)
            .isEmpty()
        assertThat(current.values.filter { it.table in knownTables && it.key !in known && it.requiresValue }.map { it.key })
            .describedAs("%s: columns added to an existing table that are NOT NULL without a default", transition)
            .isEmpty()
    }

    @Test
    @DisplayName("two current replicas ignore a version 2 and an unknown version 1 message and still apply a known event")
    fun `unknown bus messages are ignored and the next known event is applied`(@TempDir directory: Path) {
        withSharedState(directory) { state ->
            withBroker(state) { broker, bus ->
                sharedRepositoryReplica(ImageUnderTest.server, state, bus = bus, instanceId = "replica-a").use { first ->
                    first.startOrExplain()
                    val cluster = state.database.query("SELECT content FROM settings WHERE name = 'cluster-identifier'").single().single()

                    sharedRepositoryReplica(ImageUnderTest.server, state, bus = bus, instanceId = "replica-b").use { second ->
                        second.startOrExplain()
                        awaitBusConnected(first)
                        awaitBusConnected(second)

                        val before = second.status()
                        val ignoredBefore = counter(before, "ignored")

                        // The three messages go out together. A message of an unknown version or type must be ignored,
                        // while a known one must be applied, and each of them is the same snapshot payload.
                        publishUntilApplied(
                            broker = broker,
                            subject = "ingot.$cluster.events",
                            messages = listOf(
                                envelope(version = 2, type = "status.snapshot", cluster = cluster, origin = IGNORED_VERSION),
                                envelope(version = 1, type = "status.from-a-newer-release", cluster = cluster, origin = IGNORED_TYPE),
                                envelope(version = 1, type = "status.snapshot", cluster = cluster, origin = APPLIED),
                            ),
                            replica = second,
                        )

                        // The NATS connection keeps the order of one publisher, so an applied event proves the two before it were handled.
                        val after = second.status()
                        assertThat(after).describedAs("the replica lists the instance of the applied event").contains("\"id\":\"$APPLIED\"")
                        assertThat(after)
                            .describedAs("a version 2 message does not add an instance: %s", after)
                            .doesNotContain(IGNORED_VERSION)
                        assertThat(after)
                            .describedAs("a message of an unknown type does not add an instance: %s", after)
                            .doesNotContain(IGNORED_TYPE)
                        assertThat(counter(after, "ignored"))
                            .describedAs("the two unknown messages are counted as ignored: %s", after)
                            .isGreaterThanOrEqualTo(ignoredBefore + 2)
                        // The first failuresCount of the status is the one of this replica, which is listed first.
                        assertThat(counter(after, "failuresCount"))
                            .describedAs("no failure is recorded for an ignored message: %s", after)
                            .isEqualTo(counter(before, "failuresCount"))
                        assertThat(after).describedAs("the bus stays connected").contains("\"state\":\"connected\"")
                        assertThat(Http.get(second.baseUrl()).isSuccessful).describedAs("the replica keeps answering").isTrue()
                    }
                }
            }
        }
    }

    /**
     * Publishes [messages] to the broker and waits until [replica] lists the instance of [APPLIED]. Messages are sent again
     * every poll, because a replica that has not subscribed yet would miss the first round. The wait is bounded by [BUS_BOUND].
     */
    private fun publishUntilApplied(broker: GenericContainer<*>, subject: String, messages: List<String>, replica: GenericContainer<*>) {
        val deadline = System.nanoTime() + BUS_BOUND.toNanos()
        while (true) {
            publish(broker, subject, messages)
            if (replica.status().contains("\"id\":\"$APPLIED\"")) return
            check(System.nanoTime() < deadline) {
                "the replica did not apply the known event within $BUS_BOUND, its status was:\n${replica.status()}"
            }
            CountDownLatch(1).await(2, TimeUnit.SECONDS)
        }
    }

    /** Waits, within [BUS_BOUND], until [replica] reports its bus connection as connected. */
    private fun awaitBusConnected(replica: GenericContainer<*>) {
        val deadline = System.nanoTime() + BUS_BOUND.toNanos()
        while (!replica.status().contains("\"state\":\"connected\"")) {
            check(System.nanoTime() < deadline) { "the bus of ${replica.baseUrl()} did not connect, its status was:\n${replica.status()}" }
            CountDownLatch(1).await(2, TimeUnit.SECONDS)
        }
    }

    private fun GenericContainer<*>.status(): String = Http.get("${baseUrl()}/api/status/instance", MIXED_BOOTSTRAP).body

    /** The number the bus section of the status reports under [name], such as `ignored`. */
    private fun counter(status: String, name: String): Long =
        Regex("\"$name\":(\\d+)").find(status)?.groupValues?.get(1)?.toLong()
            ?: error("the status has no counter $name: $status")

    /** One envelope with the payload of a snapshot, so only its version or type decides whether it is applied. */
    private fun envelope(version: Int, type: String, cluster: String, origin: String): String =
        """{"v":$version,"type":"$type","cluster":"$cluster","origin":"$origin","at":"2026-11-10T10:00:00Z","payload":$SNAPSHOT_PAYLOAD}"""

    /**
     * Writes [messages] to the broker with the NATS text protocol, and returns once the broker has taken them: the PING
     * is answered only after the publishes before it. The socket has a read timeout, so a silent broker fails the test.
     */
    private fun publish(broker: GenericContainer<*>, subject: String, messages: List<String>) {
        Socket(broker.host, broker.getMappedPort(NATS_PORT)).use { socket ->
            socket.soTimeout = BUS_BOUND.toMillis().toInt()
            val input = socket.getInputStream().bufferedReader(StandardCharsets.UTF_8)
            val output = socket.getOutputStream()
            input.readLine() // INFO, sent by the broker first
            output.write("CONNECT {\"verbose\":false,\"pedantic\":false,\"protocol\":0,\"name\":\"mixed-version-test\"}\r\n".toByteArray())
            messages.forEach { message ->
                val bytes = message.toByteArray(StandardCharsets.UTF_8)
                output.write("PUB $subject ${bytes.size}\r\n".toByteArray())
                output.write(bytes)
                output.write("\r\n".toByteArray())
            }
            output.write("PING\r\n".toByteArray())
            output.flush()
            val reply = generateSequence { input.readLine() }.first { it.startsWith("PONG") || it.startsWith("-ERR") }
            check(reply.startsWith("PONG")) { "the broker refused a publish: $reply" }
        }
    }

    /** A NATS broker on the network of [state]. [block] gets it and the address the replicas use to reach it. */
    private fun withBroker(state: SharedState, block: (GenericContainer<*>, String) -> Unit) {
        GenericContainer(DockerImageName.parse(NATS_IMAGE))
            .withNetwork(state.network)
            .withExposedPorts(NATS_PORT)
            .waitingFor(Wait.forLogMessage(".*Server is ready.*", 1))
            .withStartupTimeout(Duration.ofMinutes(2))
            .use { broker ->
                broker.start()
                val address = checkNotNull(broker.containerInfo.networkSettings.networks.values.single().ipAddress) {
                    "the NATS broker has no address on the test network"
                }
                block(broker, "nats://$address:$NATS_PORT")
            }
    }

    @Test
    @DisplayName("a settings change saved by the current build is served by the previous release")
    fun `a settings change of the current build is served by the previous release`(@TempDir directory: Path) {
        withSharedState(directory) { state ->
            sharedRepositoryReplica(ImageUnderTest.previous, state).use { previous ->
                previous.startOrExplain()
                sharedRepositoryReplica(ImageUnderTest.server, state).use { current ->
                    current.startOrExplain()

                    // Only the forward direction is asserted. The reverse one is observed and reported in the message of the
                    // forward assertion, so a failure shows what the other direction did, and its behaviour is documented elsewhere.
                    saveTitle(previous.baseUrl(), PREVIOUS_TITLE)
                    val reverse = savedTitle(current.baseUrl())

                    saveTitle(current.baseUrl(), CURRENT_TITLE)
                    assertThat(savedTitle(current.baseUrl()))
                        .describedAs("the current build reads back its own save")
                        .isEqualTo(CURRENT_TITLE)
                    // The previous release applies a setting another replica saved on its own poll, which runs every ten
                    // seconds. The forward direction is asserted after that propagation, within SETTINGS_BOUND.
                    assertThat(awaitSavedTitle(previous.baseUrl(), CURRENT_TITLE))
                        .describedAs(
                            "the previous release serves the title the current build saved within %s (observed in the other direction, previous to current: %s)",
                            SETTINGS_BOUND,
                            reverse,
                        )
                        .isEqualTo(CURRENT_TITLE)
                }
            }
        }
    }

    private fun saveTitle(url: String, title: String) {
        val response = Http.putJson("$url/api/settings/domain/frontend", """{"title":"$title"}""", MIXED_BOOTSTRAP)
        assertThat(response.isSuccessful).describedAs("save the frontend title through %s: %s", url, response.body).isTrue()
    }

    private fun savedTitle(url: String): String? {
        val body = Http.get("$url/api/settings/domain/frontend", MIXED_BOOTSTRAP).body
        return Regex("\"title\":\"([^\"]*)\"").find(body)?.groupValues?.get(1)
    }

    /**
     * Polls [url] until it serves [expected], for at most [SETTINGS_BOUND], and returns the last title it served. A latch
     * between the polls, not a sleep.
     */
    private fun awaitSavedTitle(url: String, expected: String): String? {
        val deadline = System.nanoTime() + SETTINGS_BOUND.toNanos()
        var served = savedTitle(url)
        while (served != expected && System.nanoTime() < deadline) {
            CountDownLatch(1).await(2, TimeUnit.SECONDS)
            served = savedTitle(url)
        }
        return served
    }

    /** Puts [content] at [path] through the replica at [url]. The content is the path, so every artifact is distinct. */
    private fun deploy(url: String, path: String, content: String) {
        val response = Http.put("$url/$path", content, MIXED_BOOTSTRAP)
        assertThat(response.isSuccessful)
            .describedAs("deploy %s: status %s, body %s", path, response.status, response.body)
            .isTrue()
    }

    /** Reads [path] through the replica at [url] and expects the bytes that were deployed for it. */
    private fun assertServes(url: String, path: String, reason: String) {
        val response = Http.get("$url/$path", MIXED_BOOTSTRAP)
        assertThat(response.status).describedAs("%s: status of %s", reason, path).isEqualTo(200)
        assertThat(response.body).describedAs("%s: content of %s", reason, path).isEqualTo(path)
    }

    /**
     * Starts both deploys behind one latch, so they reach their replicas at the same moment, and waits for both within
     * [WAIT_BOUND]. A latch instead of a sleep: the test does not depend on how long a request takes.
     */
    private fun deployConcurrently(vararg deploys: Pair<String, String>) {
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(deploys.size)
        try {
            val responses = deploys.map { (url, path) ->
                pool.submit(
                    Callable {
                        start.await()
                        Http.put("$url/$path", path, MIXED_BOOTSTRAP)
                    }
                )
            }
            start.countDown()
            responses.forEachIndexed { index, future ->
                val (_, path) = deploys[index]
                val response = future.get(WAIT_BOUND.toSeconds(), TimeUnit.SECONDS)
                assertThat(response.isSuccessful)
                    .describedAs("concurrent deploy %s: status %s, body %s", path, response.status, response.body)
                    .isTrue()
            }
        } finally {
            pool.shutdownNow()
        }
    }

    private companion object {
        val WAIT_BOUND: Duration = Duration.ofSeconds(60)

        /** Long enough for a replica to connect and to receive the first snapshot of the bus. Every wait is a latch. */
        val BUS_BOUND: Duration = Duration.ofSeconds(180)

        const val NATS_IMAGE = "nats:2.12-alpine"
        const val NATS_PORT = 4222

        /** Six times the ten-second poll of the previous release, so a missed poll does not fail the case. */
        val SETTINGS_BOUND: Duration = Duration.ofSeconds(60)

        const val PREVIOUS_TITLE = "title saved by the previous release"
        const val CURRENT_TITLE = "title saved by the current build"

        /** The origins of the three messages of the bus case. Each is the instance id that the status would list. */
        const val IGNORED_VERSION = "probe-version-2"
        const val IGNORED_TYPE = "probe-unknown-type"
        const val APPLIED = "probe-known"

        /** A complete, valid snapshot payload, so a message is ignored for its version or type alone. */
        const val SNAPSHOT_PAYLOAD =
            """{"version":"1.1.1","uptimeStart":1762596000000,"usedMemory":512,"maxMemory":1024,"usedThreads":40,"maxThreads":200,""" +
                """"failuresCount":0,"health":"up","interval":60000,"sample":{"at":1762596030000,"memory":512,"threads":40}}"""

        const val FROM_OLD_TO_NEW = "releases/com/example/mixed/1.0.0/mixed-1.0.0.jar"
        const val FROM_NEW_TO_OLD = "releases/com/example/mixed/2.0.0/mixed-2.0.0.jar"
        const val CONCURRENT_FROM_OLD = "releases/com/example/mixed/3.0.0/mixed-3.0.0.jar"
        const val CONCURRENT_FROM_NEW = "releases/com/example/mixed/4.0.0/mixed-4.0.0.jar"
        const val BEFORE_RESTART = "releases/com/example/mixed/5.0.0/mixed-5.0.0.jar"
        const val AFTER_RESTART = "releases/com/example/mixed/6.0.0/mixed-6.0.0.jar"
    }
}
