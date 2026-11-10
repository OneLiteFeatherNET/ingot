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
package com.reposilite.maven

import com.reposilite.shared.ErrorResponse
import net.onelitefeather.ingot.schema.LeaseTable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.Result
import java.sql.SQLException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

private const val GUARD_SECONDS = 10L
private const val SERVICE_UNAVAILABLE = 503

/**
 * Each test builds its own clock, lease rows and replicas. Time only moves when the test moves it or a waiter pauses, so
 * no test waits on the wall clock. A held lock is released by the test itself, and threads are joined before the test ends.
 */
internal class MetadataLocksTest {

    private val start = Instant.parse("2026-11-10T10:00:00Z")
    private val clock = ManualClock(start)
    private val leases = LeaseRows()
    private val key = "releases:com/example/app"
    private val otherKey = "releases:com/example/other"

    @Test
    fun `refuses a second writer of the same artifact while the first one holds the lock`() {
        // given: a writer of the artifact holds its lock
        val locks = replica("replica-a", shared = false)
        val holding = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val first = AtomicReference<Result<Unit, ErrorResponse>>()
        val holder = thread {
            first.set(locks.withLock(key) {
                holding.countDown()
                check(finish.await(GUARD_SECONDS, SECONDS)) { "the holder was never released" }
                Result.ok(Unit)
            })
        }
        assertThat(holding.await(GUARD_SECONDS, SECONDS)).`as`("the first writer entered the lock").isTrue

        // when: a second writer of the same artifact asks for the lock while it is held
        var ranSecond = false
        val second = locks.withLock(key) {
            ranSecond = true
            Result.ok(Unit)
        }

        // then: the second writer is refused after the wait bound, and its operation does not run
        finish.countDown()
        holder.join(GUARD_SECONDS * 1000)
        assertThat(second.isErr).`as`("second writer is refused").isTrue
        assertThat(second.error.status).`as`("status of the refused second writer").isEqualTo(SERVICE_UNAVAILABLE)
        assertThat(ranSecond).`as`("second writer ran while the first one held the lock").isFalse
        assertThat(first.get().isOk).`as`("first writer finished").isTrue
    }

    @Test
    fun `runs a writer of another artifact without waiting for the holder`() {
        // given: a writer of one artifact holds its lock
        val locks = replica("replica-a", shared = false)
        val holding = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val holder = thread {
            locks.withLock(key) {
                holding.countDown()
                check(finish.await(GUARD_SECONDS, SECONDS)) { "the holder was never released" }
                Result.ok(Unit)
            }
        }
        assertThat(holding.await(GUARD_SECONDS, SECONDS)).`as`("the holder entered the lock").isTrue

        // when: a writer of another artifact of the same repository asks for its own lock
        val other = locks.withLock(otherKey) { Result.ok("other") }

        // then: it runs at once, with no time passed on the clock
        finish.countDown()
        holder.join(GUARD_SECONDS * 1000)
        assertThat(other.isOk).`as`("writer of another artifact ran").isTrue
        assertThat(clock.instant()).`as`("clock after the other artifact ran").isEqualTo(start)
    }

    @Test
    fun `lets the same thread run a nested update of the same artifact`() {
        // given: an update of the artifact runs inside the lock of the same artifact
        val locks = replica("replica-a", shared = true)

        // when: the nested update asks for the same lock
        val nested = locks.withLock(key) {
            locks.withLock(key) { Result.ok("nested") }
        }

        // then: the nested update runs without waiting for itself
        assertThat(nested.isOk).`as`("nested update of the same artifact ran").isTrue
        assertThat(clock.instant()).`as`("clock after the nested update").isEqualTo(start)
    }

    @Test
    fun `releases the lock when the operation throws`() {
        // given: an update of the artifact fails inside its lock
        val locks = replica("replica-a", shared = false)
        var raised = false
        try {
            locks.withLock<Unit>(key) { throw IllegalStateException("storage failed") }
        } catch (exception: IllegalStateException) {
            raised = true
        }
        assertThat(raised).`as`("the failing update raised its exception").isTrue

        // when: another writer of the artifact asks for the lock from another thread
        val later = AtomicReference<Result<Unit, ErrorResponse>>()
        thread { later.set(locks.withLock(key) { Result.ok(Unit) }) }.join(GUARD_SECONDS * 1000)

        // then: the lock is free again
        assertThat(later.get()?.isOk).`as`("writer after the failed update ran").isTrue
    }

    @Test
    fun `takes over the lease of an instance that stopped after its lease expired`() {
        // given: another instance took the lease of the artifact and stopped without releasing it
        val locks = replica("replica-a", shared = true)
        leases.table(key).tryAcquire("replica-crashed", clock.instant(), clock.instant().plus(LEASE))
        clock.advance(Duration.ofSeconds(61))

        // when: this instance updates the artifact
        val result = locks.withLock(key) { Result.ok("updated") }

        // then: the update runs, because the lease has expired
        assertThat(result.isOk).`as`("update after the lease expired").isTrue
    }

    @Test
    fun `refuses a write while another instance holds the lease of the artifact`() {
        // given: another instance holds a lease of the artifact that runs past the wait bound
        val locks = replica("replica-a", shared = true)
        leases.table(key).tryAcquire("replica-b", clock.instant(), clock.instant().plus(LEASE))
        var ran = false

        // when: this instance asks to update the artifact
        val result = locks.withLock(key) {
            ran = true
            Result.ok(Unit)
        }

        // then: the write is refused with 503, nothing is written, and the other instance keeps its lease
        assertThat(result.isErr).`as`("write is refused").isTrue
        assertThat(result.error.status).`as`("status of the refused write").isEqualTo(SERVICE_UNAVAILABLE)
        assertThat(ran).`as`("operation ran while another instance held the lease").isFalse
        assertThat(leases.table(key).renew("replica-b", clock.instant().plus(LEASE)))
            .`as`("the other instance still holds its lease")
            .isTrue
    }

    @Test
    fun `commits the write when the lease is still held`() {
        // given: this instance holds the lease of the artifact
        val locks = replica("replica-a", shared = true)
        var writes = 0

        // when: the update commits its metadata while it still holds the lease
        val result = locks.withLock(key) { scope ->
            scope.commit {
                writes++
                Result.ok(Unit)
            }
        }

        // then: the metadata is written once
        assertThat(result.isOk).`as`("update with a held lease").isTrue
        assertThat(writes).`as`("writes committed while the lease was held").isEqualTo(1)
    }

    @Test
    fun `refuses the commit with 503 and writes nothing when the lease is taken over before it`() {
        // given: this instance holds the lease of the artifact while it updates the metadata
        val locks = replica("replica-a", shared = true)
        var writes = 0

        // when: the lease expires and another instance takes it before the commit
        val result = locks.withLock(key) { scope ->
            clock.advance(Duration.ofSeconds(61))
            leases.table(key).tryAcquire("replica-b", clock.instant(), clock.instant().plus(LEASE))
            scope.commit {
                writes++
                Result.ok(Unit)
            }
        }

        // then: the commit is refused with 503 and the metadata is not written
        assertThat(result.isErr).`as`("commit after the takeover is refused").isTrue
        assertThat(result.error.status).`as`("status of the refused commit").isEqualTo(SERVICE_UNAVAILABLE)
        assertThat(writes).`as`("writes after the lease was taken over").isZero
    }

    @Test
    fun `takes the lease after a deadlock on the lease table and then runs the update`() {
        // given: the first attempt on the lease table hits error 1213
        val locks = replica("replica-a", shared = true)
        leases.table(key).failures.add(SQLException("Deadlock found when trying to get lock", "40001", 1213))

        // when: the update asks for the lock
        val result = locks.withLock(key) { Result.ok("updated") }

        // then: the retry takes the lease and the update runs
        assertThat(result.isOk).`as`("update after a deadlock").isTrue
    }

    @Test
    fun `takes the lease after a certification failure on the lease table and then runs the update`() {
        // given: the first attempt on the lease table fails certification with SQLSTATE 40001
        val locks = replica("replica-a", shared = true)
        leases.table(key).failures.add(SQLException("Certification failed", "40001", 0))

        // when: the update asks for the lock
        val result = locks.withLock(key) { Result.ok("updated") }

        // then: the retry takes the lease and the update runs
        assertThat(result.isOk).`as`("update after a certification failure").isTrue
    }

    @Test
    fun `refuses the write with 503 when conflicts on the lease table last until the wait bound`() {
        // given: every attempt on the lease table hits error 1213
        val locks = replica("replica-a", shared = true)
        leases.table(key).failEveryAttempt = SQLException("Deadlock found when trying to get lock", "40001", 1213)
        var ran = false

        // when: the update asks for the lock
        val result = locks.withLock(key) {
            ran = true
            Result.ok(Unit)
        }

        // then: the write is refused with 503 and nothing runs
        assertThat(result.isErr).`as`("write is refused").isTrue
        assertThat(result.error.status).`as`("status of the refused write").isEqualTo(SERVICE_UNAVAILABLE)
        assertThat(ran).`as`("operation ran without the lease").isFalse
    }

    @Test
    fun `hands the lease to the next instance as soon as the holder has finished`() {
        // given: one instance has finished its update of the artifact
        val first = replica("replica-a", shared = true)
        val second = replica("replica-b", shared = true)
        assertThat(first.withLock(key) { Result.ok(Unit) }.isOk).`as`("first update").isTrue

        // when: the next instance updates the artifact at the same moment
        val result = second.withLock(key) { Result.ok("second") }

        // then: it runs at once, with no time passed on the clock
        assertThat(result.isOk).`as`("second update").isTrue
        assertThat(clock.instant()).`as`("clock after the hand over").isEqualTo(start)
    }

    private fun replica(owner: String, shared: Boolean): MetadataLocks =
        LockingMetadataLocks(
            clock = clock,
            leaseTables = if (shared) { name -> leases.table(name) } else null,
            owner = owner,
            pause = clock::advance,
        )

    private companion object {
        val LEASE: Duration = Duration.ofSeconds(60)
    }

    /** The lease rows of one shared database, one table per lease name. */
    private class LeaseRows {

        private val tables = ConcurrentHashMap<String, InMemoryLeaseTable>()

        fun table(name: String): InMemoryLeaseTable =
            tables.computeIfAbsent(name) { InMemoryLeaseTable() }

    }

    private class InMemoryLeaseTable : LeaseTable {

        private class Lease(val owner: String, var expiresAt: Instant)

        private var lease: Lease? = null
        val failures = ArrayDeque<SQLException>()
        var failEveryAttempt: SQLException? = null
        private val attempts = AtomicInteger()

        override fun createIfAbsent() = Unit

        @Synchronized
        override fun tryAcquire(owner: String, now: Instant, expiresAt: Instant): Boolean {
            attempts.incrementAndGet()
            failEveryAttempt?.let { throw it }
            failures.removeFirstOrNull()?.let { throw it }
            val current = lease
            if (current == null || current.owner == owner || current.expiresAt.isBefore(now)) {
                lease = Lease(owner, expiresAt)
                return true
            }
            return false
        }

        @Synchronized
        override fun renew(owner: String, expiresAt: Instant): Boolean {
            val current = lease ?: return false
            if (current.owner != owner) return false
            current.expiresAt = expiresAt
            return true
        }

        @Synchronized
        override fun release(owner: String) {
            if (lease?.owner == owner) {
                lease = null
            }
        }

    }

    private class ManualClock(private var now: Instant) : Clock() {

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        @Synchronized
        override fun instant(): Instant = now

        @Synchronized
        fun advance(duration: Duration) {
            now = now.plus(duration)
        }

    }

}
