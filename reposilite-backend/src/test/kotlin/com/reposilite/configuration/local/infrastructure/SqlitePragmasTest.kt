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

package com.reposilite.configuration.local.infrastructure

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

internal class SqlitePragmasTest {

    @TempDir
    lateinit var workingDirectory: Path

    @Test
    fun `sqlite connections read pages through a 64 MiB cache and memory mapping`() {
        val connection = DatabaseConnectionFactory.createConnection(workingDirectory, "jdbc:sqlite:${workingDirectory.resolve("pragmas.db")}", 1)

        connection.use {
            assertThat(pragma(it.database, "cache_size"))
                .`as`("cache_size in KiB, negative, so 64 MiB")
                .isEqualTo(-65536L)
            assertThat(pragma(it.database, "mmap_size"))
                .`as`("bytes of the database file that may be memory mapped")
                .isEqualTo(268435456L)
        }
    }

    @Test
    fun `sqlite url parameters are kept when the pragmas are added`() {
        val connection = DatabaseConnectionFactory.createConnection(
            workingDirectory,
            "jdbc:sqlite:${workingDirectory.resolve("parameters.db")}?busy_timeout=5000",
            1,
        )

        connection.use {
            assertThat(pragma(it.database, "busy_timeout"))
                .`as`("busy_timeout given in the url")
                .isEqualTo(5000L)
            assertThat(pragma(it.database, "cache_size"))
                .`as`("cache_size added next to the given parameter")
                .isEqualTo(-65536L)
        }
    }

    private fun pragma(database: Database, name: String): Long =
        transaction(database) {
            exec("PRAGMA $name") { rows -> rows.next(); rows.getLong(1) }!!
        }

}
