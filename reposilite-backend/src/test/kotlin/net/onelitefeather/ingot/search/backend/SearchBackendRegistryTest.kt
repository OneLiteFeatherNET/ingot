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
            val factory = DatabaseSearchBackendFactory(sqlite.database, Clock.systemUTC())
            val registry = ArtifactSearchBackendRegistry(builtIn = factory, warn = {})

            val resolution = registry.resolve("database") as? BackendResolution.Installed
            assertThat(resolution).`as`("resolution of the id database").isNotNull()
            val backend = resolution!!.factory.create(UnusedContext)

            assertThat(backend).`as`("backend of the id database").isInstanceOf(DatabaseSearchBackend::class.java)
        }
    }

    @Test
    fun `an unknown id resolves to not installed`() {
        SqliteFileDatabase(directory.resolve("unknown.db")).use { sqlite ->
            val registry = ArtifactSearchBackendRegistry(
                builtIn = DatabaseSearchBackendFactory(sqlite.database, Clock.systemUTC()),
                warn = {},
            )

            assertThat(registry.resolve("opensearch")).`as`("resolution of an unknown id")
                .isInstanceOf(BackendResolution.Unavailable::class.java)
        }
    }

    @Test
    fun `the id is matched exactly, as the local configuration has always been`() {
        SqliteFileDatabase(directory.resolve("case.db")).use { sqlite ->
            val registry = ArtifactSearchBackendRegistry(
                builtIn = DatabaseSearchBackendFactory(sqlite.database, Clock.systemUTC()),
                warn = {},
            )

            assertThat(registry.resolve("Database")).`as`("resolution of a differently cased id")
                .isInstanceOf(BackendResolution.Unavailable::class.java)
        }
    }

    private object UnusedContext : SearchBackendContext {
        override val clock: Clock = Clock.systemUTC()
        override val instanceId: String = "test-instance"
        override fun environment(name: String): String? = null
        override fun secret(name: String): String? = null
        override fun logger(): BackendLogger = object : BackendLogger {
            override fun info(message: String) = Unit
            override fun warn(message: String) = Unit
            override fun error(message: String) = Unit
        }
    }

}
