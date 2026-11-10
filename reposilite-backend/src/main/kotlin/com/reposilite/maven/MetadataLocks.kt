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
package com.reposilite.maven

import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.errorResponse
import io.javalin.http.HttpStatus.SERVICE_UNAVAILABLE
import net.onelitefeather.ingot.schema.LeaseSchemaLock
import net.onelitefeather.ingot.schema.LeaseTable
import panda.std.Result
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

/**
 * Serialises the read-modify-write of `maven-metadata.xml` per repository and artifact directory, so that two writers of
 * one artifact never lose each other's entries. Writers of different artifacts never wait for each other.
 *
 * A writer takes the process lock of its key first. On a shared database it also takes the lease of the key, so every
 * instance is serialised, not only the threads of one. A writer that cannot take the lock within [MetadataLocks.WAIT_BOUND]
 * is refused with 503 and writes nothing. The lock of a key is reentrant on the thread that holds it.
 */
internal interface MetadataLocks {

    /**
     * Runs [operation] while the lock of [key] is held. The operation writes through [MetadataLockScope.commit], which
     * checks that the lease is still held right before the write.
     */
    fun <T> withLock(key: String, operation: (MetadataLockScope) -> Result<T, ErrorResponse>): Result<T, ErrorResponse>

    companion object {

        /** How long a writer waits for the lock before it is refused with 503. Fixed, see the maven-metadata-locking design. */
        val WAIT_BOUND: Duration = Duration.ofSeconds(10)

        /** How long a lease lasts without a new commit. A holder that stops loses it after this long. */
        val LEASE_LIFETIME: Duration = LeaseSchemaLock.LIFETIME

        /** How often a waiter tries again for the process lock and for the lease. */
        val POLL_INTERVAL: Duration = Duration.ofMillis(100)

    }

}

/** What a writer holding a metadata lock may do with it. */
internal interface MetadataLockScope {

    /**
     * Runs [write] only while the lease of the lock is still held. When another instance has taken the lease over, the write
     * is not run and the answer is 503, so the client retries and no stale listing is written.
     */
    fun <T> commit(write: () -> Result<T, ErrorResponse>): Result<T, ErrorResponse>

}

/**
 * The metadata locks of one instance. Without [leaseTables] the lock is the process lock only, which is enough for an
 * embedded database with a single writer. With [leaseTables] the lock is also a lease in the lease table of its key.
 *
 * Time comes from [clock], and waiting goes through [pause], so a test moves time forward without sleeping. [owner] names
 * this instance in the lease table.
 */
internal class LockingMetadataLocks(
    private val clock: Clock,
    private val leaseTables: ((key: String) -> LeaseTable)? = null,
    private val owner: String = UUID.randomUUID().toString(),
    private val waitBound: Duration = MetadataLocks.WAIT_BOUND,
    private val lifetime: Duration = MetadataLocks.LEASE_LIFETIME,
    private val pause: (Duration) -> Unit = { Thread.sleep(it.toMillis()) },
) : MetadataLocks {

    private val holders = ConcurrentHashMap<String, Holder>()

    override fun <T> withLock(key: String, operation: (MetadataLockScope) -> Result<T, ErrorResponse>): Result<T, ErrorResponse> {
        val holder = retain(key)
        try {
            if (holder.lock.isHeldByCurrentThread) {
                return operation(Scope(holder.lease))
            }

            val deadline = clock.instant().plus(waitBound)
            if (!awaitProcessLock(holder.lock, deadline)) {
                return unavailable()
            }

            try {
                holder.lease = leaseTables?.let { tables -> acquireLease(tables, key, deadline) ?: return unavailable() }
                return operation(Scope(holder.lease))
            } finally {
                holder.lease?.release()
                holder.lease = null
                holder.lock.unlock()
            }
        } finally {
            release(key)
        }
    }

    private fun awaitProcessLock(lock: ReentrantLock, deadline: Instant): Boolean {
        while (true) {
            if (lock.tryLock()) {
                return true
            }
            if (!clock.instant().isBefore(deadline)) {
                return false
            }
            pause(MetadataLocks.POLL_INTERVAL)
        }
    }

    /** Null when the lease is not taken before [deadline], or when the lease table fails. Either way nothing is written. */
    private fun acquireLease(tables: (String) -> LeaseTable, key: String, deadline: Instant): LeaseSchemaLock? =
        try {
            LeaseSchemaLock.acquire(
                table = tables(key),
                clock = clock,
                owner = owner,
                timeout = Duration.between(clock.instant(), deadline).coerceAtLeast(Duration.ZERO),
                pause = pause,
                lifetime = lifetime,
                pollInterval = MetadataLocks.POLL_INTERVAL,
            )
        } catch (exception: Exception) {
            null
        }

    private fun retain(key: String): Holder =
        holders.compute(key) { _, existing -> (existing ?: Holder()).also { it.users++ } }!!

    private fun release(key: String) {
        holders.compute(key) { _, existing ->
            when {
                existing == null -> null
                --existing.users == 0 -> null
                else -> existing
            }
        }
    }

    private fun <T> unavailable(): Result<T, ErrorResponse> =
        errorResponse(SERVICE_UNAVAILABLE, "Another request is writing the metadata of this artifact, retry the request")

    /** The users are counted under [holders] and the lease is written and read only by the thread that holds [lock]. */
    private class Holder {

        val lock = ReentrantLock()
        var users = 0
        var lease: LeaseSchemaLock? = null

    }

    private class Scope(private val lease: LeaseSchemaLock?) : MetadataLockScope {

        override fun <T> commit(write: () -> Result<T, ErrorResponse>): Result<T, ErrorResponse> {
            if (lease != null && !renewed(lease)) {
                return errorResponse(SERVICE_UNAVAILABLE, "The lease of this artifact was taken over, retry the request")
            }
            return write()
        }

        /** A lease that cannot be renewed because the lease table fails counts as lost, so nothing is written. */
        private fun renewed(lease: LeaseSchemaLock): Boolean =
            runCatching { lease.renew() }.getOrDefault(false)

    }

}
