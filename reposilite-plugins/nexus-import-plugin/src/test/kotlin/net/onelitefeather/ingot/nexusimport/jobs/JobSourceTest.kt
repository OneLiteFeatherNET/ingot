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

import com.reposilite.plugin.api.jobs.JobQuery
import com.reposilite.plugin.api.jobs.JobSource
import net.onelitefeather.ingot.jobs.JobAggregator
import net.onelitefeather.ingot.jobs.JobCursor
import net.onelitefeather.ingot.jobs.JobSourceRegistry
import net.onelitefeather.ingot.jobs.JobRun
import net.onelitefeather.ingot.jobs.JobState
import net.onelitefeather.ingot.jobs.JobTrigger
import net.onelitefeather.ingot.nexusimport.fake.AdvancingClock
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

internal class JobSourceTest {

    @TempDir
    lateinit var root: Path

    private lateinit var store: JobStore
    private lateinit var source: JobSource

    @BeforeEach
    fun setUp() {
        val database = Database.connect("jdbc:sqlite:${root.resolve("jobs.db")}", driver = "org.sqlite.JDBC")
        store = JobStore(database, AdvancingClock(), lease = Duration.ofMinutes(2))
        source = NexusMigrationJobSource(store)
    }

    /** One job per state: queued, running, paused, completed, failed and cancelled. */
    private fun seedJobsOfEveryState(): Map<String, Long> {
        val queued = store.createJob(kind = "IMPORT", parameters = "{}")
        val running = store.createJob(kind = "IMPORT", parameters = "{}").also { store.acquire(it, "running") }
        val paused = store.createJob(kind = "IMPORT", parameters = "{}").also {
            store.acquire(it, "paused")
            store.pause(it, "paused", PauseReason.OPERATOR)
        }
        val completed = store.createJob(kind = "IMPORT", parameters = "{}").also {
            store.acquire(it, "completed")
            store.finish(it, "completed", JobStatus.COMPLETED)
        }
        val failed = store.createJob(kind = "IMPORT", parameters = "{}").also {
            store.acquire(it, "failed")
            store.finish(it, "failed", JobStatus.FAILED, error = "repository unreachable")
        }
        val cancelled = store.createJob(kind = "IMPORT", parameters = "{}").also { store.cancelIdle(it) }
        return mapOf(
            "QUEUED" to queued,
            "RUNNING" to running,
            "PAUSED" to paused,
            "SUCCEEDED" to completed,
            "FAILED" to failed,
            "CANCELLED" to cancelled,
        )
    }

    @Test
    fun `the migration source lists each job with the state of its status`() {
        // given: one job in every status of the import
        val ids = seedJobsOfEveryState()

        // when: the source lists its runs
        val states = source.page(JobQuery(), before = null, limit = 50).associate { it.id to it.state }

        // then: each job carries the normalised state of its status
        assertThat(states["migration:${ids.getValue("QUEUED")}"]).describedAs("queued job").isEqualTo(JobState.QUEUED)
        assertThat(states["migration:${ids.getValue("RUNNING")}"]).describedAs("running job").isEqualTo(JobState.RUNNING)
        assertThat(states["migration:${ids.getValue("PAUSED")}"]).describedAs("paused job").isEqualTo(JobState.PAUSED)
        assertThat(states["migration:${ids.getValue("SUCCEEDED")}"]).describedAs("completed job").isEqualTo(JobState.SUCCEEDED)
        assertThat(states["migration:${ids.getValue("FAILED")}"]).describedAs("failed job").isEqualTo(JobState.FAILED)
        assertThat(states["migration:${ids.getValue("CANCELLED")}"]).describedAs("cancelled job").isEqualTo(JobState.CANCELLED)
    }

    @Test
    fun `a visitor without the instance admin node gets no migration run`() {
        // given: a finished migration job, and a maintenance source that holds one run, both registered with the core
        store.createJob(kind = "IMPORT", parameters = "{}")
        val maintenanceRun = JobRun(
            id = "index:1",
            source = "index",
            kind = "index.rebuild",
            label = "Rebuild",
            trigger = JobTrigger.MANUAL,
            state = JobState.SUCCEEDED,
            startedAt = Instant.parse("2026-11-01T10:00:00Z"),
        )
        val maintenance = object : JobSource {
            override val id = "index"
            override val kinds = setOf("index.rebuild")
            override val requiredNode = "ingot.admin.maintenance"
            override fun page(query: JobQuery, before: JobCursor?, limit: Int) = listOf(maintenanceRun)
            override fun detail(localId: String): JobRun? = maintenanceRun.takeIf { localId == "1" }
        }
        val registry = JobSourceRegistry()
        registry.register(source)
        registry.register(maintenance)
        registry.seal()

        // when: the Jobs page is read by a visitor who holds the maintenance area only
        val visible = JobAggregator(registry).page(
            JobQuery(),
            access = { node -> node == "ingot.admin.maintenance" },
        ).items

        // then: only the maintenance run is listed, and the migration run is not
        assertThat(source.requiredNode).describedAs("node of the migration source").isEqualTo("ingot.admin.*")
        assertThat(visible.map { it.id }).describedAs("runs visible to a maintenance-only visitor").containsExactly("index:1")
    }

}
