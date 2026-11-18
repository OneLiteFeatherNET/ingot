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

package net.onelitefeather.ingot.plugin.ui

import com.reposilite.frontend.api.PluginUiBlock
import net.onelitefeather.ingot.plugin.ExperimentalIngotPluginApi

/**
 * A dashboard page that anonymous visitors may read, registered with `PluginUiRegistrar.registerReaderPage`. It is data
 * only, like an admin page, but it has no area and no menu group, and its blocks only read: `text`, `keyValue` and
 * `table`. A `form`, an `action` or a `status` block is refused at registration.
 */
@ExperimentalIngotPluginApi
data class PluginUiReaderPage(
    /** Lower case letters, digits and hyphens, at most 32 characters. Unique within the plugin. */
    val id: String,
    /** 1 to 60 characters. */
    val title: String,
    /** Optional, at most 200 characters. */
    val description: String? = null,
    /** 1 to 30 blocks, drawn in order. Every source is a route of the plugin below `/api/plugins/<plugin>/`. */
    val blocks: List<PluginUiBlock>,
)
