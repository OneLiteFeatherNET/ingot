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

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.reposilite.auth.application.BruteForceProtectionSettings
import panda.std.reactive.Reference
import java.time.Clock
import java.time.Instant
import java.util.concurrent.TimeUnit.HOURS

/**
 * Counts failed logins per key, where the key is a user name and an address. It uses the brute force protection settings
 * of the authentication domain, so an operator configures one mechanism for Basic and for password logins. The decision
 * is taken against the injected clock, so bans can be tested without waiting.
 */
internal class LoginRateLimiter(
    private val settings: Reference<BruteForceProtectionSettings>,
    private val clock: Clock,
) {

    private data class Failures(val count: Int, val firstFailure: Instant)

    private val failures: Cache<String, Failures> = CacheBuilder
        .newBuilder()
        .maximumSize(MAX_TRACKED_KEYS)
        .expireAfterWrite(1, HOURS)
        .build()

    fun isBlocked(key: String): Boolean {
        val current = settings.get()
        if (!current.enabled) {
            return false
        }
        val attempts = failures.getIfPresent(key) ?: return false
        if (attempts.count < current.maxAttempts) {
            return false
        }
        if (Instant.now(clock).isBefore(attempts.firstFailure.plusSeconds(current.banDurationSeconds))) {
            return true
        }
        failures.invalidate(key)
        return false
    }

    fun recordFailure(key: String) {
        if (!settings.get().enabled) {
            return
        }
        val now = Instant.now(clock)
        val previous = failures.getIfPresent(key)
        failures.put(key, Failures(count = (previous?.count ?: 0) + 1, firstFailure = previous?.firstFailure ?: now))
    }

    fun forget(key: String) {
        failures.invalidate(key)
    }

    private companion object {
        const val MAX_TRACKED_KEYS = 10_000L
    }

}
