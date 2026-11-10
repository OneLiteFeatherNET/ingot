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

package net.onelitefeather.ingot.search.verify

import com.reposilite.scheduling.ScheduledJobClaims
import net.onelitefeather.ingot.search.application.SearchSettings
import net.onelitefeather.ingot.search.rebuild.RebuildHarness
import net.onelitefeather.ingot.search.rebuild.RebuildHarness.Companion.withHarness
import net.onelitefeather.ingot.search.runs.RunKind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration

/** The nightly verify run while the instance is read-only (read-only spec, scheduled runs). The harness clock is moved by the test. */
internal class VerifySchedulerReadOnlyTest {

    @TempDir
    lateinit var directory: Path

    private class RecordingClaims : ScheduledJobClaims {
        val keys = mutableListOf<String>()
        override fun claim(key: String): Boolean {
            keys += key
            return true
        }
    }

    private fun RebuildHarness.scheduler(
        claims: ScheduledJobClaims,
        readOnly: () -> Boolean,
    ) = VerifyScheduler(
        clock = clock,
        settings = { SearchSettings(verifyAt = "02:30") },
        claims = claims,
        verify = verifyService("instance-a"),
        runs = runs,
        readOnly = readOnly,
    )

    private fun RebuildHarness.verifyRuns() = runs.recent(20).filter { it.kind == RunKind.VERIFY }

    @Test
    fun `no verify run starts while read-only is on`() {
        withHarness(directory, "read-only-no-start") {
            // given: the harness clock is past the verify time and read-only is on
            val claims = RecordingClaims()

            // when: the scheduler ticks
            scheduler(claims, readOnly = { true }).tick()

            // then: no run is started and no claim is written
            assertThat(verifyRuns()).`as`("verify runs while read-only").isEmpty()
            assertThat(claims.keys).`as`("claims while read-only").isEmpty()
        }
    }

    @Test
    fun `a verify that fell due during read-only is not started once read-only is off`() {
        withHarness(directory, "read-only-no-later-start") {
            // given: the verify fell due while read-only was on
            var readOnly = true
            val scheduler = scheduler(RecordingClaims(), readOnly = { readOnly })
            scheduler.tick()

            // when: read-only is turned off and the scheduler ticks again on the same date
            readOnly = false
            scheduler.tick()

            // then: the skipped verify is not started later
            assertThat(verifyRuns()).`as`("verify runs on the same date").isEmpty()
        }
    }

    @Test
    fun `the verify of the next date runs as scheduled once read-only is off`() {
        withHarness(directory, "read-only-next-date") {
            // given: the verify of one date was skipped during read-only
            var readOnly = true
            val claims = RecordingClaims()
            val scheduler = scheduler(claims, readOnly = { readOnly })
            scheduler.tick()
            readOnly = false

            // when: the next date reaches the verify time with read-only off
            clock.advance(Duration.ofDays(1))
            scheduler.tick()

            // then: the verify of the next date runs once, under its own claim
            assertThat(verifyRuns()).`as`("verify runs after read-only").hasSize(1)
            assertThat(claims.keys).`as`("claim keys after read-only").containsExactly("search-verify:2026-10-18")
        }
    }

}
