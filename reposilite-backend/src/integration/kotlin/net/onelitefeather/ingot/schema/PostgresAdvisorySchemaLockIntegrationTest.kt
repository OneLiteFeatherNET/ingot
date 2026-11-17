package net.onelitefeather.ingot.schema

import com.reposilite.SharedContainers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.sql.Connection
import java.sql.DriverManager
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The PostgreSQL advisory schema lock on a real server. Advisory locks belong to a database, so every test creates a
 * database of its own and drops it afterwards. Waits are bounded: the lock's own timeout is the server's lock_timeout, and
 * a future is read with a timeout, so nothing sleeps.
 */
internal class PostgresAdvisorySchemaLockIntegrationTest {

    private val longWait = Duration.ofSeconds(10)

    /** A wait of one millisecond: a held lock fails at once. */
    private val noWait = Duration.ofMillis(1)

    @Test
    fun `fails a second holder with a timeout while another connection holds the lock`() {
        withDatabase { url ->
            // given: a holder on its own connection
            val holder = acquire(url, longWait)
            try {
                // when / then: a second holder on another connection cannot get the lock within its timeout
                assertThrows<SchemaLockTimeoutException>("second holder while the first one holds the lock") {
                    acquire(url, noWait)
                }
            } finally {
                holder.release()
            }
        }
    }

    @Test
    fun `grants the lock to a waiting holder only after the first holder releases it`() {
        withDatabase { url ->
            val executor = Executors.newSingleThreadExecutor()
            val holder = acquire(url, longWait)
            try {
                // given: a second holder that is queued behind the first one
                val waiter = CompletableFuture.supplyAsync({ acquire(url, longWait) }, executor)
                awaitQueuedWaiter(url)

                // then: it has not got the lock while the first holder keeps it
                assertThat(waiter.isDone).`as`("waiter finished while the first holder still holds the lock").isFalse

                // when: the first holder releases it
                holder.release()

                // then: the waiter gets it
                val second = waiter.get(longWait.toMillis(), TimeUnit.MILLISECONDS)
                second.release()
            } finally {
                holder.release()
                executor.shutdownNow()
            }
        }
    }

    @Test
    fun `frees the lock on release so that the next holder gets it`() {
        withDatabase { url ->
            // given: a holder that releases the lock
            acquire(url, longWait).release()

            // when / then: the next holder gets it; the wait is only a bound in case the release did not free the lock
            val next = acquire(url, longWait)
            next.release()
        }
    }

    @Test
    fun `frees the lock when the holding connection is terminated as after a crash`() {
        withDatabase { url ->
            // given: a holder whose session is terminated on the server without a release
            val holder = acquire(url, longWait)
            try {
                val holderPids = queryRows(url, "SELECT pid FROM pg_locks WHERE $HELD_ADVISORY_LOCKS") { it.getInt(1) }
                assertThat(holderPids).`as`("sessions holding the schema lock").hasSize(1)

                val terminated = withConnection(url) { connection ->
                    connection.prepareStatement("SELECT pg_terminate_backend(?)").use { statement ->
                        statement.setInt(1, holderPids.single())
                        statement.executeQuery().use { rows ->
                            rows.next()
                            rows.getBoolean(1)
                        }
                    }
                }
                assertThat(terminated).`as`("termination of the holding session").isTrue

                // when / then: the server releases the lock with the session, so the next holder gets it
                val next = acquire(url, longWait)
                next.release()
            } finally {
                holder.release()
            }
        }
    }

    @Test
    fun `takes the advisory lock under the key of the ascii bytes INGOT`() {
        withDatabase { url ->
            val holder = acquire(url, longWait)
            try {
                // pg_locks shows a single bigint key as classid (high 32 bits), objid (low 32 bits) and objsubid 1
                val key = "INGOT".toByteArray(Charsets.US_ASCII).fold(0L) { acc, byte -> (acc shl 8) or byte.toLong() }
                val expected = listOf(key ushr 32, key and 0xFFFFFFFFL, 1L)

                val locks = queryRows(url, "SELECT classid, objid, objsubid FROM pg_locks WHERE $HELD_ADVISORY_LOCKS") {
                    listOf(it.getLong("classid"), it.getLong("objid"), it.getLong("objsubid"))
                }

                assertThat(locks).`as`("advisory locks held by the schema lock").containsExactly(expected)
            } finally {
                holder.release()
            }
        }
    }

    /** Polls the server until a second session waits on an advisory lock, bounded by [longWait]. */
    private fun awaitQueuedWaiter(url: String) {
        val deadline = System.nanoTime() + longWait.toNanos()
        withConnection(url) { connection ->
            while (countWaitingAdvisoryLocks(connection) == 0) {
                check(System.nanoTime() < deadline) { "No session queued on the schema lock within $longWait" }
                Thread.onSpinWait()
            }
        }
    }

    private fun countWaitingAdvisoryLocks(connection: Connection): Int =
        connection.createStatement().use { statement ->
            statement.executeQuery(
                "SELECT count(*) FROM pg_locks WHERE locktype = 'advisory' AND NOT granted AND database = $CURRENT_DATABASE_OID"
            ).use { rows ->
                rows.next()
                rows.getInt(1)
            }
        }

    private fun acquire(url: String, timeout: Duration): PostgresAdvisorySchemaLock =
        PostgresAdvisorySchemaLock.acquire(url, container.username, container.password, timeout)

    private fun <T> queryRows(url: String, sql: String, mapRow: (java.sql.ResultSet) -> T): List<T> =
        withConnection(url) { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { rows ->
                    generateSequence { if (rows.next()) mapRow(rows) else null }.toList()
                }
            }
        }

    /** Runs [block] on a database of its own, and drops that database afterwards. */
    private fun withDatabase(block: (String) -> Unit) {
        val name = "schema_lock_${UUID.randomUUID().toString().replace("-", "")}"
        withConnection(jdbcUrl(container.databaseName)) { connection ->
            connection.createStatement().use { it.execute("CREATE DATABASE \"$name\"") }
        }
        try {
            block(jdbcUrl(name))
        } finally {
            withConnection(jdbcUrl(container.databaseName)) { connection ->
                connection.createStatement().use { it.execute("DROP DATABASE IF EXISTS \"$name\" WITH (FORCE)") }
            }
        }
    }

    private fun <T> withConnection(url: String, block: (Connection) -> T): T =
        DriverManager.getConnection(url, container.username, container.password).use(block)

    private fun jdbcUrl(database: String): String =
        "jdbc:postgresql://${container.host}:${container.getMappedPort(5432)}/$database"

    private val container get() = SharedContainers.postgres

    private companion object {

        /** The database of the session that runs the query, as the oid that pg_locks uses. */
        const val CURRENT_DATABASE_OID = "(SELECT oid FROM pg_database WHERE datname = current_database())"

        /** Granted advisory locks of the database the query runs on. */
        const val HELD_ADVISORY_LOCKS = "locktype = 'advisory' AND granted AND database = $CURRENT_DATABASE_OID"

    }

}
