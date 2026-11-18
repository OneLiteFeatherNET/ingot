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

package com.reposilite.plugin.prometheus

import net.onelitefeather.ingot.deploy.JobLastSuccessSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

/** A job facade that reports a fixed last success. The test sets it; nothing else is read. */
private class FakeJobSource(
    override val job: String,
    private val completedAt: Instant?,
) : JobLastSuccessSource {
    override fun lastSuccessAt(): Instant? = completedAt
}

internal class FacadeOperationalSourceTest {

    @Test
    fun `should report the cleanup job from a registered job source`() {
        // given: a job source for cleanup that last succeeded at a fixed instant
        val completedAt = Instant.parse("2026-11-01T03:00:00Z")
        val source = FacadeOperationalSource { listOf(FakeJobSource(job = "cleanup", completedAt = completedAt)) }

        // when: the job status is read
        val jobs = source.jobLastSuccess()

        // then: the cleanup entry carries the instant of the job source
        assertThat(jobs["cleanup"])
            .describedAs("last success of the cleanup job")
            .isEqualTo(completedAt)
    }

    @Test
    fun `should not report the cleanup job when no job source is registered`() {
        // given: no facade implements the job status
        val source = FacadeOperationalSource { emptyList() }

        // when: the job status is read
        val jobs = source.jobLastSuccess()

        // then: no cleanup value is reported, so no cleanup series is exported
        assertThat(jobs["cleanup"])
            .describedAs("cleanup entry without a job source")
            .isNull()
    }

}
