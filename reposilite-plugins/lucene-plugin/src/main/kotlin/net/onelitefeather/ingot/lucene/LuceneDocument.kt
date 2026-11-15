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

package net.onelitefeather.ingot.lucene

import net.onelitefeather.ingot.search.ArtifactFileRecord
import net.onelitefeather.ingot.search.IndexRow
import net.onelitefeather.ingot.search.Origin
import net.onelitefeather.ingot.search.Provenance
import net.onelitefeather.ingot.search.SearchTokens
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.backend.StoredRow
import net.onelitefeather.ingot.search.database.pathHash
import net.onelitefeather.ingot.search.gavKeyOf
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.LongPoint
import org.apache.lucene.document.NumericDocValuesField
import org.apache.lucene.document.StoredField
import org.apache.lucene.document.StringField
import java.util.HexFormat
import java.util.Locale

/**
 * The Lucene document of one row (design D2). The field names are the ones of the other search plugins, so a reader of the
 * documents sees the same names. The document id is the path hash of the database, which is also the update term of a write: a
 * second write of one file replaces the first.
 *
 * `indexed_at` is a numeric doc value only. A touch changes it in place with an update of the doc value, so the stored fields of a
 * document are never the source of that time; [toRow] takes it from the caller, who reads the doc values.
 */
internal object LuceneDocument {

    const val ID: String = "id"
    const val REPOSITORY: String = "repository"
    const val PATH: String = "path"
    const val PATH_LC: String = "path_lc"
    const val GROUP_ID: String = "group_id"
    const val GROUP_ID_LC: String = "group_id_lc"
    const val ARTIFACT_ID: String = "artifact_id"
    const val ARTIFACT_ID_LC: String = "artifact_id_lc"
    const val VERSION: String = "version"
    const val VERSION_LC: String = "version_lc"
    const val GAV: String = "gav"
    const val ARTIFACT_KEY: String = "artifact_key"
    const val CLASSIFIER: String = "classifier"
    const val EXTENSION: String = "extension"
    const val SNAPSHOT: String = "snapshot"
    const val ORIGIN: String = "origin"
    const val SOURCE_HOST: String = "source_host"
    const val SIZE_BYTES: String = "size_bytes"
    const val LAST_MODIFIED: String = "last_modified"
    const val INDEXED_AT: String = "indexed_at"
    const val SHA1: String = "sha1"
    const val SHA256: String = "sha256"
    const val TOKENS: String = "tokens"

    /** The document id of a path in a repository: the same hash the database uses as its key. */
    fun id(repository: String, path: String): String = pathHash(repository, path)

    fun of(row: IndexRow): Document {
        val file = row.file
        val document = Document()
        document.add(StringField(ID, id(file.repository, file.path), Field.Store.NO))
        document.add(StringField(REPOSITORY, file.repository, Field.Store.YES))
        document.add(StringField(PATH, file.path, Field.Store.YES))
        document.add(StringField(PATH_LC, lower(file.path), Field.Store.NO))
        document.add(StringField(GROUP_ID, file.groupId, Field.Store.YES))
        document.add(StringField(GROUP_ID_LC, lower(file.groupId), Field.Store.NO))
        document.add(StringField(ARTIFACT_ID, file.artifactId, Field.Store.YES))
        document.add(StringField(ARTIFACT_ID_LC, lower(file.artifactId), Field.Store.NO))
        document.add(StringField(VERSION, file.version, Field.Store.YES))
        document.add(StringField(VERSION_LC, lower(file.version), Field.Store.NO))
        document.add(StringField(GAV, HexFormat.of().formatHex(gavKeyOf(file.groupId, file.artifactId, file.version)), Field.Store.NO))
        document.add(StringField(ARTIFACT_KEY, "${lower(file.groupId)}:${lower(file.artifactId)}", Field.Store.NO))
        file.classifier?.let { document.add(StringField(CLASSIFIER, it, Field.Store.YES)) }
        document.add(StringField(EXTENSION, lower(file.extension), Field.Store.YES))
        document.add(StringField(SNAPSHOT, file.snapshot.toString(), Field.Store.YES))
        document.add(StringField(ORIGIN, row.provenance.origin.name, Field.Store.YES))
        row.provenance.sourceHost?.let { document.add(StoredField(SOURCE_HOST, it)) }
        addNumber(document, SIZE_BYTES, row.sizeBytes)
        addNumber(document, LAST_MODIFIED, row.lastModified)
        document.add(NumericDocValuesField(INDEXED_AT, row.indexedAt))
        row.sha1?.let { document.add(StringField(SHA1, it, Field.Store.YES)) }
        row.sha256?.let { document.add(StoredField(SHA256, it)) }
        SearchTokens.of(file.groupId, file.artifactId, file.version).forEach { token ->
            document.add(StringField(TOKENS, token, Field.Store.NO))
        }
        return document
    }

    /** The row a stored document holds, with the indexed time that the caller read from the doc values. */
    fun toRow(document: Document, indexedAt: Long): IndexRow {
        val file = ArtifactFileRecord(
            repository = checkNotNull(document.get(REPOSITORY)) { "a document without a repository" },
            path = checkNotNull(document.get(PATH)) { "a document without a path" },
            groupId = checkNotNull(document.get(GROUP_ID)) { "a document without a group" },
            artifactId = checkNotNull(document.get(ARTIFACT_ID)) { "a document without an artifact" },
            version = checkNotNull(document.get(VERSION)) { "a document without a version" },
            classifier = document.get(CLASSIFIER),
            extension = checkNotNull(document.get(EXTENSION)) { "a document without an extension" },
            snapshot = document.get(SNAPSHOT) == "true",
        )
        return IndexRow(
            file = file,
            provenance = Provenance(Origin.valueOf(checkNotNull(document.get(ORIGIN))), document.get(SOURCE_HOST)),
            sizeBytes = numberOf(document, SIZE_BYTES),
            lastModified = numberOf(document, LAST_MODIFIED),
            sha1 = document.get(SHA1),
            sha256 = document.get(SHA256),
            indexedAt = indexedAt,
        )
    }

    /** What a rebuild or a verify needs from a stored document: the size, the modification time and the checksums. */
    fun storedRow(document: Document): StoredRow = StoredRow(
        sizeBytes = numberOf(document, SIZE_BYTES),
        lastModified = numberOf(document, LAST_MODIFIED),
        sha1 = document.get(SHA1),
        sha256 = document.get(SHA256),
    )

    /** A number is a point for range queries, a stored value and a doc value, so that sums and maxima read it too. */
    private fun addNumber(document: Document, name: String, value: Long) {
        document.add(LongPoint(name, value))
        document.add(StoredField(name, value))
        document.add(NumericDocValuesField(name, value))
    }

    private fun numberOf(document: Document, name: String): Long =
        checkNotNull(document.getField(name)?.numericValue()) { "a document without $name" }.toLong()

    private fun lower(value: String): String = value.lowercase(Locale.ROOT)

}
