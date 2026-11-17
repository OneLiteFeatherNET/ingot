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

import com.reposilite.auth.FailedAddressAttempt
import com.reposilite.auth.FailedAddressRules
import com.reposilite.auth.FailedAddressStore
import net.onelitefeather.ingot.identity.login.storage.LoginStore
import java.time.Duration
import java.time.Instant

/**
 * The per-address counter of `authentication.bruteForceProtection`, kept in the login tables so that every replica on the
 * database counts the same failures. A row is keyed by [LoginAttemptLimiter.basicAddressKey] and uses the columns of the
 * attempt table: `failures` is the count, `lastFailureAt` the time of the last failure, and `lockedUntil` the end of the ban.
 *
 * The end of a ban is fixed when the limit is reached, so a ban duration changed later applies to the bans that start after
 * the change. A lost race is retried, and after the last retry the failure is still added, in one atomic increment. The ban
 * then starts with the next failure, as it does in the dashboard limiter.
 *
 * A database error is not caught here. It reaches the caller, which refuses the request before the secret is checked, the
 * same as a failing account throttle.
 *
 * [purgeFloor] is the longest quiet period of the dashboard limiter. Rows are removed only after it and after their own
 * lifetime, so that this counter never removes a row that the dashboard limiter would keep.
 */
internal class SharedFailedAddressStore(
    private val store: LoginStore,
    private val purgeFloor: () -> Duration = { LoginAttemptPolicy.MAX_LOCKOUT },
) : FailedAddressStore {

    override fun read(address: String, now: Instant, rules: FailedAddressRules): FailedAddressAttempt? =
        store.findAttempt(LoginAttemptLimiter.basicAddressKey(address))
            ?.state
            ?.let { toAttempt(it) }
            ?.takeIf { isAlive(it, now, rules) }

    override fun countFailure(address: String, now: Instant, rules: FailedAddressRules): FailedAddressAttempt {
        val key = LoginAttemptLimiter.basicAddressKey(address)
        repeat(MAX_WRITE_ATTEMPTS) {
            val existing = store.findAttempt(key)
            val current = existing?.state?.let { toAttempt(it) }?.takeIf { isAlive(it, now, rules) }
            val next = nextAttempt(current, now, rules)
            val state = AttemptState(
                failures = next.count,
                lockouts = 0,
                lastFailureAt = now,
                lockedUntil = next.bannedUntil,
            )
            val written = if (existing == null) {
                store.insertAttempt(key, state)
            } else {
                store.updateAttempt(key, expectedVersion = existing.version, state = state)
            }
            if (written) {
                purge(now, rules)
                return next
            }
        }
        store.incrementAttempt(key, now)
        purge(now, rules)
        return read(address, now, rules) ?: FailedAddressAttempt(count = 1, bannedUntil = null, writtenAt = now)
    }

    override fun forget(address: String) {
        store.deleteAttempt(LoginAttemptLimiter.basicAddressKey(address))
    }

    private fun nextAttempt(current: FailedAddressAttempt?, now: Instant, rules: FailedAddressRules): FailedAddressAttempt {
        val count = (current?.count ?: 0) + 1
        val bannedUntil = if (count >= rules.maxAttempts) current?.bannedUntil ?: now.plus(rules.banDuration) else null
        return FailedAddressAttempt(count = count, bannedUntil = bannedUntil, writtenAt = now)
    }

    /** A record lives [FailedAddressRules.lifetime] after its last failed attempt, as the in-memory counter does. */
    private fun isAlive(attempt: FailedAddressAttempt, now: Instant, rules: FailedAddressRules): Boolean =
        now.isBefore(attempt.writtenAt.plus(rules.lifetime))

    private fun toAttempt(state: AttemptState): FailedAddressAttempt =
        FailedAddressAttempt(
            count = state.failures,
            bannedUntil = state.lockedUntil,
            writtenAt = state.lastFailureAt ?: Instant.EPOCH,
        )

    private fun purge(now: Instant, rules: FailedAddressRules) {
        val longest = maxOf(rules.lifetime, purgeFloor())
        store.deleteAttemptsBefore(now.minus(longest), now)
    }

    private companion object {

        const val MAX_WRITE_ATTEMPTS = 5

    }

}
