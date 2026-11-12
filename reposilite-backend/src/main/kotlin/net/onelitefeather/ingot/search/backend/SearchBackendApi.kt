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

package net.onelitefeather.ingot.search.backend

import com.reposilite.plugin.api.Event
import java.time.Clock

/**
 * Marks the artifact search backend API as experimental. A plugin that implements or calls one of these types opts in
 * explicitly, and the members may change until the API is declared stable. Modelled on the repository format SPI.
 */
@RequiresOptIn(
    message = "The artifact search backend API is experimental and may change until it is declared stable.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.TYPEALIAS)
annotation class ExperimentalIngotSearchApi

/** The version of the backend API this server speaks. A factory declares the version it was built for. */
const val SEARCH_BACKEND_API_VERSION: Int = 1

/**
 * A search backend that a plugin supplies. The local key `searchEngine` names the [id] of the factory to use. The registry
 * accepts a factory only when [apiVersion] equals [SEARCH_BACKEND_API_VERSION].
 */
@ExperimentalIngotSearchApi
interface ArtifactSearchBackendFactory {

    /** The value of the local key `searchEngine` that selects this backend. */
    val id: String

    /** The API version this factory was built for. Must equal [SEARCH_BACKEND_API_VERSION]. */
    val apiVersion: Int

    /** Creates the backend of one replica. The core calls [ArtifactSearchBackend.open] afterwards, off the request threads. */
    fun create(context: SearchBackendContext): ArtifactSearchBackend

}

/** What the core gives a factory when it creates a backend: the clock, this replica and the configuration it may read. */
@ExperimentalIngotSearchApi
interface SearchBackendContext {

    /** The clock of the server. Backends take their times from here, never from the system. */
    val clock: Clock

    /** The id of this replica. */
    val instanceId: String

    /** The value of the setting [name] from the environment: `INGOT_<name>`, then the fallback `REPOSILITE_<name>`. */
    fun environment(name: String): String?

    /** The secret [name]: the environment variable `<name>`, or the file named by the variable `<name>_FILE`. */
    fun secret(name: String): String?

    /** A logger whose messages carry the prefix `SEARCH |` and the id of the backend. */
    fun logger(): BackendLogger

}

/** Log output of a backend. Messages must not contain secrets; the core redacts URL user information in what it shows. */
@ExperimentalIngotSearchApi
interface BackendLogger {

    fun info(message: String)

    fun warn(message: String)

    fun error(message: String)

}

/**
 * Emitted by the search plugin after every plugin has initialised. A plugin registers its [ArtifactSearchBackendFactory]
 * here, from its own `initialize` or `load`.
 */
@ExperimentalIngotSearchApi
class ArtifactSearchBackendRegistrationEvent internal constructor(
    private val registry: ArtifactSearchBackendRegistry,
) : Event {

    /**
     * Registers [factory]. A factory of another API version, or one whose id is taken, is refused with a warning that
     * names the classes involved. The call never throws for a refusal.
     */
    fun register(factory: ArtifactSearchBackendFactory) {
        registry.register(factory)
    }

}
