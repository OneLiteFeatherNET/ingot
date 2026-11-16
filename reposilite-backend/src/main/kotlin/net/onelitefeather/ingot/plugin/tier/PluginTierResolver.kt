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

package net.onelitefeather.ingot.plugin.tier

import com.reposilite.plugin.api.PluginTier

/** Where a plugin comes from. The origin decides the effective tier together with the declared one. */
enum class PluginOrigin {
    /** Compiled into the server. */
    BUILT_IN,

    /** A jar of the bundled plugins directory of the image. */
    BUNDLED_IMAGE,

    /** A jar of the operator's plugins directory. */
    PLUGINS_DIRECTORY,

    /** A jar verified from the plugin store. */
    PLUGIN_STORE,
}

/**
 * Computes the effective tier of a plugin from its origin and its declared tier (design D2). Origin wins: a plugin built
 * into the server or loaded from the image is Core, unless it declares Experimental. A plugin from anywhere else keeps its
 * declared tier, except that a claim of Core is shown as Community. The result never decides whether a plugin loads.
 */
object PluginTierResolver {

    fun resolve(origin: PluginOrigin, declared: PluginTier): PluginTier =
        when (origin) {
            PluginOrigin.BUILT_IN, PluginOrigin.BUNDLED_IMAGE ->
                if (declared == PluginTier.EXPERIMENTAL) PluginTier.EXPERIMENTAL else PluginTier.CORE

            PluginOrigin.PLUGINS_DIRECTORY, PluginOrigin.PLUGIN_STORE ->
                if (declared == PluginTier.CORE) PluginTier.COMMUNITY else declared
        }

}
