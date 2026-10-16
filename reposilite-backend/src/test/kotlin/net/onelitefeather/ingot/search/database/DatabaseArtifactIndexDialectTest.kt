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

import net.onelitefeather.ingot.search.ArtifactFileRecord
import net.onelitefeather.ingot.search.IndexRow
import net.onelitefeather.ingot.search.Provenance
import net.onelitefeather.ingot.search.outbox.IndexAction
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The index write path (schema ensure, outbox upserts and removals) on the databases it runs on. The H2 file database
 * is opened with the URL production builds (`MODE=MYSQL`), so a dialect that only works without that mode fails here.
 * Every test opens its own database in its own temporary directory.
 */
internal class DatabaseArtifactIndexDialectTest {

    private val clock = Clock.fixed(Instant.parse("2026-10-17T10:00:00Z"), ZoneOffset.UTC)
    private val path = "com/example/app/1.0.0/app-1.0.0.jar"

    @TempDir
    lateinit var directory: Path

    enum class Engine { SQLITE, H2 }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Engine::class)
    fun `creates the search tables once when the schema is ensured twice`(engine: Engine) {
        withDatabase(engine, "schema") { database ->
            val first = SearchSchema.ensure(database, clock)
            val second = SearchSchema.ensure(database, clock)

            assertThat(first).`as`("first start on $engine").isEqualTo(SearchSchema.Outcome.CREATED)
            assertThat(second).`as`("second start on $engine").isEqualTo(SearchSchema.Outcome.ALREADY_CREATED)
            assertThat(markerCount(database)).`as`("marker rows on $engine").isEqualTo(1)
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Engine::class)
    fun `keeps one row when the same upsert is replayed`(engine: Engine) {
        withDatabase(engine, "replay") { database ->
            SearchSchema.ensure(database, clock)
            val writer = DatabaseArtifactIndex(database)

            writer.apply(listOf(IndexAction.Upsert(row(sizeBytes = 10))))
            writer.apply(listOf(IndexAction.Upsert(row(sizeBytes = 10))))

            assertThat(rowCount(database)).`as`("rows after a replay on $engine").isEqualTo(1)
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Engine::class)
    fun `replaces the stored columns when a path is upserted again`(engine: Engine) {
        withDatabase(engine, "update") { database ->
            SearchSchema.ensure(database, clock)
            val writer = DatabaseArtifactIndex(database)

            writer.apply(listOf(IndexAction.Upsert(row(sizeBytes = 10))))
            writer.apply(listOf(IndexAction.Upsert(row(sizeBytes = 20))))

            assertThat(storedSizes(database)).`as`("size after the second upsert on $engine").containsExactly(20L)
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Engine::class)
    fun `removes the row of a removed path`(engine: Engine) {
        withDatabase(engine, "remove") { database ->
            SearchSchema.ensure(database, clock)
            val writer = DatabaseArtifactIndex(database)

            writer.apply(listOf(IndexAction.Upsert(row(sizeBytes = 10))))
            writer.apply(listOf(IndexAction.RemovePath("releases", path)))

            assertThat(rowCount(database)).`as`("rows after the removal on $engine").isZero()
        }
    }

    private fun row(sizeBytes: Long) = IndexRow(
        file = ArtifactFileRecord(
            repository = "releases",
            path = path,
            groupId = "com.example",
            artifactId = "app",
            version = "1.0.0",
            classifier = null,
            extension = "jar",
            snapshot = false,
        ),
        provenance = Provenance.HOSTED,
        sizeBytes = sizeBytes,
        lastModified = 1_000L,
        sha1 = null,
        sha256 = null,
        indexedAt = clock.millis(),
    )

    private fun <T> withDatabase(engine: Engine, name: String, block: (Database) -> T): T =
        when (engine) {
            Engine.SQLITE -> SqliteFileDatabase(directory.resolve("$name.db")).use { block(it.database) }
            Engine.H2 -> H2FileDatabase(directory.resolve("$name.h2")).use { block(it.database) }
        }

    private fun rowCount(database: Database): Int =
        transaction(database) { SearchTables(SearchDialect.of(database.vendor)).artifact.selectAll().count().toInt() }

    private fun storedSizes(database: Database): List<Long> =
        transaction(database) {
            val rows = SearchTables(SearchDialect.of(database.vendor)).artifact
            rows.selectAll().where { rows.pathHash eq pathHash("releases", path) }.map { it[rows.sizeBytes] }
        }

    private fun markerCount(database: Database): Int =
        transaction(database) {
            val tables = SearchTables(SearchDialect.of(database.vendor))
            tables.state.selectAll().where { tables.state.key eq SearchSchema.MARKER_KEY }.count().toInt()
        }

}
