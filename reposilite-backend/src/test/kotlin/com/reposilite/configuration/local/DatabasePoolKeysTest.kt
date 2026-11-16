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

package com.reposilite.configuration.local

import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class DatabasePoolKeysTest {

    @Test
    fun `should default the pool keys to the values the pool uses today`() {
        // given: a default local configuration

        // when: no external property is applied

        // then: lifetime 1800 seconds, connection timeout 30 seconds and keepalive 120 seconds, as the pool has them now
        val configuration = LocalConfiguration()
        assertThat(configuration.databaseMaxLifetime.get()).`as`("databaseMaxLifetime").isEqualTo(1800)
        assertThat(configuration.databaseConnectionTimeout.get()).`as`("databaseConnectionTimeout").isEqualTo(30)
        assertThat(configuration.databaseKeepalive.get()).`as`("databaseKeepalive").isEqualTo(120)
    }

    @Test
    fun `should read the max lifetime from the ingot prefix`() {
        // given: a default local configuration
        val localConfiguration = LocalConfiguration()

        // when: the ingot variable is set
        LocalConfigurationFactory.applyCustomProperties(
            journalist = null,
            localConfiguration = localConfiguration,
            properties = LocalConfigurationFactory.getEnvironmentVariables(mapOf("INGOT_LOCAL_DATABASEMAXLIFETIME" to "900")),
        )

        // then: the value is read in seconds
        assertThat(localConfiguration.databaseMaxLifetime.get()).isEqualTo(900)
    }

    @Test
    fun `should read the max lifetime from the legacy reposilite prefix`() {
        // given: a default local configuration
        val localConfiguration = LocalConfiguration()

        // when: only the legacy variable is set
        LocalConfigurationFactory.applyCustomProperties(
            journalist = null,
            localConfiguration = localConfiguration,
            properties = LocalConfigurationFactory.getEnvironmentVariables(mapOf("REPOSILITE_LOCAL_DATABASEMAXLIFETIME" to "600")),
        )

        // then: the legacy value is honoured
        assertThat(localConfiguration.databaseMaxLifetime.get()).isEqualTo(600)
    }

    @Test
    fun `should prefer the ingot max lifetime over the legacy one`() {
        // given: a default local configuration
        val localConfiguration = LocalConfiguration()

        // when: both variables are set
        LocalConfigurationFactory.applyCustomProperties(
            journalist = null,
            localConfiguration = localConfiguration,
            properties = LocalConfigurationFactory.getEnvironmentVariables(
                mapOf(
                    "INGOT_LOCAL_DATABASEMAXLIFETIME" to "900",
                    "REPOSILITE_LOCAL_DATABASEMAXLIFETIME" to "600",
                )
            ),
        )

        // then: the ingot value wins
        assertThat(localConfiguration.databaseMaxLifetime.get()).isEqualTo(900)
    }

    @Test
    fun `should set the maximum pool size from the ingot database thread pool key`() {
        // given: a default local configuration
        val localConfiguration = LocalConfiguration()

        // when: the ingot variable sets the pool size
        LocalConfigurationFactory.applyCustomProperties(
            journalist = null,
            localConfiguration = localConfiguration,
            properties = LocalConfigurationFactory.getEnvironmentVariables(mapOf("INGOT_LOCAL_DATABASETHREADPOOL" to "10")),
        )

        // then: the pool that the server builds has that maximum pool size
        val pool = DatabaseConnectionFactory.hikariConfig("jdbc:postgresql://localhost:5432/ingot", localConfiguration.databaseThreadPool.get())
        assertThat(pool.maximumPoolSize).isEqualTo(10)
    }

    @Test
    fun `should set the maximum pool size from the legacy database thread pool key`() {
        // given: a default local configuration
        val localConfiguration = LocalConfiguration()

        // when: only the legacy variable sets the pool size
        LocalConfigurationFactory.applyCustomProperties(
            journalist = null,
            localConfiguration = localConfiguration,
            properties = LocalConfigurationFactory.getEnvironmentVariables(mapOf("REPOSILITE_LOCAL_DATABASETHREADPOOL" to "4")),
        )

        // then: the legacy value is the maximum pool size
        val pool = DatabaseConnectionFactory.hikariConfig("jdbc:postgresql://localhost:5432/ingot", localConfiguration.databaseThreadPool.get())
        assertThat(pool.maximumPoolSize).isEqualTo(4)
    }

}
