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

import com.reposilite.ReposiliteParameters
import com.reposilite.audit.api.AuditActor
import com.reposilite.audit.application.AuditComponents
import com.reposilite.audit.application.AuditSettings
import com.reposilite.configuration.local.LocalConfigurationFactory
import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import com.reposilite.journalist.Channel
import com.reposilite.journalist.backend.PrintStreamLogger
import panda.std.reactive.reference
import java.time.Clock

/** The actor that the audit log names for a snapshot taken from the command line. */
const val COMMAND_LINE_ACTOR = "command-line"

/**
 * Runs `--snapshot-database`: writes one snapshot into the backups directory, prints its name and size, and returns the
 * exit status. Nothing else of the instance starts: no plugin, no port, no scheduler.
 */
fun runDatabaseSnapshot(parameters: ReposiliteParameters): Int {
    try {
        val localConfiguration = LocalConfigurationFactory.createLocalConfiguration(null, parameters)
        parameters.applyLoadedConfiguration(localConfiguration)

        // Refused before a connection is opened, so a MariaDB or PostgreSQL instance is never contacted by this command
        if (!isSqliteDatabaseSetting(parameters.database)) {
            System.err.println("Ingot did not write a snapshot: online snapshots are supported for SQLite only. Use the dump tool of your database instead, as described in the back up and restore guide.")
            return 1
        }

        val journalist = PrintStreamLogger(System.out, System.err, Channel.ALL, false)
        return DatabaseConnectionFactory.createConnection(
            workingDirectory = parameters.workingDirectory,
            databaseConfiguration = parameters.database,
            databaseThreadPoolSize = localConfiguration.databaseThreadPool.get(),
        ).use { connection ->
            val auditFacade = AuditComponents(
                journalist = journalist,
                database = connection.database,
                settings = reference(AuditSettings()),
            ).auditFacade()

            val service = DatabaseSnapshotService(
                jdbcUrl = connection.databaseSource.jdbcUrl,
                backupDirectory = parameters.workingDirectory.resolve(BACKUP_DIRECTORY),
                clock = Clock.systemUTC(),
                writer = JdbcSnapshotWriter(connection.databaseSource),
                audit = { event ->
                    // The snapshot exists once it is written, so a failed audit line is reported and does not undo it
                    try {
                        auditFacade.record(event)
                    } catch (exception: Exception) {
                        System.err.println("Snapshot audit record could not be written: ${exception.message}")
                    }
                },
            )

            val result = service.snapshot(AuditActor.named(COMMAND_LINE_ACTOR, null))
            if (result.isOk) {
                val info = result.get()
                println("Snapshot written: ${info.fileName} (${info.sizeBytes} bytes) in ${BACKUP_DIRECTORY}/")
                0
            } else {
                System.err.println("Ingot did not write a snapshot: ${result.error.message}")
                1
            }
        }
    } catch (exception: Exception) {
        System.err.println("Ingot did not write a snapshot: ${exception.message}")
        return 1
    }
}

/** The database setting names SQLite when it starts with `sqlite` or is a raw `jdbc:sqlite:` URL. */
internal fun isSqliteDatabaseSetting(database: String): Boolean =
    database.startsWith("sqlite") || database.startsWith("jdbc:sqlite:")
