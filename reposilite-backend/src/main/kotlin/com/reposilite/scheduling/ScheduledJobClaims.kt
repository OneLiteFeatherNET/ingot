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

    /**
     * Grants the lease of [key] only when a run of the key was already started and not completed, and its lease has
     * expired. Unlike [lease], it never creates the row, so a key that no instance started is not granted.
     */
    fun takeover(key: String, ttl: Duration): Boolean =
        throw UnsupportedOperationException("this claim store does not take over leases")

    /** Moves the expiry of a live lease of [key] forward by [ttl], only when this instance is its owner. */
    fun extend(key: String, ttl: Duration): Boolean =
        throw UnsupportedOperationException("this claim store does not extend leases")

    /**
     * Releases the lease of [key] when this instance is its owner. With [done] the key is also marked as done, so it is
     * never granted again. A call by an instance that is not the owner changes nothing.
     */
    fun complete(key: String, done: Boolean): Unit =
        throw UnsupportedOperationException("this claim store does not release leases")

    /**
     * Grants the lease of [key] like [lease] and returns it with its fencing [Lease.epoch], or returns null when the
     * lease is refused. A store without epochs grants epoch [UNFENCED_EPOCH] and fences nothing.
     */
    fun acquire(key: String, ttl: Duration): Lease? =
        if (lease(key, ttl)) Lease(key, UNFENCED_EPOCH) else null

    /**
     * Grants the lease of [key] like [takeover] and returns it with its fencing [Lease.epoch], or returns null when the
     * lease is refused. Unlike [acquire], it never creates the row of a key that no instance started.
     */
    fun acquireExpired(key: String, ttl: Duration): Lease? =
        if (takeover(key, ttl)) Lease(key, UNFENCED_EPOCH) else null

    /**
     * Moves the expiry of [lease] forward by [ttl], only while [lease] is the live grant of this instance. A holder whose
     * key was granted again, even to this same instance, is refused, because its epoch is no longer the current one.
     */
    fun renew(lease: Lease, ttl: Duration): Boolean = extend(lease.key, ttl)

    /**
     * Releases [lease] when it is the live grant of this instance, and marks the key as done with [done]. A holder whose
     * grant was superseded changes nothing of the new grant.
     */
    fun release(lease: Lease, done: Boolean): Unit = complete(lease.key, done)

    /**
     * A lease granted to an instance. [epoch] is the fencing token of the grant: every grant of a key, whether a first
     * lease, a takeover or a grant after a release, gets an epoch larger than the one before. Work that must not overlap
     * a newer holder checks the epoch before it acts, so a stale holder is detected even when the newer holder is the
     * same instance.
     */
    data class Lease(val key: String, val epoch: Long)

    companion object {
        /** The epoch of a lease from a store that keeps no epochs. Such a lease cannot be told apart from a newer one. */
        const val UNFENCED_EPOCH: Long = 0L
    }

    /** Without a shared database there is no other instance to coordinate with, so every claim and lease is granted. */
    object Unshared : ScheduledJobClaims {
        override fun claim(key: String): Boolean = true
        override fun lease(key: String, ttl: Duration): Boolean = true
        override fun takeover(key: String, ttl: Duration): Boolean = false
        override fun extend(key: String, ttl: Duration): Boolean = true
        override fun complete(key: String, done: Boolean) = Unit
    }

}

/** The key of a job that runs once per UTC day, for example `cleanup:nightly:2026-10-15`. */
fun utcDayKey(job: String, clock: Clock): String =
    "$job:${clock.instant().atZone(ZoneOffset.UTC).toLocalDate()}"
