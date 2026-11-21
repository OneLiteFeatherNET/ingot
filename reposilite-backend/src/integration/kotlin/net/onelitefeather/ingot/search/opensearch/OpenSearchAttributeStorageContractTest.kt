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

package net.onelitefeather.ingot.search.opensearch

import net.onelitefeather.ingot.search.backend.AttributeStorageContract
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.database.pathHash
import java.nio.file.Path
import java.time.Clock
import javax.net.ssl.SSLContext

/**
 * The attribute contract (search document attributes, D4 and D6) on the OpenSearch backend, against a secured OpenSearch 2.x
 * container started once for the class. Every test writes to an index of its own, and the attributes of a row are read from its
 * stored source.
 */
internal class OpenSearchAttributeStorageContractTest : AttributeStorageContract() {

    override fun open(directory: Path, clock: Clock): Opened {
        val index = newIndexName()
        val backend = createBackend(openSearchTestEnvironment(cluster.url(), index), clock, sslContext)
        backend.open()
        return Opened(
            OpenSearchSearchBackendContractTest.VisibleAfterWrites(backend) { admin.refresh(index) },
            { repository, path -> admin.attributes(index, pathHash(repository, path)) },
        ) {
            backend.close()
            admin.deleteIndex("$index-v1")
        }
    }

    private companion object {

        val cluster: StartedOpenSearch by lazy { startSecuredOpenSearch() }
        val sslContext: SSLContext by lazy { cluster.sslContext() }
        val admin: OpenSearchAdminClient by lazy { OpenSearchAdminClient(cluster, sslContext) }

    }

}
