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

package net.onelitefeather.ingot.search.backend

/** The states of a search backend (search backend registration, D4). */
internal enum class SearchBackendState {
    AVAILABLE,
    NOT_INSTALLED,
    REFUSED,
    OPEN_FAILED,
    DOWN,
    CLUSTER_MISMATCH,
}

/** What the registry answers for a configured id: a factory to create the backend with, or the reason there is none. */
internal sealed interface BackendResolution {

    data class Installed(val factory: ArtifactSearchBackendFactory) : BackendResolution

    data class Unavailable(val state: SearchBackendState, val reason: String) : BackendResolution

}

/**
 * The factories of this server, by id (search backend registration, D2). The built-in `database` factory is registered first,
 * so a plugin cannot replace it: a second factory with an id that is taken is refused. A factory of another API version is
 * refused as well, and its id resolves to [SearchBackendState.REFUSED] while no other factory has it.
 */
internal class ArtifactSearchBackendRegistry(
    builtIn: ArtifactSearchBackendFactory,
    private val warn: (String) -> Unit,
) {

    private val factories = linkedMapOf(builtIn.id to builtIn)
    private val refused = linkedMapOf<String, String>()

    /** Registers [factory]. Returns false when it was refused, and says why in a warning. */
    fun register(factory: ArtifactSearchBackendFactory): Boolean {
        if (factory.apiVersion != SEARCH_BACKEND_API_VERSION) {
            val reason = "${factory.javaClass.name} declares apiVersion=${factory.apiVersion}, expected $SEARCH_BACKEND_API_VERSION"
            warn("SEARCH | Refused search backend '${factory.id}': $reason")
            refused.putIfAbsent(factory.id, reason)
            return false
        }
        val existing = factories[factory.id]
        if (existing != null) {
            warn(
                "SEARCH | Refused search backend '${factory.id}' from ${factory.javaClass.name}: " +
                    "${existing.javaClass.name} is already registered for this id"
            )
            return false
        }
        factories[factory.id] = factory
        return true
    }

    /** The answer for the configured [id]: the factory that is registered for it, or why none is usable. */
    fun resolve(id: String): BackendResolution {
        factories[id]?.let { return BackendResolution.Installed(it) }
        refused[id]?.let { return BackendResolution.Unavailable(SearchBackendState.REFUSED, it) }
        return BackendResolution.Unavailable(SearchBackendState.NOT_INSTALLED, "no search backend is registered for '$id'")
    }

}
