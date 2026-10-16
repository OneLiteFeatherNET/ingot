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
package net.onelitefeather.ingot.search.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.v1.jdbc.Database
import java.nio.file.Path

/**
 * One H2 database in a test's temporary directory, the H2 counterpart of [SqliteFileDatabase], in the MySQL mode that
 * the production connection uses (the index writer relies on INSERT IGNORE). The data source belongs to
 * this instance, so closing it releases the file and no global connection state is shared between tests.
 */
internal class H2FileDatabase(file: Path) : AutoCloseable {

    private val dataSource = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = "jdbc:h2:${file.toAbsolutePath()};MODE=MYSQL"
            driverClassName = "org.h2.Driver"
            maximumPoolSize = 1
            minimumIdle = 1
        }
    )

    val database: Database = Database.connect(dataSource)

    override fun close() = dataSource.close()

}
