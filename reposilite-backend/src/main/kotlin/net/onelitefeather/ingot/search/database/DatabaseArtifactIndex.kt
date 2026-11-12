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

import net.onelitefeather.ingot.search.IndexRow
import net.onelitefeather.ingot.search.SearchTokens
import net.onelitefeather.ingot.search.backend.IndexAction
import net.onelitefeather.ingot.search.backend.KeyBatch
import net.onelitefeather.ingot.search.gavKeyOf
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import org.jetbrains.exposed.v1.jdbc.update
import java.security.MessageDigest
import java.util.Locale

/**
 * Writes `search_artifact` (artifact search spec, 2.3 and 3.3). The upsert key is `(repository, path_hash)`, so replaying an
 * action leaves the same row. Ranges on the path use the half-open form of section 2.10, never LIKE.
 */
internal class DatabaseArtifactIndex(private val database: Database) {

    private val dialect = SearchDialect.of(database.vendor)
    private val tables = SearchTables(dialect)
    private val rows = tables.artifact
    private val tokens = tables.token

    fun apply(actions: List<IndexAction>) {
        if (actions.isEmpty()) return
        transaction(database) {
            actions.forEach { action ->
                when (action) {
                    is IndexAction.Upsert -> {
                        val file = action.row.file
                        upsert(action.row)
                        insertTokens(file.groupId, file.artifactId, file.version)
                    }
                    is IndexAction.RemovePath -> rows.deleteWhere {
                        (rows.repository eq action.repository) and (rows.pathHash eq pathHash(action.repository, action.path))
                    }
                    is IndexAction.RemovePrefix -> removePrefix(action.repository, action.prefix, action.notAfter.toEpochMilli())
                    is IndexAction.Reorigin -> rows.update(where = { below(action.repository, action.directory) }, body = {
                        it[rows.origin] = action.provenance.origin.name
                        it[rows.sourceHost] = action.provenance.sourceHost
                    })
                }
            }
        }
    }

    /**
     * Fills the key and the tokens of up to [batchSize] rows that have no key and an id after [after], in one transaction, in id
     * order. The range on the id starts every batch at the cursor, so a pass costs one walk of the id index, not one per batch.
     * Returns the batch with the id of its last row, or null when no such row is left (full text change, D6).
     */
    fun keyRows(after: Long, batchSize: Int): KeyBatch? = transaction(database) {
        val batch = rows.select(rows.id, rows.groupId, rows.artifactId, rows.version)
            .where { (rows.id greater after) and rows.gavKey.isNull() }
            .orderBy(rows.id to SortOrder.ASC)
            .limit(batchSize)
            .toList()
        if (batch.isEmpty()) return@transaction null
        batch.forEach { row ->
            val groupId = row[rows.groupId]
            val artifactId = row[rows.artifactId]
            val version = row[rows.version]
            val key = gavKeyOf(groupId, artifactId, version)
            rows.update(where = { rows.id eq row[rows.id] }, body = { it[rows.gavKey] = key })
            insertTokens(groupId, artifactId, version)
        }
        KeyBatch(keyed = batch.size, lastId = batch.last()[rows.id])
    }

    /** The tokens of one GAV, inserted with insert-ignore: a token that exists is left as it is, so a replay adds nothing. */
    private fun insertTokens(groupId: String, artifactId: String, version: String) {
        val key = gavKeyOf(groupId, artifactId, version)
        val words = SearchTokens.of(groupId, artifactId, version)
        if (words.isEmpty()) return
        tokens.batchInsert(words, ignore = true) { word ->
            this[tokens.gavKey] = key
            this[tokens.token] = word
        }
    }

    fun pathsAt(repository: String, prefix: String, after: String?, limit: Int): List<String> = transaction(database) {
        rows.selectAll()
            .where { at(repository, prefix) and (after?.let { rows.path greater it } ?: Op.TRUE) }
            .orderBy(rows.path)
            .limit(limit)
            .map { it[rows.path] }
    }

    /**
     * Inserts the row or replaces the one with the same `(repository, path_hash)`. The vendor decides how the conflict is
     * named: [SearchDialect.namedUpsertKeys] says whether the statement lists the key or relies on the unique index.
     */
    private fun upsert(row: IndexRow) {
        val file = row.file
        val conflictKeys = if (dialect.namedUpsertKeys) arrayOf<Column<*>>(rows.repository, rows.pathHash) else emptyArray()
        rows.upsert(*conflictKeys) {
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
            it[rows.origin] = row.provenance.origin.name
            it[rows.sourceHost] = row.provenance.sourceHost
            it[rows.sizeBytes] = row.sizeBytes
            it[rows.lastModified] = row.lastModified
            it[rows.sha1] = row.sha1
            it[rows.sha256] = row.sha256
            it[rows.indexedAt] = row.indexedAt
            it[rows.gavKey] = gavKeyOf(file.groupId, file.artifactId, file.version)
        }
    }

    /**
     * Deletes the rows at [prefix] and below it that were indexed at or before [notAfter], 1000 ids per statement. A row
     * indexed later, by a concurrent deployment, is kept.
     */
    private fun removePrefix(repository: String, prefix: String, notAfter: Long) {
        while (true) {
            val ids = rows.selectAll()
                .where { at(repository, prefix) and (rows.indexedAt lessEq notAfter) }
                .limit(DELETE_BATCH)
                .map { it[rows.id] }
            if (ids.isEmpty()) return
            rows.deleteWhere { rows.id inList ids }
        }
    }

    /** The row of exactly [prefix] and every row below it. `prefix + "0"` is the first string above `prefix + "/"`. */
    private fun at(repository: String, prefix: String): Op<Boolean> =
        (rows.repository eq repository) and ((rows.path eq prefix) or below(repository, prefix))

    /** The rows strictly below [directory]: paths from `directory/` up to `directory0`, half-open (2.10). */
    private fun below(repository: String, directory: String): Op<Boolean> =
        (rows.repository eq repository) and (rows.path greaterEq "$directory/") and (rows.path less "${directory}0")

    private companion object {
        const val DELETE_BATCH = 1000
    }

}

/** The SHA-256 of `repository`, a NUL byte and `path`, as lower-case hex: the upsert key of a row (2.3). */
internal fun pathHash(repository: String, path: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest("$repository\u0000$path".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
