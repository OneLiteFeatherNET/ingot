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

/** The read side of the engine on H2, one file database per test. */
internal class H2DatabaseArtifactSearchEngineTest : DatabaseArtifactSearchEngineTest() {

    override fun open(directory: Path): OpenDatabase {
        val file = directory.resolve("search").toAbsolutePath()
        val dataSource = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = "jdbc:h2:file:$file;MODE=MYSQL"  // the schema marker uses insertIgnore, which H2 accepts in this mode
                driverClassName = "org.h2.Driver"
                maximumPoolSize = 1
                minimumIdle = 1
            }
        )
        return OpenDatabase(Database.connect(dataSource), dataSource)
    }

}
