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

@file:OptIn(net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi::class)

package net.onelitefeather.ingot.search.database

import com.reposilite.audit.MutableClock
import net.onelitefeather.ingot.search.ArtifactPathParser
import net.onelitefeather.ingot.search.ArtifactPathResult
import net.onelitefeather.ingot.search.IndexRow
import net.onelitefeather.ingot.search.Origin
import net.onelitefeather.ingot.search.Provenance
import net.onelitefeather.ingot.search.backend.IndexAction
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Clock
import java.time.Instant

/**
 * The upgrade of an installation that was created before the token index (full text change, D4 to D6). Each test opens
 * a fresh database in its own temporary directory. The vendor subclasses pick the database.
 *
 * A database at `schema-1` is simulated from a fresh one: the token table, the key column and its index are dropped,
 * and the `schema-2` marker is removed. The statements that remain are exactly the layout of the previous version.
 */
internal abstract class SearchUpgradeContract {

    @TempDir
    lateinit var directory: Path

    /** A database and what closes it after its test. */
    protected class Opened(val database: Database, val close: () -> Unit)

    /** Opens a fresh database in [directory]. */
    protected abstract fun open(directory: Path): Opened

    private val clock: Clock = MutableClock(Instant.parse(NOW))

    private fun withDatabase(block: (Database) -> Unit) {
        val opened = open(directory)
        try {
            block(opened.database)
        } finally {
            opened.close()
        }
    }

    /** Creates the database at the layout of `schema-1`, with rows written through the production backend. */
    private fun createSchemaOneWithRows(database: Database) {
        SearchSchema.ensure(database, clock)
        val backend = DatabaseSearchBackend(database, clock)
        backend.apply(listOf(IndexAction.Upsert(row(JAR)), IndexAction.Upsert(row(SPRING_CORE_JAR))))
        transaction(database) {
            // MariaDB drops an index with its only column; the other databases need the index dropped first.
            if (SearchDialect.of(database.vendor) != SearchDialect.MARIADB) exec("DROP INDEX ix_search_gav_key")
            SchemaUtils.drop(SearchTables(SearchDialect.of(database.vendor)).token)
            exec("ALTER TABLE search_artifact DROP COLUMN gav_key")
            val state = SearchTables(SearchDialect.of(database.vendor)).state
            state.deleteWhere { state.key eq SearchSchema.TOKEN_MARKER_KEY }
        }
    }

    @Test
    fun `an installation at schema-1 keeps its rows when this version opens it`() = withDatabase { database ->
        createSchemaOneWithRows(database)

        SearchSchema.ensure(database, clock)

        assertThat(DatabaseSearchBackend(database, clock).count())
            .`as`("rows after the upgrade of a schema-1 database")
            .isEqualTo(2L)
    }

    @Test
    fun `an installation at schema-1 gains the key column and the token table`() = withDatabase { database ->
        createSchemaOneWithRows(database)

        SearchSchema.ensure(database, clock)

        transaction(database) {
            exec("SELECT gav_key FROM search_artifact WHERE 1 = 0") { }
            exec("SELECT token FROM search_token WHERE 1 = 0") { }
        }
    }

    @Test
    fun `the upgrade of a schema-1 database leaves the rows without a key`() = withDatabase { database ->
        createSchemaOneWithRows(database)

        SearchSchema.ensure(database, clock)

        assertThat(DatabaseSearchBackend(database, clock).status().state)
            .`as`("state of an upgraded index whose rows have no key yet")
            .isEqualTo(net.onelitefeather.ingot.search.IndexState.BUILDING)
    }

    @Test
    fun `the upgrade statements only add a column, a table and indexes`() = withDatabase { database ->
        val statements = SearchSchema.upgradeStatements(database)

        assertThat(statements).`as`("upgrade statements").isNotEmpty()
        statements.forEach { statement ->
            val upper = statement.uppercase()
            assertThat(upper)
                .`as`("statement that only adds: $statement")
                .matches("(?s)^(ALTER TABLE .* ADD (COLUMN )?.*|CREATE INDEX .*|CREATE TABLE .*)$")
            assertThat(listOf("DROP", "RENAME", "MODIFY", "ALTER COLUMN", "TYPE"))
                .`as`("destructive keywords in: $statement")
                .noneMatch { keyword -> upper.contains(keyword) }
        }
    }

    @Test
    fun `a second upgrade of the same database writes one schema-2 marker`() = withDatabase { database ->
        createSchemaOneWithRows(database)

        SearchSchema.ensure(database, clock)
        SearchSchema.ensure(database, clock)

        assertThat(markerCount(database, "schema-2"))
            .`as`("schema-2 markers after two starts")
            .isEqualTo(1)
    }

    @Test
    fun `five unkeyed rows are keyed in three batches and the index is then complete`() = withDatabase { database ->
        SearchSchema.ensure(database, clock)
        val backend = DatabaseSearchBackend(database, clock)
        backend.apply((1..5).map { IndexAction.Upsert(row("com/example/lib$it/1.0/lib$it-1.0.jar")) })
        transaction(database) { exec("UPDATE search_artifact SET gav_key = NULL") }

        val keyed = mutableListOf<Int>()
        var after = 0L
        while (true) {
            val batch = backend.keyRows(after, 2) ?: break
            keyed += batch.keyed
            after = batch.lastId
        }

        assertThat(keyed).`as`("rows keyed per batch, batches of two").containsExactly(2, 2, 1)
        assertThat(backend.status().tokensComplete)
            .`as`("tokensComplete after the sweep")
            .isTrue()
    }

    @Test
    fun `a batch starts after the id it is given even when rows before it are unkeyed`() = withDatabase { database ->
        SearchSchema.ensure(database, clock)
        val backend = DatabaseSearchBackend(database, clock)
        backend.apply((1..3).map { IndexAction.Upsert(row("com/example/lib$it/1.0/lib$it-1.0.jar")) })
        transaction(database) { exec("UPDATE search_artifact SET gav_key = NULL") }
        val ids = transaction(database) {
            val rows = SearchTables(SearchDialect.of(database.vendor)).artifact
            rows.selectAll().orderBy(rows.id).map { it[rows.id] }
        }

        val batch = backend.keyRows(ids.first(), 1)

        assertThat(batch?.lastId).`as`("last id of the batch that starts after the first row").isEqualTo(ids[1])
        assertThat(backend.status().tokensComplete)
            .`as`("tokensComplete while the first row is still unkeyed")
            .isFalse()
    }

    @Test
    fun `a replayed upsert leaves one set of tokens`() = withDatabase { database ->
        SearchSchema.ensure(database, clock)
        val backend = DatabaseSearchBackend(database, clock)
        val upsert = IndexAction.Upsert(row(SPRING_CORE_JAR))

        backend.apply(listOf(upsert))
        backend.apply(listOf(upsert))

        assertThat(tokenCount(database))
            .`as`("token rows of one GAV after a replayed upsert")
            .isEqualTo(SPRING_CORE_TOKENS)
    }

    private fun row(path: String): IndexRow {
        val parsed = ArtifactPathParser.parse(REPOSITORY, path) as ArtifactPathResult.Row
        return IndexRow(
            file = parsed.file,
            provenance = Provenance(Origin.HOSTED, null),
            sizeBytes = 10L,
            lastModified = NOW_MILLIS,
            sha1 = null,
            sha256 = null,
            indexedAt = NOW_MILLIS,
        )
    }

    private fun markerCount(database: Database, key: String): Int = transaction(database) {
        val state = SearchTables(SearchDialect.of(database.vendor)).state
        state.selectAll().where { state.key eq key }.count().toInt()
    }

    private fun tokenCount(database: Database): Int = transaction(database) {
        SearchTables(SearchDialect.of(database.vendor)).token.selectAll().count().toInt()
    }

    private companion object {
        const val NOW = "2026-11-11T10:00:00Z"
        val NOW_MILLIS: Long = Instant.parse(NOW).toEpochMilli()
        const val REPOSITORY = "releases"
        const val JAR = "com/example/app/1.0/app-1.0.jar"
        const val SPRING_CORE_JAR = "org/springframework/spring-core/6.1.0/spring-core-6.1.0.jar"

        /** The tokens of `org.springframework:spring-core:6.1.0`: org, springframework, spring, core, 6, 1, 0. */
        const val SPRING_CORE_TOKENS = 7
    }

}
