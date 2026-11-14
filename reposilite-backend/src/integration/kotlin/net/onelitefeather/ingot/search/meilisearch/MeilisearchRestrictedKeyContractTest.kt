@file:OptIn(ExperimentalIngotSearchApi::class)

package net.onelitefeather.ingot.search.meilisearch

import net.onelitefeather.ingot.search.backend.ArtifactSearchBackendContract
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.opensearch.newIndexName
import java.nio.file.Path
import java.time.Clock

/**
 * The shared search contract with a key that carries only the actions the reference page documents (the minimal key). A case that
 * needs an action outside that list fails with the refusal of the server, which proves the documented list is complete.
 */
internal class MeilisearchRestrictedKeyContractTest : ArtifactSearchBackendContract() {

    override fun open(directory: Path, clock: Clock): Opened {
        val index = newIndexName()
        val backend = createMeilisearchBackend(meilisearchTestEnvironment(server.url(), index, restrictedKey))
        backend.open()
        return Opened(backend) {
            backend.close()
            admin.deleteIndex(index)
        }
    }

    private companion object {

        val server: StartedMeilisearch by lazy { startMeilisearch() }
        val admin: MeilisearchAdminClient by lazy { MeilisearchAdminClient(server) }
        val restrictedKey: String by lazy { admin.createKey(MEILISEARCH_MINIMAL_ACTIONS, listOf("ingot-*")) }

    }

}
