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

/**
 * Decides, before a Basic or Bearer secret is checked, whether a name may be tried at all from an address. The login of the
 * dashboard uses the same rule. The facade installs it once the login plugin is loaded; until then no request is delayed.
 */
interface AccountLoginGuard {

    /** The time left of the delay that applies to [name] from [address], or `null` when the request may be checked. */
    fun retryAfter(name: String, address: String): Duration?

    /** Counts one rejected credential of [name]. */
    fun recordFailure(name: String)

    /** Reports a successful credential of [name] from [address]. */
    fun recordSuccess(name: String, address: String)

    companion object {

        /** The guard before one is installed: it delays nothing and records nothing. */
        val NONE: AccountLoginGuard = object : AccountLoginGuard {
            override fun retryAfter(name: String, address: String): Duration? = null
            override fun recordFailure(name: String) = Unit
            override fun recordSuccess(name: String, address: String) = Unit
        }

    }

}
