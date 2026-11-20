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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode

/** The api document fragment that a plugin ships at `META-INF/ingot/openapi/<plugin id>.json`. */
internal class PluginOpenApiFragment(val pluginId: String, val bytes: ByteArray)

/**
 * Adds the `paths` and `components.schemas` of plugin fragments to the served document. Server entries are never replaced,
 * and between plugins the lower id wins. A fragment that cannot be used is skipped whole, with one warning line.
 */
internal object PluginOpenApiFragments {

    private val mapper = ObjectMapper()

    fun merge(document: ObjectNode, fragments: List<PluginOpenApiFragment>, warn: (String) -> Unit): ObjectNode {
        if (fragments.isEmpty()) {
            return document
        }
        fragments.sortedBy { it.pluginId }.forEach { fragment ->
            val parsed = parse(fragment, warn) ?: return@forEach
            val paths = parsed.paths
            if (paths.size() > 0) {
                val target = document.get("paths") as? ObjectNode ?: document.putObject("paths")
                copyAbsent(target, paths, fragment.pluginId, "path", warn)
            }
            val schemas = parsed.schemas
            if (schemas.size() > 0) {
                val components = document.get("components") as? ObjectNode ?: document.putObject("components")
                val target = components.get("schemas") as? ObjectNode ?: components.putObject("schemas")
                copyAbsent(target, schemas, fragment.pluginId, "schema", warn)
            }
        }
        return document
    }

    private fun parse(fragment: PluginOpenApiFragment, warn: (String) -> Unit): Parsed? {
        val root = try {
            mapper.readTree(fragment.bytes)
        } catch (exception: Exception) {
            warn("[OpenApi] Plugin '${fragment.pluginId}' ships an api document fragment that is skipped: ${exception.message}")
            return null
        }
        if (root !is ObjectNode) {
            warn("[OpenApi] Plugin '${fragment.pluginId}' ships an api document fragment that is skipped: it is not a JSON object")
            return null
        }
        val paths = root.get("paths")
        val components = root.get("components")
        val schemas = components?.get("schemas")
        val invalid = when {
            paths != null && paths !is ObjectNode -> "paths is not an object"
            components != null && components !is ObjectNode -> "components is not an object"
            schemas != null && schemas !is ObjectNode -> "components.schemas is not an object"
            else -> null
        }
        if (invalid != null) {
            warn("[OpenApi] Plugin '${fragment.pluginId}' ships an api document fragment that is skipped: $invalid")
            return null
        }
        return Parsed(
            paths = paths as? ObjectNode ?: mapper.createObjectNode(),
            schemas = schemas as? ObjectNode ?: mapper.createObjectNode(),
        )
    }

    private fun copyAbsent(target: ObjectNode, entries: ObjectNode, pluginId: String, kind: String, warn: (String) -> Unit) {
        entries.fields().forEachRemaining { (key, value) ->
            if (target.has(key)) {
                warn("[OpenApi] Plugin '$pluginId' documents $kind '$key', which is already documented; keeping the existing entry")
            } else {
                target.set(key, value)
            }
        }
    }

    private class Parsed(val paths: ObjectNode, val schemas: ObjectNode)
}
