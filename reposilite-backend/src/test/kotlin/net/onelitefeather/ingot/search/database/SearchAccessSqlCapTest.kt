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

import com.reposilite.maven.RepositoryVisibility.PRIVATE
import net.onelitefeather.ingot.identity.model.AccessLevel
import net.onelitefeather.ingot.identity.model.AccessSubject
import net.onelitefeather.ingot.identity.model.DecisionSource
import net.onelitefeather.ingot.identity.model.Grant
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.model.Role
import net.onelitefeather.ingot.search.access.FixtureRow
import net.onelitefeather.ingot.search.access.SearchAccessCompiler
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** The rendered row filter at the term cap runs on SQLite and returns the granted rows (artifact search spec, 5.3). */
internal class SearchAccessSqlCapTest {

    private val clock = Clock.fixed(Instant.parse("2026-10-16T10:00:00Z"), ZoneOffset.UTC)

    @TempDir
    lateinit var directory: Path

    @Test
    fun `a row filter at the per-repository term cap runs on SQLite and returns the granted rows`() {
        val grantedPrefixes = (0 until SearchAccessCompiler.MAX_TERMS_PER_REPOSITORY).map { "p$it" }
        val rows = (grantedPrefixes + "p200").map { FixtureRow("private", "$it/app/1.0/app-1.0.jar") }
        val caller = AccessSubject(
            principalId = 1,
            principalName = "alice",
            kind = PrincipalKind.USER,
            disabled = false,
            levels = listOf(
                AccessLevel(
                    DecisionSource.TEAM,
                    0,
                    emptyList(),
                    grantedPrefixes.map { Grant(team = "readers", role = Role.READ, repository = "private", pathPrefix = it) },
                ),
            ),
            tokenRules = null,
        )

        SqliteFileDatabase(directory.resolve("cap.db")).use { shared ->
            SearchSchema.ensure(shared.database, clock)
            val table = SearchTables(SearchDialect.of(shared.database.vendor)).artifact
            insertFixtureRows(shared.database, table, rows, clock.millis())

            val condition = SearchAccessCompiler.compile(caller, mapOf("private" to PRIVATE)).toCondition(table)
            val visible = transaction(shared.database) {
                table.selectAll().where { condition }.map { it[table.path] }.toSet()
            }

            assertThat(visible)
                .describedAs("paths visible at the cap")
                .containsExactlyInAnyOrderElementsOf(rows.map { it.path }.filterNot { it.startsWith("p200/") })
        }
    }

}
