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

import com.reposilite.createFromParameters
import com.reposilite.parseParameters
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager

internal class DatabaseSnapshotCommandTest {

    @TempDir
    lateinit var workingDirectory: Path

    private val snapshotName = Regex("ingot-snapshot-\\d{8}-\\d{6}\\.db")

    @Test
    fun `should select the snapshot run for the flag and start no server`() {
        // when: the flag is parsed
        val parameters = parseParameters("--snapshot-database", "--working-directory=$workingDirectory")

        // then: the flag is set, and the launch path returns no instance to start
        assertThat(parameters.snapshotDatabaseRequested).`as`("snapshot flag is parsed").isTrue
        assertThat(createFromParameters(parameters)).`as`("no server instance is created for a snapshot run").isNull()
    }

    @Test
    fun `should write one snapshot into the backups directory and exit with status zero`() {
        // when: the snapshot command runs against the SQLite database of the working directory
        val status = runDatabaseSnapshot(parseParameters("--snapshot-database", "--working-directory=$workingDirectory", "--database=sqlite reposilite.db"))

        // then: the command succeeds and one snapshot file is written
        assertThat(status).`as`("exit status of a successful snapshot").isEqualTo(0)
        val snapshots = Files.list(workingDirectory.resolve(BACKUP_DIRECTORY)).use { files -> files.toList() }
        assertThat(snapshots.map { it.fileName.toString() }).`as`("snapshot files").hasSize(1)
        assertThat(snapshotName.matches(snapshots.single().fileName.toString())).`as`("snapshot file name").isTrue
    }

    @Test
    fun `should audit the command line snapshot in the audit log`() {
        // when: the snapshot command runs
        runDatabaseSnapshot(parseParameters("--snapshot-database", "--working-directory=$workingDirectory", "--database=sqlite reposilite.db"))

        // then: the audit table of the database holds one snapshot event
        val actions = DriverManager.getConnection("jdbc:sqlite:${workingDirectory.resolve("reposilite.db")}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT action FROM audit_events").use { rows ->
                    generateSequence { if (rows.next()) rows.getString(1) else null }.toList()
                }
            }
        }
        assertThat(actions).`as`("audited actions").containsExactly("DATABASE_SNAPSHOT_CREATED")
    }

    @Test
    fun `should refuse a database that is not SQLite with status one and write no backups directory`() {
        // when: the snapshot command is pointed at a MariaDB database
        val status = runDatabaseSnapshot(parseParameters("--snapshot-database", "--working-directory=$workingDirectory", "--database=mariadb://db.internal/ingot"))

        // then: the command fails without connecting and without writing a file
        assertThat(status).`as`("exit status of a refused snapshot").isEqualTo(1)
        assertThat(Files.exists(workingDirectory.resolve(BACKUP_DIRECTORY))).`as`("no backups directory is created").isFalse
    }

}
