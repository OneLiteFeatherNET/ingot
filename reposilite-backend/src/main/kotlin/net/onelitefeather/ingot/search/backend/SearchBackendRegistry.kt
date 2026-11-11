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
import org.jetbrains.exposed.v1.jdbc.Database
import java.time.Clock

/** Builds a backend on the database of the instance. */
internal typealias SearchBackendFactory = (Database, Clock) -> ArtifactSearchBackend

/**
 * Resolves the local key `searchEngine` to a backend (search backend seam, D9). The registry holds the database only. An
 * unknown id resolves to null, and the plugin then keeps search switched off, as it always has. The match is exact.
 */
internal object SearchBackendRegistry {

    private val factories: Map<String, SearchBackendFactory> = mapOf(
        DatabaseSearchBackend.ID to { database, clock -> DatabaseSearchBackend(database, clock) },
    )

    /** The factory of the backend called [id], or null when no backend has that id. */
    fun factoryOf(id: String): SearchBackendFactory? = factories[id]

}
