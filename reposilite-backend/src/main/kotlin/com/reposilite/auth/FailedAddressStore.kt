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

package com.reposilite.auth

import java.time.Duration
import java.time.Instant

/** The settings of `authentication.bruteForceProtection` that decide one failure count. */
data class FailedAddressRules(
    val maxAttempts: Int,
    val banDuration: Duration,
    /** How long a record lives after its last failed attempt. */
    val lifetime: Duration,
)

/** The failed attempts of one address as they were last written. */
data class FailedAddressAttempt(
    val count: Int,
    /** The end of the ban, or `null` while the limit has not been reached. */
    val bannedUntil: Instant?,
    /** The time of the last failed attempt. */
    val writtenAt: Instant,
)

/**
 * Keeps the failed attempts per address for the per-address brute force protection of [AuthenticationFacade]. The default
 * keeps them in the memory of one instance. While the account throttle is on, the login tables keep them, so that every
 * replica on the database counts the same attempts.
 */
interface FailedAddressStore {

    /** The record of [address] if it is still alive at [now]. */
    fun read(address: String, now: Instant, rules: FailedAddressRules): FailedAddressAttempt?

    /**
     * Counts one failed attempt of [address] at [now]. A record that is no longer alive starts again at one. The ban starts
     * with the attempt that reaches [FailedAddressRules.maxAttempts], and stays with the record for the attempts after it.
     */
    fun countFailure(address: String, now: Instant, rules: FailedAddressRules): FailedAddressAttempt

    /** Forgets the record of [address]. */
    fun forget(address: String)

}
