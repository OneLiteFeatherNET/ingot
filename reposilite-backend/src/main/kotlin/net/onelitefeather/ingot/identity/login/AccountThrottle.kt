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

import com.reposilite.auth.AccountLoginGuard
import net.onelitefeather.ingot.identity.login.storage.LoginStore
import panda.std.reactive.Reference
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * The per-account delay of every credential path. A name that gets too many failed credentials, from any address, is delayed
 * for the next guess from an address the name has not signed in from within [AccountThrottleSettings.knownAddressDays].
 * The delay is never a lock: it ends by itself, and a success clears it.
 *
 * It is off unless the switch [SWITCH] is on. While it is off, nothing is read from or written to the database by it, so
 * an installation that does not ask for it answers as before.
 */
internal class AccountThrottle(
    private val store: LoginStore,
    private val settings: Reference<LoginSettings>,
    private val clock: Clock,
    private val enabled: Boolean,
) : AccountLoginGuard {

    private val limiter = LoginAttemptLimiter(store, settings, clock)

    /** The time left of the delay that applies to [name] from [address], or `null` when the request may go on. */
    override fun retryAfter(name: String, address: String): Duration? {
        if (!enabled || name.isBlank()) {
            return null
        }
        val now = Instant.now(clock)
        val wait = limiter.retryAfter(listOf(LoginAttemptLimiter.accountKey(name))) ?: return null
        return if (isKnown(name, address, now)) null else wait
    }

    /** Counts one failed credential of [name], whatever address it came from. */
    override fun recordFailure(name: String) {
        if (!enabled || name.isBlank()) {
            return
        }
        limiter.recordDelayedFailure(LoginAttemptLimiter.accountKey(name), rule())
    }

    /** Clears the delay of [name] and remembers [address] as one it signed in from. */
    override fun recordSuccess(name: String, address: String) {
        if (!enabled || name.isBlank()) {
            return
        }
        val now = Instant.now(clock)
        limiter.forget(LoginAttemptLimiter.accountKey(name))
        val subject = name.lowercase()
        store.recordKnownAddress(subject, addressDigest(address), now)
        store.deleteKnownAddressesBefore(now.minus(Duration.ofDays(settings.get().accountThrottle.knownAddressDays)))
    }

    private fun isKnown(name: String, address: String, now: Instant): Boolean =
        store.isKnownAddress(name.lowercase(), addressDigest(address), now.minus(Duration.ofDays(settings.get().accountThrottle.knownAddressDays)))

    private fun rule(): DelayRule {
        val current = settings.get().accountThrottle
        return DelayRule(
            threshold = current.threshold,
            quiet = Duration.ofMinutes(current.quietMinutes),
            maxDelay = Duration.ofSeconds(current.maxDelaySeconds),
        )
    }

    companion object {

        /** The environment variable that turns the account throttle on. `REPOSILITE_LOGIN_ACCOUNT_THROTTLE` is the fallback. */
        const val SWITCH = "INGOT_LOGIN_ACCOUNT_THROTTLE"
        const val LEGACY_SWITCH = "REPOSILITE_LOGIN_ACCOUNT_THROTTLE"

        /** Whether the switch is on: `true` in either variable, the `INGOT_` one first. Blank or anything else is off. */
        fun enabledIn(environment: Map<String, String> = System.getenv()): Boolean {
            val value = environment[SWITCH]?.takeIf { it.isNotBlank() } ?: environment[LEGACY_SWITCH]
            return value?.trim()?.equals("true", ignoreCase = true) == true
        }

        /** The SHA-256 of an address in hex, so that the table holds no plain address. */
        internal fun addressDigest(address: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(address.toByteArray(Charsets.UTF_8))
                .joinToString(separator = "") { "%02x".format(it) }

    }

}
