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

package com.reposilite.maven.infrastructure

import com.reposilite.maven.MAX_PROVENANCE_PATH_LENGTH
import com.reposilite.maven.MAX_PROVENANCE_REPOSITORY_LENGTH
import com.reposilite.maven.MirrorCacheLedger
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant

/**
 * One row per cached version directory. The table is created with the instance database on first start, like the
 * other tables of the instance; an existing database gains it without any migration step.
 */
internal object MirrorCacheEntryTable : Table("mirror_cache_entry") {
    val repositoryId = varchar("repository", MAX_PROVENANCE_REPOSITORY_LENGTH)
    val entryPath = varchar("path", MAX_PROVENANCE_PATH_LENGTH)
    val cachedAt = long("cached_at")
    val lastAccessedAt = long("last_accessed_at")

    override val primaryKey = PrimaryKey(repositoryId, entryPath)
}

/**
 * Keeps the provenance of mirror copies in the instance database.
 */
internal class SqlMirrorCacheLedger(private val database: Database) : MirrorCacheLedger {

    init {
        transaction(database) {
            SchemaUtils.create(MirrorCacheEntryTable)
        }
    }

    /**
     * A single INSERT ... IGNORE (or ON CONFLICT DO NOTHING), so the primary key decides the race between instances
     * that share the database. A row that another instance inserted first is left untouched.
     */
    override fun insertIfAbsent(repository: String, path: String, now: Instant) {
        transaction(database) {
            MirrorCacheEntryTable.insertIgnore {
                it[repositoryId] = repository
                it[entryPath] = path
                it[cachedAt] = now.toEpochMilli()
                it[lastAccessedAt] = now.toEpochMilli()
            }
        }
    }

    override fun contains(repository: String, path: String): Boolean =
        transaction(database) { entryExists(repository, path) }

    /**
     * Conditional UPDATE: the row only moves forward. Two instances whose clocks or requests interleave cannot
     * overwrite a newer last access with an older one.
     */
    override fun markAccessed(repository: String, path: String, now: Instant): Boolean =
        transaction(database) {
            MirrorCacheEntryTable.update({
                (MirrorCacheEntryTable.repositoryId eq repository) and
                    (MirrorCacheEntryTable.entryPath eq path) and
                    (MirrorCacheEntryTable.lastAccessedAt less now.toEpochMilli())
            }) {
                it[lastAccessedAt] = now.toEpochMilli()
            } > 0
        }

    override fun deleteVersion(repository: String, path: String) {
        transaction(database) {
            MirrorCacheEntryTable.deleteWhere { (repositoryId eq repository) and (entryPath eq path) }
        }
    }

    override fun deleteSubtree(repository: String, path: String) {
        transaction(database) {
            val entries = MirrorCacheEntryTable.selectAll()
                .where { MirrorCacheEntryTable.repositoryId eq repository }
                .map { it[MirrorCacheEntryTable.entryPath] }
                .filter { it == path || it.startsWith("$path/") }

            entries.forEach { entry ->
                MirrorCacheEntryTable.deleteWhere { (repositoryId eq repository) and (entryPath eq entry) }
            }
        }
    }

    override fun find(repository: String): Map<String, Instant> =
        transaction(database) {
            MirrorCacheEntryTable.selectAll()
                .where { MirrorCacheEntryTable.repositoryId eq repository }
                .associate { row ->
                    row[MirrorCacheEntryTable.entryPath] to Instant.ofEpochMilli(row[MirrorCacheEntryTable.lastAccessedAt])
                }
        }

    private fun entryExists(repository: String, path: String): Boolean =
        MirrorCacheEntryTable.selectAll()
            .where { (MirrorCacheEntryTable.repositoryId eq repository) and (MirrorCacheEntryTable.entryPath eq path) }
            .any()

}
