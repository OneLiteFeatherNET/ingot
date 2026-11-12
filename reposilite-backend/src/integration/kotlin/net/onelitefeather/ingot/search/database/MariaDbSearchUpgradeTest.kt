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

import com.reposilite.IsolatedResources
import com.reposilite.SharedContainers
import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import com.reposilite.databaseToken
import java.nio.file.Path

/** The upgrade contract on MariaDB, in a database of its own that the test drops afterwards. */
internal class MariaDbSearchUpgradeTest : SearchUpgradeContract() {

    override fun open(directory: Path): Opened {
        val name = IsolatedResources.createMariaDbDatabase(SharedContainers.mariaDb)
        val connection = DatabaseConnectionFactory.createConnection(directory, SharedContainers.mariaDb.databaseToken(name), 2)
        return Opened(connection.database) {
            connection.close()
            IsolatedResources.dropMariaDbDatabase(SharedContainers.mariaDb, name)
        }
    }

}
