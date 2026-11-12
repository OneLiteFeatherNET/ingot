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

@file:OptIn(net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi::class)

package net.onelitefeather.ingot.search.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import net.onelitefeather.ingot.search.backend.ArtifactSearchBackendContract
import org.jetbrains.exposed.v1.jdbc.Database
import java.nio.file.Path
import java.time.Clock

/** The contract on the database backend, on H2 in MySQL mode in a file of the test's temporary directory. */
internal class H2DatabaseSearchBackendContractTest : ArtifactSearchBackendContract() {

    override fun open(directory: Path, clock: Clock): Opened {
        val file = directory.resolve("search").toAbsolutePath()
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
        return Opened(DatabaseSearchBackend(database, clock)) { dataSource.close() }
    }

}
