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

import net.onelitefeather.ingot.search.SearchColumn
import org.jetbrains.exposed.v1.core.Table

/**
 * The search tables of artifact search spec, section 2. The tables take the collation of the binary text columns from
 * the [SearchDialect] they are created for, so one set of tables describes one database. Their names and columns are
 * the contract of the spec; the classes only exist because the collation differs per vendor.
 */
internal class SearchTables(dialect: SearchDialect) {

    private val binary = dialect.binaryCollation

    /** One row per stored file (section 2.3). */
    val artifact = SearchArtifactTable(binary)

    /** The durable queue of index work (section 2.5). */
    val outbox = SearchOutboxTable(binary)

    /** Rebuild and verify runs (section 2.6). */
    val rebuildRun = SearchRebuildRunTable()

    /** Key and value state: heartbeat, schema marker, locks, counters (section 2.7). */
    val state = SearchStateTable()

    /** Every table in creation order. */
    fun all(): List<Table> = listOf(state, artifact, outbox, rebuildRun)

}

internal class SearchArtifactTable(binary: String?) : Table("search_artifact") {
    val id = long("id").autoIncrement()
    val repository = varchar("repository", SearchColumn.REPOSITORY.limit, binary)
    val path = varchar("path", SearchColumn.PATH.limit, binary)
    val pathLc = varchar("path_lc", SearchColumn.PATH.limit, binary)
    val pathHash = char("path_hash", PATH_HASH_LENGTH)
    val groupId = varchar("group_id", SearchColumn.GROUP_ID.limit)
    val groupIdLc = varchar("group_id_lc", SearchColumn.GROUP_ID.limit, binary)
    val artifactId = varchar("artifact_id", SearchColumn.ARTIFACT_ID.limit)
    val artifactIdLc = varchar("artifact_id_lc", SearchColumn.ARTIFACT_ID.limit, binary)
    val version = varchar("version", SearchColumn.VERSION.limit)
    val versionLc = varchar("version_lc", SearchColumn.VERSION.limit, binary)
    val classifier = varchar("classifier", SearchColumn.CLASSIFIER.limit).nullable()
    val extension = varchar("extension", SearchColumn.EXTENSION.limit)
    val snapshot = bool("snapshot")
    val origin = varchar("origin", ORIGIN_LENGTH)
    val sourceHost = varchar("source_host", SOURCE_HOST_LENGTH).nullable()
    val sizeBytes = long("size_bytes")
    val lastModified = long("last_modified")
    val sha1 = char("sha1", SHA1_LENGTH).nullable()
    val sha256 = char("sha256", SHA256_LENGTH).nullable()
    val indexedAt = long("indexed_at")

    override val primaryKey = PrimaryKey(id)

    init {
        index(customIndexName = "uq_search_artifact_path", isUnique = true, repository, pathHash)
        index(customIndexName = "ix_search_gav", isUnique = false, groupIdLc, artifactIdLc, versionLc)
        index(customIndexName = "ix_search_artifact", isUnique = false, artifactIdLc)
        index(customIndexName = "ix_search_version", isUnique = false, versionLc)
        index(customIndexName = "ix_search_repo_origin", isUnique = false, repository, origin)
        index(customIndexName = "ix_search_sha1", isUnique = false, sha1)
        index(customIndexName = "ix_search_modified", isUnique = false, lastModified)
        index(customIndexName = "ix_search_extension", isUnique = false, extension)
        index(customIndexName = "ix_search_path", isUnique = false, repository, path)
        index(customIndexName = "ix_search_path_lc", isUnique = false, repository, pathLc)
    }

    companion object {
        const val PATH_HASH_LENGTH = 64
        const val SHA1_LENGTH = 40
        const val SHA256_LENGTH = 64
        const val ORIGIN_LENGTH = 8
        const val SOURCE_HOST_LENGTH = 255
    }

}

internal class SearchOutboxTable(binary: String?) : Table("search_outbox") {
    val id = long("id").autoIncrement()
    val repository = varchar("repository", SearchColumn.REPOSITORY.limit, binary)
    val path = varchar("path", SearchColumn.PATH.limit, binary)
    val op = varchar("op", OP_LENGTH)
    val createdAt = long("created_at")
    val leaseOwner = varchar("lease_owner", LEASE_OWNER_LENGTH).nullable()
    val leaseUntil = long("lease_until").nullable()
    val attempts = integer("attempts").default(0)
    val lastError = varchar("last_error", LAST_ERROR_LENGTH).nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        index(customIndexName = "ix_search_outbox_lease", isUnique = false, leaseUntil, id)
        index(customIndexName = "ix_search_outbox_path", isUnique = false, repository, path)
    }

    companion object {
        const val OP_LENGTH = 24
        const val LEASE_OWNER_LENGTH = 64
        const val LAST_ERROR_LENGTH = 512
    }

}

internal class SearchRebuildRunTable : Table("search_rebuild_run") {
    val id = long("id").autoIncrement()
    val kind = varchar("kind", 8)
    val trigger = varchar("trigger", 12)
    val status = varchar("status", 12)
    val startedAt = long("started_at")
    val finishedAt = long("finished_at").nullable()
    val leaseOwner = varchar("lease_owner", 64).nullable()
    val leaseUntil = long("lease_until").nullable()
    val runStartedAt = long("run_started_at")
    val repositories = varchar("repositories", REPOSITORIES_LENGTH)
    val cursorRepository = varchar("cursor_repository", SearchColumn.REPOSITORY.limit).nullable()
    val cursorPath = varchar("cursor_path", SearchColumn.PATH.limit).nullable()
    val processed = long("processed")
    val upserted = long("upserted")
    val deleted = long("deleted")
    val unparsed = long("unparsed")
    val skippedTooLong = long("skipped_too_long")
    val errors = long("errors")
    val lastError = varchar("last_error", 512).nullable()

    override val primaryKey = PrimaryKey(id)

    companion object {
        const val REPOSITORIES_LENGTH = 2048
    }

}

internal class SearchStateTable : Table("search_state") {
    val key = varchar("key", KEY_LENGTH)
    val value = varchar("value", VALUE_LENGTH).nullable()
    val updatedAt = long("updated_at")
    val leaseUntil = long("lease_until").nullable()

    override val primaryKey = PrimaryKey(key)

    companion object {
        const val KEY_LENGTH = 64
        const val VALUE_LENGTH = 512
    }

}
