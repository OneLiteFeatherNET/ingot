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

package net.onelitefeather.ingot.elasticsearch

import com.fasterxml.jackson.databind.node.ObjectNode
import com.reposilite.ReposiliteObjectMapper
import net.onelitefeather.ingot.search.IndexRow
import net.onelitefeather.ingot.search.SearchTokens
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.database.pathHash
import net.onelitefeather.ingot.search.gavKeyOf
import java.util.HexFormat
import java.util.Locale

/**
 * The document of one row in the index (design D3). The fields are the ones of the strict mapping, and the values are the ones the
 * database holds: the same path hash as the document id, the same GAV key and the same tokens, from the shared core functions.
 */
internal object ElasticsearchDocument {

    /** The document id: the path hash of the database, so that writing the same file twice replaces one document. */
    fun id(row: IndexRow): String = pathHash(row.file.repository, row.file.path)

    fun of(row: IndexRow): ObjectNode {
        val file = row.file
        val document = ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER.createObjectNode()
        document.put("repository", file.repository)
        document.put("path", file.path)
        document.put("path_lc", lower(file.path))
        document.put("group_id", file.groupId)
        document.put("group_id_lc", lower(file.groupId))
        document.put("artifact_id", file.artifactId)
        document.put("artifact_id_lc", lower(file.artifactId))
        document.put("version", file.version)
        document.put("version_lc", lower(file.version))
        document.put("gav", HexFormat.of().formatHex(gavKeyOf(file.groupId, file.artifactId, file.version)))
        document.put("artifact_key", "${lower(file.groupId)}:${lower(file.artifactId)}")
        document.put("classifier", file.classifier)
        document.put("extension", lower(file.extension))
        document.put("snapshot", file.snapshot)
        document.put("origin", row.provenance.origin.name)
        document.put("source_host", row.provenance.sourceHost)
        document.put("size_bytes", row.sizeBytes)
        document.put("last_modified", row.lastModified)
        document.put("indexed_at", row.indexedAt)
        document.put("sha1", row.sha1)
        document.put("sha256", row.sha256)
        val tokens = document.putArray("tokens")
        SearchTokens.of(file.groupId, file.artifactId, file.version).forEach { token -> tokens.add(token) }
        return document
    }

    private fun lower(value: String): String = value.lowercase(Locale.ROOT)

}
