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
package com.reposilite.frontend.api

/**
 * A dashboard page a plugin contributes. It is data only: the dashboard draws the blocks with its own components,
 * and no field carries HTML, script or style. The plugin is not a field here, because the facade takes it from the
 * registering plugin, so a page cannot name another plugin's data routes.
 */
data class PluginUiPage(
    /** Lower case letters, digits and hyphens, at most 32 characters. Unique within the plugin. */
    val id: String,
    /** 1 to 60 characters. */
    val title: String,
    /** Optional, at most 200 characters. */
    val description: String? = null,
    /** One of [PluginUiModel.GROUPS]. */
    val group: String = PluginUiModel.DEFAULT_GROUP,
    /** An admin area of the identity model, or `*` for the full instance administrator. */
    val area: String,
    /** 1 to 30 blocks, drawn in order. */
    val blocks: List<PluginUiBlock>,
)

/**
 * A block of a page. The `type` field of the JSON selects the shape. Every path is a server route below
 * `/api/plugins/<plugin>/`, checked at registration.
 */
sealed interface PluginUiBlock {

    val type: String

    /** Plain text, shown as text. 1 to 2000 characters. */
    data class Text(val text: String) : PluginUiBlock {
        override val type: String = "text"
    }

    /** The object a GET of [source] returns, shown as key and value rows. */
    data class KeyValue(val source: String) : PluginUiBlock {
        override val type: String = "keyValue"
    }

    /** The array a GET of [source] returns, shown as a table with [columns]. */
    data class Table(val source: String, val columns: List<PluginUiColumn>) : PluginUiBlock {
        override val type: String = "table"
    }

    /**
     * A JSON form. [source] is read with GET, and [target] receives the object with [method], which is PUT or POST.
     * [schema] is a JSON schema whose references must stay inside the document.
     */
    data class Form(
        val source: String,
        val target: String,
        val method: String,
        val schema: Map<String, Any?>,
        val uiSchema: Map<String, Any?>? = null,
    ) : PluginUiBlock {
        override val type: String = "form"
    }

    /** A button that sends [method] (POST, PUT or DELETE) to [path]. A [confirm] text is asked before it sends. */
    data class Action(
        val label: String,
        val method: String,
        val path: String,
        val confirm: String? = null,
        val danger: Boolean = false,
    ) : PluginUiBlock {
        override val type: String = "action"
    }

    /** The object a GET of [source] returns, polled every [refreshSeconds] while the page is visible. */
    data class Status(val source: String, val refreshSeconds: Int = 10) : PluginUiBlock {
        override val type: String = "status"
    }
}

/** A column of a [PluginUiBlock.Table]: the key of the object field and its heading. */
data class PluginUiColumn(val key: String, val label: String)

/**
 * A settings domain of the registering plugin, given a label and a menu group. The domain must be the plugin's own
 * settings domain, which the facade checks.
 */
data class PluginUiSection(
    val domain: String,
    val label: String,
    val group: String = PluginUiModel.DEFAULT_SECTION_GROUP,
)

/** The constants of the model, shared by the validator, the facade and the endpoints. */
object PluginUiModel {
    const val VERSION: Int = 1
    const val WILDCARD_AREA: String = "*"
    val GROUPS: List<String> = listOf("Repository", "Security", "System", "Support")
    const val DEFAULT_GROUP: String = "System"
    val SECTION_GROUPS: List<String> = listOf("Repository", "System")
    const val DEFAULT_SECTION_GROUP: String = "System"

    /** The settings domain name the dashboard reserves for its own address `/settings/plugins/...`. */
    const val RESERVED_DOMAIN: String = "plugins"

    const val MAX_PAGES: Int = 20
    const val MAX_BLOCKS: Int = 30
    const val MAX_MANIFEST_BYTES: Int = 64 * 1024
}

/** The manifest the dashboard reads from `GET /api/frontend/plugin-ui`. */
data class PluginUiManifest(
    val version: Int = PluginUiModel.VERSION,
    val pages: List<PluginUiManifestPage>,
    val sections: List<PluginUiManifestSection>,
    /** The names of the loaded server plugins. Names only, for any signed in caller. */
    val enabledPlugins: List<String>,
    /** One entry per plugin with an accepted stylesheet. Added in version 1, so a dashboard that does not know it ignores it. */
    val stylesheets: List<PluginUiManifestStylesheet> = emptyList(),
)

/** A stylesheet the dashboard loads. [path] is served from the server, and [sha256] is the digest of the served bytes. */
data class PluginUiManifestStylesheet(
    val plugin: String,
    val path: String,
    val sha256: String,
)

data class PluginUiManifestPage(
    val plugin: String,
    val id: String,
    val title: String,
    val description: String?,
    val group: String,
    val area: String,
    val blocks: List<PluginUiBlock>,
) {
    companion object {
        fun of(plugin: String, page: PluginUiPage): PluginUiManifestPage =
            PluginUiManifestPage(plugin, page.id, page.title, page.description, page.group, page.area, page.blocks)
    }
}

data class PluginUiManifestSection(
    val plugin: String,
    val domain: String,
    val label: String,
    val group: String,
)
