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
import com.reposilite.scheduling.infrastructure.SqlScheduledJobClaims
import net.onelitefeather.ingot.search.application.SearchSettings
import net.onelitefeather.ingot.search.rebuild.RebuildHarness
import net.onelitefeather.ingot.search.rebuild.RebuildHarness.Companion.jar
import net.onelitefeather.ingot.search.rebuild.RebuildHarness.Companion.withHarness
import net.onelitefeather.ingot.search.runs.RunKind
import net.onelitefeather.ingot.search.runs.RunStatus
import net.onelitefeather.ingot.search.runs.RunTrigger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration

/** The nightly verify run (artifact search spec, 3.8 and 3.9). The clock starts at 10:00 UTC on 2026-10-17. */
internal class VerifySchedulerTest {

    @TempDir
    lateinit var directory: Path

    private class RecordingClaims(private val grant: Boolean = true) : ScheduledJobClaims {
        val keys = mutableListOf<String>()
        override fun claim(key: String): Boolean {
            keys += key
            return grant
        }
    }

    private fun RebuildHarness.scheduler(
        settings: SearchSettings = SearchSettings(verifyAt = "02:30"),
        claims: ScheduledJobClaims = RecordingClaims(),
        owner: String = "instance-a",
    ) = VerifyScheduler(clock = clock, settings = { settings }, claims = claims, verify = verifyService(owner), runs = runs)

    private fun RebuildHarness.verifyRuns() = runs.recent(20).filter { it.kind == RunKind.VERIFY }

    @Test
    fun `the nightly verify claims the run under the key of the UTC date and runs`() {
        withHarness(directory, "claim-key") {
            val claims = RecordingClaims()

            scheduler(claims = claims).tick()

            assertThat(claims.keys).`as`("claim keys").containsExactly("search-verify:2026-10-17")
            val run = verifyRuns().single()
            assertThat(run.trigger).`as`("trigger").isEqualTo(RunTrigger.SCHEDULED)
            assertThat(run.status).`as`("status").isEqualTo(RunStatus.COMPLETED)
        }
    }

    @Test
    fun `the claim lets only one of two instances verify on a UTC date`() {
        withHarness(directory, "two-instances") {
            val first = scheduler(claims = SqlScheduledJobClaims(database, clock, "instance-a"), owner = "instance-a")
            val second = scheduler(claims = SqlScheduledJobClaims(database, clock, "instance-b"), owner = "instance-b")

            first.tick()
            second.tick()

            assertThat(verifyRuns()).`as`("verify runs on one date, although the first has finished and released its lock").hasSize(1)
        }
    }

    @Test
    fun `the claim of a date does not stop the run of the next date`() {
        withHarness(directory, "next-day") {
            val claims = SqlScheduledJobClaims(database, clock, "instance-a")
            val scheduler = scheduler(claims = claims)
            scheduler.tick()

            clock.advance(Duration.ofDays(1))
            scheduler.tick()

            assertThat(verifyRuns()).`as`("verify runs over two dates").hasSize(2)
        }
    }

    @Test
    fun `one instance verifies once per date however often it ticks`() {
        withHarness(directory, "once") {
            val scheduler = scheduler(claims = SqlScheduledJobClaims(database, clock, "instance-a"))

            repeat(3) { scheduler.tick() }

            assertThat(verifyRuns()).`as`("verify runs after three ticks").hasSize(1)
        }
    }

    @Test
    fun `no verify run starts while verify is disabled`() {
        withHarness(directory, "disabled") {
            val claims = RecordingClaims()

            scheduler(SearchSettings(verifyEnabled = false), claims).tick()

            assertThat(verifyRuns()).`as`("runs").isEmpty()
            assertThat(claims.keys).`as`("claims").isEmpty()
        }
    }

    @Test
    fun `no verify run starts before the time of day`() {
        withHarness(directory, "early") {
            val claims = RecordingClaims()

            scheduler(SearchSettings(verifyAt = "11:00"), claims).tick()

            assertThat(verifyRuns()).`as`("runs").isEmpty()
            assertThat(claims.keys).`as`("claims").isEmpty()
        }
    }

    @Test
    fun `a tick after the time of day still verifies that date`() {
        withHarness(directory, "late") {
            scheduler(SearchSettings(verifyAt = "10:00")).tick()

            assertThat(verifyRuns()).`as`("runs at the minute of the time of day").hasSize(1)
        }
    }

    @Test
    fun `no verify run starts when another instance holds the claim`() {
        withHarness(directory, "claimed") {
            scheduler(claims = RecordingClaims(grant = false)).tick()

            assertThat(verifyRuns()).`as`("runs").isEmpty()
        }
    }

    @Test
    fun `verify waits without using the claim while a rebuild is live`() {
        withHarness(directory, "rebuild-live") {
            entries.put("releases", jar(1))
            val claims = RecordingClaims()
            val rebuild = service().start(RunTrigger.MANUAL)!!

            scheduler(claims = claims).tick()
            assertThat(verifyRuns()).`as`("runs while a rebuild is live").isEmpty()
            assertThat(claims.keys).`as`("the claim is kept for a later tick").isEmpty()

            service().execute(rebuild)
            scheduler(claims = claims).tick()
            assertThat(verifyRuns()).`as`("runs after the rebuild ended").hasSize(1)
        }
    }

}
