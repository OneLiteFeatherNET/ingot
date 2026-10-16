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

import net.onelitefeather.ingot.search.access.FixtureRow
import net.onelitefeather.ingot.search.access.SearchAccessCompiler
import net.onelitefeather.ingot.search.access.SearchAccessFixtures
import net.onelitefeather.ingot.search.access.SubjectCase
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The SQL row filter returns the rows the evaluator allows to browse, on SQLite and on H2 (artifact search spec, 5.7). The
 * fixed rows are inserted once per database; each caller then queries the same rows with its own compiled condition. The
 * rows are inserted by [insertFixtureRows]. Each test owns its database in the temporary directory.
 */
internal class SearchAccessSqlParityTest {

    private val clock = Clock.fixed(Instant.parse("2026-10-16T10:00:00Z"), ZoneOffset.UTC)

    @TempDir
    lateinit var directory: Path

    @Test
    fun `the SQL row filter on SQLite returns exactly the rows the evaluator allows to browse`() {
        SqliteFileDatabase(directory.resolve("parity.db")).use { shared -> assertSqlAgreesWithEvaluator(shared.database) }
    }

    @Test
    fun `the SQL row filter on H2 returns exactly the rows the evaluator allows to browse`() {
        H2FileDatabase(directory.resolve("parity")).use { shared -> assertSqlAgreesWithEvaluator(shared.database) }
    }

    private fun assertSqlAgreesWithEvaluator(database: Database) {
        SearchSchema.ensure(database, clock)
        val table = SearchTables(SearchDialect.of(database.vendor)).artifact
        insertFixtureRows(database, table, SearchAccessFixtures.rows, clock.millis())

        val disagreements = SearchAccessFixtures.subjects().toList().mapNotNull { case ->
            val expected = expectedRows(case)
            val actual = actualRows(database, table, case)
            if (actual == expected) null else "${case.name}: missing ${expected - actual}, extra ${actual - expected}"
        }

        assertThat(disagreements)
            .describedAs("callers whose SQL row filter differs from the evaluator on ${database.vendor}")
            .isEmpty()
    }

    private fun expectedRows(case: SubjectCase): Set<FixtureRow> =
        SearchAccessFixtures.rows.filter { SearchAccessFixtures.evaluatorAllows(case.subject, it) }.toSet()

    private fun actualRows(database: Database, table: SearchArtifactTable, case: SubjectCase): Set<FixtureRow> {
        val condition = SearchAccessCompiler.compile(case.subject, SearchAccessFixtures.repositories).toCondition(table)
        return transaction(database) {
            table.selectAll().where { condition }
                .map { FixtureRow(it[table.repository], it[table.path]) }
                .toSet()
        }
    }

}
