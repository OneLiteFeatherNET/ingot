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

internal class LoginAttemptPolicyTest {

    private val start = Instant.parse("2026-10-15T10:00:00Z")
    private val lockout = Duration.ofMinutes(15)

    /** Records failures at one instant until the key is locked, and returns the resulting state. */
    private fun failUntilLocked(state: AttemptState, now: Instant, maxFailedAttempts: Int = 5): AttemptState {
        var current = state
        repeat(maxFailedAttempts) { current = LoginAttemptPolicy.onFailure(current, now, maxFailedAttempts, lockout) }
        return current
    }

    @Test
    fun `does not lock before the maximum number of failures`() {
        val state = (1..4).fold(AttemptState.NONE) { current, _ -> LoginAttemptPolicy.onFailure(current, start, 5, lockout) }

        assertThat(LoginAttemptPolicy.blockedUntil(state, start)).describedAs("four failures of five must not lock the key").isNull()
    }

    @Test
    fun `locks the key for the lockout time after the maximum number of failures`() {
        val state = failUntilLocked(AttemptState.NONE, start)

        assertThat(LoginAttemptPolicy.blockedUntil(state, start)).isEqualTo(start.plus(lockout))
    }

    @Test
    fun `doubles the lock with every lockout that follows without a quiet period`() {
        val first = failUntilLocked(AttemptState.NONE, start)
        val afterFirstLock = start.plus(lockout)
        val second = failUntilLocked(first, afterFirstLock)

        assertThat(LoginAttemptPolicy.blockedUntil(second, afterFirstLock))
            .describedAs("the second lockout must last twice the lockout time")
            .isEqualTo(afterFirstLock.plus(lockout.multipliedBy(2)))
    }

    @Test
    fun `caps the lock at 24 hours`() {
        assertThat(LoginAttemptPolicy.lockDuration(lockout, lockouts = 30))
            .describedAs("the backoff must stop growing")
            .isEqualTo(LoginAttemptPolicy.MAX_LOCKOUT)
    }

    @Test
    fun `forgets the backoff after a quiet period once the lock has ended`() {
        val locked = failUntilLocked(AttemptState.NONE, start)
        val quietUntil = start.plus(lockout).plus(lockout)

        val afterQuietPeriod = LoginAttemptPolicy.onFailure(locked, quietUntil, 5, lockout)

        assertThat(afterQuietPeriod.lockouts).describedAs("a quiet period of one lockout time must reset the lockouts").isZero()
    }

    @Test
    fun `counts only failures inside the lockout window`() {
        val first = (1..4).fold(AttemptState.NONE) { current, _ -> LoginAttemptPolicy.onFailure(current, start, 5, lockout) }
        val later = start.plus(lockout)

        val state = LoginAttemptPolicy.onFailure(first, later, 5, lockout)

        assertThat(state.failures).describedAs("failures older than the window must not add up with new ones").isEqualTo(1)
        assertThat(LoginAttemptPolicy.blockedUntil(state, later)).isNull()
    }

    @Test
    fun `does not count a failure that arrives while the key is locked`() {
        val locked = failUntilLocked(AttemptState.NONE, start)

        val state = LoginAttemptPolicy.onFailure(locked, start.plusSeconds(1), 5, lockout)

        assertThat(state).describedAs("a locked key stays as it is").isEqualTo(locked)
    }

    @Test
    fun `reports no lock once the lock time has passed`() {
        val locked = failUntilLocked(AttemptState.NONE, start)

        assertThat(LoginAttemptPolicy.blockedUntil(locked, start.plus(lockout))).describedAs("the lock ends at its end time").isNull()
    }

}
