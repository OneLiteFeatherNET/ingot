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
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

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

    private fun tableNames(database: Database): List<String> =
        transaction(database) { SchemaUtils.listTables().map { it.lowercase() } }

    private fun markerCount(database: Database): Int =
        transaction(database) {
            val tables = SearchTables(SearchDialect.of(database.vendor))
            tables.state.selectAll().where { tables.state.key eq SearchSchema.MARKER_KEY }.count().toInt()
        }

}
