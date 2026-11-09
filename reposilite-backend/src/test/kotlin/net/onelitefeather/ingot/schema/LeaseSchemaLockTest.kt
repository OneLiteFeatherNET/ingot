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
package net.onelitefeather.ingot.schema

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.sql.SQLException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

internal class LeaseSchemaLockTest {

    private val start = Instant.parse("2026-11-10T10:00:00Z")
    private val clock = ManualClock(start)
    private val table = InMemoryLeaseTable()

    @Test
    fun `acquires the lease when it is free`() {
        // when: a replica acquires the lock on an empty table
        acquire(owner = "replica-a")

        // then: the lease is held by that replica
        assertThat(table.lease?.owner).`as`("owner of the lease").isEqualTo("replica-a")
    }

    @Test
    fun `does not take a lease that is held and not expired`() {
        // given: replica a holds the lease
        acquire(owner = "replica-a")

        // when: replica b waits for it with a short timeout
        assertThrows<SchemaLockTimeoutException> { acquire(owner = "replica-b", timeout = Duration.ofSeconds(5)) }

        // then: the lease stays with replica a
        assertThat(table.lease?.owner).`as`("owner of the lease").isEqualTo("replica-a")
    }

    @Test
    fun `gives up exactly when the injected deadline passes`() {
        // given: replica a holds the lease
        acquire(owner = "replica-a")

        // when: replica b waits for five seconds of injected time
        assertThrows<SchemaLockTimeoutException> { acquire(owner = "replica-b", timeout = Duration.ofSeconds(5)) }

        // then: the waiter stopped at the deadline, not after the wall clock
        assertThat(clock.instant()).`as`("clock after the timeout").isEqualTo(start.plusSeconds(5))
    }

    @Test
    fun `names the schema lock in the timeout message`() {
        // given: replica a holds the lease
        acquire(owner = "replica-a")

        // when / then: the waiter fails with a message that names the lock
        assertThatThrownBy { acquire(owner = "replica-b", timeout = Duration.ofSeconds(2)) }
            .isInstanceOf(SchemaLockTimeoutException::class.java)
            .hasMessageContaining("schema lock")
    }

    @Test
    fun `takes over the lease once its holder has let it expire`() {
        // given: replica a holds the lease for its default lifetime and never renews it
        acquire(owner = "replica-a")
        clock.advance(Duration.ofSeconds(61))

        // when: replica b acquires the lock
        acquire(owner = "replica-b")

        // then: replica b holds the lease
        assertThat(table.lease?.owner).`as`("owner after expiry").isEqualTo("replica-b")
    }

    @Test
    fun `keeps the lease for a holder that renews it before the expiry`() {
        // given: replica a holds the lease and renews it halfway through its lifetime
        val holder = acquire(owner = "replica-a")
        clock.advance(Duration.ofSeconds(50))
        assertThat(holder.renew()).`as`("renewal by the holder").isTrue

        // when: replica b waits past the original expiry, but within the renewed lease
        clock.advance(Duration.ofSeconds(50))
        assertThrows<SchemaLockTimeoutException> { acquire(owner = "replica-b", timeout = Duration.ofSeconds(1)) }

        // then: replica a still holds the lease
        assertThat(table.lease?.owner).`as`("owner of the renewed lease").isEqualTo("replica-a")
    }

    @Test
    fun `a stale holder does not release the lease of the replica that took over`() {
        // given: replica a held the lease, expired, and replica b took it over
        val stale = acquire(owner = "replica-a")
        clock.advance(Duration.ofSeconds(61))
        acquire(owner = "replica-b")

        // when: the stale holder releases its lock
        stale.release()

        // then: the lease of replica b is untouched
        assertThat(table.lease?.owner).`as`("owner after the stale release").isEqualTo("replica-b")
    }

    @Test
    fun `a released lease is taken by a waiter without waiting`() {
        // given: replica a releases its lease
        acquire(owner = "replica-a").release()

        // when: replica b acquires the lock
        acquire(owner = "replica-b", timeout = Duration.ofSeconds(1))

        // then: replica b took it at once, with no time passed
        assertThat(clock.instant()).`as`("clock after the acquisition").isEqualTo(start)
    }

    @Test
    fun `retries an acquisition that fails with error 1213 and succeeds afterwards`() {
        // given: the first attempt hits a deadlock, as Galera reports a certification failure
        table.failures.add(SQLException("Deadlock found when trying to get lock", "40001", 1213))

        // when: the replica acquires the lock
        acquire(owner = "replica-a")

        // then: the second attempt holds the lease
        assertThat(table.acquireAttempts).`as`("attempts").isEqualTo(2)
        assertThat(table.lease?.owner).`as`("owner of the lease").isEqualTo("replica-a")
    }

    @Test
    fun `does not retry an acquisition that fails with an error other than 1213`() {
        // given: the database refuses the connection with access denied
        table.failures.add(SQLException("Access denied", "28000", 1045))

        // when / then: the error is raised at once
        assertThrows<SQLException> { acquire(owner = "replica-a") }
        assertThat(table.acquireAttempts).`as`("attempts").isEqualTo(1)
    }

    private fun acquire(owner: String, timeout: Duration = Duration.ofMinutes(5)): LeaseSchemaLock =
        LeaseSchemaLock.acquire(
            table = table,
            clock = clock,
            owner = owner,
            timeout = timeout,
            pause = clock::advance,
        )

    private class InMemoryLeaseTable : LeaseTable {

        class Lease(val owner: String, var expiresAt: Instant)

        var lease: Lease? = null
        val failures = ArrayDeque<SQLException>()
        var acquireAttempts = 0

        override fun createIfAbsent() = Unit

        override fun tryAcquire(owner: String, now: Instant, expiresAt: Instant): Boolean {
            acquireAttempts++
            failures.removeFirstOrNull()?.let { throw it }
            val current = lease
            if (current == null || current.owner == owner || current.expiresAt.isBefore(now)) {
                lease = Lease(owner, expiresAt)
                return true
            }
            return false
        }

        override fun renew(owner: String, expiresAt: Instant): Boolean {
            val current = lease ?: return false
            if (current.owner != owner) return false
            current.expiresAt = expiresAt
            return true
        }

        override fun release(owner: String) {
            if (lease?.owner == owner) {
                lease = null
            }
        }

    }

    private class ManualClock(private var now: Instant) : Clock() {

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = now

        fun advance(duration: Duration) {
            now = now.plus(duration)
        }

    }

}
