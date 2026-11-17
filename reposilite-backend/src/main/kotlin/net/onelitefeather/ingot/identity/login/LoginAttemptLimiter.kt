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

    /** Counts one failure of [key] under the per-account delay rule. The delay is read back by [retryAfter]. */
    fun recordDelayedFailure(key: String, rule: DelayRule) {
        val now = Instant.now(clock)
        write(key) { state -> LoginDelayPolicy.onFailure(state, now, rule) }
    }

    private fun recordFailure(key: String, now: Instant, maxFailedAttempts: Int, lockout: Duration) {
        write(key) { state -> LoginAttemptPolicy.onFailure(state, now, maxFailedAttempts, lockout) }
    }

    /**
     * Applies [next] to the state of [key] under the version it was read at, and retries a lost race against the winner.
     * After [MAX_WRITE_ATTEMPTS] lost races the failure is still counted, in one atomic increment.
     */
    private fun write(key: String, next: (AttemptState) -> AttemptState) {
        repeat(MAX_WRITE_ATTEMPTS) {
            val existing = store.findAttempt(key)
            val state = next(existing?.state ?: AttemptState.NONE)
            val written = if (existing == null) {
                store.insertAttempt(key, state)
            } else {
                store.updateAttempt(key, expectedVersion = existing.version, state = state)
            }
            if (written) {
                return
            }
        }
        store.incrementAttempt(key, Instant.now(clock))
    }

    /**
     * Keeps the table small: a row whose last failure is older than any lock could last can hold nothing that matters. The
     * table would otherwise grow with every user name an attacker invents.
     */
    private fun purgeQuietKeys(now: Instant, lockout: Duration) {
        val longest = if (lockout > LoginAttemptPolicy.MAX_LOCKOUT) lockout else LoginAttemptPolicy.MAX_LOCKOUT
        store.deleteAttemptsBefore(now.minus(longest), now)
    }

    companion object {

        private const val MAX_WRITE_ATTEMPTS = 5

        /** The key of a user name from one address. The user name is compared without its letter case. */
        fun userKey(username: String, address: String): String =
            digest("user|${username.lowercase()}|$address")

        /** The key of an address, across all user names. */
        fun addressKey(address: String): String =
            digest("address|$address")

        /**
         * The key of the legacy per-address counter of Basic authentication. It is not [addressKey]: the dashboard counts
         * under that key, and the two counters must not refuse each other's requests.
         */
        fun basicAddressKey(address: String): String =
            digest("basic-address|$address")

        /**
         * The key of a user name across every address. It delays guesses from addresses the name never signed in from, and
         * is kept apart from [userKey] and [addressKey].
         */
        fun accountKey(username: String): String =
            digest("account|${username.lowercase()}")

        /**
         * The keys of failed second factor codes. They are kept apart from the password keys, so a password login that
         * succeeds does not reset the failures of the codes.
         */
        fun mfaUserKey(username: String, address: String): String =
            digest("mfa|user|${username.lowercase()}|$address")

        fun mfaAddressKey(address: String): String =
            digest("mfa|address|$address")

        /** The key of an account across every address, so that rotating addresses does not multiply the attempts. */
        fun mfaAccountKey(username: String): String =
            digest("mfa|account|${username.lowercase()}")

        private fun digest(value: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))
                .joinToString(separator = "") { "%02x".format(it) }

    }

}
