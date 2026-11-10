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
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import java.time.Duration

internal class SqlScheduledJobLeasesTest {

    private val key = "cleanup:nightly:active"
    private val ttl = Duration.ofSeconds(60)

    @Test
    fun `grants a lease on a key that has no row yet`() {
        SqliteTestDatabase().use { shared ->
            val claims = SqlScheduledJobClaims(shared.database, MutableClock(), instanceId = "instance-a")

            assertThat(claims.lease(key, ttl)).`as`("lease of an absent key").isTrue()
        }
    }

    @Test
    fun `refuses a lease while another owner holds a live lease on the key`() {
        SqliteTestDatabase().use { shared ->
            val clock = MutableClock()
            assertThat(SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-a").lease(key, ttl)).isTrue()

            val second = SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-b").lease(key, ttl)

            assertThat(second).`as`("lease of a key with a live lease of another owner").isFalse()
        }
    }

    @Test
    fun `grants a lease after the expiry of the previous lease`() {
        SqliteTestDatabase().use { shared ->
            val clock = MutableClock()
            assertThat(SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-a").lease(key, ttl)).isTrue()

            clock.advance(ttl.plusSeconds(1))
            val takeover = SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-b").lease(key, ttl)

            assertThat(takeover).`as`("lease of a key whose previous lease expired").isTrue()
        }
    }

    @Test
    fun `refuses a lease on a key claimed by the previous release, so a mixed upgrade does not run a day twice`() {
        SqliteTestDatabase().use { shared ->
            val clock = MutableClock()
            val dayKey = "cleanup:nightly:2026-10-15"
            assertThat(SqlScheduledJobClaims(shared.database, clock, instanceId = "old-release").claim(dayKey)).isTrue()

            clock.advance(ttl.multipliedBy(10))
            val lease = SqlScheduledJobClaims(shared.database, clock, instanceId = "new-release").lease(dayKey, ttl)

            assertThat(lease).`as`("lease of a day claimed by a row without lease columns").isFalse()
        }
    }

    @Test
    fun `refuses a lease on a done key even after its former lease has expired`() {
        SqliteTestDatabase().use { shared ->
            val clock = MutableClock()
            val claims = SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-a")
            assertThat(claims.lease(key, ttl)).isTrue()
            claims.complete(key, done = true)

            clock.advance(Duration.ofDays(2))
            val response = SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-b").lease(key, ttl)

            assertThat(response).`as`("lease of a key marked done").isFalse()
        }
    }

    @Test
    fun `extends a live lease for its owner so that another owner is still refused after the former expiry`() {
        SqliteTestDatabase().use { shared ->
            val clock = MutableClock()
            val owner = SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-a")
            assertThat(owner.lease(key, ttl)).isTrue()

            clock.advance(ttl.minusSeconds(1))
            assertThat(owner.extend(key, ttl)).`as`("extend by the owner of a live lease").isTrue()
            clock.advance(ttl.minusSeconds(1))
            val other = SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-b").lease(key, ttl)

            assertThat(other).`as`("lease of a key whose owner extended it").isFalse()
        }
    }

    @Test
    fun `refuses an extension by an instance that does not own the lease`() {
        SqliteTestDatabase().use { shared ->
            val clock = MutableClock()
            assertThat(SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-a").lease(key, ttl)).isTrue()

            val extension = SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-b").extend(key, ttl)

            assertThat(extension).`as`("extend by an instance that is not the owner").isFalse()
        }
    }

    @Test
    fun `refuses an extension of a lease whose expiry has passed`() {
        SqliteTestDatabase().use { shared ->
            val clock = MutableClock()
            val owner = SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-a")
            assertThat(owner.lease(key, ttl)).isTrue()

            clock.advance(ttl.plusSeconds(1))

            assertThat(owner.extend(key, ttl)).`as`("extend of a lease that already expired").isFalse()
        }
    }

    @Test
    fun `leaves the lease of the owner unchanged when another instance completes it`() {
        SqliteTestDatabase().use { shared ->
            val clock = MutableClock()
            assertThat(SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-a").lease(key, ttl)).isTrue()

            SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-b").complete(key, done = false)
            val response = SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-c").lease(key, ttl)

            assertThat(response).`as`("lease of a key completed by a stale instance").isFalse()
        }
    }

    @Test
    fun `refuses later grants of a key that its owner completed as done`() {
        SqliteTestDatabase().use { shared ->
            val clock = MutableClock()
            val owner = SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-a")
            assertThat(owner.lease(key, ttl)).isTrue()

            owner.complete(key, done = true)
            val response = SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-b").lease(key, ttl)

            assertThat(response).`as`("lease of a key completed as done").isFalse()
        }
    }

    @Test
    fun `grants a lease again on a key that its owner released without the done flag`() {
        SqliteTestDatabase().use { shared ->
            val clock = MutableClock()
            val owner = SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-a")
            assertThat(owner.lease(key, ttl)).isTrue()

            owner.complete(key, done = false)
            val response = SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-b").lease(key, ttl)

            assertThat(response).`as`("lease of a key released without the done flag").isTrue()
        }
    }

    @Test
    fun `deletes a released row older than seven days`() {
        SqliteTestDatabase().use { shared ->
            val clock = MutableClock()
            val oldKey = "cleanup:nightly:active"
            SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-a").apply {
                lease(oldKey, ttl)
                complete(oldKey, done = false)
            }

            clock.advance(Duration.ofDays(8))
            SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-b").lease("audit-purge:2026-10-23", ttl)

            assertThat(keysInTable(shared)).`as`("rows after the retention of the released row").doesNotContain(oldKey)
        }
    }

    @Test
    fun `keeps a row whose lease is still live even when it is older than seven days`() {
        SqliteTestDatabase().use { shared ->
            val clock = MutableClock()
            val liveKey = "cleanup:nightly:active"
            SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-a").lease(liveKey, Duration.ofDays(10))

            clock.advance(Duration.ofDays(8))
            SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-b").lease("audit-purge:2026-10-23", ttl)

            assertThat(keysInTable(shared)).`as`("rows after the retention of a row with a live lease").contains(liveKey)
        }
    }

    private fun keysInTable(shared: SqliteTestDatabase): List<String> =
        transaction(shared.database) {
            ScheduledJobClaimTable.selectAll().map { it[ScheduledJobClaimTable.jobKey] }
        }

}
