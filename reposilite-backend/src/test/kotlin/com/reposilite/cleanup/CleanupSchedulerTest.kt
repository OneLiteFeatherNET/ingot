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

package com.reposilite.cleanup

import com.reposilite.cleanup.application.CleanupPolicy
import com.reposilite.cleanup.application.CleanupScope
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.scheduling.ScheduledJobClaims
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The scheduler is driven by [CleanupScheduler.tick] and reads the time from a clock that the test moves by hand.
 */
internal class CleanupSchedulerTest {

    /** A clock whose instant the test sets directly. */
    private class MovableClock(var now: Instant, private val zone: ZoneId = ZoneOffset.UTC) : Clock() {
        override fun getZone(): ZoneId = zone
        override fun withZone(zone: ZoneId): Clock = MovableClock(now, zone)
        override fun instant(): Instant = now
    }

    private fun policy(id: String, enabled: Boolean = true, schedule: String = "02:00") = CleanupPolicy(
        id = id,
        enabled = enabled,
        repositories = listOf("releases"),
        scope = CleanupScope.RELEASES,
        keepLatest = 1,
        schedule = schedule,
    )

    private fun scheduler(clock: Clock, vararg policies: CleanupPolicy, started: MutableList<String>) =
        CleanupScheduler(
            clock = clock,
            policies = { policies.toList() },
            start = { started += it.id },
            claims = ScheduledJobClaims.Unshared,
            journalist = InMemoryLogger(),
        )

    @Test
    fun `should start an enabled policy at its scheduled minute`() {
        // given: a daily policy at 02:00 and a clock at exactly 02:00 UTC
        val clock = MovableClock(Instant.parse("2026-10-15T02:00:00Z"))
        val started = mutableListOf<String>()

        // when: the scheduler ticks
        scheduler(clock, policy("nightly"), started = started).tick()

        // then: the policy is started
        assertEquals(listOf("nightly"), started)
    }

    @Test
    fun `should not start a policy outside its scheduled minute`() {
        // given: a daily policy at 02:00 and a clock one minute later
        val clock = MovableClock(Instant.parse("2026-10-15T02:01:00Z"))
        val started = mutableListOf<String>()

        // when: the scheduler ticks
        scheduler(clock, policy("nightly"), started = started).tick()

        // then: nothing is started
        assertEquals(emptyList<String>(), started)
    }

    @Test
    fun `should start a policy only once within the same day`() {
        // given: a daily policy at 02:00 and a clock at 02:00, ticking twice in that minute
        val clock = MovableClock(Instant.parse("2026-10-15T02:00:00Z"))
        val started = mutableListOf<String>()
        val scheduler = scheduler(clock, policy("nightly"), started = started)

        // when: the scheduler ticks twice
        scheduler.tick()
        clock.now = Instant.parse("2026-10-15T02:00:30Z")
        scheduler.tick()

        // then: the policy started once
        assertEquals(listOf("nightly"), started)
    }

    @Test
    fun `should start the policy again on the next day`() {
        // given: a daily policy that has run on one day
        val clock = MovableClock(Instant.parse("2026-10-15T02:00:00Z"))
        val started = mutableListOf<String>()
        val scheduler = scheduler(clock, policy("nightly"), started = started)
        scheduler.tick()

        // when: the next day reaches the scheduled minute
        clock.now = Instant.parse("2026-10-16T02:00:00Z")
        scheduler.tick()

        // then: the policy starts again
        assertEquals(listOf("nightly", "nightly"), started)
    }

    @Test
    fun `should read the scheduled time in the zone of the clock`() {
        // given: a policy at 02:00 local time, and a clock in Berlin where that is 00:00 UTC
        val clock = MovableClock(Instant.parse("2026-10-15T00:00:00Z"), zone = ZoneId.of("Europe/Berlin"))
        val started = mutableListOf<String>()

        // when: the scheduler ticks at that instant
        scheduler(clock, policy("nightly"), started = started).tick()

        // then: the policy starts because 02:00 is the local time in Berlin
        assertEquals(listOf("nightly"), started)
    }

    @Test
    fun `should never start a disabled policy`() {
        // given: a disabled policy scheduled for the current minute
        val clock = MovableClock(Instant.parse("2026-10-15T02:00:00Z"))
        val started = mutableListOf<String>()

        // when: the scheduler ticks
        scheduler(clock, policy("nightly", enabled = false), started = started).tick()

        // then: nothing is started
        assertEquals(emptyList<String>(), started)
    }

    @Test
    fun `should never start a manual policy`() {
        // given: an enabled policy set to manual at the current minute
        val clock = MovableClock(Instant.parse("2026-10-15T02:00:00Z"))
        val started = mutableListOf<String>()

        // when: the scheduler ticks
        scheduler(clock, policy("on-demand", schedule = "manual"), started = started).tick()

        // then: nothing is started
        assertEquals(emptyList<String>(), started)
    }

}
