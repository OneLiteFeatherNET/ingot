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

package com.reposilite.status

import com.reposilite.journalist.Channel
import com.reposilite.journalist.Logger
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.status.specification.FailureSpecification
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

internal class FailureFacadeTest : FailureSpecification() {

    @Test
    fun `should store failure`() {
        // given: an exception with a message
        val message = "Unlucky"
        val exception = RuntimeException(message)

        // when: an error has been registered in failure facade
        failureFacade.throwException("PATH /com/reposilite", exception)

        // then: service properly registered thrown exception
        assertThat(failureFacade.hasFailures()).isTrue
        assertThat(failureFacade.getFailures().iterator().next().contains(message)).isTrue
    }

    @Test
    fun `should timestamp a failure with the injected clock`() {
        // given: a clock frozen at a known instant
        val facade = FailureFacade(InMemoryLogger(), MutableClock(Instant.parse("2026-03-01T10:15:30Z")))

        // when: a failure is recorded
        facade.throwException("PATH /com/reposilite", RuntimeException("boom"))

        // then: the record carries the instant reported by the clock
        assertThat(facade.getFailureRecords().single().timestamp).isEqualTo(Instant.parse("2026-03-01T10:15:30Z"))
    }

    @Test
    fun `should describe a failure by its source, exception type and message`() {
        // given: a failure reported for a request
        val facade = FailureFacade(InMemoryLogger(), MutableClock(Instant.EPOCH))

        // when: the failure is recorded
        facade.throwException("/api/maven/details", IllegalStateException("repository is broken"))

        // then: the record names the source, the exception class and its message
        val record = facade.getFailureRecords().single()
        assertThat(record.source).isEqualTo("/api/maven/details")
        assertThat(record.type).isEqualTo("IllegalStateException")
        assertThat(record.message).isEqualTo("repository is broken")
    }

    @Test
    fun `should include the stacktrace of the failure`() {
        // given: a facade and an exception raised with a stacktrace
        val facade = FailureFacade(InMemoryLogger(), MutableClock(Instant.EPOCH))

        // when: the failure is recorded
        facade.throwException("/api/maven/details", RuntimeException("boom"))

        // then: the stacktrace lists the frames of the exception
        assertThat(facade.getFailureRecords().single().stacktrace).contains("  at ")
    }

    @Test
    fun `should aggregate identical failures into a single record with a count`() {
        // given: a facade and an identical failure
        val facade = FailureFacade(InMemoryLogger(), MutableClock(Instant.EPOCH))
        val exception = RuntimeException("same")

        // when: the failure is recorded three times
        repeat(3) { facade.throwException("/com/reposilite", exception) }

        // then: one record remains and counts every occurrence
        assertThat(facade.getFailureRecords()).hasSize(1)
        assertThat(facade.getFailureRecords().single().count).isEqualTo(3)
    }

    @Test
    fun `should list failures newest first`() {
        // given: two failures recorded at different instants
        val clock = MutableClock(Instant.parse("2026-03-01T10:00:00Z"))
        val facade = FailureFacade(InMemoryLogger(), clock)
        facade.throwException("first", RuntimeException("first"))
        clock.current = Instant.parse("2026-03-01T11:00:00Z")
        facade.throwException("second", RuntimeException("second"))

        // when: the records are requested
        val sources = facade.getFailureRecords().map { it.source }

        // then: the most recent failure comes first
        assertThat(sources).containsExactly("second", "first")
    }

    @Test
    fun `should move a recurring failure to the top with the time of its latest occurrence`() {
        // given: an old failure followed by a newer one
        val clock = MutableClock(Instant.parse("2026-03-01T10:00:00Z"))
        val facade = FailureFacade(InMemoryLogger(), clock)
        val recurring = RuntimeException("recurring")
        facade.throwException("recurring", recurring)
        clock.current = Instant.parse("2026-03-01T11:00:00Z")
        facade.throwException("other", RuntimeException("other"))

        // when: the old failure happens again
        clock.current = Instant.parse("2026-03-01T12:00:00Z")
        facade.throwException("recurring", recurring)

        // then: it is listed first, with the time of its latest occurrence and the total count
        val top = facade.getFailureRecords().first()
        assertThat(top.source).isEqualTo("recurring")
        assertThat(top.timestamp).isEqualTo(Instant.parse("2026-03-01T12:00:00Z"))
        assertThat(top.count).isEqualTo(2)
    }

    @Test
    fun `should remove every recorded failure when cleared`() {
        // given: a facade holding a failure
        val facade = FailureFacade(InMemoryLogger(), MutableClock(Instant.EPOCH))
        facade.throwException("/com/reposilite", RuntimeException("boom"))

        // when: the failures are cleared
        facade.clearFailures()

        // then: nothing is left to report
        assertThat(facade.hasFailures()).isFalse
        assertThat(facade.getFailureRecords()).isEmpty()
    }

    @Test
    fun `should keep the failure recorded when publishing it fails`() {
        // given: a journal whose subscribers throw while a message is published
        val facade = FailureFacade(FailingJournal(), MutableClock(Instant.EPOCH))

        // when: a failure is reported
        // then: the caller is not interrupted, and the failure is still recorded for the console
        assertThatCode { facade.throwException("/api/console/sock", RuntimeException("boom")) }.doesNotThrowAnyException()
        assertThat(facade.getFailureRecords().single().source).isEqualTo("/api/console/sock")
    }

}

/** A journal whose subscribers fail on every message, as a socket that is already closed does. */
private class FailingJournal : InMemoryLogger() {

    override fun log(channel: Channel, message: Any?, vararg args: Any?): Logger =
        throw NullPointerException("subscriber failed")

    override fun exception(channel: Channel, throwable: Throwable): Logger =
        throw NullPointerException("subscriber failed")

}

private class MutableClock(var current: Instant) : Clock() {

    override fun getZone(): ZoneId = ZoneId.of("UTC")

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = current

}
