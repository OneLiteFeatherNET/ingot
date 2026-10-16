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

package net.onelitefeather.ingot.search.rebuild

import com.reposilite.audit.MutableClock
import com.reposilite.maven.infrastructure.InMemoryMirrorCacheLedger
import net.onelitefeather.ingot.search.ArtifactPathParser
import net.onelitefeather.ingot.search.ArtifactPathResult
import net.onelitefeather.ingot.search.IndexRow
import net.onelitefeather.ingot.search.Provenance
import net.onelitefeather.ingot.search.database.DatabaseArtifactIndex
import net.onelitefeather.ingot.search.database.IndexRowStore
import net.onelitefeather.ingot.search.database.SearchDialect
import net.onelitefeather.ingot.search.database.SearchSchema
import net.onelitefeather.ingot.search.database.SearchStateStore
import net.onelitefeather.ingot.search.database.SearchTables
import net.onelitefeather.ingot.search.database.SqliteFileDatabase
import net.onelitefeather.ingot.search.outbox.IndexAction
import net.onelitefeather.ingot.search.outbox.OutboxStore
import net.onelitefeather.ingot.search.runs.FakeRepositoryEntries
import net.onelitefeather.ingot.search.runs.RunStore
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.nio.file.Path
import java.time.Instant

/**
 * One fresh SQLite database in the test's temporary directory with everything a rebuild needs around it: a fake
 * storage, an in-memory mirror ledger and a clock the test moves. Every test builds its own, so none shares state.
 */
internal class RebuildHarness(val database: Database, val clock: MutableClock, private val pageSize: Int = 2) {

    val tables = SearchTables(SearchDialect.of(database.vendor))
    val entries = FakeRepositoryEntries()
    val ledger = InMemoryMirrorCacheLedger()
    val outbox = OutboxStore(database, clock)
    val rows = IndexRowStore(database)
    val runs = RunStore(database, clock)
    val state = SearchStateStore(database, clock)

    fun service(owner: String = "instance-a"): RebuildService = RebuildService(
        owner = owner,
        runs = runs,
        rows = rows,
        index = DatabaseArtifactIndex(database),
        outbox = outbox,
        repositories = entries,
        ledger = ledger,
        state = state,
        clock = clock,
        pageSize = pageSize,
    )

    fun indexRows(): List<ResultRow> = transaction(database) { tables.artifact.selectAll().toList() }

    fun indexRow(repository: String, path: String): ResultRow? =
        indexRows().firstOrNull { it[tables.artifact.repository] == repository && it[tables.artifact.path] == path }

    fun indexedPaths(repository: String): Set<String> =
        indexRows().filter { it[tables.artifact.repository] == repository }.map { it[tables.artifact.path] }.toSet()

    /** Writes a row as an earlier run or a worker would have, with the given size and indexing time. */
    fun seedRow(repository: String, path: String, size: Long, indexedAt: Instant, sha1: String? = null) {
        val parsed = ArtifactPathParser.parse(repository, path) as ArtifactPathResult.Row
        val row = IndexRow(parsed.file, Provenance.HOSTED, size, Instant.parse("2026-10-01T00:00:00Z").toEpochMilli(), sha1, null, indexedAt.toEpochMilli())
        DatabaseArtifactIndex(database).apply(listOf(IndexAction.Upsert(row)))
    }

    companion object {
        const val START = "2026-10-17T10:00:00Z"

        /** A releasable jar path whose name sorts by [n] for single digits. */
        fun jar(n: Int): String = "com/example/app$n/1.0/app$n-1.0.jar"

        fun <T> withHarness(directory: Path, name: String, pageSize: Int = 2, block: RebuildHarness.() -> T): T {
            val clock = MutableClock(Instant.parse(START))
            return SqliteFileDatabase(directory.resolve("$name.db")).use { shared ->
                SearchSchema.ensure(shared.database, clock)
                RebuildHarness(shared.database, clock, pageSize).block()
            }
        }
    }

}
