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

package net.onelitefeather.ingot.search.outbox

import com.reposilite.audit.MutableClock
import net.onelitefeather.ingot.search.Origin
import net.onelitefeather.ingot.search.Provenance
import net.onelitefeather.ingot.search.database.DatabaseArtifactIndex
import net.onelitefeather.ingot.search.database.SearchDialect
import net.onelitefeather.ingot.search.database.SearchSchema
import net.onelitefeather.ingot.search.database.SearchStateKey
import net.onelitefeather.ingot.search.database.SearchStateStore
import net.onelitefeather.ingot.search.database.SearchTables
import net.onelitefeather.ingot.search.database.SqliteFileDatabase
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

/**
 * One tick of the worker against a SQLite index, with storage and the mirror ledger replaced by fakes whose content the
 * test sets (artifact search spec, 3.3, 3.7 and 10.2).
 */
internal class OutboxWorkerTest {

    private val start = Instant.parse("2026-10-16T10:00:00Z")
    private val clock = MutableClock(start)
    private val jar = "com/example/app/1.0/app-1.0.jar"

    @TempDir
    lateinit var directory: Path

    private class FakeStorage : IndexStorage {
        val files = mutableMapOf<String, StoredFile>()
        val heads = mutableMapOf<String, String>()
        val unavailable = mutableSetOf<String>()
        var fileReads = 0

        override fun file(repository: String, path: String): StoredFile? {
            fileReads++
            if (path in unavailable) throw IllegalStateException("storage unavailable")
            return files[path]
        }

        override fun exists(repository: String, path: String): Boolean =
            files.keys.any { it == path || it.startsWith("$path/") }

        override fun head(repository: String, path: String, maxBytes: Int): String? =
            heads[path]?.take(maxBytes)
    }

    private inner class Harness(val database: Database) {
        val tables = SearchTables(SearchDialect.of(database.vendor))
        val outbox = OutboxStore(database, clock)
        val storage = FakeStorage()
        val mirrorDirectories = mutableMapOf<String, String?>()
        val state = SearchStateStore(database, clock)
        val worker = OutboxWorker(
            owner = "instance-a",
            outbox = outbox,
            index = DatabaseArtifactIndex(database),
            storage = storage,
            origins = { repository, directory ->
                if (mirrorDirectories.containsKey("$repository/$directory")) {
                    Provenance(Origin.MIRROR, mirrorDirectories["$repository/$directory"])
                } else {
                    Provenance.HOSTED
                }
            },
            state = state,
            clock = clock,
        )

        fun rows(): List<ResultRow> = transaction(database) { tables.artifact.selectAll().toList() }

        fun rowPaths(): Set<String> = rows().map { it[tables.artifact.path] }.toSet()
    }

    private fun withHarness(name: String, block: Harness.() -> Unit) {
        SqliteFileDatabase(directory.resolve("$name.db")).use { shared ->
            SearchSchema.ensure(shared.database, clock)
            Harness(shared.database).block()
        }
    }

    @Test
    fun `a changed path stores a row with the size and modification time of the file`() {
        withHarness("upsert") {
            storage.files[jar] = StoredFile(sizeBytes = 2048, lastModified = start.minusSeconds(60))
            outbox.enqueue("releases", jar, OutboxOp.CHANGED_PATH)

            worker.tick()

            val row = rows().single()
            assertThat(row[tables.artifact.sizeBytes]).`as`("size").isEqualTo(2048L)
            assertThat(row[tables.artifact.lastModified]).`as`("modification time").isEqualTo(start.minusSeconds(60).toEpochMilli())
            assertThat(row[tables.artifact.groupId]).`as`("group from the directories").isEqualTo("com.example")
            assertThat(row[tables.artifact.origin]).`as`("origin of a hosted file").isEqualTo("HOSTED")
        }
    }

    @Test
    fun `a checksum sidecar fills the sha1 column of its row`() {
        withHarness("sidecar") {
            storage.files[jar] = StoredFile(sizeBytes = 1, lastModified = start)
            storage.heads["$jar.sha1"] = "DA39A3EE5E6B4B0D3255BFEF95601890AFD80709  app-1.0.jar\n"
            outbox.enqueue("releases", jar, OutboxOp.CHANGED_PATH)

            worker.tick()

            assertThat(rows().single()[tables.artifact.sha1])
                .`as`("sha1 read from the sidecar, lower-cased")
                .isEqualTo("da39a3ee5e6b4b0d3255bfef95601890afd80709")
        }
    }

    @Test
    fun `a changed path that is no longer stored removes its row`() {
        withHarness("gone") {
            storage.files[jar] = StoredFile(sizeBytes = 1, lastModified = start)
            outbox.enqueue("releases", jar, OutboxOp.CHANGED_PATH)
            worker.tick()
            storage.files.remove(jar)
            outbox.enqueue("releases", jar, OutboxOp.CHANGED_PATH)

            worker.tick()

            assertThat(rows()).`as`("rows after the file was removed").isEmpty()
        }
    }

    @Test
    fun `a removed directory drops its rows and keeps a sibling whose name starts the same`() {
        withHarness("prefix") {
            val sibling = "com/example/app2/1.0/app2-1.0.jar"
            storage.files[jar] = StoredFile(sizeBytes = 1, lastModified = start)
            storage.files[sibling] = StoredFile(sizeBytes = 1, lastModified = start)
            outbox.enqueue("releases", jar, OutboxOp.CHANGED_PATH)
            outbox.enqueue("releases", sibling, OutboxOp.CHANGED_PATH)
            worker.tick()
            storage.files.remove(jar)
            outbox.enqueue("releases", "com/example/app", OutboxOp.REMOVED_PREFIX)

            worker.tick()

            assertThat(rowPaths()).`as`("rows left").containsExactly(sibling)
        }
    }

    @Test
    fun `a removed prefix keeps the rows of files that are still stored`() {
        withHarness("partial") {
            val kept = "com/example/app/2.0/app-2.0.jar"
            storage.files[jar] = StoredFile(sizeBytes = 1, lastModified = start)
            storage.files[kept] = StoredFile(sizeBytes = 1, lastModified = start)
            outbox.enqueue("releases", jar, OutboxOp.CHANGED_PATH)
            outbox.enqueue("releases", kept, OutboxOp.CHANGED_PATH)
            worker.tick()
            storage.files.remove(jar)
            outbox.enqueue("releases", "com/example/app", OutboxOp.REMOVED_PREFIX)

            worker.tick()

            assertThat(rowPaths()).`as`("rows left").containsExactly(kept)
        }
    }

    @Test
    fun `a reorigin marks the rows of a mirror directory with their upstream host`() {
        withHarness("mirror") {
            storage.files[jar] = StoredFile(sizeBytes = 1, lastModified = start)
            outbox.enqueue("releases", jar, OutboxOp.CHANGED_PATH)
            worker.tick()
            mirrorDirectories["releases/com/example/app/1.0"] = "repo.example.org"

            outbox.enqueue("releases", "com/example/app/1.0", OutboxOp.REORIGIN_DIRECTORY)
            worker.tick()

            val row = rows().single()
            assertThat(row[tables.artifact.origin]).`as`("origin after the reorigin").isEqualTo("MIRROR")
            assertThat(row[tables.artifact.sourceHost]).`as`("upstream host").isEqualTo("repo.example.org")
        }
    }

    @Test
    fun `a reorigin of a directory without a ledger row marks its rows hosted`() {
        withHarness("hosted") {
            storage.files[jar] = StoredFile(sizeBytes = 1, lastModified = start)
            outbox.enqueue("releases", jar, OutboxOp.CHANGED_PATH)
            mirrorDirectories["releases/com/example/app/1.0"] = "repo.example.org"
            worker.tick()
            mirrorDirectories.clear()

            outbox.enqueue("releases", "com/example/app/1.0", OutboxOp.REORIGIN_DIRECTORY)
            worker.tick()

            assertThat(rows().single()[tables.artifact.origin]).`as`("origin after a deployment").isEqualTo("HOSTED")
        }
    }

    @Test
    fun `entries that share a path and operation cost one storage read`() {
        withHarness("burst") {
            storage.files[jar] = StoredFile(sizeBytes = 1, lastModified = start)
            repeat(3) { outbox.enqueue("releases", jar, OutboxOp.CHANGED_PATH) }

            val result = worker.tick()

            assertThat(storage.fileReads).`as`("storage reads").isEqualTo(1)
            assertThat(result.completed).`as`("entries completed").isEqualTo(3)
            assertThat(outbox.pending()).`as`("entries left").isZero()
        }
    }

    @Test
    fun `a failing read releases its own entry with a backoff and completes the others`() {
        withHarness("failing") {
            val other = "com/example/other/1.0/other-1.0.jar"
            storage.files[other] = StoredFile(sizeBytes = 1, lastModified = start)
            storage.unavailable += jar
            outbox.enqueue("releases", jar, OutboxOp.CHANGED_PATH)
            outbox.enqueue("releases", other, OutboxOp.CHANGED_PATH)

            val result = worker.tick()

            assertThat(result.completed).`as`("completed entries").isEqualTo(1)
            assertThat(result.failed).`as`("failed entries").isEqualTo(1)
            assertThat(rowPaths()).`as`("rows written").containsExactly(other)
            assertThat(outbox.pending()).`as`("entries left").isEqualTo(1)
        }
    }

    @Test
    fun `the heartbeat is written on the first tick and then once a minute`() {
        withHarness("heartbeat") {
            worker.tick()
            val first = state.get(SearchStateKey.HEARTBEAT)
            assertThat(first).`as`("heartbeat after the first tick").isEqualTo(start.toEpochMilli().toString())

            clock.advance(Duration.ofSeconds(59))
            worker.tick()
            assertThat(state.get(SearchStateKey.HEARTBEAT)).`as`("heartbeat before a minute has passed").isEqualTo(first)

            clock.advance(Duration.ofSeconds(1))
            worker.tick()
            assertThat(state.get(SearchStateKey.HEARTBEAT))
                .`as`("heartbeat after a minute")
                .isEqualTo(start.plusSeconds(60).toEpochMilli().toString())
        }
    }

}
