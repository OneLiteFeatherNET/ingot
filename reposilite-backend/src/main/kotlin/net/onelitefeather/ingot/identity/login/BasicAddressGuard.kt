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

import com.reposilite.auth.AddressLoginGuard
import com.reposilite.auth.application.BruteForceProtectionSettings
import net.onelitefeather.ingot.identity.login.storage.LoginStore
import panda.std.reactive.Reference
import java.time.Clock
import java.time.Duration

/**
 * The legacy per-address limit of Basic and Bearer requests. Its counter lives in the shared login table under
 * [LoginAttemptLimiter.basicAddressKey], so replicas on one database count together and a restart forgets nothing.
 *
 * The limit uses the shared rule of [LoginAttemptPolicy]: `maxAttempts` failures lock the address for `banDurationSeconds`,
 * and repeated locks double that time. The switch is read on every call, and while it is off nothing is read or written.
 */
internal class BasicAddressGuard(
    store: LoginStore,
    private val settings: Reference<BruteForceProtectionSettings>,
    private val clock: Clock,
) : AddressLoginGuard {

    /** The thresholds come from the brute force settings on every call, so the password login settings are not used here. */
    private val limiter = LoginAttemptLimiter(store, Reference.reference(LoginSettings()), clock)

    override fun isLocked(address: String): Boolean {
        if (!settings.get().enabled) {
            return false
        }
        return limiter.retryAfter(listOf(LoginAttemptLimiter.basicAddressKey(address))) != null
    }

    override fun recordFailure(address: String): Boolean {
        val current = settings.get()
        if (!current.enabled) {
            return false
        }
        return limiter.recordLimitedFailure(
            key = LoginAttemptLimiter.basicAddressKey(address),
            maxFailedAttempts = current.maxAttempts,
            lockout = Duration.ofSeconds(current.banDurationSeconds),
        )
    }

    override fun recordSuccess(address: String) {
        if (!settings.get().enabled) {
            return
        }
        val key = LoginAttemptLimiter.basicAddressKey(address)
        // A valid login from a shared address must not lift a ban that is still running
        if (limiter.retryAfter(listOf(key)) == null) {
            limiter.forget(key)
        }
    }

}
