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
import net.onelitefeather.ingot.search.rebuild.RebuildHarness.Companion.jar
import net.onelitefeather.ingot.search.rebuild.RebuildHarness.Companion.withHarness
import net.onelitefeather.ingot.search.runs.RunStatus
import net.onelitefeather.ingot.search.runs.RunTrigger
import net.onelitefeather.ingot.search.runs.SimulatedCrash
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration

/** When rebuilds start by themselves (artifact search spec, 3.7 and 3.9). The clock starts at 10:00 UTC. */
internal class RebuildSchedulerTest {

    @TempDir
    lateinit var directory: Path

    private class RecordingClaims(private val grant: Boolean = true) : ScheduledJobClaims {
        val keys = mutableListOf<String>()
        override fun claim(key: String): Boolean {
            keys += key
            return grant
        }
    }

    private fun schedule(at: String, everyDays: Int = 7, enabled: Boolean = true) =
        SearchSettings(rebuildSchedule = RebuildSchedule(enabled = enabled, at = at, everyDays = everyDays))

    private fun RebuildHarness.scheduler(
        settings: SearchSettings,
        claims: ScheduledJobClaims = RecordingClaims(),
        startup: RunTrigger? = null,
        service: RebuildService = service(),
    ) = RebuildScheduler(clock = clock, settings = { settings }, claims = claims, rebuild = service, startup = startup)

    @Test
    fun `a startup rebuild decided at boot runs on the first tick`() {
        withHarness(directory, "startup") {
            entries.put("releases", jar(1))

            scheduler(schedule("11:00", enabled = false), startup = RunTrigger.STARTUP).tick()

            val run = runs.recent(1).single()
            assertThat(run.trigger).`as`("trigger").isEqualTo(RunTrigger.STARTUP)
            assertThat(run.status).`as`("status").isEqualTo(RunStatus.COMPLETED)
        }
    }

    @Test
    fun `a startup rebuild runs only once`() {
        withHarness(directory, "startup-once") {
            entries.put("releases", jar(1))
            val scheduler = scheduler(schedule("11:00", enabled = false), startup = RunTrigger.GAP)

            scheduler.tick()
            scheduler.tick()

            assertThat(runs.recent(10)).`as`("runs after two ticks").hasSize(1)
        }
    }

    @Test
    fun `no rebuild starts while the schedule is off`() {
        withHarness(directory, "off") {
            val claims = RecordingClaims()

            scheduler(schedule("03:30", enabled = false), claims).tick()

            assertThat(runs.recent(10)).`as`("runs").isEmpty()
            assertThat(claims.keys).`as`("claims").isEmpty()
        }
    }

    @Test
    fun `a scheduled rebuild waits for its time of day`() {
        withHarness(directory, "early") {
            val claims = RecordingClaims()

            scheduler(schedule("11:00"), claims).tick()

            assertThat(runs.recent(10)).`as`("runs before the time of day").isEmpty()
            assertThat(claims.keys).`as`("claims before the time of day").isEmpty()
        }
    }

    @Test
    fun `a scheduled rebuild starts on a tick after its minute`() {
        withHarness(directory, "late") {
            entries.put("releases", jar(1))
            val claims = RecordingClaims()

            scheduler(schedule("03:30"), claims).tick()

            assertThat(runs.recent(1).single().trigger).`as`("trigger").isEqualTo(RunTrigger.SCHEDULED)
            assertThat(claims.keys).`as`("claim key by UTC date").containsExactly("search-rebuild:2026-10-17")
        }
    }

    @Test
    fun `a scheduled rebuild starts once per UTC day`() {
        withHarness(directory, "once-a-day") {
            entries.put("releases", jar(1))
            val scheduler = scheduler(schedule("03:30", everyDays = 1))

            scheduler.tick()
            scheduler.tick()

            assertThat(runs.recent(10)).`as`("runs after two ticks of one day").hasSize(1)
        }
    }

    @Test
    fun `a scheduled rebuild waits until the days since the last full rebuild have passed`() {
        withHarness(directory, "every-days") {
            entries.put("releases", jar(1))
            val scheduler = scheduler(schedule("03:30", everyDays = 7))
            scheduler.tick()

            clock.advance(Duration.ofDays(6))
            scheduler.tick()
            assertThat(runs.recent(10)).`as`("runs after six days").hasSize(1)

            clock.advance(Duration.ofDays(1))
            scheduler.tick()
            assertThat(runs.recent(10)).`as`("runs after seven days").hasSize(2)
        }
    }

    @Test
    fun `a scheduled rebuild does not start when another instance holds the claim`() {
        withHarness(directory, "claimed") {
            entries.put("releases", jar(1))

            scheduler(schedule("03:30"), RecordingClaims(grant = false)).tick()

            assertThat(runs.recent(10)).`as`("runs").isEmpty()
        }
    }

    @Test
    fun `a tick resumes a run whose instance died`() {
        withHarness(directory, "takeover") {
            (1..6).forEach { entries.put("releases", jar(it)) }
            entries.crashOnRequest = 3
            assertThatThrownBy { service("instance-a").rebuild(RunTrigger.MANUAL) }.isInstanceOf(SimulatedCrash::class.java)
            entries.crashOnRequest = null
            clock.advance(Duration.ofMinutes(3))

            scheduler(schedule("11:00", enabled = false), service = service("instance-b")).tick()

            val run = runs.recent(1).single()
            assertThat(run.status).`as`("status of the resumed run").isEqualTo(RunStatus.COMPLETED)
            assertThat(run.trigger).`as`("trigger stays the one of the first start").isEqualTo(RunTrigger.MANUAL)
            assertThat(indexedPaths("releases")).`as`("indexed files").hasSize(6)
        }
    }

}
