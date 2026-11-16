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

/**
 * The per-address limit of Basic and Bearer requests. The login plugin installs the implementation that counts in the shared
 * login store, so that replicas on one database count together.
 */
interface AddressLoginGuard {

    /** Whether requests from [address] are refused now, before any secret is checked. */
    fun isLocked(address: String): Boolean

    /** Counts one failed credential from [address]. Answers `true` when this failure locked the address. */
    fun recordFailure(address: String): Boolean

    /** Reports a successful credential from [address], which clears its counter unless the address is locked. */
    fun recordSuccess(address: String)

    companion object {

        /** The guard before one is installed: it refuses nothing and counts nothing. */
        val NONE: AddressLoginGuard = object : AddressLoginGuard {
            override fun isLocked(address: String): Boolean = false
            override fun recordFailure(address: String): Boolean = false
            override fun recordSuccess(address: String) = Unit
        }

    }

}
