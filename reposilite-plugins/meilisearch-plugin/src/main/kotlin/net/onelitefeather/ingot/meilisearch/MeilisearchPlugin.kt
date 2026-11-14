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

package net.onelitefeather.ingot.meilisearch

import com.reposilite.plugin.api.Facade
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.ReposilitePlugin
import com.reposilite.plugin.event
import net.onelitefeather.ingot.search.backend.ArtifactSearchBackendFactory
import net.onelitefeather.ingot.search.backend.ArtifactSearchBackendRegistrationEvent
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import java.nio.file.Files
import java.nio.file.Path

/**
 * The Meilisearch search backend. It is not part of the image: the jar in the plugins directory is the opt-in, and selecting the
 * backend takes the local `searchEngine` setting as well. It registers the factory `meilisearch` on the registration event of the
 * search plugin.
 */
@Plugin(name = "meilisearch-search", dependencies = ["search"])
class MeilisearchPlugin : ReposilitePlugin() {

    override fun initialize(): Facade? {
        val environment = System.getenv()
        event { event: ArtifactSearchBackendRegistrationEvent ->
            registerMeilisearch(environment, ::readSecretFile, event::register)
        }
        return null
    }

    private fun readSecretFile(path: String): String = Files.readString(Path.of(path))

}

/**
 * Registers the factory of this plugin through [accept]. Nothing is read here: the settings are read from [environment] and the key
 * file through [readFile] when a backend is created, so a refused setting does not stop the server.
 */
internal fun registerMeilisearch(
    environment: Map<String, String>,
    readFile: (String) -> String,
    accept: (ArtifactSearchBackendFactory) -> Unit,
) {
    accept(MeilisearchBackendFactory(settings = { log -> MeilisearchSettings.read(environment, readFile, log) }))
}
