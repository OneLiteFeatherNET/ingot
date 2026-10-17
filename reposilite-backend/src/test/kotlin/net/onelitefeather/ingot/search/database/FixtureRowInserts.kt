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

import net.onelitefeather.ingot.search.access.FixtureRow
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.Locale

/**
 * Inserts [rows] into [table] in one transaction, with the derived columns the index writer would set. The rows go in through
 * the table and not the index writer: H2 in the MySQL mode the production connection uses rejects the upsert with conflict
 * keys, and the row filter does not depend on how a row got there.
 */
internal fun insertFixtureRows(database: Database, table: SearchArtifactTable, rows: List<FixtureRow>, indexedAt: Long) {
    transaction(database) {
        rows.forEach { row ->
            table.insert {
                it[table.repository] = row.repository
                it[table.path] = row.path
                it[table.pathLc] = row.path.lowercase(Locale.ROOT)
                it[table.pathHash] = pathHash(row.repository, row.path)
                it[table.groupId] = "net.onelitefeather"
                it[table.groupIdLc] = "net.onelitefeather"
                it[table.artifactId] = "mycelium-bom"
                it[table.artifactIdLc] = "mycelium-bom"
                it[table.version] = "1.0"
                it[table.versionLc] = "1.0"
                it[table.extension] = "jar"
                it[table.snapshot] = false
                it[table.origin] = "HOSTED"
                it[table.sizeBytes] = 1L
                it[table.lastModified] = 1L
                it[table.indexedAt] = indexedAt
            }
        }
    }
}
