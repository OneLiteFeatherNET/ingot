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

import com.reposilite.IsolatedResources
import com.reposilite.SharedContainers
import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import com.reposilite.databaseToken
import com.reposilite.shared.ErrorResponse
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.Result
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

private const val GUARD_SECONDS = 20L
private const val SERVICE_UNAVAILABLE = 503

/**
 * The metadata lock between instances that share one MariaDB database. Each instance is a [LockingMetadataLocks] of its own,
 * so it holds no JVM lock another instance can see: the only thing the instances share is the `ingot_lock` table.
 * Time is injected, and the waiter's pause moves the clock forward, so a lease of a minute passes without a wait.
 * Each test gets its own database, dropped afterwards.
 */
internal class MetadataLeaseIntegrationTest {

    private val start = Instant.parse("2026-11-10T10:00:00Z")
    private val artifact = "releases:com/example/app"
    private val otherArtifact = "releases:com/example/other"

    @TempDir
    lateinit var workingDirectory: Path

    @Test
    fun `refuses a write of another instance while the lease of the artifact is held`() {
        withDatabase { database, clock ->
            // given: one instance holds the lease of the artifact while it writes
            val holder = replica(database, clock, "replica-a")
            val holding = CountDownLatch(1)
            val finish = CountDownLatch(1)
            val held = AtomicReference<Result<Unit, ErrorResponse>>()
            val holderThread = thread {
                held.set(holder.withLock(artifact) {
                    holding.countDown()
                    check(finish.await(GUARD_SECONDS, SECONDS)) { "the holder was never released" }
                    Result.ok(Unit)
                })
            }
            assertThat(holding.await(GUARD_SECONDS, SECONDS)).`as`("the holder entered its lock").isTrue

            // when: another instance asks for the lock of the same artifact
            var ranOther = false
            val other = replica(database, clock, "replica-b").withLock(artifact) {
                ranOther = true
                Result.ok(Unit)
            }

            // then: it is refused with 503 and writes nothing, and the holder finishes its own write
            finish.countDown()
            holderThread.join(GUARD_SECONDS * 1000)
            assertThat(other.isErr).`as`("write of the other instance is refused").isTrue
            assertThat(other.error.status).`as`("status of the refused write").isEqualTo(SERVICE_UNAVAILABLE)
            assertThat(ranOther).`as`("write of the other instance ran under the lease of another instance").isFalse
            assertThat(held.get().isOk).`as`("write of the holder finished").isTrue
        }
    }

    @Test
    fun `takes over the lease of an instance that stopped after the lease expired`() {
        withDatabase { database, clock ->
            // given: a crashed instance took the lease of the artifact and never released it
            val crashed = metadataLeaseTables(database)(artifact)
            assertThat(crashed.tryAcquire("replica-crashed", clock.instant(), clock.instant().plus(LEASE)))
                .`as`("crashed instance took the lease")
                .isTrue

            // when: another instance writes the artifact after the lease has expired
            clock.advance(Duration.ofSeconds(61))
            val result = replica(database, clock, "replica-b").withLock(artifact) { Result.ok("written") }

            // then: the write runs under the taken over lease
            assertThat(result.isOk).`as`("write after the takeover").isTrue
            assertThat(clock.instant()).`as`("time of the takeover").isEqualTo(start.plusSeconds(61))
        }
    }

    @Test
    fun `does not wait for another instance that holds the lease of a different artifact`() {
        withDatabase { database, clock ->
            // given: one instance holds the lease of one artifact
            val holder = replica(database, clock, "replica-a")
            val holding = CountDownLatch(1)
            val finish = CountDownLatch(1)
            val holderThread = thread {
                holder.withLock(artifact) {
                    holding.countDown()
                    check(finish.await(GUARD_SECONDS, SECONDS)) { "the holder was never released" }
                    Result.ok(Unit)
                }
            }
            assertThat(holding.await(GUARD_SECONDS, SECONDS)).`as`("the holder entered its lock").isTrue

            // when: another instance writes another artifact of the same database
            val other = replica(database, clock, "replica-b").withLock(otherArtifact) { Result.ok("other") }

            // then: the write runs at once, with no time passed on the clock
            finish.countDown()
            holderThread.join(GUARD_SECONDS * 1000)
            assertThat(other.isOk).`as`("write of another artifact").isTrue
            assertThat(clock.instant()).`as`("time after the write of another artifact").isEqualTo(start)
        }
    }

    @Test
    fun `hands the lease to the next instance at once after the holder has released it`() {
        withDatabase { database, clock ->
            // given: one instance has finished its write of the artifact and released the lease
            assertThat(replica(database, clock, "replica-a").withLock(artifact) { Result.ok(Unit) }.isOk)
                .`as`("first write")
                .isTrue

            // when: the next instance writes the artifact
            val result = replica(database, clock, "replica-b").withLock(artifact) { Result.ok("second") }

            // then: it runs without waiting for the lease to expire
            assertThat(result.isOk).`as`("second write").isTrue
            assertThat(clock.instant()).`as`("time of the second write").isEqualTo(start)
        }
    }

    private fun replica(database: Database, clock: ManualClock, owner: String): MetadataLocks =
        LockingMetadataLocks(
            clock = clock,
            leaseTables = metadataLeaseTables(database),
            owner = owner,
            pause = clock::advance,
        )

    private fun withDatabase(block: (Database, ManualClock) -> Unit) {
        val database = IsolatedResources.createMariaDbDatabase(SharedContainers.mariaDb)
        try {
            DatabaseConnectionFactory.createConnection(workingDirectory, SharedContainers.mariaDb.databaseToken(database), 4).use { connection ->
                block(connection.database, ManualClock(start))
            }
        } finally {
            IsolatedResources.dropMariaDbDatabase(SharedContainers.mariaDb, database)
        }
    }

    private companion object {
        val LEASE: Duration = Duration.ofSeconds(60)
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
