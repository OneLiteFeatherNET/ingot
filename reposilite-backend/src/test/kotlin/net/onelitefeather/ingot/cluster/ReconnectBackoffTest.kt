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

package net.onelitefeather.ingot.cluster

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.Random

internal class ReconnectBackoffTest {

    @Test
    fun `the reconnect delay doubles from one second`() {
        // given: a backoff whose jitter keeps every delay as it is
        val backoff = ReconnectBackoff(jitter = { it })

        // when / then: the first six attempts double the delay
        assertThat((1..6).map { backoff.delay(it) })
            .containsExactly(
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                Duration.ofSeconds(4),
                Duration.ofSeconds(8),
                Duration.ofSeconds(16),
                Duration.ofSeconds(32),
            )
    }

    @Test
    fun `the reconnect delay never exceeds sixty seconds`() {
        val backoff = ReconnectBackoff(jitter = { it })

        assertThat(listOf(7, 8, 50, 10_000).map { backoff.delay(it) })
            .containsOnly(Duration.ofSeconds(60))
    }

    @Test
    fun `the jitter is applied to the capped delay`() {
        // given: a backoff that halves every delay it is given
        val backoff = ReconnectBackoff(jitter = { it.dividedBy(2) })

        // when / then: the halved value of the capped delay, not of the uncapped one
        assertThat(backoff.delay(8)).isEqualTo(Duration.ofSeconds(30))
    }

    @Test
    fun `equal jitter keeps every delay between half of it and its full value`() {
        // given: a seeded random source, so the test is repeatable
        val backoff = ReconnectBackoff(jitter = ReconnectBackoff.equalJitter(Random(42)))

        // when / then: every attempt stays within its bounds
        (1..12).forEach { attempt ->
            val full = ReconnectBackoff(jitter = { it }).delay(attempt)
            val actual = backoff.delay(attempt)
            assertThat(actual)
                .describedAs("delay of attempt $attempt")
                .isBetween(full.dividedBy(2), full)
        }
    }
}
