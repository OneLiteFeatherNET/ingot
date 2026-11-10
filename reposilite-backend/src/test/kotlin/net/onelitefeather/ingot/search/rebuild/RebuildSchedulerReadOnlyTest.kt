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

package net.onelitefeather.ingot.search.rebuild

import com.reposilite.scheduling.ScheduledJobClaims
import net.onelitefeather.ingot.search.application.RebuildSchedule
import net.onelitefeather.ingot.search.application.SearchSettings
import net.onelitefeather.ingot.search.rebuild.RebuildHarness.Companion.withHarness
import net.onelitefeather.ingot.search.runs.RunKind
import net.onelitefeather.ingot.search.runs.RunTrigger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration

/** The search index rebuild while the instance is read-only (read-only spec, scheduled runs). The harness clock is moved by the test. */
internal class RebuildSchedulerReadOnlyTest {

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
        settings: SearchSettings,
        claims: ScheduledJobClaims,
        startup: RunTrigger?,
        readOnly: () -> Boolean,
    ) = RebuildScheduler(
        clock = clock,
        settings = { settings },
        claims = claims,
        rebuild = service(),
        startup = startup,
        readOnly = readOnly,
    )

    private fun RebuildHarness.rebuildRuns() = runs.recent(20).filter { it.kind == RunKind.REBUILD }

    /** The scheduled rebuild is due at 09:00 and the harness clock is at 10:00, so it is due at every tick of the test. */
    private val scheduled = SearchSettings(rebuildSchedule = RebuildSchedule(enabled = true, at = "09:00", everyDays = 7))

    @Test
    fun `a scheduled rebuild that fell due during read-only is not started once read-only is off`() {
        withHarness(directory, "rebuild-read-only-no-later-start") {
            // given: the scheduled rebuild fell due while read-only was on
            var readOnly = true
            val scheduler = scheduler(scheduled, RecordingClaims(), startup = null, readOnly = { readOnly })
            scheduler.tick()

            // when: read-only is turned off and the scheduler ticks again on the same date
            readOnly = false
            scheduler.tick()

            // then: the skipped rebuild is not started later
            assertThat(rebuildRuns()).`as`("rebuild runs on the same date").isEmpty()
        }
    }

    @Test
    fun `no scheduled rebuild claim is written while read-only is on`() {
        withHarness(directory, "rebuild-read-only-no-claim") {
            // given: the scheduled rebuild is due and read-only is on
            val claims = RecordingClaims()

            // when: the scheduler ticks
            scheduler(scheduled, claims, startup = null, readOnly = { true }).tick()

            // then: no claim was written and no rebuild started
            assertThat(claims.keys).`as`("claims while read-only").isEmpty()
            assertThat(rebuildRuns()).`as`("rebuild runs while read-only").isEmpty()
        }
    }

    @Test
    fun `a startup rebuild waits while read-only is on and runs once read-only is off`() {
        withHarness(directory, "rebuild-read-only-startup") {
            // given: a rebuild the startup rules asked for, and read-only on
            var readOnly = true
            val scheduler = scheduler(SearchSettings(), RecordingClaims(), startup = RunTrigger.STARTUP, readOnly = { readOnly })
            scheduler.tick()
            assertThat(rebuildRuns()).`as`("rebuild runs while read-only").isEmpty()

            // when: read-only is turned off and the scheduler ticks again
            readOnly = false
            clock.advance(Duration.ofSeconds(1))
            scheduler.tick()

            // then: the startup rebuild runs once
            assertThat(rebuildRuns()).`as`("rebuild runs after read-only").hasSize(1)
        }
    }

}
