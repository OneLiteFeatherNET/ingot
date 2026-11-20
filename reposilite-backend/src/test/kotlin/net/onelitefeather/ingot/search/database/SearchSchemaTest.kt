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

package net.onelitefeather.ingot.search.database

import com.reposilite.scheduling.runAtOnce
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.Executors

internal class SearchSchemaTest {

    private val clock = Clock.fixed(Instant.parse("2026-10-16T10:00:00Z"), ZoneOffset.UTC)
    private val searchTables = listOf("search_artifact", "search_outbox", "search_rebuild_run", "search_state")

    @TempDir
    lateinit var directory: Path

    @Test
    fun `creates the four search tables on the first start`() {
        SqliteFileDatabase(directory.resolve("first.db")).use { database ->
            SearchSchema.ensure(database.database, clock)

            assertThat(tableNames(database.database))
                .`as`("tables after the first start")
                .containsAll(searchTables)
        }
    }

    @Test
    fun `reports the tables as already created when a second start finds the marker`() {
        SqliteFileDatabase(directory.resolve("twice.db")).use { database ->
            val first = SearchSchema.ensure(database.database, clock)
            val second = SearchSchema.ensure(database.database, clock)

            assertThat(first).`as`("first start").isEqualTo(SearchSchema.Outcome.CREATED)
            assertThat(second).`as`("second start").isEqualTo(SearchSchema.Outcome.ALREADY_CREATED)
        }
    }

    @Test
    fun `writes exactly one marker row when a second start repeats the creation`() {
        SqliteFileDatabase(directory.resolve("marker.db")).use { database ->
            SearchSchema.ensure(database.database, clock)
            SearchSchema.ensure(database.database, clock)

            assertThat(markerCount(database.database))
                .`as`("marker rows after two starts")
                .isEqualTo(1)
        }
    }

    @Test
    fun `creates the tables once when two instances start at the same moment`() {
        val file = directory.resolve("concurrent.db")
        val first = SqliteFileDatabase(file)
        val second = SqliteFileDatabase(file)
        try {
            val outcomes = runAtOnce(
                listOf(
                    { SearchSchema.ensure(first.database, clock) },
                    { SearchSchema.ensure(second.database, clock) },
                ),
            )

            assertThat(outcomes.count { it == SearchSchema.Outcome.CREATED })
                .`as`("instances that created the tables")
                .isEqualTo(1)
            assertThat(tableNames(first.database)).`as`("tables after the race").containsAll(searchTables)
            assertThat(markerCount(first.database)).`as`("marker rows after the race").isEqualTo(1)
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun `leaves the token index alone when a second start sees the tables while the first is still creating them`() {
        val file = directory.resolve("interleaved.db")
        SqliteFileDatabase(file).use { second ->
            var secondStart: Result<SearchSchema.Outcome>? = null
            val first = databaseObservingCommits(file) {
                if (secondStart == null && markerCommitted(file)) {
                    secondStart = onOtherThread { SearchSchema.ensure(second.database, clock) }
                }
            }

            val firstStart = SearchSchema.ensure(first, clock)

            assertThat(firstStart).`as`("first start").isEqualTo(SearchSchema.Outcome.CREATED)
            assertThat(secondStart?.getOrThrow())
                .`as`("second start, run right after the first start committed its marker")
                .isEqualTo(SearchSchema.Outcome.ALREADY_CREATED)
        }
    }

    /** A database on [file] that calls [afterCommit] each time one of its connections has committed. */
    private fun databaseObservingCommits(file: Path, afterCommit: () -> Unit): Database =
        Database.connect({
            val connection = DriverManager.getConnection("jdbc:sqlite:${file.toAbsolutePath()}")
            Proxy.newProxyInstance(Connection::class.java.classLoader, arrayOf(Connection::class.java)) { _, method, arguments ->
                val result = try {
                    method.invoke(connection, *(arguments ?: emptyArray()))
                } catch (failure: InvocationTargetException) {
                    throw failure.targetException
                }
                if (method.name == "commit") afterCommit()
                result
            } as Connection
        })

    /** Whether the marker of the first schema is visible to a connection of its own. */
    private fun markerCommitted(file: Path): Boolean =
        DriverManager.getConnection("jdbc:sqlite:${file.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT count(*) FROM sqlite_master WHERE name = 'search_state'").use { it.next(); it.getInt(1) } == 1 &&
                    statement.executeQuery("SELECT count(*) FROM search_state WHERE \"key\" = '${SearchSchema.MARKER_KEY}'").use { it.next(); it.getInt(1) } == 1
            }
        }

    /** Runs [action] on a thread with no Exposed transaction of its own and waits for it. */
    private fun <T> onOtherThread(action: () -> T): Result<T> {
        val executor = Executors.newSingleThreadExecutor()
        try {
            return executor.submit<Result<T>> { runCatching(action) }.get()
        } finally {
            executor.shutdown()
        }
    }

    private fun tableNames(database: Database): List<String> =
        transaction(database) { SchemaUtils.listTables().map { it.lowercase() } }

    private fun markerCount(database: Database): Int =
        transaction(database) {
            val tables = SearchTables(SearchDialect.of(database.vendor))
            tables.state.selectAll().where { tables.state.key eq SearchSchema.MARKER_KEY }.count().toInt()
        }

}
