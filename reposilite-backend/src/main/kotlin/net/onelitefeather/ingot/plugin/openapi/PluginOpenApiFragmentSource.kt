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

package net.onelitefeather.ingot.plugin.openapi

import java.io.IOException

/** Reads the api document fragments of plugins through each plugin's own class loader, keyed by plugin id. */
internal object PluginOpenApiFragmentSource {

    const val DIRECTORY = "META-INF/ingot/openapi"

    /**
     * Returns the fragment of each plugin id that its loader holds, sorted by id. A loader that holds no file for an id
     * contributes nothing, and a file that cannot be read is reported and skipped.
     */
    fun load(loaders: Map<String, ClassLoader>, warn: (String) -> Unit = {}): List<PluginOpenApiFragment> =
        loaders.toSortedMap().mapNotNull { (pluginId, loader) ->
            try {
                loader.getResourceAsStream("$DIRECTORY/$pluginId.json")?.use { PluginOpenApiFragment(pluginId, it.readBytes()) }
            } catch (exception: IOException) {
                warn("[OpenApi] Plugin '$pluginId' ships an api document fragment that cannot be read: ${exception.message}")
                null
            }
        }
}
