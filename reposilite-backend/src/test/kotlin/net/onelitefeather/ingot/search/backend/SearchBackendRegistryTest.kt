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

package net.onelitefeather.ingot.search.backend

import net.onelitefeather.ingot.search.database.DatabaseSearchBackend
import net.onelitefeather.ingot.search.database.SqliteFileDatabase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Clock

internal class SearchBackendRegistryTest {

    @TempDir
    lateinit var directory: Path

    @Test
    fun `the id database resolves to the database backend`() {
        SqliteFileDatabase(directory.resolve("registry.db")).use { sqlite ->
            val factory = SearchBackendRegistry.factoryOf("database")

            val backend = factory?.invoke(sqlite.database, Clock.systemUTC())

            assertThat(backend).`as`("backend of the id database").isInstanceOf(DatabaseSearchBackend::class.java)
        }
    }

    @Test
    fun `an unknown id resolves to no backend`() {
        assertThat(SearchBackendRegistry.factoryOf("opensearch")).`as`("factory of an unknown id").isNull()
    }

    @Test
    fun `the id is matched exactly, as the local configuration has always been`() {
        assertThat(SearchBackendRegistry.factoryOf("Database")).`as`("factory of a differently cased id").isNull()
    }

}
