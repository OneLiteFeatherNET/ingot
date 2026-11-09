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

import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.maven.api.Identifier
import com.reposilite.statistics.api.IncrementResolvedRequest
import com.reposilite.statistics.api.ResolvedEntry
import com.reposilite.token.AccessTokenFacade
import com.reposilite.token.AccessTokenSecurityProvider
import com.reposilite.token.ExportService
import com.reposilite.token.infrastructure.InMemoryAccessTokenRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.reactive.toMutableReference
import panda.std.reactive.toReference
import java.sql.SQLException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

internal class StatisticsFlushTest {

    private val logger = InMemoryLogger()
    private val repository = ScriptedStatisticsRepository()
    private val statisticsEnabled = true.toMutableReference()
    private val clock = Clock.fixed(Instant.parse("2026-11-10T23:30:00Z"), ZoneOffset.UTC)
    private val statisticsFacade = StatisticsFacade(
        journalist = logger,
        statisticsEnabled = statisticsEnabled,
        dateIntervalProvider = DailyDateIntervalProvider.toReference(),
        statisticsRepository = repository,
        accessTokenFacade = AccessTokenFacade(
            logger,
            InMemoryAccessTokenRepository(),
            InMemoryAccessTokenRepository(),
            ExportService(),
            bcryptStrength = AccessTokenSecurityProvider.MIN_BCRYPT_STRENGTH,
        ),
        clock = clock
    )

    private val identifier = Identifier("releases", "com/example/a")

    @Test
    fun `should take the bucket date of a flush from the injected clock`() {
        // given: a request counted while the injected clock reads 2026-11-10 23:30 UTC
        statisticsFacade.incrementResolvedRequest(IncrementResolvedRequest(identifier, 1))

        // when: the flush runs
        statisticsFacade.saveRecordsBulk()

        // then: the count is stored under the date of the injected clock, without reading the system time
        assertThat(repository.dates)
            .describedAs("the bucket date must come from the injected clock")
            .containsExactly(LocalDate.of(2026, 11, 10))
    }

    @Test
    fun `should write buffered counts when statistics is switched off after counting`() {
        // given: three requests counted while statistics was on, and statistics is switched off afterwards
        statisticsFacade.incrementResolvedRequest(IncrementResolvedRequest(identifier, 3))
        statisticsEnabled.update(false)

        // when: the shutdown flush runs
        statisticsFacade.saveRecordsBulk()

        // then: the buffered counts are written anyway
        assertThat(repository.written[identifier])
            .describedAs("counts buffered before statistics was switched off must still be written on shutdown")
            .isEqualTo(3L)
    }

    @Test
    fun `should keep a count that arrives while a flush is writing`() {
        // given: two requests buffered, and a flush that blocks inside the repository write
        statisticsFacade.incrementResolvedRequest(IncrementResolvedRequest(identifier, 2))
        val writeStarted = CountDownLatch(1)
        val releaseWrite = CountDownLatch(1)
        repository.onWrite = { call ->
            if (call == 1) {
                writeStarted.countDown()
                releaseWrite.await(TIMEOUT_SECONDS, SECONDS)
            }
        }
        val flush = thread { statisticsFacade.saveRecordsBulk() }
        assertThat(writeStarted.await(TIMEOUT_SECONDS, SECONDS))
            .describedAs("the flush must reach the repository write")
            .isTrue()

        // when: five more requests arrive while the write is still running, and the write then completes
        statisticsFacade.incrementResolvedRequest(IncrementResolvedRequest(identifier, 5))
        releaseWrite.countDown()
        joinOrFail(flush)

        // then: the running flush wrote only the two counts it took
        assertThat(repository.written[identifier])
            .describedAs("the running flush must write only the counts it took")
            .isEqualTo(2L)

        // when: the next flush runs
        statisticsFacade.saveRecordsBulk()

        // then: the requests that arrived during the earlier flush are written once
        assertThat(repository.written[identifier])
            .describedAs("a request that arrived during a flush must be written by a later flush")
            .isEqualTo(7L)
    }

    @Test
    fun `should write the counts of a failed flush once on the next flush`() {
        // given: three requests buffered, and a write that fails with an error that is not a deadlock
        statisticsFacade.incrementResolvedRequest(IncrementResolvedRequest(identifier, 3))
        repository.onWrite = { call ->
            if (call == 1) throw IllegalStateException("database is locked")
        }

        // when: the flush runs and fails
        statisticsFacade.saveRecordsBulk()

        // then: nothing is written, and the failure is not retried
        assertThat(repository.written)
            .describedAs("a failed flush must not write any count")
            .isEmpty()
        assertThat(repository.calls.get())
            .describedAs("a failure that is not a deadlock must not be retried")
            .isEqualTo(1)

        // when: the next flush succeeds, and a flush after it runs
        statisticsFacade.saveRecordsBulk()
        statisticsFacade.saveRecordsBulk()

        // then: the counts of the failed flush are written exactly once
        assertThat(repository.written[identifier])
            .describedAs("the counts of the failed flush must be written once by the next flush")
            .isEqualTo(3L)
    }

    @Test
    fun `should write each count once when two flushes run at the same time`() {
        // given: two requests buffered, and a first flush that blocks inside the repository write
        statisticsFacade.incrementResolvedRequest(IncrementResolvedRequest(identifier, 2))
        val writeStarted = CountDownLatch(1)
        val releaseWrite = CountDownLatch(1)
        repository.onWrite = { call ->
            if (call == 1) {
                writeStarted.countDown()
                releaseWrite.await(TIMEOUT_SECONDS, SECONDS)
            }
        }
        val first = thread { statisticsFacade.saveRecordsBulk() }
        assertThat(writeStarted.await(TIMEOUT_SECONDS, SECONDS))
            .describedAs("the first flush must reach the repository write")
            .isTrue()

        // when: a second flush starts while the first one is still writing, and the first one completes
        val second = thread { statisticsFacade.saveRecordsBulk() }
        awaitBlockedOrFinished(second)
        releaseWrite.countDown()
        joinOrFail(first)
        joinOrFail(second)

        // then: the counts were written once, by the flush that took them
        assertThat(repository.written[identifier])
            .describedAs("two flushes running at the same time must not write the same counts twice")
            .isEqualTo(2L)
    }

    @Test
    fun `should retry a deadlock on the first attempt and write each count once`() {
        // given: three requests buffered, and a first write that fails with SQLState 40001
        statisticsFacade.incrementResolvedRequest(IncrementResolvedRequest(identifier, 3))
        repository.onWrite = { call ->
            if (call == 1) throw SQLException("Deadlock found when trying to get lock", "40001")
        }

        // when: the flush runs
        statisticsFacade.saveRecordsBulk()

        // then: the write is retried and the counts are written once
        assertThat(repository.calls.get())
            .describedAs("a deadlock on the first attempt must be retried once")
            .isEqualTo(2)
        assertThat(repository.written[identifier])
            .describedAs("the retried write must store each count once")
            .isEqualTo(3L)

        // when: another flush runs
        statisticsFacade.saveRecordsBulk()

        // then: the counts are not written again
        assertThat(repository.written[identifier])
            .describedAs("a count written by a retried flush must not be written again")
            .isEqualTo(3L)
    }

    @Test
    fun `should retry a deadlock that is wrapped in another exception`() {
        // given: three requests buffered, and a first write that fails with a wrapped SQLState 40001
        statisticsFacade.incrementResolvedRequest(IncrementResolvedRequest(identifier, 3))
        repository.onWrite = { call ->
            if (call == 1) throw RuntimeException("batch failed", SQLException("Galera certification failed", "40001"))
        }

        // when: the flush runs
        statisticsFacade.saveRecordsBulk()

        // then: the wrapped deadlock is retried and the counts are written once
        assertThat(repository.calls.get())
            .describedAs("a wrapped deadlock must be retried")
            .isEqualTo(2)
        assertThat(repository.written[identifier])
            .describedAs("the retried write must store each count once")
            .isEqualTo(3L)
    }

    @Test
    fun `should keep the counts when a deadlock persists after three retries`() {
        // given: three requests buffered, and a write that keeps failing with SQLState 40001
        statisticsFacade.incrementResolvedRequest(IncrementResolvedRequest(identifier, 3))
        repository.onWrite = { throw SQLException("Deadlock found", "40001") }

        // when: the flush runs
        statisticsFacade.saveRecordsBulk()

        // then: the first attempt and three retries were made, and nothing is written
        assertThat(repository.calls.get())
            .describedAs("a deadlock is retried three times after the first attempt")
            .isEqualTo(4)
        assertThat(repository.written)
            .describedAs("a flush that gave up must not write any count")
            .isEmpty()

        // when: the database recovers and the next flush runs
        repository.onWrite = {}
        statisticsFacade.saveRecordsBulk()

        // then: the counts are written once
        assertThat(repository.written[identifier])
            .describedAs("the counts kept after a give-up must be written by the next flush")
            .isEqualTo(3L)
    }

    @Test
    fun `should log a failed shutdown write without throwing`() {
        // given: three requests buffered, and a database that refuses every write
        statisticsFacade.incrementResolvedRequest(IncrementResolvedRequest(identifier, 3))
        repository.onWrite = { throw IllegalStateException("connection refused") }

        // when: the flush runs
        statisticsFacade.saveRecordsBulk()

        // then: the failure is logged, and the flush returned normally
        assertThat(logger.messages.any { "Cannot save" in it.value })
            .describedAs("a failed write must be logged through the journalist")
            .isTrue()
    }

    private fun awaitBlockedOrFinished(thread: Thread) {
        var spins = 0
        while (thread.isAlive && thread.state != Thread.State.BLOCKED) {
            check(++spins < MAX_SPINS) { "thread neither blocked on a monitor nor finished" }
            Thread.onSpinWait()
        }
    }

    private fun joinOrFail(thread: Thread) {
        thread.join(TIMEOUT_SECONDS * 1000)
        assertThat(thread.isAlive)
            .describedAs("the thread must finish within $TIMEOUT_SECONDS seconds")
            .isFalse()
    }

    private companion object {
        const val TIMEOUT_SECONDS = 10L
        const val MAX_SPINS = 50_000_000
    }

}

/**
 * A repository that counts its calls and stores the counts of every successful write. [onWrite] runs before each write
 * with the 1-based number of the call and may block or throw, which makes the failure and the concurrency cases repeatable.
 */
private class ScriptedStatisticsRepository : StatisticsRepository {

    val written = ConcurrentHashMap<Identifier, Long>()
    val dates = CopyOnWriteArrayList<LocalDate>()
    val calls = AtomicInteger()

    @Volatile
    var onWrite: (Int) -> Unit = {}

    override fun incrementResolvedRequests(requests: Map<Identifier, Long>, date: LocalDate) {
        onWrite(calls.incrementAndGet())
        dates.add(date)
        requests.forEach { (identifier, count) -> written.merge(identifier, count) { a, b -> a + b } }
    }

    override fun findResolvedRequestsByPhrase(
        repository: String,
        phrase: String,
        limit: Int,
        accessibleGavPrefixes: Set<String>?
    ): List<ResolvedEntry> = emptyList()

    override fun getAllResolvedRequestsPerRepositoryAsTimeSeries(): Map<String, Map<LocalDate, Long>> = emptyMap()

    override fun countUniqueResolvedRequests(): Long = written.size.toLong()

    override fun countResolvedRequests(): Long = written.values.sum()

}
