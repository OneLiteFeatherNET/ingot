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

import net.onelitefeather.ingot.identity.login.storage.LoginStore
import panda.std.reactive.Reference
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Limits failed password logins. The state lives in the database, so every instance on the same database sees the same
 * failures: a client that is locked out on one pod is locked out on every other pod too.
 *
 * Each failed login counts twice: for its user name with its address, and for its address alone. The address counter is
 * never reset by a successful login, so an attacker who owns an account cannot reset the failures of their own address.
 * Writes are conditional on the version that was read, and a lost race is retried against the state that won it.
 */
internal class LoginAttemptLimiter(
    private val store: LoginStore,
    private val settings: Reference<LoginSettings>,
    private val clock: Clock,
) {

    /** The time left of the longest lock among [keys], or `null` when none of them is locked. */
    fun retryAfter(keys: List<String>): Duration? {
        val now = Instant.now(clock)
        return keys
            .mapNotNull { key -> store.findAttempt(key)?.state?.let { LoginAttemptPolicy.blockedUntil(it, now) } }
            .maxOrNull()
            ?.let { Duration.between(now, it) }
    }

    fun recordFailure(keys: List<String>) {
        val now = Instant.now(clock)
        val current = settings.get()
        val lockout = Duration.ofMinutes(current.lockoutMinutes)
        keys.forEach { key -> recordFailure(key, now, current.maxFailedAttempts, lockout) }
        purgeQuietKeys(now, lockout)
    }

    /** Forgets the failures of one key, after a successful login of its user name. */
    fun forget(key: String) {
        store.deleteAttempt(key)
    }

    private fun recordFailure(key: String, now: Instant, maxFailedAttempts: Int, lockout: Duration) {
        repeat(MAX_WRITE_ATTEMPTS) {
            val existing = store.findAttempt(key)
            val next = LoginAttemptPolicy.onFailure(existing?.state ?: AttemptState.NONE, now, maxFailedAttempts, lockout)
            val written = if (existing == null) {
                store.insertAttempt(key, next)
            } else {
                store.updateAttempt(key, expectedVersion = existing.version, state = next)
            }
            if (written) {
                return
            }
        }
    }

    /**
     * Keeps the table small: a row whose last failure is older than any lock could last can hold nothing that matters. The
     * table would otherwise grow with every user name an attacker invents.
     */
    private fun purgeQuietKeys(now: Instant, lockout: Duration) {
        val longest = if (lockout > LoginAttemptPolicy.MAX_LOCKOUT) lockout else LoginAttemptPolicy.MAX_LOCKOUT
        store.deleteAttemptsBefore(now.minus(longest))
    }

    companion object {

        private const val MAX_WRITE_ATTEMPTS = 5

        /** The key of a user name from one address. The user name is compared without its letter case. */
        fun userKey(username: String, address: String): String =
            digest("user|${username.lowercase()}|$address")

        /** The key of an address, across all user names. */
        fun addressKey(address: String): String =
            digest("address|$address")

        private fun digest(value: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))
                .joinToString(separator = "") { "%02x".format(it) }

    }

}
