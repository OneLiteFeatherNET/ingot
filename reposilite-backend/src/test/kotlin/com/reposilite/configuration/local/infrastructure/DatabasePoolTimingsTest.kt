/*
 * Copyright (c) 2023 dzikoysk
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

package com.reposilite.configuration.local.infrastructure

import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory.hikariConfig
import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory.hikariTimings
import com.zaxxer.hikari.HikariConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class DatabasePoolTimingsTest {

    @Test
    fun `should map the default seconds to the default milliseconds of the pool`() {
        // given: the defaults of the three configuration keys

        // when: the seconds are mapped to the pool settings
        val timings = hikariTimings(maxLifetimeSeconds = 1800, connectionTimeoutSeconds = 30, keepaliveSeconds = 120)

        // then: the milliseconds are 1800000, 30000 and 120000
        assertThat(timings).`as`("default timings").isEqualTo(HikariTimings(maxLifetime = 1_800_000L, connectionTimeout = 30_000L, keepaliveTime = 120_000L))
    }

    @Test
    fun `should map whole seconds to milliseconds`() {
        // given: whole-number values that differ from the defaults

        // when: the seconds are mapped to the pool settings
        val timings = hikariTimings(maxLifetimeSeconds = 60, connectionTimeoutSeconds = 5, keepaliveSeconds = 45)

        // then: each value is multiplied by one thousand
        assertThat(timings).`as`("timings in milliseconds").isEqualTo(HikariTimings(maxLifetime = 60_000L, connectionTimeout = 5_000L, keepaliveTime = 45_000L))
    }

    @Test
    fun `should map zero keepalive to zero, which keeps the keepalive off`() {
        // given: a keepalive of zero seconds

        // when: it is mapped to the pool settings
        val timings = hikariTimings(maxLifetimeSeconds = 1800, connectionTimeoutSeconds = 30, keepaliveSeconds = 0)

        // then: the pool receives zero, the value Hikari reads as off
        assertThat(timings.keepaliveTime).`as`("keepalive time in milliseconds").isZero()
    }

    @Test
    fun `should keep the defaults equal to the values the pool uses before the keys existed`() {
        // given: a pool configuration without any of the keys

        // when: the pool reports its own timings
        val pool = HikariConfig()

        // then: the configuration keys default to exactly these values, so an installation without the keys does not change
        assertThat(pool.maxLifetime).`as`("Hikari default maxLifetime").isEqualTo(1_800_000L)
        assertThat(pool.connectionTimeout).`as`("Hikari default connectionTimeout").isEqualTo(30_000L)
        assertThat(pool.keepaliveTime).`as`("Hikari default keepaliveTime").isEqualTo(120_000L)
    }

    @Test
    fun `should apply the timings to a postgresql pool`() {
        // given: a postgresql URL and whole-second timings

        // when: the pool configuration is built
        val pool = hikariConfig("jdbc:postgresql://localhost:5432/ingot", 5, hikariTimings(60, 5, 45))

        // then: the pool size and the three timings are set
        assertThat(pool.maximumPoolSize).`as`("maximum pool size").isEqualTo(5)
        assertThat(pool.maxLifetime).`as`("maxLifetime").isEqualTo(60_000L)
        assertThat(pool.connectionTimeout).`as`("connectionTimeout").isEqualTo(5_000L)
        assertThat(pool.keepaliveTime).`as`("keepaliveTime").isEqualTo(45_000L)
    }

    @Test
    fun `should apply the timings to a mariadb pool`() {
        // given: a mariadb URL and whole-second timings

        // when: the pool configuration is built
        val pool = hikariConfig("jdbc:mariadb://localhost:3306/ingot", 3, hikariTimings(60, 5, 45))

        // then: the three timings are set
        assertThat(pool.maxLifetime).`as`("maxLifetime").isEqualTo(60_000L)
        assertThat(pool.keepaliveTime).`as`("keepaliveTime").isEqualTo(45_000L)
    }

    @Test
    fun `should apply the timings to a custom jdbc url`() {
        // given: a custom JDBC URL and whole-second timings

        // when: the pool configuration is built
        val pool = hikariConfig("jdbc:postgresql://localhost:5432/ingot?sslmode=require", 2, hikariTimings(60, 5, 45))

        // then: the timings are set as for the standard server URLs
        assertThat(pool.connectionTimeout).`as`("connectionTimeout").isEqualTo(5_000L)
        assertThat(pool.keepaliveTime).`as`("keepaliveTime").isEqualTo(45_000L)
    }

    @Test
    fun `should ignore the timings for sqlite`() {
        // given: an embedded SQLite URL and timings that differ from the defaults

        // when: the pool configuration is built
        val pool = hikariConfig("jdbc:sqlite:/work/reposilite.db", 1, hikariTimings(60, 5, 45))

        // then: the pool keeps its own timings, so the single SQLite connection behaves as before
        assertThat(pool.maxLifetime).`as`("maxLifetime").isEqualTo(1_800_000L)
        assertThat(pool.connectionTimeout).`as`("connectionTimeout").isEqualTo(30_000L)
        assertThat(pool.keepaliveTime).`as`("keepaliveTime").isEqualTo(120_000L)
    }

    @Test
    fun `should ignore the timings for h2`() {
        // given: an embedded H2 URL and timings that differ from the defaults

        // when: the pool configuration is built
        val pool = hikariConfig("jdbc:h2:/work/reposilite;MODE=MYSQL", 1, hikariTimings(60, 5, 45))

        // then: the pool keeps its own timings
        assertThat(pool.maxLifetime).`as`("maxLifetime").isEqualTo(1_800_000L)
        assertThat(pool.keepaliveTime).`as`("keepaliveTime").isEqualTo(120_000L)
    }

    @Test
    fun `should keep the pool timings when none are given`() {
        // given: a server URL without timings

        // when: the pool configuration is built
        val pool = hikariConfig("jdbc:postgresql://localhost:5432/ingot", 1, null)

        // then: the pool keeps its own timings
        assertThat(pool.keepaliveTime).`as`("keepaliveTime").isEqualTo(120_000L)
    }

}
