package com.reposilite.cleanup

import com.reposilite.cleanup.application.CleanupPolicy
import com.reposilite.cleanup.application.CleanupScope
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.scheduling.ScheduledJobClaims
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The scheduler reads the day key and the scheduled minute from the injected clock, and [CleanupScheduler.runTick] runs one
 * tick without waiting for the timer.
 */
internal class CleanupSchedulerTickTest {

    /** A clock whose instant the test sets directly. */
    private class MovableClock(var now: Instant, private val zone: ZoneId = ZoneOffset.UTC) : Clock() {
        override fun getZone(): ZoneId = zone
        override fun withZone(zone: ZoneId): Clock = MovableClock(now, zone)
        override fun instant(): Instant = now
    }

    private fun policy(id: String) = CleanupPolicy(
        id = id,
        enabled = true,
        repositories = listOf("releases"),
        scope = CleanupScope.RELEASES,
        keepLatest = 1,
        schedule = "02:00",
    )

    private fun scheduler(
        clock: Clock,
        started: MutableList<String>,
        keys: MutableList<String>,
        start: ((CleanupPolicy) -> Unit)? = null,
    ) = CleanupScheduler(
        clock = clock,
        policies = { listOf(policy("nightly")) },
        start = { policy, lease ->
            started += policy.id
            keys += lease.key
            start?.invoke(policy)
        },
        claims = ScheduledJobClaims.Unshared,
        journalist = InMemoryLogger(),
    )

    @Test
    fun `should take the day key of a run from the injected clock`() {
        // given: a daily policy at 02:00 and a fake clock on 2026-10-15 at its scheduled minute
        val clock = MovableClock(Instant.parse("2026-10-15T02:00:00Z"))
        val started = mutableListOf<String>()
        val keys = mutableListOf<String>()

        // when: the scheduler ticks
        scheduler(clock, started, keys).tick()

        // then: the run holds the day key of the clock's UTC date
        assertEquals(listOf("cleanup:nightly:2026-10-15"), keys, "The day key must come from the injected clock")
    }

    @Test
    fun `should run a due policy once when the tick is called directly`() {
        // given: a daily policy at 02:00 and a fake clock at its scheduled minute, with no timer involved
        val clock = MovableClock(Instant.parse("2026-10-15T02:00:00Z"))
        val started = mutableListOf<String>()
        val keys = mutableListOf<String>()

        // when: the tick is run directly
        scheduler(clock, started, keys).runTick()

        // then: the policy ran exactly once
        assertEquals(listOf("nightly"), started, "runTick must run the due policy once without waiting for the timer")
    }

    @Test
    fun `should not throw when a run fails during a direct tick`() {
        // given: a daily policy at 02:00 whose run fails
        val clock = MovableClock(Instant.parse("2026-10-15T02:00:00Z"))
        val started = mutableListOf<String>()
        val keys = mutableListOf<String>()
        val failing = scheduler(clock, started, keys) { error("boom") }

        // when / then: the tick logs the failure instead of propagating it, as the timer registration relies on
        assertDoesNotThrow({ failing.runTick() }, "runTick must contain a failing tick, as the scheduled registration does")
    }

}
