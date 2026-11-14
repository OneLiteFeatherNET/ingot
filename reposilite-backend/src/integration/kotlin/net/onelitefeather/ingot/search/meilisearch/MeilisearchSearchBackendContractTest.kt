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

import net.onelitefeather.ingot.meilisearch.meilisearchBackendFactory
import net.onelitefeather.ingot.search.backend.ArtifactSearchBackend
import net.onelitefeather.ingot.search.backend.ArtifactSearchBackendContract
import net.onelitefeather.ingot.search.backend.CoreSearchBackendContext
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.opensearch.newIndexName
import net.onelitefeather.ingot.search.http.HttpExchange
import java.nio.file.Path
import java.time.Clock
import java.util.UUID

/**
 * The shared search contract on the Meilisearch backend (search backend seam, D10), against a Meilisearch 1.15 container. Each test
 * writes to an index of its own. The backend reads the system clock: its task wait is bounded by the clock, and a fixed test clock
 * would never reach the bound.
 */
internal class MeilisearchSearchBackendContractTest : ArtifactSearchBackendContract() {

    override fun open(directory: Path, clock: Clock): Opened {
        val index = newIndexName()
        val backend = createMeilisearchBackend(meilisearchTestEnvironment(server.url(), index))
        backend.open()
        return Opened(backend) {
            backend.close()
            admin.deleteIndex(index)
        }
    }

    private companion object {

        val server: StartedMeilisearch by lazy { startMeilisearch() }
        val admin: MeilisearchAdminClient by lazy { MeilisearchAdminClient(server) }

    }

}

/**
 * A backend created by the factory of the plugin, the way the core creates one, and not yet opened. The clock is the system clock,
 * so that the bound of a task wait is real.
 */
internal fun createMeilisearchBackend(environment: Map<String, String>, exchange: HttpExchange? = null): ArtifactSearchBackend {
    val context = CoreSearchBackendContext(
        clock = Clock.systemUTC(),
        instanceId = "test-replica-${UUID.randomUUID()}",
        backendId = "meilisearch",
        variables = environment::get,
    ) { _, _ -> }
    return meilisearchBackendFactory(environment, exchange).create(context)
}
