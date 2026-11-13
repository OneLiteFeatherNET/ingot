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

package net.onelitefeather.ingot.opensearch

import com.reposilite.plugin.PluginLoader
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
 * The OpenSearch search backend, bundled in the image and loaded only when `INGOT_OPENSEARCH_ENABLED` is true (or the
 * `REPOSILITE_` fallback). It registers the factory `opensearch` on the registration event of the search plugin. Selecting it
 * takes the local `searchEngine` setting as well, so an instance that loads the plugin keeps the database until it is switched.
 */
@Plugin(name = "opensearch-search", dependencies = ["search"])
class OpenSearchPlugin : ReposilitePlugin() {

    override fun initialize(): Facade? {
        val environment = System.getenv()
        event { event: ArtifactSearchBackendRegistrationEvent ->
            registerOpenSearch(environment, ::readSecretFile, event::register)
        }
        return null
    }

    private fun readSecretFile(path: String): String = Files.readString(Path.of(path))

}

/**
 * Registers the factory of this plugin through [accept], and nothing while the switch of this jar is off. The switch is the one
 * the loader reads, so both agree. The settings are read from [environment] and the password file through [readFile] when the
 * backend is created, so a refused setting reports the backend as `OPEN_FAILED` and does not stop the server.
 */
internal fun registerOpenSearch(
    environment: Map<String, String>,
    readFile: (String) -> String,
    accept: (ArtifactSearchBackendFactory) -> Unit,
) {
    if (!OPENSEARCH_JAR_SWITCH.isOn(environment)) return
    accept(OpenSearchBackendFactory(settings = { log -> OpenSearchSettings.read(environment, readFile, log) }))
}

private val OPENSEARCH_JAR_SWITCH: PluginLoader.BundledSwitch =
    PluginLoader.BUNDLED_SWITCHES.getValue("opensearch-plugin.jar")
