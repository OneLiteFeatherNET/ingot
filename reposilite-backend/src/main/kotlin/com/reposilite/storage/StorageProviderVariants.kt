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

package com.reposilite.storage

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.configuration.shared.cleanupScheme
import com.fasterxml.jackson.databind.node.TextNode
import net.onelitefeather.ingot.storage.ExperimentalIngotStorageApi
import java.util.concurrent.ConcurrentHashMap

/**
 * Appends one variant per loaded storage plugin to the served `storageProvider` property of a settings schema. The built-in variants
 * stay as they are and come first; the plugin variants follow, sorted by type id. A schema that has no such property, or a registry
 * without plugin factories, is returned as the same bytes, so a server without a storage plugin serves what it served before.
 *
 * A plugin whose settings schema cannot be generated is left out with a warning, and the other variants are still offered. The warning is
 * written once per plugin, not on every served schema.
 *
 * The nested definitions of a plugin schema (`definitions` or `$defs`) move to the root of the served schema under the name
 * `storage-<type id>-<name>`, and the references of the variant follow them, so a definition of the served schema is never replaced.
 */
@OptIn(ExperimentalIngotStorageApi::class)
internal class StorageProviderVariants(
    private val registry: StorageProviderRegistry,
    private val generate: (Class<*>) -> JsonNode,
    private val warn: (String) -> Unit = {},
) {

    /** The plugins that were already reported as broken, by type id and source. */
    private val reported = ConcurrentHashMap.newKeySet<String>()

    /** The schema with the plugin variants appended to every `storageProvider` one-of list, see the class documentation. */
    fun apply(schema: ByteArray): ByteArray {
        if (registry.pluginProviders().isEmpty()) return schema

        val root = DEFAULT_OBJECT_MAPPER.readTree(schema)
        val oneOfs = root.storageProviderOneOfs()
        if (oneOfs.isEmpty()) return schema

        val offered = oneOfs.flatMap { oneOf -> oneOf.mapNotNull { it.typeId() } }.toSet()
        val variants = pluginVariants(offered)
        if (variants.isEmpty()) return schema

        variants.forEach { (_, definitions) -> definitions.forEach { (keyword, merged) -> (root as ObjectNode).with(keyword).setAll<JsonNode>(merged) } }
        oneOfs.forEach { oneOf -> variants.forEach { (variant) -> oneOf.add(variant) } }
        return DEFAULT_OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(root)
    }

    /** The variants of the plugins whose type the served list does not offer yet, each with the definitions it moves to the root. */
    private fun pluginVariants(offered: Set<String>): List<Pair<ObjectNode, Map<String, ObjectNode>>> =
        registry.pluginProviders()
            .filter { it.type !in offered }
            .sortedBy { it.type }
            .mapNotNull { provider ->
                try {
                    variantOf(provider)
                } catch (exception: Exception) {
                    if (reported.add("${provider.type} ${provider.source}")) {
                        warn("Storage provider variant '${provider.type}' of ${provider.source} is not offered, its settings schema cannot be generated: ${exception.message}")
                    }
                    null
                }
            }

    /**
     * The settings schema of the plugin, with its `type` fixed to the type id of its factory, and its nested definitions by keyword,
     * renamed for the root of the served schema.
     */
    private fun variantOf(provider: StorageProviderRegistry.PluginStorageProvider): Pair<ObjectNode, Map<String, ObjectNode>> {
        val schema = generate(provider.factory.settingsType) as ObjectNode
        cleanupScheme(schema)
        schema.remove("\$schema")
        val definitions = DEFINITION_KEYWORDS
            .mapNotNull { keyword -> (schema.remove(keyword) as? ObjectNode)?.let { keyword to it } }
            .toMap()
        val renamed = definitions.flatMap { (keyword, nodes) ->
            nodes.fieldNames().asSequence().map { name -> "#/$keyword/${escape(name)}" to "#/$keyword/${escape("storage-${provider.type}-$name")}" }.toList()
        }.toMap()
        val moved = definitions.mapValues { (_, nodes) ->
            DEFAULT_OBJECT_MAPPER.createObjectNode().also { target ->
                nodes.fields().forEach { (name, node) -> target.set<JsonNode>("storage-${provider.type}-$name", node) }
            }
        }
        schema.renameReferences(renamed)
        moved.values.forEach { it.renameReferences(renamed) }
        schema.with("properties").set<JsonNode>(
            "type",
            DEFAULT_OBJECT_MAPPER.createObjectNode()
                .put("type", "string")
                .put("const", provider.type)
        )
        return schema to moved
    }

    /** Points every `$ref` of this node that [renamed] knows at its new name. */
    private fun JsonNode.renameReferences(renamed: Map<String, String>) {
        when (this) {
            is ObjectNode -> {
                (get(REFERENCE) as? TextNode)?.textValue()?.let { renamed[it] }?.let { put(REFERENCE, it) }
                forEach { it.renameReferences(renamed) }
            }
            is ArrayNode -> forEach { it.renameReferences(renamed) }
            else -> Unit
        }
    }

    /** The type id a served variant stands for, from the constant or the single enum value of its `type`. */
    private fun JsonNode.typeId(): String? {
        val type = get("properties")?.get("type") ?: return null
        return type.get("const")?.asText() ?: type.get("enum")?.singleOrNull()?.asText()
    }

    /** A definition name as a JSON pointer segment. */
    private fun escape(name: String): String =
        name.replace("~", "~0").replace("/", "~1")

    /** The one-of lists of every `storageProvider` property in the schema, at any depth. */
    private fun JsonNode.storageProviderOneOfs(): List<ArrayNode> {
        val own = (get("properties")?.get(STORAGE_PROVIDER_PROPERTY)?.get("oneOf") as? ArrayNode)
        val nested = when (this) {
            is ObjectNode -> fields().asSequence().flatMap { it.value.storageProviderOneOfs() }.toList()
            is ArrayNode -> flatMap { it.storageProviderOneOfs() }
            else -> emptyList()
        }
        return listOfNotNull(own) + nested
    }

    private companion object {
        const val STORAGE_PROVIDER_PROPERTY = "storageProvider"
        const val REFERENCE = "\$ref"
        val DEFINITION_KEYWORDS = listOf("definitions", "\$defs")
    }

}
