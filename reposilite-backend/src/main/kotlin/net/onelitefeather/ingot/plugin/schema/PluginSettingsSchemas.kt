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

package net.onelitefeather.ingot.plugin.schema

import java.io.IOException

/** Reads the compile-time schema of a plugin's settings class from the plugin's own jar. */
internal object PluginSettingsSchemas {

    const val DIRECTORY = "json-schemes"

    /**
     * Returns the bytes of `json-schemes/<type name>` that the loader of [type] holds, or null when it holds none or the file cannot
     * be read. A type defined by [serverLoader] is not looked up, so the server's own settings keep their path.
     */
    fun find(type: Class<*>, serverLoader: ClassLoader?): ByteArray? {
        val loader = type.classLoader ?: return null
        if (loader === serverLoader) {
            return null
        }
        return try {
            loader.getResourceAsStream("$DIRECTORY/${type.name}")?.use { it.readBytes() }
        } catch (exception: IOException) {
            null
        }
    }
}
