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

import com.reposilite.plugin.api.Facade
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.ReposilitePlugin
import com.reposilite.plugin.event
import net.onelitefeather.ingot.search.backend.ArtifactSearchBackendFactory
import net.onelitefeather.ingot.search.backend.ArtifactSearchBackendRegistrationEvent
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi

/**
 * The Elasticsearch search backend. It is not part of the image: the operator installs the jar in the plugins directory, from the
 * plugin store or by hand, and the jar is its own switch. It registers the factory `elasticsearch` on the registration event of
 * the search plugin. Selecting it takes the local `searchEngine` setting as well, so an instance that loads the jar keeps the
 * database until it is switched.
 */
@Plugin(name = "elasticsearch-search", dependencies = ["search"])
class ElasticsearchPlugin : ReposilitePlugin() {

    override fun initialize(): Facade? {
        val environment = System.getenv()
        event { event: ArtifactSearchBackendRegistrationEvent ->
            registerElasticsearch(environment, event::register)
        }
        return null
    }

}

/**
 * Registers the factory of this plugin through [accept]. The registration reads no variable: the settings are read from
 * [environment] when the backend is created, so a refused setting reports the backend as `OPEN_FAILED` and does not stop the server.
 */
internal fun registerElasticsearch(
    environment: Map<String, String>,
    accept: (ArtifactSearchBackendFactory) -> Unit,
) {
    accept(ElasticsearchBackendFactory(settings = { log -> ElasticsearchSettings.read(environment, log = log) }))
}
