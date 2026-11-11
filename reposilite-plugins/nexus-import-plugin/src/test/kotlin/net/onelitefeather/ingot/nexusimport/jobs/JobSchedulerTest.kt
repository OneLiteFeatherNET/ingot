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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.Result
import com.reposilite.shared.ErrorResponse
import java.nio.file.Path

/** The scheduler's tick: what it runs, what it leaves alone, and how it takes over. The clock is the scenario's fake clock. */
internal class JobSchedulerTest {

    @TempDir
    lateinit var root: Path

    private lateinit var scenario: ImportScenario

    @BeforeEach
    fun setUp() {
        scenario = ImportScenario(root)
    }

    @AfterEach
    fun tearDown() {
        scenario.close()
    }

    private fun create(service: NexusImportService): Long = service.create(
        JobRequest(kind = "IMPORT", url = scenario.nexus.baseUri.toString(), user = "importer", password = "pw"),
    ).orThrow().id

    @Test
    fun `should run a queued job to its end on a tick`() {
        // given: a queued import
        val service = scenario.service()
        val id = create(service)

        // when: the scheduler ticks
        scenario.scheduler(service).tick()

        // then: the job is complete
        assertThat(service.job(id).orThrow().status).isEqualTo(JobStatus.COMPLETED)
    }

    @Test
    fun `should leave every job alone on a tick while the instance is read-only`() {
        // given: a queued import and read-only mode on
        val service = scenario.service()
        val id = create(service)
        scenario.readOnly = true

        // when: the scheduler ticks
        scenario.scheduler(service).tick()

        // then: nothing ran and Nexus was not contacted
        assertThat(service.job(id).orThrow().status).isEqualTo(JobStatus.QUEUED)
        assertThat(scenario.nexus.requests()).isEmpty()
    }

    @Test
    fun `should not take a job whose lease a live replica holds`() {
        // given: replica A holds the job with a live lease
        val service = scenario.service()
        val id = create(service)
        assertThat(scenario.store.acquire(id, "replica-a")).isTrue

        // when: the scheduler on replica B ticks
        scenario.scheduler(scenario.service(token = "replica-b")).tick()

        // then: B does not touch the job
        assertThat(scenario.store.job(id)?.owner).isEqualTo("replica-a")
        assertThat(scenario.nexus.requests()).isEmpty()
    }

    @Test
    fun `should take over a job whose lease ran out and finish it`() {
        // given: replica A held the job and its lease ran out without a commit
        val service = scenario.service(token = "replica-b")
        val id = create(service)
        assertThat(scenario.store.acquire(id, "replica-a")).isTrue
        scenario.clock.advance(ImportScenario.LEASE.plusSeconds(1))

        // when: the scheduler on replica B ticks
        scenario.scheduler(service).tick()

        // then: B has finished the job, with one final row per file
        assertThat(service.job(id).orThrow().status).isEqualTo(JobStatus.COMPLETED)
        assertThat(scenario.store.assets(id)).hasSize(scenario.files.size)
    }

    private fun <T> Result<T, ErrorResponse>.orThrow(): T =
        if (isOk) get() else throw AssertionError("Expected success but got ${error.status}: ${error.message}")
}
