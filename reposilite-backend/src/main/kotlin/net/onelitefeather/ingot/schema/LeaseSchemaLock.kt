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
package net.onelitefeather.ingot.schema

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The lease row of the schema lock, read and written by each replica. Each call is one short statement or transaction, so
 * no connection is held between attempts.
 */
internal interface LeaseTable {

    /** Creates the table when it does not exist yet. Two replicas may run this at the same moment. */
    fun createIfAbsent()

    /** Takes the lease when it is free, expired, or already held by [owner]. Returns whether [owner] holds it now. */
    fun tryAcquire(owner: String, now: Instant, expiresAt: Instant): Boolean

    /** Moves the expiry of the lease held by [owner]. Returns false when [owner] no longer holds it. */
    fun renew(owner: String, expiresAt: Instant): Boolean

    /** Frees the lease when it is held by [owner]. Another owner's lease is left alone. */
    fun release(owner: String)

}

/**
 * A lease with an expiry, for MariaDB and MySQL, where `GET_LOCK` is not available on Galera. The holder renews the lease
 * while it initialises, so a slow but healthy initialisation is not taken over. A holder that stops renewing loses the
 * lease once it expires, and the next replica takes over.
 *
 * Time comes from [clock], and waiting between attempts goes through [pause], so a test moves time forward without sleeping.
 * Error 1213 is retried, because Galera reports a certification failure with it. Every other error is raised at once.
 */
internal class LeaseSchemaLock private constructor(
    private val table: LeaseTable,
    private val clock: Clock,
    private val owner: String,
    private val lifetime: Duration,
    private val pause: (Duration) -> Unit,
) : SchemaLock {

    private val released = AtomicBoolean(false)

    @Volatile
    private var renewal: ScheduledFuture<*>? = null

    /** Renews the lease on [scheduler] until the lock is released. A failed renewal is retried on the next tick. */
    fun keepRenewed(scheduler: ScheduledExecutorService) {
        renewal = scheduler.scheduleAtFixedRate(
            { runCatching { renew() } },
            RENEW_EVERY.toMillis(),
            RENEW_EVERY.toMillis(),
            TimeUnit.MILLISECONDS,
        )
    }

    /** Extends the lease by its lifetime from now. Returns false when this holder no longer owns it. */
    fun renew(): Boolean {
        if (released.get()) return false
        val expiresAt = clock.instant().plus(lifetime)
        return retryingOnDeadlock(until = expiresAt) { table.renew(owner, expiresAt) }
    }

    override fun release() {
        if (!released.compareAndSet(false, true)) return
        renewal?.cancel(false)
        // A failed release leaves the lease to expire on its own, so the start does not fail on it.
        runCatching { table.release(owner) }
    }

    private fun <T> retryingOnDeadlock(until: Instant, operation: () -> T): T {
        while (true) {
            try {
                return operation()
            } catch (exception: Exception) {
                if (!exception.hasSqlErrorCode(DEADLOCK) || !clock.instant().isBefore(until)) {
                    throw exception
                }
                pause(RETRY_BACKOFF)
            }
        }
    }

    companion object {

        /** How long a lease lasts without renewal. A replica that dies loses the lock after this long. */
        val LIFETIME: Duration = Duration.ofSeconds(60)

        /** How often the holder renews its lease while it initialises. */
        val RENEW_EVERY: Duration = Duration.ofSeconds(10)

        private val POLL_INTERVAL: Duration = Duration.ofSeconds(1)
        private val RETRY_BACKOFF: Duration = Duration.ofMillis(200)
        private const val DEADLOCK = 1213

        /**
         * Waits until the lease is taken, or fails with [SchemaLockTimeoutException] once [timeout] has passed on [clock].
         */
        fun acquire(
            table: LeaseTable,
            clock: Clock,
            owner: String = UUID.randomUUID().toString(),
            timeout: Duration,
            pause: (Duration) -> Unit = { Thread.sleep(it.toMillis()) },
            lifetime: Duration = LIFETIME,
            pollInterval: Duration = POLL_INTERVAL,
        ): LeaseSchemaLock {
            val lock = LeaseSchemaLock(table, clock, owner, lifetime, pause)
            val deadline = clock.instant().plus(timeout)
            while (true) {
                val now = clock.instant()
                val acquired = lock.retryingOnDeadlock(until = deadline) {
                    table.tryAcquire(owner, now, now.plus(lifetime))
                }
                if (acquired) {
                    return lock
                }
                if (!clock.instant().isBefore(deadline)) {
                    throw SchemaLockTimeoutException(timeout)
                }
                pause(pollInterval)
            }
        }
    }

}

/** The lease table of the instance database, created next to the schema it protects. */
internal class ExposedLeaseTable(private val database: Database) : LeaseTable {

    override fun createIfAbsent() {
        try {
            transaction(database) {
                exec(
                    "CREATE TABLE IF NOT EXISTS ${IngotLockTable.tableName} (" +
                        "${IngotLockTable.leaseName.name} VARCHAR($LEASE_NAME_LENGTH) NOT NULL PRIMARY KEY, " +
                        "${IngotLockTable.owner.name} VARCHAR($OWNER_LENGTH) NOT NULL, " +
                        "${IngotLockTable.expiresAt.name} BIGINT NOT NULL)"
                )
            }
        } catch (exception: Exception) {
            // The lock cannot protect its own creation, so the replica that loses the race finds the table already there.
            if (exception.hasSqlErrorCode(TABLE_EXISTS)) {
                return
            }
            throw exception
        }
    }

    override fun tryAcquire(owner: String, now: Instant, expiresAt: Instant): Boolean =
        transaction(database) {
            val taken = IngotLockTable.update({
                (IngotLockTable.leaseName eq LEASE_NAME) and
                    ((IngotLockTable.owner eq owner) or (IngotLockTable.expiresAt less now.toEpochMilli()))
            }) {
                it[IngotLockTable.owner] = owner
                it[IngotLockTable.expiresAt] = expiresAt.toEpochMilli()
            }
            if (taken > 0) {
                return@transaction true
            }
            IngotLockTable.insertIgnore {
                it[IngotLockTable.leaseName] = LEASE_NAME
                it[IngotLockTable.owner] = owner
                it[IngotLockTable.expiresAt] = expiresAt.toEpochMilli()
            }.insertedCount == 1
        }

    override fun renew(owner: String, expiresAt: Instant): Boolean =
        transaction(database) {
            IngotLockTable.update({
                (IngotLockTable.leaseName eq LEASE_NAME) and (IngotLockTable.owner eq owner)
            }) {
                it[IngotLockTable.expiresAt] = expiresAt.toEpochMilli()
            } > 0
        }

    override fun release(owner: String) {
        transaction(database) {
            IngotLockTable.deleteWhere {
                (IngotLockTable.leaseName eq LEASE_NAME) and (IngotLockTable.owner eq owner)
            }
        }
    }

    private companion object {
        const val LEASE_NAME = "schema"
        const val LEASE_NAME_LENGTH = 64
        const val OWNER_LENGTH = 128
        const val TABLE_EXISTS = 1050
    }

}

/** The lease row. The primary key on the name keeps one lease per database. */
internal object IngotLockTable : Table("ingot_lock") {
    val leaseName = varchar("name", 64)
    val owner = varchar("owner", 128)
    val expiresAt = long("expires_at")

    override val primaryKey = PrimaryKey(leaseName)
}
