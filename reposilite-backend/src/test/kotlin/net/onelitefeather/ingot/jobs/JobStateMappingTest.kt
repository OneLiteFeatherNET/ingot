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
package net.onelitefeather.ingot.jobs

import com.reposilite.cleanup.CleanupRunStatus
import net.onelitefeather.ingot.search.runs.RunStatus
import net.onelitefeather.ingot.search.runs.RunTrigger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JobStateMappingTest {

    @Test
    fun `index run statuses map to the shared states`() {
        val mapped = RunStatus.entries.associateWith { JobStateMapping.index(it) }

        assertThat(mapped)
            .describedAs("index statuses must map to the shared states, a completed index run is a succeeded job")
            .isEqualTo(
                mapOf(
                    RunStatus.RUNNING to JobState.RUNNING,
                    RunStatus.COMPLETED to JobState.SUCCEEDED,
                    RunStatus.FAILED to JobState.FAILED,
                    RunStatus.CANCELLED to JobState.CANCELLED,
                )
            )
    }

    @Test
    fun `cleanup run statuses map to the state of the same name`() {
        val mapped = CleanupRunStatus.entries.associateWith { JobStateMapping.cleanup(it) }

        assertThat(mapped)
            .describedAs("cleanup statuses must keep their own state")
            .isEqualTo(
                mapOf(
                    CleanupRunStatus.SUCCEEDED to JobState.SUCCEEDED,
                    CleanupRunStatus.PARTIAL to JobState.PARTIAL,
                    CleanupRunStatus.FAILED to JobState.FAILED,
                )
            )
    }

    @Test
    fun `index startup, gap and backend triggers map to system and keep the raw trigger as detail`() {
        assertThat(JobStateMapping.indexTrigger(RunTrigger.STARTUP))
            .describedAs("a startup run is a system run, its raw trigger is kept")
            .isEqualTo(JobTrigger.SYSTEM to "STARTUP")
        assertThat(JobStateMapping.indexTrigger(RunTrigger.GAP))
            .describedAs("a gap run is a system run, its raw trigger is kept")
            .isEqualTo(JobTrigger.SYSTEM to "GAP")
        assertThat(JobStateMapping.indexTrigger(RunTrigger.BACKEND))
            .describedAs("a backend run is a system run, its raw trigger is kept")
            .isEqualTo(JobTrigger.SYSTEM to "BACKEND")
    }

    @Test
    fun `index manual and scheduled triggers keep their own trigger without a detail`() {
        assertThat(JobStateMapping.indexTrigger(RunTrigger.MANUAL))
            .describedAs("a manual run keeps the manual trigger")
            .isEqualTo(JobTrigger.MANUAL to null)
        assertThat(JobStateMapping.indexTrigger(RunTrigger.SCHEDULED))
            .describedAs("a scheduled run keeps the scheduled trigger")
            .isEqualTo(JobTrigger.SCHEDULED to null)
    }
}
