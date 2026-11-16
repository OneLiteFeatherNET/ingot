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

package net.onelitefeather.ingot.identity.login

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

internal class LoginDelayPolicyTest {

    private val start = Instant.parse("2026-10-15T10:00:00Z")
    private val rule = DelayRule(threshold = 10, quiet = Duration.ofMinutes(60), maxDelay = Duration.ofSeconds(300))

    /** Records [count] failures at one instant and returns the state after them. */
    private fun failures(count: Int, at: Instant = start, from: AttemptState = AttemptState.NONE): AttemptState =
        (1..count).fold(from) { state, _ -> LoginDelayPolicy.onFailure(state, at, rule) }

    @Test
    fun `does not delay failures below the threshold`() {
        val state = failures(9)

        assertThat(LoginDelayPolicy.blockedUntil(state, start))
            .describedAs("nine failures of a threshold of ten must not delay the name")
            .isNull()
    }

    @Test
    fun `delays the tenth failure by one second`() {
        val state = failures(10)

        assertThat(LoginDelayPolicy.blockedUntil(state, start)).isEqualTo(start.plusSeconds(1))
    }

    @Test
    fun `doubles the delay with every further failure`() {
        val eleventh = failures(11)
        val twelfth = LoginDelayPolicy.onFailure(eleventh, start, rule)

        assertThat(LoginDelayPolicy.blockedUntil(eleventh, start)).isEqualTo(start.plusSeconds(2))
        assertThat(LoginDelayPolicy.blockedUntil(twelfth, start)).isEqualTo(start.plusSeconds(4))
    }

    @Test
    fun `caps the delay at the maximum`() {
        val state = failures(30)

        assertThat(LoginDelayPolicy.blockedUntil(state, start)).isEqualTo(start.plusSeconds(300))
    }

    @Test
    fun `ends the delay when the clock reaches it`() {
        val state = failures(10)

        assertThat(LoginDelayPolicy.blockedUntil(state, start.plusMillis(999))).isNotNull()
        assertThat(LoginDelayPolicy.blockedUntil(state, start.plusSeconds(1)))
            .describedAs("the delay ends at the instant it names")
            .isNull()
    }

    @Test
    fun `a quiet period resets the count`() {
        val nine = failures(9)
        val afterQuiet = LoginDelayPolicy.onFailure(nine, start.plus(Duration.ofMinutes(60)), rule)

        assertThat(LoginDelayPolicy.blockedUntil(afterQuiet, start.plus(Duration.ofMinutes(60))))
            .describedAs("a failure after a quiet hour starts counting again at one")
            .isNull()
    }

    @Test
    fun `failures closer together than the quiet period keep counting`() {
        val nine = failures(9)
        val soon = LoginDelayPolicy.onFailure(nine, start.plus(Duration.ofMinutes(59)), rule)

        assertThat(LoginDelayPolicy.blockedUntil(soon, start.plus(Duration.ofMinutes(59)))).isNotNull()
    }

}
