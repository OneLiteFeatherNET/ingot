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

@file:OptIn(ExperimentalIngotSearchApi::class)

package net.onelitefeather.ingot.search.backend

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import net.onelitefeather.ingot.search.database.SearchSchema
import org.jetbrains.exposed.v1.jdbc.Database
import java.nio.file.Path
import java.time.Clock

/**
 * The contract suite of search backends, run against the database backend that the registry hands out for the id `database`
 * (search backend registration, D9 and task 5.3). The backend is created by the registered factory, opened, and closed after
 * each test, so the path from the configured id to a working index is covered, access cases included.
 */
internal class DatabaseSearchBackendRegistryContractIntegrationTest : ArtifactSearchBackendContract() {

    override fun open(directory: Path, clock: Clock): Opened {
        val file = directory.resolve("registry-search").toAbsolutePath()
        val dataSource = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = "jdbc:h2:file:$file;MODE=MYSQL"
                driverClassName = "org.h2.Driver"
                maximumPoolSize = 1
                minimumIdle = 1
            }
        )
        val database = Database.connect(dataSource)
        SearchSchema.ensure(database, clock)
        val factory = DatabaseSearchBackendFactory(database, clock)
        val registry = ArtifactSearchBackendRegistry(builtIn = factory) { }
        val resolution = registry.resolve(factory.id)
        check(resolution is BackendResolution.Installed) { "the registry resolves the database id to its built-in factory" }
        val backend = resolution.factory.create(CoreSearchBackendContext(clock, "contract-instance", factory.id) { _, _ -> })
        backend.open()
        return Opened(backend) {
            backend.close()
            dataSource.close()
        }
    }

}
