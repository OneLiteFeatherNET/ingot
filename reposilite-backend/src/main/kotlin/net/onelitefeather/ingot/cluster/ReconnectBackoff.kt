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

import java.time.Duration
import java.util.Random

/**
 * The delay before reconnect attempt number [attempt] (starting at 1): one second, doubling, capped at sixty seconds.
 * The [jitter] is applied to the capped delay. It is a function of the delay only, so tests replace it.
 */
class ReconnectBackoff(private val jitter: (Duration) -> Duration = equalJitter(Random())) {

    fun delay(attempt: Int): Duration {
        val exponent = (attempt - 1).coerceIn(0, MAXIMUM_EXPONENT)
        val base = minOf(INITIAL.multipliedBy(1L shl exponent), MAXIMUM)
        return jitter(base)
    }

    companion object {

        val INITIAL: Duration = Duration.ofSeconds(1)
        val MAXIMUM: Duration = Duration.ofSeconds(60)

        /** Keeps the delay between half of [Duration] and the whole of it, so that instances do not reconnect in step. */
        fun equalJitter(random: Random): (Duration) -> Duration = { base ->
            val half = base.dividedBy(2)
            half.plusMillis(random.nextLong(half.toMillis() + 1))
        }

        // 2^6 seconds is already above the maximum, so larger exponents add nothing.
        private const val MAXIMUM_EXPONENT = 6
    }
}
