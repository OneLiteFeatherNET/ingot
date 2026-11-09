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

import com.fasterxml.jackson.databind.ObjectMapper
import com.reposilite.IsolatedResources
import com.reposilite.SharedContainers
import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import com.reposilite.databaseToken
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.plugin.Extensions
import com.reposilite.schemaToken
import com.reposilite.scheduling.ScheduledJobClaims
import com.reposilite.scheduling.infrastructure.SqlScheduledJobClaims
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The database transport on MariaDB and PostgreSQL (cluster bus, tasks 9.5 and 9.6). Two instances connect to one
 * database through their own connection pools. Polls are driven by the test, so no test waits on a timer; the
 * database itself is real.
 */
internal class DatabaseClusterTransportIntegrationTest {

    private val now: Instant = Instant.parse("2026-11-08T10:15:30Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

    @TempDir
    lateinit var workingDirectory: Path

    enum class Remote { MARIADB, POSTGRESQL }

    enum class FileDatabase(val configuration: String) { SQLITE("sqlite --temporary"), H2("h2") }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Remote::class)
    fun `delivers a token change to the other instance on its next poll`(remote: Remote) {
        withSharedDatabase(remote) { first, second ->
            val publisher = Instance(first, "ingot-a")
            val receiver = Instance(second, "ingot-b")
            publisher.subscribe()
            receiver.subscribe()

            publisher.publishToken("ci-bot")
            receiver.poll()

            assertThat(receiver.receivedTokenNames()).`as`("token names received on $remote").containsExactly("ci-bot")
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Remote::class)
    fun `own events are not delivered back to the publishing instance`(remote: Remote) {
        withSharedDatabase(remote) { first, _ ->
            val publisher = Instance(first, "ingot-a")
            publisher.subscribe()

            publisher.publishToken("ci-bot")
            publisher.poll()

            assertThat(publisher.received).`as`("own events on $remote").isEmpty()
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Remote::class)
    fun `rows stored before an instance connects are not replayed`(remote: Remote) {
        withSharedDatabase(remote) { first, second ->
            Instance(first, "ingot-a").publishToken("stored-before-connect")
            val receiver = Instance(second, "ingot-b")
            receiver.subscribe()

            receiver.poll()
            assertThat(receiver.received).`as`("stored rows on $remote").isEmpty()

            Instance(first, "ingot-a").publishToken("stored-after-connect")
            receiver.poll()
            assertThat(receiver.receivedTokenNames()).`as`("rows after connect on $remote").containsExactly("stored-after-connect")
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Remote::class)
    fun `a row older than the retention window is removed by exactly one instance`(remote: Remote) {
        withSharedDatabase(remote) { databaseA, databaseB ->
            val granted = CopyOnWriteArrayList<String>()
            Instance(databaseA, "ingot-old", staleClock(), granted).publishToken("stale")
            Instance(databaseA, "ingot-a", granted = granted).publishToken("fresh")

            val receiverA = Instance(databaseA, "ingot-a", granted = granted)
            val receiverB = Instance(databaseB, "ingot-b", granted = granted)
            receiverA.subscribe()
            receiverB.subscribe()
            receiverA.poll()
            receiverB.poll()

            assertThat(granted).`as`("prune claims granted on $remote").hasSize(1)
            assertThat(granted.single()).startsWith("cluster-event-prune:")
            assertThat(storedPayloadNames(databaseA)).`as`("rows left on $remote").containsExactly("fresh")
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(FileDatabase::class)
    fun `the database scheme is refused on a file database and the instance keeps serving`(file: FileDatabase) {
        DatabaseConnectionFactory.createConnection(workingDirectory, file.configuration, 1).use { connection ->
            val logger = InMemoryLogger()
            val extensions = Extensions(logger)
            val plugin = ClusterPlugin(
                sources = ClusterSources(property = { null }, variable = mapOf("INGOT_CLUSTER_URL" to "database:")::get, hostName = { "ingot-a" }),
                transports = mapOf("database" to DatabaseTransportFactory(database = { connection.database }, clock = clock)),
                clusterIdentifier = { CLUSTER },
                clock = clock,
            )
            extensions.registerPlugin(plugin)

            val facade = plugin.initialize()

            assertThat(facade).`as`("no cluster facade on ${file.name}").isNull()
            assertThat(logger.messages.map { it.value }.filter { "MariaDB" in it })
                .`as`("refusal lines on ${file.name}")
                .hasSize(1)
        }
    }

    /** One instance of the cluster, with its own transport and its own manual poll schedule. */
    private inner class Instance(
        val database: Database,
        val origin: String,
        instanceClock: Clock = clock,
        granted: MutableList<String> = mutableListOf(),
    ) {
        private val scheduler = ManualPollScheduler()
        private val factory = DatabaseTransportFactory(
            database = { database },
            clock = instanceClock,
            scheduler = scheduler,
            claims = { db, c -> RecordingClaims(SqlScheduledJobClaims(db, c), granted) },
        )
        private val transport: ClusterTransport = factory.open(
            ClusterBusUrl.parse("database:"),
            ClusterSettings.read(ClusterSources(property = { null }, variable = mapOf("INGOT_CLUSTER_INSTANCE_ID" to origin)::get, hostName = { origin })),
            CLUSTER,
        )
        val received = CopyOnWriteArrayList<String>()

        fun subscribe() {
            transport.subscribe { received.add(it) }
        }

        fun publishToken(name: String) {
            transport.publish(tokenChanged(origin, name))
        }

        fun poll() {
            scheduler.tick()
        }

        fun receivedTokenNames(): List<String> =
            received.mapNotNull { raw ->
                (ClusterEnvelopeCodec.decode(raw) as? ClusterDecodeResult.Accepted)?.envelope?.payload?.get("name")?.asText()
            }
    }

    private fun staleClock(): Clock = Clock.fixed(now.minus(Duration.ofMinutes(10)), ZoneOffset.UTC)

    private fun tokenChanged(origin: String, name: String): String =
        ClusterEnvelopeCodec.encode(
            ClusterEnvelope(
                type = ClusterEventType.TOKEN_CHANGED,
                cluster = CLUSTER,
                origin = origin,
                at = clock.instant(),
                payload = ObjectMapper().createObjectNode().put("name", name),
            )
        )

    private fun storedPayloadNames(database: Database): List<String> =
        transaction(database) {
            ClusterEventTable.selectAll().map { row ->
                (ClusterEnvelopeCodec.decode(row[ClusterEventTable.payload]) as ClusterDecodeResult.Accepted)
                    .envelope.payload.get("name").asText()
            }
        }

    /** The claims of the real table, with every granted key recorded for the assertions. */
    private class RecordingClaims(private val delegate: ScheduledJobClaims, private val granted: MutableList<String>) : ScheduledJobClaims {
        override fun claim(key: String): Boolean =
            delegate.claim(key).also { if (it) granted.add(key) }
    }

    private class ManualPollScheduler : PollScheduler {
        private val tasks = CopyOnWriteArrayList<() -> Unit>()

        override fun every(intervalMillis: Long, task: () -> Unit): AutoCloseable {
            tasks.add(task)
            return AutoCloseable { tasks.remove(task) }
        }

        fun tick() {
            tasks.forEach { it() }
        }
    }

    /** Two connections to one fresh database or schema, dropped after the test. */
    private fun <T> withSharedDatabase(remote: Remote, block: (Database, Database) -> T): T =
        when (remote) {
            Remote.MARIADB -> {
                val name = IsolatedResources.createMariaDbDatabase(SharedContainers.mariaDb)
                try {
                    val token = SharedContainers.mariaDb.databaseToken(name)
                    DatabaseConnectionFactory.createConnection(workingDirectory, token, 4).use { first ->
                        DatabaseConnectionFactory.createConnection(workingDirectory, token, 4).use { second ->
                            block(first.database, second.database)
                        }
                    }
                } finally {
                    IsolatedResources.dropMariaDbDatabase(SharedContainers.mariaDb, name)
                }
            }
            Remote.POSTGRESQL -> {
                val schema = IsolatedResources.createPostgresSchema(SharedContainers.postgres)
                try {
                    val token = SharedContainers.postgres.schemaToken(schema)
                    DatabaseConnectionFactory.createConnection(workingDirectory, token, 4).use { first ->
                        DatabaseConnectionFactory.createConnection(workingDirectory, token, 4).use { second ->
                            block(first.database, second.database)
                        }
                    }
                } finally {
                    IsolatedResources.dropPostgresSchema(SharedContainers.postgres, schema)
                }
            }
        }

    private companion object {
        const val CLUSTER = "cluster-1"
    }
}
