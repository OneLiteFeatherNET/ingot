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

@file:OptIn(ExperimentalIngotSearchApi::class)

package net.onelitefeather.ingot.search.http

import net.onelitefeather.ingot.search.backend.BackendBusyException
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.io.IOException
import java.time.Duration

/** The circuit breaker of the transport, driven by an injected clock. */
internal class SearchCircuitBreakerTest {

    private val clock = ManualClock()
    private val breaker = SearchCircuitBreaker(clock)

    private fun transportFailure(): Nothing = throw IOException("connection refused")

    private fun failTimes(count: Int) = repeat(count) {
        assertThatThrownBy { breaker.call { transportFailure() } }.isInstanceOf(IOException::class.java)
    }

    @Test
    fun `should stay closed after four consecutive transport failures`() {
        // when: four requests fail with a transport error
        failTimes(4)

        // then: the circuit is still closed
        assertThat(breaker.openRemaining()).`as`("remaining open time").isNull()
    }

    @Test
    fun `should open after five consecutive transport failures`() {
        // when: five requests fail with a transport error
        failTimes(5)

        // then: the circuit is open for the thirty seconds of the design
        assertThat(breaker.openRemaining()).`as`("remaining open time").isEqualTo(Duration.ofSeconds(30))
    }

    @Test
    fun `should count only consecutive failures`() {
        // given: four failures, a success and four more failures
        failTimes(4)
        breaker.call { "ok" }
        failTimes(4)

        // then: the success reset the count, so the circuit stays closed
        assertThat(breaker.openRemaining()).`as`("remaining open time").isNull()
    }

    @Test
    fun `should reject a request while open and report the time left as retry delay`() {
        // given: an open circuit with ten seconds gone
        failTimes(5)
        clock.advance(Duration.ofSeconds(10))
        var called = false

        // when: a request is made
        val rejection = runCatching { breaker.call { called = true } }.exceptionOrNull()

        // then: the request is not sent and the retry delay is the time left
        assertThat(called).`as`("request sent while open").isFalse()
        assertThat(rejection).`as`("rejection").isInstanceOf(BackendBusyException::class.java)
        assertThat((rejection as BackendBusyException).retryAfter)
            .`as`("retry delay")
            .isEqualTo(Duration.ofSeconds(20))
    }

    @Test
    fun `should stay open for thirty seconds and no longer after`() {
        // given: an open circuit
        failTimes(5)

        // when: 29 seconds have passed
        clock.advance(Duration.ofSeconds(29))

        // then: the circuit is still open
        assertThat(breaker.openRemaining()).`as`("open after 29 seconds").isEqualTo(Duration.ofSeconds(1))

        // when: the thirtieth second has passed
        clock.advance(Duration.ofSeconds(1))

        // then: the circuit is no longer open
        assertThat(breaker.openRemaining()).`as`("open after 30 seconds").isNull()
    }

    @Test
    fun `should let one request through after thirty seconds`() {
        // given: an open circuit that has been open for thirty seconds
        failTimes(5)
        clock.advance(Duration.ofSeconds(30))

        // when: a request is made
        val answer = breaker.call { "answered" }

        // then: the request is sent
        assertThat(answer).`as`("answer of the half open request").isEqualTo("answered")
    }

    @Test
    fun `should reject a second request while the half open request is running`() {
        // given: an open circuit that has been open for thirty seconds
        failTimes(5)
        clock.advance(Duration.ofSeconds(30))

        // when: a second request is made inside the half open request
        val nested = runCatching { breaker.call { breaker.call { "second" } } }.exceptionOrNull()

        // then: the second request is rejected
        assertThat(nested).`as`("second request while half open").isInstanceOf(BackendBusyException::class.java)
    }

    @Test
    fun `should close after the half open request succeeds`() {
        // given: an open circuit that has been open for thirty seconds and a request that succeeds
        failTimes(5)
        clock.advance(Duration.ofSeconds(30))
        breaker.call { "ok" }

        // then: the circuit is closed again
        assertThat(breaker.openRemaining()).`as`("remaining open time").isNull()
    }

    @Test
    fun `should open for another thirty seconds when the half open request fails`() {
        // given: an open circuit that has been open for thirty seconds and a request that fails
        failTimes(5)
        clock.advance(Duration.ofSeconds(30))
        failTimes(1)

        // then: the circuit is open again for thirty seconds from the failed request
        assertThat(breaker.openRemaining()).`as`("remaining open time").isEqualTo(Duration.ofSeconds(30))
    }

}
