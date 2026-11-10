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

internal class SqlScheduledJobClaimsTakeoverTest {

    private val key = "cleanup:nightly:2026-10-15"
    private val ttl = Duration.ofSeconds(60)

    @Test
    fun `takes over a daily key whose claimant stopped before its run ended`() {
        SqliteTestDatabase().use { shared ->
            // given: instance A leases the day's key and then stops without finishing the run
            val clock = MutableClock()
            assertThat(SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-a").lease(key, ttl)).isTrue()

            // when: instance B ticks later the same day
            clock.advance(Duration.ofHours(1))
            val takeover = SqlScheduledJobClaims(shared.database, clock, instanceId = "instance-b").lease(key, ttl)

            // then: instance B may take the key over, otherwise the day's run is lost until the next day
            assertThat(takeover)
                .`as`("takeover of the day's key after its claimant stopped, but the key stays claimed for the whole day")
                .isTrue()
        }
    }

}
