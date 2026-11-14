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

package net.onelitefeather.ingot.plugin.store

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import panda.std.Result

/** The Ingot versions an entry works with, `min` inclusive and `below` exclusive, and the store's plugin API integer. */
data class Compatibility(
    val ingotMin: String,
    val ingotBelow: String,
    val pluginApi: Int,
)

/** One plugin of a catalogue. [sha256] is the authority for the jar; the `.sha256` sidecar of a Maven repository is not read. */
data class PluginCatalogueEntry(
    val id: String,
    val name: String,
    val publisher: String,
    val coordinate: PluginCoordinate,
    val sha256: String,
    /** Null when the entry has no usable compatibility block, which the compatibility rule refuses. */
    val compatibility: Compatibility?,
    val yanked: Boolean,
) {

    /** A yanked entry stays visible, but a new selection of it is refused. A jar already cached still loads. */
    val selectable: Boolean
        get() = !yanked

}

/** The parsed index file `ingot-plugin-catalogue.json` of one source. Unknown fields are ignored, so schema 1 readers accept later additions. */
class PluginCatalogue(val entries: List<PluginCatalogueEntry>) {

    companion object {

        const val SCHEMA: Int = 1
        const val FILE_NAME: String = "ingot-plugin-catalogue.json"

        private val mapper = ObjectMapper()

        /** Parses the bytes of a catalogue, or refuses it with a reason that names the problem. */
        fun parse(bytes: ByteArray): Result<PluginCatalogue, String> {
            val root = try {
                mapper.readTree(bytes)
            } catch (exception: Exception) {
                return Result.error("The catalogue is not readable JSON, so no entry of its source is used")
            }

            val schema = root?.get("schema")?.takeIf { it.isInt }?.asInt()
                ?: return Result.error("The catalogue declares no schema number, so no entry of its source is used")
            if (schema != SCHEMA) {
                return Result.error("catalogue schema $schema is not supported, this Ingot reads schema $SCHEMA")
            }

            val plugins = root.get("plugins")?.takeIf { it.isArray }
                ?: return Result.error("The catalogue has no plugins list, so no entry of its source is used")

            return try {
                Result.ok(PluginCatalogue(plugins.map { parseEntry(it) }))
            } catch (exception: CatalogueFormatException) {
                Result.error(exception.message ?: "The catalogue is malformed")
            }
        }

        private fun parseEntry(node: JsonNode): PluginCatalogueEntry {
            val coordinateText = text(node, "coordinates")
            val coordinate = PluginCoordinate.parse(coordinateText)
                ?: throw CatalogueFormatException("The catalogue entry coordinates '$coordinateText' is not groupId:artifactId:version")

            return PluginCatalogueEntry(
                id = text(node, "id"),
                name = text(node, "name"),
                publisher = text(node, "publisher"),
                coordinate = coordinate,
                sha256 = text(node, "sha256"),
                compatibility = parseCompatibility(node.get("compatibility")),
                yanked = node.get("yanked")?.asBoolean(false) ?: false,
            )
        }

        private fun parseCompatibility(node: JsonNode?): Compatibility? {
            if (node == null || node.isNull || !node.isObject) {
                return null
            }
            val ingot = node.get("ingot")?.takeIf { it.isObject } ?: return null
            val min = ingot.get("min")?.takeIf { it.isTextual }?.asText() ?: return null
            val below = ingot.get("below")?.takeIf { it.isTextual }?.asText() ?: return null
            val pluginApi = node.get("pluginApi")?.takeIf { it.isInt }?.asInt() ?: return null
            return Compatibility(ingotMin = min, ingotBelow = below, pluginApi = pluginApi)
        }

        private fun text(node: JsonNode, field: String): String =
            node.get(field)?.takeIf { it.isTextual && it.asText().isNotBlank() }?.asText()
                ?: throw CatalogueFormatException("The catalogue entry has no $field, so the catalogue is refused")

    }

}

private class CatalogueFormatException(message: String) : RuntimeException(message)

/** The compatibility rule of the store. It returns null when the entry may be selected, or the reason why it may not. */
object CompatibilityRule {

    /** The plugin API integer that this Ingot reads. It moves only when the plugin API breaks existing plugins. */
    const val PLUGIN_API: Int = 1

    fun check(compatibility: Compatibility?, ingotVersion: String, pluginApi: Int = PLUGIN_API): String? {
        if (compatibility == null) {
            return "the entry has no compatibility block, or it is incomplete, so the entry is refused"
        }
        if (compatibility.pluginApi != pluginApi) {
            return "pluginApi ${compatibility.pluginApi} is not the pluginApi $pluginApi that this Ingot reads"
        }
        val outsideMin = compareVersions(ingotVersion, compatibility.ingotMin) < 0
        val outsideBelow = compareVersions(ingotVersion, compatibility.ingotBelow) >= 0
        if (outsideMin || outsideBelow) {
            return "Ingot $ingotVersion is outside [${compatibility.ingotMin}, ${compatibility.ingotBelow}), so the entry is refused"
        }
        return null
    }

    /** Compares dotted numeric versions segment by segment. A segment reads its leading digits, and a missing segment counts as zero. */
    internal fun compareVersions(left: String, right: String): Int {
        val leftParts = left.split(".").map { leadingNumber(it) }
        val rightParts = right.split(".").map { leadingNumber(it) }
        for (index in 0 until maxOf(leftParts.size, rightParts.size)) {
            val difference = leftParts.getOrElse(index) { 0 }.compareTo(rightParts.getOrElse(index) { 0 })
            if (difference != 0) {
                return difference
            }
        }
        return 0
    }

    private fun leadingNumber(segment: String): Long =
        segment.takeWhile { it.isDigit() }.toLongOrNull() ?: 0L

}
