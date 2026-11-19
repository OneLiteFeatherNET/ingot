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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CleanupJobStateTest {

    @Test
    fun `cleanup run statuses map to the state of the same name`() {
        val mapped = CleanupRunStatus.entries.associateWith { cleanupJobState(it) }

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

}
