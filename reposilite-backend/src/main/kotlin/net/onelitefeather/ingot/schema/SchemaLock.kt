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

import org.jetbrains.exposed.v1.jdbc.Database
import java.sql.SQLException
import java.time.Clock
import java.time.Duration
import java.util.concurrent.ScheduledExecutorService

/**
 * One lock for the whole schema initialisation of an instance, shared by every replica of the same database.
 *
 * [release] is idempotent: a start can fail on several paths, and every one of them releases the lock.
 */
interface SchemaLock {

    fun release()

    companion object {

        /** The lock of an embedded database, which has a single writer and needs none. */
        val NONE: SchemaLock = object : SchemaLock {
            override fun release() = Unit
        }

    }

}

/**
 * Raised when another replica holds the schema lock for the whole wait. The message names the lock, so an operator can
 * tell a stuck initialisation from a database that is down.
 */
class SchemaLockTimeoutException(timeout: Duration) : IllegalStateException(
    "Could not acquire the schema lock within ${timeout.toSeconds()} seconds. Another instance is still initialising the " +
        "database schema, or it stopped without releasing the schema lock. Raise INGOT_LOCAL_SCHEMALOCKTIMEOUT if the " +
        "initialisation is slow, and check the instance that holds the lock."
)

/** Everything a lock needs from the instance that asks for it. */
class SchemaLockRequest(
    val database: Database,
    val jdbcUrl: String,
    val username: String?,
    val password: String?,
    val timeout: Duration,
    val clock: Clock,
    val scheduler: ScheduledExecutorService,
)

/** Chooses the schema lock for the database of an instance. */
fun interface SchemaLockFactory {

    fun acquire(request: SchemaLockRequest): SchemaLock

    companion object {

        /** MariaDB and MySQL take a lease row, PostgreSQL an advisory lock, and embedded databases take no lock. */
        val DEFAULT: SchemaLockFactory = SchemaLockFactory { request ->
            when (request.database.vendor.lowercase()) {
                "mariadb", "mysql" -> {
                    val table = ExposedLeaseTable(request.database).also { it.createIfAbsent() }
                    LeaseSchemaLock.acquire(table, request.clock, timeout = request.timeout)
                        .also { it.keepRenewed(request.scheduler) }
                }
                "postgresql" -> PostgresAdvisorySchemaLock.acquire(request.jdbcUrl, request.username, request.password, request.timeout)
                else -> SchemaLock.NONE
            }
        }

    }

}

/** Whether this error, or one of its causes, is the database error with the given code. */
internal fun Throwable.hasSqlErrorCode(code: Int): Boolean =
    generateSequence(this) { it.cause }.any { it is SQLException && it.errorCode == code }
