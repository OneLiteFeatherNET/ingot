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

import com.reposilite.IsolatedResources
import com.reposilite.SharedContainers
import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import com.reposilite.databaseToken
import net.onelitefeather.ingot.search.backend.AttributeQueryContract
import java.nio.file.Path
import java.time.Clock

/** The attribute query contract on the database backend, on MariaDB, in a database of its own that the test drops afterwards. */
internal class MariaDbAttributeQueryContractTest : AttributeQueryContract() {

    override fun open(directory: Path, clock: Clock): Opened {
        val name = IsolatedResources.createMariaDbDatabase(SharedContainers.mariaDb)
        val connection = DatabaseConnectionFactory.createConnection(directory, SharedContainers.mariaDb.databaseToken(name), 2)
        val database = connection.database
        SearchSchema.ensure(database, clock)
        return Opened(DatabaseSearchBackend(database, clock)) {
            connection.close()
            IsolatedResources.dropMariaDbDatabase(SharedContainers.mariaDb, name)
        }
    }

}
