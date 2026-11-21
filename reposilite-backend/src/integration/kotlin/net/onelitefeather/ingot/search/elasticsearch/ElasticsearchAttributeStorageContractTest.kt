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

package net.onelitefeather.ingot.search.elasticsearch

import net.onelitefeather.ingot.search.backend.AttributeStorageContract
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.database.pathHash
import java.nio.file.Path
import java.time.Clock
import javax.net.ssl.SSLContext

/**
 * The attribute contract (search document attributes, D4 and D6) on the Elasticsearch backend, against the secured 8.x container
 * of the backend contract. Every test writes to an index of its own, and the attributes of a row are read from its stored source.
 */
internal open class ElasticsearchAttributeStorageContractTest : AttributeStorageContract() {

    /** The image of the cluster this suite runs against. The 9.x suite overrides it. */
    protected open val image: String get() = ELASTICSEARCH_8_IMAGE

    private val cluster: StartedElasticsearch get() = ElasticsearchClusters.of(image)

    private val sslContext: SSLContext get() = cluster.sslContext()

    private val admin: ElasticsearchAdminClient get() = ElasticsearchAdminClient(cluster, sslContext)

    override fun open(directory: Path, clock: Clock): Opened {
        val index = newElasticsearchIndexName()
        val apiKey = admin.createLeastPrivilegeApiKey(index)
        val backend = createElasticsearchBackend(elasticsearchTestEnvironment(cluster.url(), index, apiKey), clock, sslContext)
        backend.open()
        return Opened(
            ElasticsearchSearchBackendContractTest.VisibleAfterWrites(backend) { admin.refresh(index) },
            { repository, path -> admin.attributes(index, pathHash(repository, path)) },
        ) {
            backend.close()
            admin.deleteIndex("$index-v1")
        }
    }

}

/** The same contract against the secured Elasticsearch 9.x container. */
internal class ElasticsearchAttributeStorage9ContractTest : ElasticsearchAttributeStorageContractTest() {

    override val image: String get() = ELASTICSEARCH_9_IMAGE

}
