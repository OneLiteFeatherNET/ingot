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

@file:OptIn(ExperimentalIngotSearchApi::class)

package net.onelitefeather.ingot.search.outbox

import com.reposilite.audit.MutableClock
import net.onelitefeather.ingot.search.Provenance
import net.onelitefeather.ingot.search.backend.BackendBusyException
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.backend.FakeArtifactSearchBackend
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
 * A busy backend releases its claims without an attempt (search backend registration, D7). The worker runs against the
 * fake backend and a SQLite outbox of the test's own directory.
 */
internal class OutboxBusyBackendTest {

    private val start = Instant.parse("2026-10-16T10:00:00Z")
    private val clock = MutableClock(start)
    private val jar = "com/example/app/1.0/app-1.0.jar"
    private val storage = FakeStorage()

    @TempDir
    lateinit var directory: Path

    private class FakeStorage : IndexStorage {
        val files = mutableMapOf<String, StoredFile>()

        override fun file(repository: String, path: String): StoredFile? = files[path]

        override fun exists(repository: String, path: String): Boolean = files.containsKey(path)

        override fun head(repository: String, path: String, maxBytes: Int): String? = null
    }

    private fun withWorker(block: (OutboxStore, FakeArtifactSearchBackend, OutboxWorker) -> Unit) {
        SqliteFileDatabase(directory.resolve("busy.db")).use { sqlite ->
            SearchSchema.ensure(sqlite.database, clock)
            val outbox = OutboxStore(sqlite.database, clock)
            val backend = FakeArtifactSearchBackend()
            val worker = OutboxWorker(
                owner = "instance-a",
                outbox = outbox,
                index = backend,
                storage = storage,
                origins = { _, _ -> Provenance.HOSTED },
                state = SearchStateStore(sqlite.database, clock),
                clock = clock,
            )
            block(outbox, backend, worker)
        }
    }

    @Test
    fun `a busy backend releases its batch and nothing is counted as failed`() = withWorker { outbox, backend, worker ->
        storage.files[jar] = StoredFile(sizeBytes = 2048, lastModified = start)
        outbox.enqueue("releases", jar, OutboxOp.CHANGED_PATH)
        backend.onApply = { throw BackendBusyException("busy", Duration.ofMinutes(2)) }

        val result = worker.tick()

        assertThat(result.completed).`as`("entries completed while the backend is busy").isZero()
        assertThat(result.failed).`as`("entries counted as failed while the backend is busy").isZero()
    }

    @Test
    fun `a busy release keeps the entries claimable only after the delay the backend asked for`() = withWorker { outbox, backend, worker ->
        storage.files[jar] = StoredFile(sizeBytes = 2048, lastModified = start)
        outbox.enqueue("releases", jar, OutboxOp.CHANGED_PATH)
        backend.onApply = { throw BackendBusyException("busy", Duration.ofMinutes(2)) }
        worker.tick()

        assertThat(outbox.claim("instance-b", 10)).`as`("entries claimable during the busy delay").isEmpty()

        clock.advance(Duration.ofMinutes(2).plusSeconds(1))
        assertThat(outbox.claim("instance-b", 10)).`as`("entries claimable after the busy delay").hasSize(1)
    }

    @Test
    fun `a busy release does not count an attempt`() = withWorker { outbox, backend, worker ->
        storage.files[jar] = StoredFile(sizeBytes = 2048, lastModified = start)
        outbox.enqueue("releases", jar, OutboxOp.CHANGED_PATH)
        backend.onApply = { throw BackendBusyException("busy", Duration.ofMinutes(2)) }
        worker.tick()
        clock.advance(Duration.ofMinutes(2).plusSeconds(1))

        val claimed = outbox.claim("instance-b", 10).single()

        assertThat(claimed.attempts).`as`("attempts of the entry claimed after the busy release").isEqualTo(1)
    }

    @Test
    fun `ten real failures after a busy release leave a dead letter`() = withWorker { outbox, backend, worker ->
        storage.files[jar] = StoredFile(sizeBytes = 2048, lastModified = start)
        outbox.enqueue("releases", jar, OutboxOp.CHANGED_PATH)
        var calls = 0
        backend.onApply = {
            calls++
            if (calls == 1) throw BackendBusyException("busy", Duration.ofMinutes(1))
            throw IllegalStateException("write failed")
        }
        worker.tick()

        repeat(9) {
            clock.advance(Duration.ofDays(1))
            worker.tick()
        }
        assertThat(outbox.deadLetters()).`as`("dead letters after nine real failures").isZero()

        clock.advance(Duration.ofDays(1))
        worker.tick()
        assertThat(outbox.deadLetters()).`as`("dead letters after ten real failures").isEqualTo(1L)
    }

}
