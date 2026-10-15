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

import java.time.Duration
import java.time.Instant

/** What the limiter remembers about one key: the failures since the last lockout, the lockouts, and the lock itself. */
internal data class AttemptState(
    val failures: Int,
    val lockouts: Int,
    val lastFailureAt: Instant?,
    val lockedUntil: Instant?,
) {

    companion object {
        val NONE = AttemptState(failures = 0, lockouts = 0, lastFailureAt = null, lockedUntil = null)
    }

}

/**
 * The rules of the password login limit, as pure functions of the state and the clock.
 *
 * A key collects failures. Once [maxFailedAttempts] of them come, the key is locked for the lockout time. Every further
 * lockout that follows without a quiet period doubles that time, up to [MAX_LOCKOUT]. Failures older than the lockout time
 * no longer count, and a quiet period of one lockout time after a lock ended forgets the backoff.
 */
internal object LoginAttemptPolicy {

    val MAX_LOCKOUT: Duration = Duration.ofHours(24)

    private const val MAX_DOUBLINGS = 20

    /** The instant the lock ends, when [now] is still inside it. */
    fun blockedUntil(state: AttemptState, now: Instant): Instant? =
        state.lockedUntil?.takeIf { now.isBefore(it) }

    /** The state after one more failure at [now]. A key that is locked does not count further failures. */
    fun onFailure(state: AttemptState, now: Instant, maxFailedAttempts: Int, lockout: Duration): AttemptState {
        if (blockedUntil(state, now) != null) {
            return state
        }

        var current = state
        val lockEnded = current.lockedUntil
        if (lockEnded != null && !now.isBefore(lockEnded.plus(lockout))) {
            current = current.copy(lockouts = 0, lockedUntil = null)
        }
        val lastFailure = current.lastFailureAt
        if (lastFailure != null && !now.isBefore(lastFailure.plus(lockout))) {
            current = current.copy(failures = 0)
        }

        val failures = current.failures + 1
        if (failures < maxFailedAttempts) {
            return current.copy(failures = failures, lastFailureAt = now)
        }
        val lockouts = current.lockouts + 1
        return current.copy(
            failures = 0,
            lockouts = lockouts,
            lastFailureAt = now,
            lockedUntil = now.plus(lockDuration(lockout, lockouts)),
        )
    }

    /** The lock time of the n-th lockout: the lockout time, doubled for every lockout before it, capped at [MAX_LOCKOUT]. */
    fun lockDuration(lockout: Duration, lockouts: Int): Duration {
        val doublings = (lockouts - 1).coerceIn(0, MAX_DOUBLINGS)
        val duration = lockout.multipliedBy(1L shl doublings)
        return if (duration > MAX_LOCKOUT) MAX_LOCKOUT else duration
    }

}
