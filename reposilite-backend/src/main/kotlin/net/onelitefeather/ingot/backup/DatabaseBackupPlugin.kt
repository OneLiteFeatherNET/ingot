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

import com.reposilite.audit.api.AuditEvent
import com.reposilite.plugin.api.Facade
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.ReposilitePlugin
import com.reposilite.plugin.event
import com.reposilite.plugin.reposilite
import com.reposilite.web.api.RoutingSetupEvent
import java.time.Clock

/** Registers the snapshot endpoint. The command line flag does not load this plugin. */
@Plugin(name = "database-backup")
internal class DatabaseBackupPlugin : ReposilitePlugin() {

    override fun initialize(): Facade? {
        val instance = reposilite()
        val connection = instance.databaseConnection

        val service = DatabaseSnapshotService(
            jdbcUrl = connection.databaseSource.jdbcUrl,
            backupDirectory = instance.parameters.workingDirectory.resolve(BACKUP_DIRECTORY),
            clock = Clock.systemUTC(),
            writer = JdbcSnapshotWriter(connection.databaseSource),
            audit = { event: AuditEvent -> extensions().emitEvent(event) },
        )

        event { event: RoutingSetupEvent ->
            event.registerRoutes(DatabaseBackupEndpoints(service))
        }

        return null
    }

}
