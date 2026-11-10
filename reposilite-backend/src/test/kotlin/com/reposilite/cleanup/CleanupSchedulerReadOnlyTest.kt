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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Scheduled cleanup while the instance is read-only (read-only spec, scheduled runs). The clock is moved by the test. */
internal class CleanupSchedulerReadOnlyTest {

    private class MovableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
    }

    /** Records every claim call, so a test can tell that read-only wrote none. */
    private class RecordingClaims : ScheduledJobClaims {
        val calls = mutableListOf<String>()
        override fun claim(key: String): Boolean {
            calls += "claim $key"
            return true
        }
        override fun lease(key: String, ttl: Duration): Boolean {
            calls += "lease $key"
            return true
        }
        override fun takeover(key: String, ttl: Duration): Boolean {
            calls += "takeover $key"
            return false
        }
    }

    private val nightly = CleanupPolicy(
        id = "nightly",
        enabled = true,
        repositories = listOf("releases"),
        scope = CleanupScope.RELEASES,
        keepLatest = 1,
        schedule = "02:00",
    )

    private fun scheduler(
        clock: Clock,
        started: MutableList<String>,
        claims: ScheduledJobClaims,
        readOnly: () -> Boolean,
    ) = CleanupScheduler(
        clock = clock,
        policies = { listOf(nightly) },
        start = { started += it.id },
        claims = claims,
        journalist = InMemoryLogger(),
        readOnly = readOnly,
    )

    @Test
    fun `should not start a due cleanup run while read-only is on`() {
        // given: a daily policy at 02:00 and a clock at that minute, with read-only on
        val clock = MovableClock(Instant.parse("2026-11-10T02:00:00Z"))
        val started = mutableListOf<String>()

        // when: the scheduler ticks
        scheduler(clock, started, RecordingClaims(), readOnly = { true }).tick()

        // then: the run does not start
        assertThat(started).`as`("runs started while read-only").isEmpty()
    }

    @Test
    fun `should write no claim for a due cleanup run while read-only is on`() {
        // given: a daily policy at 02:00, with read-only on
        val clock = MovableClock(Instant.parse("2026-11-10T02:00:00Z"))
        val claims = RecordingClaims()

        // when: the scheduler ticks at the scheduled minute
        scheduler(clock, mutableListOf(), claims, readOnly = { true }).tick()

        // then: no claim or lease was written
        assertThat(claims.calls).`as`("claim calls while read-only").isEmpty()
    }

    @Test
    fun `should not start a run that fell due during read-only once read-only is turned off`() {
        // given: the run fell due at 02:00 while read-only was on
        val clock = MovableClock(Instant.parse("2026-11-10T02:00:00Z"))
        val started = mutableListOf<String>()
        var readOnly = true
        val scheduler = scheduler(clock, started, RecordingClaims(), readOnly = { readOnly })
        scheduler.tick()

        // when: read-only is turned off and the scheduler ticks again later that day
        readOnly = false
        clock.now = Instant.parse("2026-11-10T02:05:00Z")
        scheduler.tick()

        // then: the skipped run is not started later
        assertThat(started).`as`("runs started later the same day").isEmpty()
    }

    @Test
    fun `should start the next day's run as scheduled once read-only is off`() {
        // given: a run skipped during read-only on the previous day
        val clock = MovableClock(Instant.parse("2026-11-10T02:00:00Z"))
        val started = mutableListOf<String>()
        var readOnly = true
        val scheduler = scheduler(clock, started, RecordingClaims(), readOnly = { readOnly })
        scheduler.tick()
        readOnly = false

        // when: the next day reaches the scheduled minute with read-only off
        clock.now = Instant.parse("2026-11-11T02:00:00Z")
        scheduler.tick()

        // then: the policy runs once, as scheduled
        assertThat(started).`as`("runs started on the next day").containsExactly("nightly")
    }

}
