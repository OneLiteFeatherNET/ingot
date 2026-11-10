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

package net.onelitefeather.ingot.backup

import java.nio.file.Path
import javax.sql.DataSource

/**
 * Runs `VACUUM INTO` on a connection of the pool. `VACUUM INTO` reads one transaction, so the copy is consistent while
 * other connections keep writing, and it cannot run inside a transaction, which is why this does not use Exposed.
 */
class JdbcSnapshotWriter(
    private val dataSource: DataSource,
    private val busyTimeoutMillis: Int = DEFAULT_BUSY_TIMEOUT_MILLIS,
) : SnapshotWriter {

    override fun write(target: Path) {
        dataSource.connection.use { connection ->
            connection.autoCommit = true
            connection.createStatement().use { statement ->
                // A concurrent writer that holds the lock makes the snapshot wait instead of failing at once
                statement.execute("PRAGMA busy_timeout = $busyTimeoutMillis")
                statement.execute("VACUUM INTO '${target.toString().replace("'", "''")}'")
            }
        }
    }

    private companion object {
        const val DEFAULT_BUSY_TIMEOUT_MILLIS = 30_000
    }

}
