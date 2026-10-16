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
 * One connection to a SQLite file inside a test's temporary directory. Two of these on the same file stand in for two
 * Ingot instances that share a database. Unlike the shared in-memory helper, the data source is created here, so no
 * global transaction setting is touched.
 */
internal class SqliteFileDatabase(file: Path) : AutoCloseable {

    private val dataSource = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = "jdbc:sqlite:${file.toAbsolutePath()}"
            driverClassName = "org.sqlite.JDBC"
            maximumPoolSize = 1
            minimumIdle = 1
        }
    )

    val database: Database = Database.connect(dataSource)

    override fun close() = dataSource.close()

}
