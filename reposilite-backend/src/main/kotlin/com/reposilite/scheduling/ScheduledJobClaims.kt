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

package com.reposilite.scheduling

import java.time.Clock
import java.time.Duration
import java.time.ZoneOffset

/**
 * Decides which instance of a cluster runs a scheduled job. Every instance that shares a database runs the same
 * schedule, so each job is claimed under a key before it starts, and only the first claim of a key is granted.
 *
 * A lease is a claim that expires. An instance that stops holding a lease without extending it loses the key to the
 * next instance that asks, so a run that died in the middle is taken over. A key completed as done is never granted
 * again.
 */
interface ScheduledJobClaims {

    /** Returns true for exactly one caller per [key] among the instances that share the database. Keys are not reused. */
    fun claim(key: String): Boolean

    /**
     * Grants the lease of [key] for [ttl] when the key is not done and holds no live lease. Returns true for exactly one
     * caller among the instances that share the database while the lease is live.
     */
    fun lease(key: String, ttl: Duration): Boolean =
        throw UnsupportedOperationException("this claim store does not grant leases")

    /** Moves the expiry of a live lease of [key] forward by [ttl], only when this instance is its owner. */
    fun extend(key: String, ttl: Duration): Boolean =
        throw UnsupportedOperationException("this claim store does not extend leases")

    /**
     * Releases the lease of [key] when this instance is its owner. With [done] the key is also marked as done, so it is
     * never granted again. A call by an instance that is not the owner changes nothing.
     */
    fun complete(key: String, done: Boolean): Unit =
        throw UnsupportedOperationException("this claim store does not release leases")

    /** Without a shared database there is no other instance to coordinate with, so every claim and lease is granted. */
    object Unshared : ScheduledJobClaims {
        override fun claim(key: String): Boolean = true
        override fun lease(key: String, ttl: Duration): Boolean = true
        override fun extend(key: String, ttl: Duration): Boolean = true
        override fun complete(key: String, done: Boolean) = Unit
    }

}

/** The key of a job that runs once per UTC day, for example `cleanup:nightly:2026-10-15`. */
fun utcDayKey(job: String, clock: Clock): String =
    "$job:${clock.instant().atZone(ZoneOffset.UTC).toLocalDate()}"
