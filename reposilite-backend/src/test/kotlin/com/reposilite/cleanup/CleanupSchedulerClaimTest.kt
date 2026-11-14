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

import com.reposilite.audit.MutableClock
import com.reposilite.cleanup.application.CleanupPolicy
import com.reposilite.cleanup.application.CleanupScope
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.scheduling.ScheduledJobClaims
import com.reposilite.scheduling.SqliteTestDatabase
import com.reposilite.scheduling.infrastructure.SqlScheduledJobClaims
import com.reposilite.scheduling.runAtOnce
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Every instance of a cluster runs the same schedule. These tests start two schedulers at the same instant and check
 * that the shared database lets exactly one of them run the policy.
 */
internal class CleanupSchedulerClaimTest {

    private val clock = MutableClock(Instant.parse("2026-10-15T02:00:00Z"))
    private val policy = CleanupPolicy(
        id = "nightly",
        enabled = true,
        repositories = listOf("releases"),
        scope = CleanupScope.RELEASES,
        keepLatest = 1,
        schedule = "02:00",
    )

    @Test
    fun `runs a scheduled policy on one instance only when two instances share the database`() {
        SqliteTestDatabase().use { shared ->
            val started = ConcurrentLinkedQueue<String>()
            val schedulers = listOf("instance-a", "instance-b").map { instanceId ->
                scheduler(SqlScheduledJobClaims(shared.database, clock, instanceId), started)
            }

            runAtOnce(schedulers.map { scheduler -> { scheduler.tick() } })

            assertThat(started.toList()).`as`("runs of the policy").containsExactly("nightly")
        }
    }

    @Test
    fun `runs the policy again on the next day on exactly one instance`() {
        SqliteTestDatabase().use { shared ->
            val started = ConcurrentLinkedQueue<String>()
            val schedulers = listOf("instance-a", "instance-b").map { instanceId ->
                scheduler(SqlScheduledJobClaims(shared.database, clock, instanceId), started)
            }
            runAtOnce(schedulers.map { scheduler -> { scheduler.tick() } })

            clock.advance(Duration.ofDays(1))
            runAtOnce(schedulers.map { scheduler -> { scheduler.tick() } })

            assertThat(started.toList()).`as`("runs of the policy over two days").containsExactly("nightly", "nightly")
        }
    }

    @Test
    fun `runs the policy on every instance when no shared database exists`() {
        val started = ConcurrentLinkedQueue<String>()
        val schedulers = listOf("instance-a", "instance-b").map { scheduler(ScheduledJobClaims.Unshared, started) }

        schedulers.forEach { it.tick() }

        assertThat(started.toList()).`as`("runs without a shared database").containsExactly("nightly", "nightly")
    }

    @Test
    fun `takes over a run whose instance stopped before it finished, and runs it once`() {
        SqliteTestDatabase().use { shared ->
            val started = ConcurrentLinkedQueue<String>()
            // given: instance A starts the day's run and stops in the middle of it, so the day is never completed
            val stopped = StoppedClaims(SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-a"))
            assertThrows(SimulatedStop::class.java) { instanceScheduler(stopped, started, "instance-a", stopsInRun = true).tick() }

            // when: instance B ticks after the lease of that run expired, and then instance C ticks later the same day
            clock.advance(Duration.ofMinutes(5))
            instanceScheduler(SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-b"), started, "instance-b").tick()
            clock.advance(Duration.ofMinutes(5))
            instanceScheduler(SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-c"), started, "instance-c").tick()

            // then: B takes the run over, and no run starts after it on that day
            assertThat(started.toList()).`as`("runs of the policy: the stopped run and one takeover").containsExactly("instance-a", "instance-b")
        }
    }

    @Test
    fun `does not take over a run whose instance is still within its lease`() {
        SqliteTestDatabase().use { shared ->
            val started = ConcurrentLinkedQueue<String>()
            // given: instance A starts the run at the last second of the scheduled minute and stops, so its lease runs to 02:01:59
            clock.advance(Duration.ofSeconds(59))
            val stopped = StoppedClaims(SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-a"))
            assertThrows(SimulatedStop::class.java) { instanceScheduler(stopped, started, "instance-a", stopsInRun = true).tick() }

            // when: instance B ticks at 02:01:30, while the lease of A is still live
            clock.advance(Duration.ofSeconds(31))
            instanceScheduler(SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-b"), started, "instance-b").tick()

            // then: B does not take the run over
            assertThat(started.toList()).`as`("runs of the policy while the lease of the stopped instance is live").containsExactly("instance-a")
        }
    }

    @Test
    fun `starts no run at a later tick after another instance completed the day`() {
        SqliteTestDatabase().use { shared ->
            val started = ConcurrentLinkedQueue<String>()
            // given: instance A completes the day's run at the scheduled minute
            instanceScheduler(SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-a"), started, "instance-a").tick()

            // when: instance B ticks five minutes later the same day
            clock.advance(Duration.ofMinutes(5))
            instanceScheduler(SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-b"), started, "instance-b").tick()

            // then: B starts no run
            assertThat(started.toList()).`as`("runs of the policy after the day was completed").containsExactly("instance-a")
        }
    }

    @Test
    fun `does not start a run at a later tick when no instance started it at the scheduled minute`() {
        SqliteTestDatabase().use { shared ->
            val started = ConcurrentLinkedQueue<String>()
            // given: no instance started the day's run at 02:00

            // when: instance A ticks at 02:01
            clock.advance(Duration.ofMinutes(1))
            instanceScheduler(SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-a"), started, "instance-a").tick()

            // then: the run is not started, because a later tick takes over only a run that was already started
            assertThat(started.toList()).`as`("runs of the policy that were never started at the scheduled minute").isEmpty()
        }
    }

    /** Stands for an instance that stops in the middle of a run: the run never reports its end. */
    private class SimulatedStop : RuntimeException("instance stopped in the middle of its run")

    /** The claims of an instance that stops before it completes a run, so the key is never marked as done. */
    private class StoppedClaims(private val delegate: ScheduledJobClaims) : ScheduledJobClaims by delegate {
        override fun complete(key: String, done: Boolean) = Unit
        override fun release(lease: ScheduledJobClaims.Lease, done: Boolean) = Unit
    }

    private fun instanceScheduler(
        claims: ScheduledJobClaims,
        started: ConcurrentLinkedQueue<String>,
        instance: String,
        stopsInRun: Boolean = false,
    ) = CleanupScheduler(
        clock = clock,
        policies = { listOf(policy) },
        start = { _, _ ->
            started.add(instance)
            if (stopsInRun) throw SimulatedStop()
        },
        claims = claims,
        journalist = InMemoryLogger(),
    )

    private fun scheduler(claims: ScheduledJobClaims, started: ConcurrentLinkedQueue<String>) =
        CleanupScheduler(
            clock = clock,
            policies = { listOf(policy) },
            start = { policy, _ -> started.add(policy.id) },
            claims = claims,
            journalist = InMemoryLogger(),
        )

}
