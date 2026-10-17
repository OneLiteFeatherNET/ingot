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

import net.onelitefeather.ingot.search.ArtifactFileRecord
import net.onelitefeather.ingot.search.Origin
import net.onelitefeather.ingot.search.outbox.OutboxOp
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Clock
import java.util.Locale

/**
 * Writes rows straight into a test database, so a test states the rows it reads and nothing else. The rows are plain
 * inserts, not the production upsert, so the read side of the engine is tested without depending on the writer. The
 * state rows go through the production store.
 */
internal class IndexFixture(private val database: Database, private val clock: Clock) {

    private val tables = SearchTables(SearchDialect.of(database.vendor))
    private val state = SearchStateStore(database, clock)

    fun putState(key: String, value: String) = state.put(key, value)

    /** One stored file, with the columns the production writer derives from it. */
    fun insertFile(
        file: ArtifactFileRecord,
        origin: Origin,
        sourceHost: String?,
        sizeBytes: Long,
        lastModified: Long,
        sha1: String?,
        indexedAt: Long,
    ) {
        val rows = tables.artifact
        transaction(database) {
            rows.insert {
                it[rows.repository] = file.repository
                it[rows.path] = file.path
                it[rows.pathLc] = file.path.lowercase(Locale.ROOT)
                it[rows.pathHash] = pathHash(file.repository, file.path)
                it[rows.groupId] = file.groupId
                it[rows.groupIdLc] = file.groupId.lowercase(Locale.ROOT)
                it[rows.artifactId] = file.artifactId
                it[rows.artifactIdLc] = file.artifactId.lowercase(Locale.ROOT)
                it[rows.version] = file.version
                it[rows.versionLc] = file.version.lowercase(Locale.ROOT)
                it[rows.classifier] = file.classifier
                it[rows.extension] = file.extension.lowercase(Locale.ROOT)
                it[rows.snapshot] = file.snapshot
                it[rows.origin] = origin.name
                it[rows.sourceHost] = sourceHost
                it[rows.sizeBytes] = sizeBytes
                it[rows.lastModified] = lastModified
                it[rows.sha1] = sha1
                it[rows.sha256] = null
                it[rows.indexedAt] = indexedAt
            }
        }
    }

    /** One pending or dead outbox row with the given creation time in epoch milliseconds and attempts. */
    fun enqueue(createdAtMillis: Long, attempts: Int) {
        transaction(database) {
            tables.outbox.insert {
                it[tables.outbox.repository] = "releases"
                it[tables.outbox.path] = "com/example/app/1.0.0/app-1.0.0.jar"
                it[tables.outbox.op] = OutboxOp.CHANGED_PATH.name
                it[tables.outbox.createdAt] = createdAtMillis
                it[tables.outbox.attempts] = attempts
            }
        }
    }

    /** [count] distinct groups in one artifact, one file each, in one transaction. */
    fun insertGroups(count: Int, lastModified: Long, indexedAt: Long) {
        val rows = tables.artifact
        transaction(database) {
            rows.batchInsert((1..count).toList()) { version ->
                val path = "bulk/lib/1.0.$version/lib-1.0.$version.jar"
                this[rows.repository] = "releases"
                this[rows.path] = path
                this[rows.pathLc] = path
                this[rows.pathHash] = pathHash("releases", path)
                this[rows.groupId] = "bulk"
                this[rows.groupIdLc] = "bulk"
                this[rows.artifactId] = "lib"
                this[rows.artifactIdLc] = "lib"
                this[rows.version] = "1.0.$version"
                this[rows.versionLc] = "1.0.$version"
                this[rows.classifier] = null
                this[rows.extension] = "jar"
                this[rows.snapshot] = false
                this[rows.origin] = Origin.HOSTED.name
                this[rows.sourceHost] = null
                this[rows.sizeBytes] = 1L
                this[rows.lastModified] = lastModified
                this[rows.sha1] = null
                this[rows.sha256] = null
                this[rows.indexedAt] = indexedAt
            }
        }
    }

}
