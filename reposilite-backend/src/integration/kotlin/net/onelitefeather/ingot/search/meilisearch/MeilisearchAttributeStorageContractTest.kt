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

package net.onelitefeather.ingot.search.meilisearch

import com.reposilite.ReposiliteObjectMapper
import net.onelitefeather.ingot.search.backend.AttributeStorageContract
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.database.pathHash
import net.onelitefeather.ingot.search.opensearch.newIndexName
import java.nio.file.Path
import java.time.Clock

/**
 * The attribute contract on the Meilisearch backend (search document attributes, D6), against a Meilisearch 1.15 container. Each
 * test writes to an index of its own, and the stored attributes of a row are read from its document with the master key.
 */
internal class MeilisearchAttributeStorageContractTest : AttributeStorageContract() {

    override fun open(directory: Path, clock: Clock): Opened {
        val index = newIndexName()
        val backend = createMeilisearchBackend(meilisearchTestEnvironment(server.url(), index))
        backend.open()
        return Opened(
            backend,
            { repository, path -> storedAttributes(index, repository, path) },
        ) {
            backend.close()
            admin.deleteIndex(index)
        }
    }

    /** The `attributes` object of the document of [path], or an empty map when the document has none or does not exist. */
    private fun storedAttributes(index: String, repository: String, path: String): Map<String, List<String>> {
        val mapper = ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
        val body = mapper.writeValueAsString(mapOf("ids" to listOf(pathHash(repository, path)), "fields" to listOf("attributes")))
        val document = mapper.readTree(admin.request("POST", "/indexes/$index/documents/fetch", body)).path("results").firstOrNull()
        val attributes = document?.path("attributes")?.takeIf { it.isObject } ?: return emptyMap()
        return attributes.properties().associate { (key, values) -> key to values.map { it.asText() } }
    }

    private companion object {

        val server: StartedMeilisearch by lazy { startMeilisearch() }
        val admin: MeilisearchAdminClient by lazy { MeilisearchAdminClient(server) }

    }

}
