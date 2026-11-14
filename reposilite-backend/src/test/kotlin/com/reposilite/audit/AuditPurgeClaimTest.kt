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

package com.reposilite.audit

import com.reposilite.audit.application.AuditSettings
import com.reposilite.audit.infrastructure.SqlAuditRepository
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.scheduling.ScheduledJobClaims
import com.reposilite.scheduling.SqliteTestDatabase
import com.reposilite.scheduling.infrastructure.SqlScheduledJobClaims
import com.reposilite.scheduling.runAtOnce
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import panda.std.reactive.Reference
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

/**
 * Every instance of a cluster runs the retention purge on its own daily timer. These tests start two facades at the
 * same instant and count the purges that reach the database.
 */
internal class AuditPurgeClaimTest {

    private val clock = MutableClock()
    private val settings = Reference.reference(AuditSettings(retentionDays = 90))

    /** Counts the purges that reach the repository. */
    private class CountingRepository(private val delegate: AuditRepository) : AuditRepository by delegate {

        val purges = AtomicInteger()

        override fun deleteBefore(timestampMillis: Long): Long {
            purges.incrementAndGet()
            return delegate.deleteBefore(timestampMillis)
        }

    }

    @Test
    fun `runs the retention purge on one instance only when two instances share the database`() {
        SqliteTestDatabase().use { shared ->
            val repository = CountingRepository(SqlAuditRepository(shared.database))
            val instances = listOf("instance-a", "instance-b").map { instanceId ->
                facade(repository, SqlScheduledJobClaims(shared.database, clock, instanceId))
            }

            runAtOnce(instances.map { instance -> { instance.purgeExpired() } })

            assertThat(repository.purges.get()).`as`("purges that reached the database").isEqualTo(1)
        }
    }

    @Test
    fun `runs the retention purge again on the next day on one instance only`() {
        SqliteTestDatabase().use { shared ->
            val repository = CountingRepository(SqlAuditRepository(shared.database))
            val instances = listOf("instance-a", "instance-b").map { instanceId ->
                facade(repository, SqlScheduledJobClaims(shared.database, clock, instanceId))
            }
            runAtOnce(instances.map { instance -> { instance.purgeExpired() } })

            clock.advance(Duration.ofDays(1))
            runAtOnce(instances.map { instance -> { instance.purgeExpired() } })

            assertThat(repository.purges.get()).`as`("purges that reached the database over two days").isEqualTo(2)
        }
    }

    @Test
    fun `runs the retention purge on every instance when no shared database exists`() {
        SqliteTestDatabase().use { database ->
            val repository = CountingRepository(SqlAuditRepository(database.database))
            val instances = listOf(facade(repository, ScheduledJobClaims.Unshared), facade(repository, ScheduledJobClaims.Unshared))

            instances.forEach { it.purgeExpired() }

            assertThat(repository.purges.get()).`as`("purges without a shared database").isEqualTo(2)
        }
    }

    @Test
    fun `runs the retention purge of a day once more when the instance that held it stopped before it deleted`() {
        SqliteTestDatabase().use { shared ->
            val counting = CountingRepository(SqlAuditRepository(shared.database))
            // given: instance A takes the day's purge and stops in the middle of it, before any row is deleted
            val stopped = facade(
                FailingRepository(counting),
                StoppedClaims(SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-a")),
            )
            assertThrows(SimulatedStop::class.java) { stopped.purgeExpired() }

            // when: instance B purges after the lease of A expired, and then again later the same day
            clock.advance(Duration.ofMinutes(5))
            val takeover = facade(counting, SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-b"))
            takeover.purgeExpired()
            clock.advance(Duration.ofMinutes(5))
            takeover.purgeExpired()

            // then: the purge of the day ran once on B, and no third run happened that day
            assertThat(counting.purges.get()).`as`("purges of the day after the stopped holder").isEqualTo(1)
        }
    }

    /** Stands for an instance that stops in the middle of a purge. */
    private class SimulatedStop : RuntimeException("instance stopped in the middle of the purge")

    /** The claims of an instance that stops before it completes its purge, so the day is never marked as done. */
    private class StoppedClaims(private val delegate: ScheduledJobClaims) : ScheduledJobClaims by delegate {
        override fun complete(key: String, done: Boolean) = Unit
        override fun release(lease: ScheduledJobClaims.Lease, done: Boolean) = Unit
    }

    /** Stops every purge before it deletes anything. */
    private class FailingRepository(private val delegate: AuditRepository) : AuditRepository by delegate {
        override fun deleteBefore(timestampMillis: Long): Long = throw SimulatedStop()
    }

    private fun facade(repository: AuditRepository, claims: ScheduledJobClaims) =
        AuditFacade(InMemoryLogger(), repository, settings, clock, claims)

}
