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
import net.onelitefeather.ingot.nexusimport.plan.LossRow
import net.onelitefeather.ingot.nexusimport.plan.LossStatus
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration

/** The control flags, the repository and report tables and the delete rules of the job store. One database per test. */
internal class JobStoreControlTest {

    @TempDir
    lateinit var root: Path

    private lateinit var clock: AdvancingClock
    private lateinit var store: JobStore

    @BeforeEach
    fun setUp() {
        val database = Database.connect("jdbc:sqlite:${root.resolve("control.db")}", driver = "org.sqlite.JDBC")
        clock = AdvancingClock()
        store = JobStore(database, clock, lease = LEASE)
    }

    private fun job(): Long = store.createJob(kind = "IMPORT", parameters = "{}")

    private fun repository(name: String, status: RepositoryStatus = RepositoryStatus.PLANNED) =
        RepositoryRow(nexusName = name, targetName = "$name-target", type = "hosted", status = status)

    @Test
    fun `should keep the lease of the holder when it acquires its own running job again`() {
        // given: worker A holds the job
        val id = job()
        store.acquire(id, token = "worker-a")

        // when: worker A acquires it again, as the planning step and the copy step both do
        val again = store.acquire(id, token = "worker-a")

        // then: the acquisition succeeds for the same token and the job still has one owner
        assertThat(again).describedAs("the holder of a live lease keeps it").isTrue
        assertThat(store.job(id)?.owner).isEqualTo("worker-a")
    }

    @Test
    fun `should store the conflict detail with the asset row and list only the conflicts`() {
        // given: one conflicting file and one copied file
        val id = job()
        store.acquire(id, "worker-a")
        store.commitAsset(id, "worker-a", AssetRecord("releases", "a.jar", 3, AssetResult.CONFLICT, "nexus sha1 aa; target sha1 bb"), cursor = null)
        store.commitAsset(id, "worker-a", AssetRecord("releases", "b.jar", 3, AssetResult.COPIED), cursor = null)

        // when: the conflicts are read
        val conflicts = store.conflicts(id)

        // then: only the conflict is listed, with its detail
        assertThat(conflicts.map { it.path }).containsExactly("a.jar")
        assertThat(conflicts.single().detail).isEqualTo("nexus sha1 aa; target sha1 bb")
    }

    @Test
    fun `should cancel a queued job at once and leave a running job to its worker`() {
        // given: one queued and one running job
        val queued = job()
        val running = job()
        store.acquire(running, "worker-a")

        // when: both are cancelled
        store.cancelIdle(queued)
        store.requestCancel(running)

        // then: the queued job is cancelled, the running one carries the request until its worker sees it
        assertThat(store.job(queued)?.status).isEqualTo(JobStatus.CANCELLED)
        assertThat(store.job(running)?.status).isEqualTo(JobStatus.RUNNING)
        assertThat(store.job(running)?.cancelRequested).isTrue
    }

    @Test
    fun `should pause a queued job at once and pause a running job at its next file`() {
        // given: a queued job and a running job
        val queued = job()
        val running = job()
        store.acquire(running, "worker-a")

        // when: the operator pauses both
        store.requestPause(queued)
        store.requestPause(running)

        // then: the queued one is paused by the operator, the running one carries the request
        assertThat(store.job(queued)?.status).isEqualTo(JobStatus.PAUSED)
        assertThat(store.job(queued)?.pauseReason).isEqualTo(PauseReason.OPERATOR)
        assertThat(store.job(running)?.pauseRequested).isTrue
        assertThat(store.job(running)?.status).isEqualTo(JobStatus.RUNNING)
    }

    @Test
    fun `should clear the pause request and queue the job again when the operator resumes it`() {
        // given: a job paused by the operator
        val id = job()
        store.requestPause(id)

        // when: the operator resumes it
        assertThat(store.resume(id, PauseReason.OPERATOR)).isTrue

        // then: the job is queued and carries no pause request
        assertThat(store.job(id)?.status).isEqualTo(JobStatus.QUEUED)
        assertThat(store.job(id)?.pauseRequested).isFalse
    }

    @Test
    fun `should store the repository rows once and let only the holder change their status`() {
        // given: a planned job, held by worker A
        val id = job()
        store.acquire(id, "worker-a")

        // when: the rows are planned twice and worker A marks one copied while worker B holds nothing
        store.planRepositories(id, listOf(repository("releases"), repository("snapshots")))
        store.planRepositories(id, listOf(repository("releases", RepositoryStatus.FAILED)))
        val copied = store.setRepositoryStatus(id, "worker-a", "releases", RepositoryStatus.COPIED)
        val stale = store.setRepositoryStatus(id, "worker-b", "snapshots", RepositoryStatus.COPIED)

        // then: two rows, the first planning kept, the holder's change stored and the stale one refused
        assertThat(store.repositories(id).map { it.nexusName }).containsExactly("releases", "snapshots")
        assertThat(store.repositories(id).single { it.nexusName == "releases" }.status).isEqualTo(RepositoryStatus.COPIED)
        assertThat(copied).isTrue
        assertThat(stale).describedAs("a stale holder cannot change a repository").isFalse
    }

    @Test
    fun `should replace the report rows of a job and read them back in order`() {
        // given: a report of two rows, replaced by a report of one row
        val id = job()
        val first = LossRow("repository", "releases", "releases", LossStatus.MAPPED, "reason", "consequence", "action")
        store.replaceReport(id, listOf(first, first.copy(nexusId = "snapshots")))

        // when: the report is replaced
        store.replaceReport(id, listOf(first.copy(nexusId = "npm-proxy", status = LossStatus.UNSUPPORTED)))

        // then: only the new row is kept, with every field
        val rows = store.report(id)
        assertThat(rows).containsExactly(first.copy(nexusId = "npm-proxy", status = LossStatus.UNSUPPORTED))
    }

    @Test
    fun `should report a running job whose lease expired as needing credentials`() {
        // given: a job held by worker A, whose lease runs out
        val id = job()
        store.acquire(id, "worker-a")
        clock.advance(LEASE.plusSeconds(1))

        // when: another replica finds it without the credentials
        val marked = store.markCredentialsNeeded(id)

        // then: the job waits for the operator
        assertThat(marked).isTrue
        assertThat(store.job(id)?.status).isEqualTo(JobStatus.PAUSED)
        assertThat(store.job(id)?.pauseReason).isEqualTo(PauseReason.CREDENTIALS_NEEDED)
    }

    @Test
    fun `should list queued jobs and jobs with an expired lease as runnable and nothing else`() {
        // given: a queued job, a live running job, an expired running job and a finished job
        val queued = job()
        val live = job()
        val expired = job()
        val finished = job()
        store.acquire(live, "worker-a")
        store.acquire(expired, "worker-b")
        store.acquire(finished, "worker-c")
        store.finish(finished, "worker-c", JobStatus.COMPLETED)
        clock.advance(LEASE.plusSeconds(1))
        store.acquire(live, "worker-a")

        // when: the runnable jobs are listed
        val runnable = store.runnable().map { it.id }

        // then: the queued job and the job with the expired lease, but not the live or finished one
        assertThat(runnable).containsExactlyInAnyOrder(queued, expired)
    }

    @Test
    fun `should delete a finished job with its rows and refuse to delete a running one`() {
        // given: a finished job with rows and a running job
        val finished = job()
        store.acquire(finished, "worker-a")
        store.commitAsset(finished, "worker-a", AssetRecord("releases", "a.jar", 3, AssetResult.COPIED), cursor = null)
        store.finish(finished, "worker-a", JobStatus.COMPLETED)
        store.planRepositories(finished, listOf(repository("releases")))
        val running = job()
        store.acquire(running, "worker-b")

        // when / then: the finished job goes with its rows, the running one stays
        assertThat(store.deleteJob(running)).describedAs("a running job is not deleted").isFalse
        assertThat(store.deleteJob(finished)).isTrue
        assertThat(store.job(finished)).isNull()
        assertThat(store.assets(finished)).isEmpty()
        assertThat(store.repositories(finished)).isEmpty()
    }

    private companion object {
        val LEASE: Duration = Duration.ofSeconds(30)
    }
}
