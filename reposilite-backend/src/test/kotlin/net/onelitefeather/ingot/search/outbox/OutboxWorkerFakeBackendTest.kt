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

package net.onelitefeather.ingot.search.outbox

import com.reposilite.audit.MutableClock
import net.onelitefeather.ingot.search.Provenance
import net.onelitefeather.ingot.search.backend.FakeArtifactSearchBackend
import net.onelitefeather.ingot.search.backend.IndexAction
import net.onelitefeather.ingot.search.database.SearchSchema
import net.onelitefeather.ingot.search.database.SearchStateStore
import net.onelitefeather.ingot.search.database.SqliteFileDatabase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

/**
 * The outbox worker against the in-memory backend: the worker sees only the seam, so these tests show what the core does
 * with the backend's answers. The outbox itself stays on a SQLite file of the test's own directory.
 */
internal class OutboxWorkerFakeBackendTest {

    private val start = Instant.parse("2026-10-16T10:00:00Z")
    private val clock = MutableClock(start)
    private val jar = "com/example/app/1.0/app-1.0.jar"

    @TempDir
    lateinit var directory: Path

    private class FakeStorage : IndexStorage {
        val files = mutableMapOf<String, StoredFile>()

        override fun file(repository: String, path: String): StoredFile? = files[path]

        override fun exists(repository: String, path: String): Boolean = files.containsKey(path)

        override fun head(repository: String, path: String, maxBytes: Int): String? = null
    }

    private inner class Harness(database: org.jetbrains.exposed.v1.jdbc.Database) {
        val backend = FakeArtifactSearchBackend()
        val outbox = OutboxStore(database, clock)
        val storage = FakeStorage()
        val worker = OutboxWorker(
            owner = "instance-a",
            outbox = outbox,
            index = backend,
            storage = storage,
            origins = { _, _ -> Provenance.HOSTED },
            state = SearchStateStore(database, clock),
            clock = clock,
        )
    }

    private fun withWorker(block: Harness.() -> Unit) {
        SqliteFileDatabase(directory.resolve("outbox.db")).use { sqlite ->
            SearchSchema.ensure(sqlite.database, clock)
            Harness(sqlite.database).block()
        }
    }

    @Test
    fun `a tick applies the actions of its batch in one call and completes the entries`() = withWorker {
        storage.files[jar] = StoredFile(sizeBytes = 2048, lastModified = start)
        outbox.enqueue("releases", jar, OutboxOp.CHANGED_PATH)

        val result = worker.tick()

        assertThat(result.completed).`as`("entries completed by the tick").isEqualTo(1)
        assertThat(backend.batches).`as`("batches the backend received").hasSize(1)
        val action = backend.batches.single().single() as IndexAction.Upsert
        assertThat(action.row.file.path).`as`("path of the upserted row").isEqualTo(jar)
        assertThat(outbox.pending()).`as`("entries still pending").isZero()
    }

    @Test
    fun `a failed apply releases the entries of the batch with a backoff`() = withWorker {
        storage.files[jar] = StoredFile(sizeBytes = 2048, lastModified = start)
        outbox.enqueue("releases", jar, OutboxOp.CHANGED_PATH)
        backend.failApplyOnCall = 1

        val result = worker.tick()

        assertThat(result.completed).`as`("entries completed after a failed apply").isZero()
        assertThat(result.failed).`as`("entries counted as failed").isEqualTo(1)
        assertThat(outbox.pending()).`as`("entries still pending").isEqualTo(1L)
        assertThat(outbox.claim("instance-b", 10)).`as`("entries claimable during the backoff").isEmpty()
    }

    @Test
    fun `an entry released after a failed apply is applied by a later tick`() = withWorker {
        storage.files[jar] = StoredFile(sizeBytes = 2048, lastModified = start)
        outbox.enqueue("releases", jar, OutboxOp.CHANGED_PATH)
        backend.failApplyOnCall = 1
        worker.tick()
        clock.advance(Duration.ofDays(1))

        val result = worker.tick()

        assertThat(result.completed).`as`("entries completed once the backoff has passed").isEqualTo(1)
        assertThat(backend.batches).`as`("batches the backend received, including the failed one").hasSize(2)
    }

}
