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

package net.onelitefeather.ingot.nexusimport.jobs

import net.onelitefeather.ingot.nexusimport.fake.AdvancingClock
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration

/** One database file per test, one clock per test. The lease is 30 seconds and the tests move the clock past it. */
internal class JobStoreTest {

    @TempDir
    lateinit var root: Path

    private lateinit var database: Database
    private lateinit var clock: AdvancingClock
    private lateinit var store: JobStore

    @BeforeEach
    fun setUp() {
        database = Database.connect("jdbc:sqlite:${root.resolve("jobs.db")}", driver = "org.sqlite.JDBC")
        clock = AdvancingClock()
        store = JobStore(database, clock, lease = LEASE)
    }

    @AfterEach
    fun tearDown() {
        // The file lives in the temporary directory, which JUnit removes; nothing else to release.
    }

    private fun job(): Long = store.createJob(kind = "IMPORT", parameters = "{}")

    private fun file(index: Int, repository: String = "releases") = AssetRecord(
        repository = repository,
        path = "com/example/app/%04d/app.jar".format(index),
        size = 5,
        result = AssetResult.COPIED,
    )

    @Test
    fun `should let only one of two takers win a lease that is live`() {
        // given: a job that the first worker takes
        val id = job()
        assertThat(store.acquire(id, token = "worker-a")).isTrue

        // when: the second worker tries while the lease is still live
        val second = store.acquire(id, token = "worker-b")

        // then: the second worker is refused
        assertThat(second).describedAs("a live lease must not be taken").isFalse
    }

    @Test
    fun `should let another worker take the job once the lease has expired`() {
        // given: a job held by the first worker, whose lease then runs out
        val id = job()
        store.acquire(id, token = "worker-a")
        clock.advance(LEASE.plusSeconds(1))

        // when: the second worker tries
        val taken = store.acquire(id, token = "worker-b")

        // then: the second worker holds the job now
        assertThat(taken).describedAs("an expired lease is taken over").isTrue
        assertThat(store.job(id)?.owner).isEqualTo("worker-b")
    }

    @Test
    fun `should commit nothing with a stale token after the job was taken over`() {
        // given: worker A holds the job, its lease expires and worker B takes over
        val id = job()
        store.acquire(id, token = "worker-a")
        clock.advance(LEASE.plusSeconds(1))
        store.acquire(id, token = "worker-b")

        // when: worker A tries to commit a file with its old token
        val committed = store.commitAsset(id, token = "worker-a", asset = file(1), cursor = "x")

        // then: nothing is committed
        assertThat(committed).describedAs("a stale token must not commit").isFalse
        assertThat(store.assets(id)).isEmpty()
    }

    @Test
    fun `should finish with one final row per file after a takeover at 400 of 1000 files`() {
        // given: worker A commits the first 400 files, then stalls past its lease
        val id = job()
        store.acquire(id, token = "worker-a")
        (1..400).forEach { store.commitAsset(id, "worker-a", file(it), cursor = file(it).path) }
        clock.advance(LEASE.plusSeconds(1))

        // when: worker B takes over and continues from the committed cursor
        assertThat(store.acquire(id, token = "worker-b")).isTrue
        val cursor = store.job(id)?.cursor
        (401..1000).forEach { store.commitAsset(id, "worker-b", file(it), cursor = file(it).path) }
        store.finish(id, "worker-b", JobStatus.COMPLETED)

        // then: the job has 1000 files, one row each, and the cursor was the last committed path of A
        assertThat(store.assets(id)).hasSize(1000)
        assertThat(store.assets(id).map { it.path }.toSet()).hasSize(1000)
        assertThat(cursor).isEqualTo(file(400).path)
        assertThat(store.job(id)?.status).isEqualTo(JobStatus.COMPLETED)
    }

    @Test
    fun `should release the lease when paused for read-only and let the job continue after resume`() {
        // given: a running job
        val id = job()
        store.acquire(id, token = "worker-a")

        // when: the worker pauses it for read-only mode
        assertThat(store.pause(id, "worker-a", PauseReason.READ_ONLY)).isTrue

        // then: no worker may take it while it is paused
        assertThat(store.acquire(id, token = "worker-b")).describedAs("a paused job is not taken").isFalse

        // when: the operator resumes it
        assertThat(store.resume(id, PauseReason.READ_ONLY)).isTrue

        // then: a worker takes it again
        assertThat(store.acquire(id, token = "worker-b")).isTrue
    }

    @Test
    fun `should refuse to pause with a stale token`() {
        // given: a job taken over by worker B
        val id = job()
        store.acquire(id, token = "worker-a")
        clock.advance(LEASE.plusSeconds(1))
        store.acquire(id, token = "worker-b")

        // when / then: worker A cannot pause it
        assertThat(store.pause(id, "worker-a", PauseReason.READ_ONLY)).isFalse
        assertThat(store.job(id)?.status).isEqualTo(JobStatus.RUNNING)
    }

    private companion object {
        val LEASE: Duration = Duration.ofSeconds(30)
    }
}
