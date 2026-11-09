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

import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A session-level advisory lock on PostgreSQL.
 *
 * The lock lives on its own connection, opened outside the pool for the duration of the initialisation. The schema work
 * runs on pooled connections, so a pool of one is enough and the lock never waits for a connection it holds itself. Closing
 * the session releases the lock, also when the process dies, so a crashed replica cannot keep it.
 */
internal class PostgresAdvisorySchemaLock private constructor(
    private val connection: Connection,
) : SchemaLock {

    private val released = AtomicBoolean(false)

    override fun release() {
        if (!released.compareAndSet(false, true)) return
        runCatching { connection.close() }
    }

    companion object {

        /** Key of the schema lock in the advisory lock space, the ASCII bytes of "INGOT". */
        private const val SCHEMA_LOCK_KEY = 0x494E474F54L

        /** SQLSTATE 55P03: the lock could not be acquired within lock_timeout. */
        private const val LOCK_NOT_AVAILABLE = "55P03"

        fun acquire(jdbcUrl: String, username: String?, password: String?, timeout: Duration): PostgresAdvisorySchemaLock {
            val connection = DriverManager.getConnection(jdbcUrl, username, password)
            try {
                // lock_timeout is in milliseconds, and 0 would wait forever, so the smallest wait is one millisecond.
                val timeoutMillis = maxOf(1L, timeout.toMillis())
                connection.createStatement().use { it.execute("SET lock_timeout = $timeoutMillis") }
                connection.prepareStatement("SELECT pg_advisory_lock(?)").use { statement ->
                    statement.setLong(1, SCHEMA_LOCK_KEY)
                    statement.execute()
                }
                return PostgresAdvisorySchemaLock(connection)
            } catch (exception: SQLException) {
                runCatching { connection.close() }
                if (exception.sqlState == LOCK_NOT_AVAILABLE) {
                    throw SchemaLockTimeoutException(timeout)
                }
                throw exception
            }
        }

    }

}
