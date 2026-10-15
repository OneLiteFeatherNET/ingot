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
import com.reposilite.scheduling.runAtOnce
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration

internal class SqlScheduledJobClaimsTest {

    private val clock = MutableClock()
    private val key = "cleanup:nightly:2026-10-15"

    @Test
    fun `grants a key to exactly one of two instances that share the database`() {
        SqliteTestDatabase().use { shared ->
            val first = SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-a")
            val second = SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-b")

            assertThat(first.claim(key)).`as`("first claim").isTrue()
            assertThat(second.claim(key)).`as`("second claim of the same key").isFalse()
        }
    }

    @Test
    fun `grants the key of the next day to an instance after the previous day`() {
        SqliteTestDatabase().use { shared ->
            val claims = SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-a")
            assertThat(claims.claim(key)).isTrue()

            val nextDayKey = "cleanup:nightly:2026-10-16"

            assertThat(claims.claim(nextDayKey)).`as`("claim of the next day").isTrue()
        }
    }

    @Test
    fun `grants a key again once its claim is older than seven days`() {
        SqliteTestDatabase().use { shared ->
            SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-a").claim(key)
            clock.advance(Duration.ofDays(8))

            val response = SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-b").claim(key)

            assertThat(response).`as`("claim after the retention of the claim").isTrue()
        }
    }

    @Test
    fun `keeps a claim that is younger than seven days`() {
        SqliteTestDatabase().use { shared ->
            SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-a").claim(key)
            clock.advance(Duration.ofDays(6))

            val response = SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-b").claim(key)

            assertThat(response).`as`("claim while the claim is still held").isFalse()
        }
    }

    @Test
    fun `grants a key to exactly one of several instances that claim it at the same instant`() {
        SqliteTestDatabase().use { shared ->
            val claims = (1..8).map { SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-$it") }

            val granted = runAtOnce(claims.map { instance -> { instance.claim(key) } })

            assertThat(granted.count { it }).`as`("granted claims of one key").isEqualTo(1)
        }
    }

}
