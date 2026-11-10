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

package net.onelitefeather.ingot.backup

import com.reposilite.audit.api.AuditAction
import com.reposilite.audit.api.AuditActor
import com.reposilite.audit.api.AuditEvent
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.javalin.http.HttpStatus.CONFLICT
import io.javalin.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.sql.SQLException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS

internal class DatabaseSnapshotServiceTest {

    @TempDir
    lateinit var workingDirectory: Path

    private val clock = Clock.fixed(Instant.parse("2026-11-09T12:00:00Z"), ZoneOffset.UTC)
    private val actor = AuditActor.named("manager", "127.0.0.1")
    private val backups: Path
        get() = workingDirectory.resolve(BACKUP_DIRECTORY)
    private val sqliteUrl: String
        get() = "jdbc:sqlite:${workingDirectory.resolve("reposilite.db")}"
    private val emitted = mutableListOf<AuditEvent>()

    private fun service(writer: SnapshotWriter, jdbcUrl: String = sqliteUrl) =
        DatabaseSnapshotService(
            jdbcUrl = jdbcUrl,
            backupDirectory = backups,
            clock = clock,
            writer = writer,
            audit = { emitted += it },
        )

    @Test
    fun `should name the snapshot after the UTC time of the injected clock`() {
        // when: a snapshot is taken with a clock fixed at 2026-11-09T12:00:00Z
        val result = service(ContentWriter("snapshot")).snapshot(actor)

        // then: the file is named after that instant and the answer carries the same instant
        assertThat(result.isOk).`as`("snapshot succeeds").isTrue
        assertThat(result.get().fileName).`as`("file name from the fixed clock").isEqualTo("ingot-snapshot-20261109-120000.db")
        assertThat(result.get().createdAt).`as`("creation time from the fixed clock").isEqualTo(clock.instant())
        assertThat(Files.readString(backups.resolve(result.get().fileName))).`as`("written content").isEqualTo("snapshot")
    }

    @Test
    fun `should refuse a snapshot whose file name is already taken and keep the existing bytes`() {
        // given: a snapshot with the name the clock produces already exists
        Files.createDirectories(backups)
        val existing = backups.resolve("ingot-snapshot-20261109-120000.db")
        Files.writeString(existing, "older snapshot")

        // when: another snapshot is taken at the same instant
        val result = service(ContentWriter("newer snapshot")).snapshot(actor)

        // then: the request is refused as a conflict and the existing file keeps its bytes
        assertThat(result.isErr).`as`("taken name is refused").isTrue
        assertThat(result.error.status).`as`("status of the refusal").isEqualTo(CONFLICT.code)
        assertThat(Files.readString(existing)).`as`("existing snapshot keeps its bytes").isEqualTo("older snapshot")
        assertThat(emitted).`as`("no audit event for a refused snapshot").isEmpty()
    }

    @Test
    fun `should refuse a database that is not SQLite, write nothing and name the dump tool`() {
        // when: a snapshot is requested for a MariaDB instance
        val result = service(UnusedWriter, jdbcUrl = "jdbc:mariadb://db.internal/ingot").snapshot(actor)

        // then: the request is a conflict that names the documented dump tool, and no file is written
        assertThat(result.isErr).`as`("non-SQLite database is refused").isTrue
        assertThat(result.error.status).`as`("status of the refusal").isEqualTo(CONFLICT.code)
        assertThat(result.error.message).`as`("refusal names the dump tool").contains("mariadb-dump")
        assertThat(Files.exists(backups)).`as`("no backups directory is created").isFalse
    }

    @Test
    fun `should not create the backups directory until a snapshot is requested`() {
        // when: the service is built but no snapshot is requested
        service(UnusedWriter)

        // then: nothing is written into the working directory
        assertThat(Files.exists(backups)).`as`("backups directory before any request").isFalse
    }

    @Test
    fun `should remove the partial file when writing the snapshot fails`() {
        // when: the writer fails part way through the file
        val result = service(FailingWriter).snapshot(actor)

        // then: the request fails, no partial file is left, and nothing is audited
        assertThat(result.isErr).`as`("failed write is an error").isTrue
        assertThat(result.error.status).`as`("status of the failure").isEqualTo(INTERNAL_SERVER_ERROR.code)
        assertThat(Files.exists(backups.resolve("ingot-snapshot-20261109-120000.db"))).`as`("partial file is removed").isFalse
        assertThat(emitted).`as`("no audit event for a failed snapshot").isEmpty()
    }

    @Test
    fun `should audit a written snapshot with the acting principal and the file name`() {
        // when: a snapshot is written
        service(ContentWriter("snapshot")).snapshot(actor)

        // then: exactly one audit event names the principal and the file, and no path
        assertThat(emitted).`as`("audit events of one snapshot").hasSize(1)
        val event = emitted.single()
        assertThat(event.action).`as`("audit action").isEqualTo(AuditAction.DATABASE_SNAPSHOT_CREATED)
        assertThat(event.actor).`as`("acting principal").isEqualTo(actor)
        assertThat(event.details).`as`("audited file name").containsEntry("file", "ingot-snapshot-20261109-120000.db")
    }

    @Test
    fun `should write a snapshot that opens, passes the integrity check and holds the committed rows`() {
        // given: a SQLite database with ten committed rows
        val source = sourceDatabase()
        try {
            // when: a snapshot is taken with the JDBC writer
            val result = service(JdbcSnapshotWriter(source), jdbcUrl = source.jdbcUrl).snapshot(actor)

            // then: the snapshot is a standalone database with the same rows
            assertThat(result.isOk).`as`("snapshot succeeds").isTrue
            val snapshot = backups.resolve(result.get().fileName)
            assertThat(integrityCheck(snapshot)).`as`("integrity check of the snapshot").isEqualTo("ok")
            assertThat(rowCount(snapshot)).`as`("committed rows in the snapshot").isEqualTo(ROWS_BEFORE_SNAPSHOT)
        } finally {
            source.close()
        }
    }

    @Test
    fun `should write a snapshot without the open write of a concurrent transaction and stay consistent`() {
        // given: a database with ten committed rows, and a writer that holds an open transaction with one more row
        val source = sourceDatabase()
        val pendingWritten = CountDownLatch(1)
        val snapshotTaken = CountDownLatch(1)
        val writer = Executors.newSingleThreadExecutor()
        try {
            val pendingCommit = writer.submit<Boolean> {
                source.connection.use { connection ->
                    connection.autoCommit = false
                    connection.createStatement().use { it.executeUpdate("INSERT INTO items (value) VALUES ('open write')") }
                    pendingWritten.countDown()
                    val released = snapshotTaken.await(WAIT_SECONDS, SECONDS)
                    connection.commit()
                    released
                }
            }
            assertThat(pendingWritten.await(WAIT_SECONDS, SECONDS)).`as`("writer holds an open transaction").isTrue

            // when: the snapshot is taken while the transaction is still open
            val result = service(JdbcSnapshotWriter(source), jdbcUrl = source.jdbcUrl).snapshot(actor)
            snapshotTaken.countDown()

            // then: the snapshot succeeds, is consistent and holds only the rows committed before it started
            assertThat(pendingCommit.get(WAIT_SECONDS, SECONDS)).`as`("writer was released after the snapshot").isTrue
            assertThat(result.isOk).`as`("snapshot during the open transaction succeeds").isTrue
            val snapshot = backups.resolve(result.get().fileName)
            assertThat(integrityCheck(snapshot)).`as`("integrity check of the snapshot").isEqualTo("ok")
            assertThat(rowCount(snapshot)).`as`("rows of the snapshot excludes the open write").isEqualTo(ROWS_BEFORE_SNAPSHOT)
            assertThat(rowCount(workingDirectory.resolve("source.db"))).`as`("the open write is committed afterwards").isEqualTo(ROWS_BEFORE_SNAPSHOT + 1)
        } finally {
            snapshotTaken.countDown()
            writer.shutdownNow()
            source.close()
        }
    }

    private fun sourceDatabase(): HikariDataSource {
        val source = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = "jdbc:sqlite:${workingDirectory.resolve("source.db")}"
                driverClassName = "org.sqlite.JDBC"
                maximumPoolSize = 2
            }
        )
        source.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("CREATE TABLE items (id INTEGER PRIMARY KEY, value TEXT)")
                repeat(ROWS_BEFORE_SNAPSHOT) { statement.execute("INSERT INTO items (value) VALUES ('row $it')") }
            }
        }
        return source
    }

    private fun integrityCheck(database: Path): String =
        DriverManager.getConnection("jdbc:sqlite:$database").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA integrity_check").use { rows ->
                    rows.next()
                    rows.getString(1)
                }
            }
        }

    private fun rowCount(database: Path): Int =
        DriverManager.getConnection("jdbc:sqlite:$database").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM items").use { rows ->
                    rows.next()
                    rows.getInt(1)
                }
            }
        }

    /** Stands in for a successful `VACUUM INTO`: it writes the given content into the target. */
    private class ContentWriter(private val content: String) : SnapshotWriter {
        override fun write(target: Path) {
            Files.writeString(target, content)
        }
    }

    /** Stands in for a `VACUUM INTO` that fails after part of the file is written. */
    private object FailingWriter : SnapshotWriter {
        override fun write(target: Path) {
            Files.writeString(target, "partial")
            throw SQLException("database is locked")
        }
    }

    /** A writer that must never run, because the database is refused before any write. */
    private object UnusedWriter : SnapshotWriter {
        override fun write(target: Path) {
            throw AssertionError("the writer must not run for this database, target: $target")
        }
    }

    private companion object {
        const val ROWS_BEFORE_SNAPSHOT = 10
        const val WAIT_SECONDS = 10L
    }

}
