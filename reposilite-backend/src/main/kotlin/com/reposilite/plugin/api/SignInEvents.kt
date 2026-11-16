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

package com.reposilite.plugin.api

// The sign-in events are experimental until one release has shipped. They are published while the account throttle switch
// (INGOT_LOGIN_ACCOUNT_THROTTLE) is on, and never carry a secret, a password or a second-factor code.

/**
 * A sign-in was answered with a delay by the account throttle. [path] is one of `dashboard`, `basic`, `bearer` and `console`.
 * Published once per answered request, after the delay is stored. [retryAfterSeconds] is the time left, rounded up.
 */
data class LoginThrottledEvent(
    val name: String,
    val address: String,
    val path: String,
    val retryAfterSeconds: Long,
) : Event

/**
 * A sign-in succeeded from an address that is not in the known-address list of the account. [name] is the principal or the
 * token name, [path] the path of the sign-in. [userAgentHash] is the SHA-256 of the user agent in hex, or `null` when the
 * request carried none. The raw user agent is never published.
 */
data class NewSignInEvent(
    val name: String,
    val address: String,
    val path: String,
    val userAgentHash: String?,
) : Event
