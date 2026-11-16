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

/**
 * The thresholds of a delay: the failure that starts it, the quiet period that forgets the failures, and the longest delay.
 */
internal data class DelayRule(
    val threshold: Int,
    val quiet: Duration,
    val maxDelay: Duration,
)

/**
 * The per-account delay, as a pure function of the state and the clock. Unlike [LoginAttemptPolicy] it never locks: failure
 * number `n` at or above the threshold delays the next attempt by `min(maxDelay, 2^(n - threshold))` seconds, and the delay
 * ends by itself. Failures count only while they are less than the quiet period apart.
 */
internal object LoginDelayPolicy {

    private const val MAX_EXPONENT = 30

    /** The state after one more failure at [now]. Requests refused by the delay are not failures and never reach this. */
    fun onFailure(state: AttemptState, now: Instant, rule: DelayRule): AttemptState {
        val lastFailure = state.lastFailureAt
        val current = if (lastFailure != null && !now.isBefore(lastFailure.plus(rule.quiet))) {
            state.copy(failures = 0, lockedUntil = null)
        } else {
            state
        }
        val failures = current.failures + 1
        val delay = if (failures < rule.threshold) null else delayOf(failures - rule.threshold, rule.maxDelay)
        return current.copy(
            failures = failures,
            lastFailureAt = now,
            lockedUntil = delay?.let { now.plus(it) },
        )
    }

    /** The instant the delay ends, when [now] is still inside it. */
    fun blockedUntil(state: AttemptState, now: Instant): Instant? =
        LoginAttemptPolicy.blockedUntil(state, now)

    private fun delayOf(exponent: Int, maxDelay: Duration): Duration {
        val seconds = 1L shl exponent.coerceIn(0, MAX_EXPONENT)
        val delay = Duration.ofSeconds(seconds)
        return if (delay > maxDelay) maxDelay else delay
    }

}
