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
package com.reposilite.statistics

import com.reposilite.ReposiliteJournalist
import com.reposilite.console.CommandExecutor
import com.reposilite.console.StructuredRun
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.status.FailureFacade
import com.reposilite.statistics.infrastructure.InMemoryStatisticsRepository
import com.reposilite.token.AccessTokenFacade
import com.reposilite.token.AccessTokenSecurityProvider
import com.reposilite.token.ExportService
import com.reposilite.token.infrastructure.InMemoryAccessTokenRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.reactive.toMutableReference
import panda.std.reactive.toReference
import java.io.ByteArrayInputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

internal class StatsCommandTest {

    @Test
    fun `should read each record count once for the text and the structured data`() {
        // given: a statistics store that counts how often each record count is read
        val logger = InMemoryLogger()
        val repository = CountingStatisticsRepository(InMemoryStatisticsRepository())
        val statisticsFacade = StatisticsFacade(
            journalist = logger,
            statisticsEnabled = true.toMutableReference(),
            dateIntervalProvider = DailyDateIntervalProvider.toReference(),
            statisticsRepository = repository,
            accessTokenFacade = AccessTokenFacade(
                logger,
                InMemoryAccessTokenRepository(),
                InMemoryAccessTokenRepository(),
                ExportService(),
                bcryptStrength = AccessTokenSecurityProvider.MIN_BCRYPT_STRENGTH,
            ),
            clock = Clock.fixed(Instant.parse("2026-11-10T23:30:00Z"), ZoneOffset.UTC),
        )
        val executor = CommandExecutor(
            journalist = ReposiliteJournalist(logger, 0, testEnv = true, noColor = false),
            failureFacade = FailureFacade(logger, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)),
            source = ByteArrayInputStream(ByteArray(0)),
        )
        executor.registerCommand(StatsCommand(statisticsFacade))

        // when: the stats command runs in structured mode
        val answer = executor.prepare("stats").executeStructured(confirmed = false)

        // then: each count was read once, and the text and the data are built from that one read
        assertThat(answer).isInstanceOf(StructuredRun::class.java)
        assertThat(repository.uniqueReads)
            .`as`("reads of the unique record count by one stats call")
            .isEqualTo(1)
        assertThat(repository.allReads)
            .`as`("reads of the record count by one stats call")
            .isEqualTo(1)
    }

}

private class CountingStatisticsRepository(
    private val delegate: StatisticsRepository,
) : StatisticsRepository by delegate {

    var uniqueReads = 0
        private set

    var allReads = 0
        private set

    override fun countUniqueResolvedRequests(): Long {
        uniqueReads++
        return delegate.countUniqueResolvedRequests()
    }

    override fun countResolvedRequests(): Long {
        allReads++
        return delegate.countResolvedRequests()
    }

}
