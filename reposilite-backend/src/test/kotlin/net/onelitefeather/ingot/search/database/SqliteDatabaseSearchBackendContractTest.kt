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

import net.onelitefeather.ingot.search.backend.ArtifactSearchBackendContract
import java.nio.file.Path
import java.time.Clock

/** The contract on the database backend, on SQLite in a file of the test's temporary directory. */
internal class SqliteDatabaseSearchBackendContractTest : ArtifactSearchBackendContract() {

    override fun open(directory: Path, clock: Clock): Opened {
        val sqlite = SqliteFileDatabase(directory.resolve("search.db"))
        SearchSchema.ensure(sqlite.database, clock)
        return Opened(DatabaseSearchBackend(sqlite.database, clock)) { sqlite.close() }
    }

}
