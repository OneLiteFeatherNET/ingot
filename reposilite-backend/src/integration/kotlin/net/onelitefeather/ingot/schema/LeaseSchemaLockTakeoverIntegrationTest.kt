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

import com.reposilite.IsolatedResources
import com.reposilite.SharedContainers
import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import com.reposilite.databaseToken
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The lease on MariaDB with a real lock table. Time is injected: the waiter's pause moves the clock forward, so an expiry
 * of a minute passes without any wait. Each test gets its own database, dropped afterwards.
 */
internal class LeaseSchemaLockTakeoverIntegrationTest {

    private val start = Instant.parse("2026-11-10T10:00:00Z")

    @TempDir
    lateinit var workingDirectory: Path

    @Test
    fun `takes over a lease whose holder stopped renewing once it has expired`() {
        withLeaseTable { table, clock ->
            // given: a replica takes the lease and then stops, without renewing or releasing it
            LeaseSchemaLock.acquire(table, clock, owner = "crashed-replica", timeout = Duration.ofMinutes(1), pause = clock::advance)

            // when: another replica waits for the lock
            val replacement = LeaseSchemaLock.acquire(table, clock, owner = "replica-b", timeout = Duration.ofMinutes(5), pause = clock::advance)

            // then: it takes the lease the moment the default lifetime of sixty seconds has passed
            assertThat(clock.instant()).`as`("time of the takeover").isEqualTo(start.plusSeconds(61))
            replacement.release()
        }
    }

    @Test
    fun `does not take over the lease of a healthy holder that keeps renewing it`() {
        withLeaseTable { table, clock ->
            // given: a replica holds the lease and renews it every ten seconds while the waiter waits
            val healthy = LeaseSchemaLock.acquire(table, clock, owner = "healthy-replica", timeout = Duration.ofMinutes(1), pause = clock::advance)
            var lastRenewal = clock.instant()
            val renewingPause: (Duration) -> Unit = { duration ->
                clock.advance(duration)
                if (Duration.between(lastRenewal, clock.instant()) >= Duration.ofSeconds(10)) {
                    assertThat(healthy.renew()).`as`("renewal at ${clock.instant()}").isTrue
                    lastRenewal = clock.instant()
                }
            }

            // when: the waiter gives up after two minutes of injected time
            assertThrows<SchemaLockTimeoutException> {
                LeaseSchemaLock.acquire(table, clock, owner = "replica-b", timeout = Duration.ofMinutes(2), pause = renewingPause)
            }

            // then: the healthy holder still owns the lease, so it can still renew it
            assertThat(healthy.renew()).`as`("renewal after the wait").isTrue
            healthy.release()
        }
    }

    private fun withLeaseTable(block: (LeaseTable, ManualClock) -> Unit) {
        val database = IsolatedResources.createMariaDbDatabase(SharedContainers.mariaDb)
        try {
            DatabaseConnectionFactory.createConnection(workingDirectory, SharedContainers.mariaDb.databaseToken(database), 2).use { connection ->
                val table = ExposedLeaseTable(connection.database)
                table.createIfAbsent()
                block(table, ManualClock(start))
            }
        } finally {
            IsolatedResources.dropMariaDbDatabase(SharedContainers.mariaDb, database)
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
