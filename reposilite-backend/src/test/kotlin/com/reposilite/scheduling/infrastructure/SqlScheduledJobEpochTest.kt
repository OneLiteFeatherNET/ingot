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

package com.reposilite.scheduling.infrastructure

import com.reposilite.audit.MutableClock
import com.reposilite.scheduling.SqliteTestDatabase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * The fencing token of a lease: every grant of a key gets a larger epoch, and renewal or release needs the epoch of the
 * grant the caller holds. Each test builds its own database and clock.
 */
internal class SqlScheduledJobEpochTest {

    private val key = "cleanup:nightly:active"
    private val ttl = Duration.ofSeconds(60)

    @Test
    fun `grants the first lease of a key with epoch one`() {
        SqliteTestDatabase().use { shared ->
            val claims = SqlScheduledJobClaims(shared.database, MutableClock(), instanceId = "instance-a")

            val lease = claims.acquire(key, ttl)

            assertThat(lease?.epoch).`as`("epoch of the first grant").isEqualTo(1L)
        }
    }

    @Test
    fun `refuses an acquire while another owner holds a live lease`() {
        SqliteTestDatabase().use { shared ->
            val clock = MutableClock()
            assertThat(SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-a").acquire(key, ttl)).isNotNull()

            val second = SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-b").acquire(key, ttl)

            assertThat(second).`as`("acquire of a key with a live lease").isNull()
        }
    }

    @Test
    fun `refuses an expired takeover of a key that no instance started`() {
        SqliteTestDatabase().use { shared ->
            val takeover = SqlScheduledJobClaims(shared.database, MutableClock(), instanceId = "instance-a").acquireExpired(key, ttl)

            assertThat(takeover).`as`("takeover of a key without a row").isNull()
        }
    }

    @Test
    fun `takes over an expired lease with a higher epoch`() {
        SqliteTestDatabase().use { shared ->
            val clock = MutableClock()
            SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-a").acquire(key, ttl)
            clock.advance(ttl.plusSeconds(1))

            val takeover = SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-b").acquireExpired(key, ttl)

            assertThat(takeover?.epoch).`as`("epoch of the takeover of an expired lease").isEqualTo(2L)
        }
    }

    @Test
    fun `grants a higher epoch to the next owner after the expiry`() {
        SqliteTestDatabase().use { shared ->
            val clock = MutableClock()
            val first = SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-a").acquire(key, ttl)
            clock.advance(ttl.plusSeconds(1))

            val takeover = SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-b").acquire(key, ttl)

            assertThat(first?.epoch).`as`("epoch of the first grant").isEqualTo(1L)
            assertThat(takeover?.epoch).`as`("epoch of the takeover").isEqualTo(2L)
        }
    }

    @Test
    fun `refuses to renew with the epoch of a grant that the same owner has superseded`() {
        SqliteTestDatabase().use { shared ->
            val clock = MutableClock()
            val claims = SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-a")
            val stale = claims.acquire(key, ttl)!!
            clock.advance(ttl.plusSeconds(1))
            val regranted = claims.acquire(key, ttl)!!

            assertThat(claims.renew(stale, ttl)).`as`("renewal with the superseded epoch").isFalse()
            assertThat(claims.renew(regranted, ttl)).`as`("renewal with the current epoch").isTrue()
        }
    }

    @Test
    fun `renews a live lease so that other owners stay refused`() {
        SqliteTestDatabase().use { shared ->
            val clock = MutableClock()
            val owner = SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-a")
            val lease = owner.acquire(key, ttl)!!
            clock.advance(Duration.ofSeconds(30))

            assertThat(owner.renew(lease, ttl)).`as`("renewal of a live lease").isTrue()

            clock.advance(Duration.ofSeconds(45))
            val other = SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-b").acquire(key, ttl)
            assertThat(other).`as`("acquire after the renewed lease would have expired without renewal").isNull()
        }
    }

    @Test
    fun `refuses to renew a lease after its expiry`() {
        SqliteTestDatabase().use { shared ->
            val clock = MutableClock()
            val owner = SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-a")
            val lease = owner.acquire(key, ttl)!!
            clock.advance(ttl.plusSeconds(1))

            assertThat(owner.renew(lease, ttl)).`as`("renewal of an expired lease").isFalse()
        }
    }

    @Test
    fun `a superseded holder releases nothing of the new grant`() {
        SqliteTestDatabase().use { shared ->
            val clock = MutableClock()
            val stale = SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-a").acquire(key, ttl)!!
            clock.advance(ttl.plusSeconds(1))
            SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-b").acquire(key, ttl)!!

            SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-a").release(stale, done = false)

            val third = SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-c").acquire(key, ttl)
            assertThat(third).`as`("acquire while the new grant is live").isNull()
        }
    }

    @Test
    fun `releasing the current lease makes the key grantable again with a higher epoch`() {
        SqliteTestDatabase().use { shared ->
            val clock = MutableClock()
            val owner = SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-a")
            owner.release(owner.acquire(key, ttl)!!, done = false)

            val next = SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-b").acquire(key, ttl)

            assertThat(next?.epoch).`as`("epoch after a release").isEqualTo(2L)
        }
    }

    @Test
    fun `a key released as done is never granted again`() {
        SqliteTestDatabase().use { shared ->
            val clock = MutableClock()
            val owner = SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-a")
            owner.release(owner.acquire(key, ttl)!!, done = true)
            clock.advance(Duration.ofDays(1))

            val later = SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-b").acquire(key, ttl)

            assertThat(later).`as`("acquire of a key completed as done").isNull()
        }
    }

}
