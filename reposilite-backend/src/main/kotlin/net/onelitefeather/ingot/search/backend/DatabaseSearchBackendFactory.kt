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
import org.jetbrains.exposed.v1.jdbc.Database
import java.time.Clock

/** The built-in factory of the `database` backend. It is registered before any plugin and cannot be replaced. */
internal class DatabaseSearchBackendFactory(
    private val database: Database,
    private val clock: Clock,
) : ArtifactSearchBackendFactory {

    override val id: String = DatabaseSearchBackend.ID

    override val apiVersion: Int = SEARCH_BACKEND_API_VERSION

    override fun create(context: SearchBackendContext): ArtifactSearchBackend = DatabaseSearchBackend(database, clock)

}
