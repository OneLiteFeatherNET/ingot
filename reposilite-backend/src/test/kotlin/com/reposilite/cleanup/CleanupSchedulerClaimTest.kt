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

    private fun scheduler(claims: ScheduledJobClaims, started: ConcurrentLinkedQueue<String>) =
        CleanupScheduler(
            clock = clock,
            policies = { listOf(policy) },
            start = { started.add(it.id) },
            claims = claims,
            journalist = InMemoryLogger(),
        )

}
