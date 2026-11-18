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
import net.onelitefeather.ingot.storage.ExperimentalIngotStorageApi

/**
 * Appends one variant per loaded storage plugin to the served `storageProvider` property of a settings schema. The built-in variants
 * stay as they are and come first; the plugin variants follow, sorted by type id. A schema that has no such property, or a registry
 * without plugin factories, is returned as the same bytes, so a server without a storage plugin serves what it served before.
 *
 * A plugin whose settings schema cannot be generated is left out with a warning, and the other variants are still offered.
 */
@OptIn(ExperimentalIngotStorageApi::class)
internal class StorageProviderVariants(
    private val registry: StorageProviderRegistry,
    private val generate: (Class<*>) -> JsonNode,
    private val warn: (String) -> Unit = {},
) {

    /** The schema with the plugin variants appended to every `storageProvider` one-of list, see the class documentation. */
    fun apply(schema: ByteArray): ByteArray {
        if (registry.pluginProviders().isEmpty()) return schema

        val root = DEFAULT_OBJECT_MAPPER.readTree(schema)
        val oneOfs = root.storageProviderOneOfs()
        if (oneOfs.isEmpty()) return schema

        val variants = pluginVariants()
        if (variants.isEmpty()) return schema

        oneOfs.forEach { oneOf -> variants.forEach { oneOf.add(it) } }
        return DEFAULT_OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(root)
    }

    private fun pluginVariants(): List<ObjectNode> =
        registry.pluginProviders()
            .sortedBy { it.type }
            .mapNotNull { provider ->
                try {
                    variantOf(provider)
                } catch (exception: Exception) {
                    warn("Storage provider variant '${provider.type}' of ${provider.source} is not offered, its settings schema cannot be generated: ${exception.message}")
                    null
                }
            }

    /** The settings schema of the plugin, with its `type` fixed to the type id of its factory. */
    private fun variantOf(provider: StorageProviderRegistry.PluginStorageProvider): ObjectNode {
        val schema = generate(provider.factory.settingsType) as ObjectNode
        cleanupScheme(schema)
        schema.remove("\$schema")
        schema.with("properties").set<JsonNode>(
            "type",
            DEFAULT_OBJECT_MAPPER.createObjectNode()
                .put("type", "string")
                .put("const", provider.type)
        )
        return schema
    }

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
    }

}
