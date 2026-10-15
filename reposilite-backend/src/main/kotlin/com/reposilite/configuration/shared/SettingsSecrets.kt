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

package com.reposilite.configuration.shared

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.configuration.shared.api.Sensitive
import com.reposilite.configuration.shared.api.SettingsKey
import io.javalin.openapi.OneOf
import panda.std.Result
import panda.std.asError
import panda.std.asSuccess
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties

/**
 * Stands in for every non-empty secret when the settings API hides them.
 * Sending it back unchanged keeps the stored value.
 */
internal const val HIDDEN_SECRET = "********"

private const val WITHHELD_DOMAIN = "<withheld>"
private const val UNREADABLE_DOCUMENT = "<unreadable shared configuration withheld>"

/**
 * Where the secrets of one settings type sit, derived once from the annotations of its properties.
 * [fields] only holds the children that contain a secret, [items] describes the entries of a list.
 */
internal class SecretShape(
    val sensitive: Boolean = false,
    val fields: Map<String, SecretShape> = emptyMap(),
    val items: ItemShape? = null,
) {

    val isEmpty: Boolean
        get() = !sensitive && fields.isEmpty() && items == null

    companion object {
        val EMPTY = SecretShape()
    }

}

/**
 * The entries of a list. [key] names the property that identifies an entry, so a placeholder can be matched to the stored entry.
 */
internal class ItemShape(
    val key: String,
    val element: SecretShape,
)

/**
 * Hides and restores secrets of shared settings documents, as JSON trees.
 *
 * Masking replaces every non-empty [Sensitive] value with [HIDDEN_SECRET]. Restoring walks the incoming tree next to the stored one:
 * a placeholder takes the stored value at the same place, lists are matched by their [SettingsKey], and a placeholder without a stored
 * counterpart is rejected, so a secret is never handed to a different entry.
 */
internal object SettingsSecrets {

    fun mask(type: Class<*>, settings: JsonNode): JsonNode =
        maskNode(settings.deepCopy(), shapeOf(type))

    fun restore(type: Class<*>, incoming: JsonNode, stored: JsonNode): Result<JsonNode, String> =
        restoreNode(incoming, stored, shapeOf(type), path = "")

    /**
     * Masks a shared configuration document for logging. Domains without a known type are withheld completely.
     */
    fun redactDocument(document: String, types: Map<String, Class<*>>): String {
        val root = runCatching { DEFAULT_OBJECT_MAPPER.readTree(document) }.getOrNull() as? ObjectNode
            ?: return UNREADABLE_DOCUMENT

        root.fieldNames().asSequence().toList().forEach { domain ->
            val type = types[domain]
            root.set<JsonNode>(domain, if (type == null) TextNode(WITHHELD_DOMAIN) else mask(type, root.get(domain)))
        }

        return DEFAULT_OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root)
    }

    private fun maskNode(node: JsonNode, shape: SecretShape): JsonNode {
        if (shape.sensitive) {
            return if (node.isTextual && node.textValue().isNotEmpty()) TextNode(HIDDEN_SECRET) else node
        }

        when (node) {
            is ObjectNode -> shape.fields.forEach { (name, child) ->
                node.get(name)?.let { node.set<JsonNode>(name, maskNode(it, child)) }
            }
            is ArrayNode -> shape.items?.let { items ->
                for (index in 0 until node.size()) {
                    node.set(index, maskNode(node[index], items.element))
                }
            }
        }

        return node
    }

    private fun restoreNode(incoming: JsonNode, stored: JsonNode?, shape: SecretShape, path: String): Result<JsonNode, String> {
        if (shape.sensitive) {
            if (!incoming.isTextual || incoming.textValue() != HIDDEN_SECRET) {
                return incoming.asSuccess()
            }

            return stored
                ?.takeIf { it.isTextual }
                ?.asSuccess()
                ?: "Cannot keep the hidden value of '$path': no stored value matches this entry. Send the actual value instead of $HIDDEN_SECRET.".asError()
        }

        if (incoming is ObjectNode && shape.fields.isNotEmpty()) {
            val restored = incoming.deepCopy()
            for ((name, child) in shape.fields) {
                val value = incoming.get(name) ?: continue
                val result = restoreNode(value, stored?.get(name), child, if (path.isEmpty()) name else "$path.$name")
                if (result.isErr) {
                    return result
                }
                restored.set<JsonNode>(name, result.get())
            }
            return restored.asSuccess()
        }

        val items = shape.items
        if (incoming is ArrayNode && items != null) {
            val storedEntries = stored as? ArrayNode
            val restored = JsonNodeFactory.instance.arrayNode()
            incoming.forEachIndexed { index, entry ->
                val key = entry.get(items.key)?.asText()
                val counterpart = key?.let { id -> storedEntries?.filter { it.get(items.key)?.asText() == id }?.singleOrNull() }
                val result = restoreNode(entry, counterpart, items.element, "$path[${key ?: index}]")
                if (result.isErr) {
                    return result
                }
                restored.add(result.get())
            }
            return restored.asSuccess()
        }

        return incoming.asSuccess()
    }

    private fun shapeOf(type: Class<*>): SecretShape =
        objectShape(type.kotlin, enclosing = emptySet())

    private fun objectShape(type: KClass<*>, enclosing: Set<KClass<*>>): SecretShape {
        if (type in enclosing) {
            return SecretShape.EMPTY
        }

        val path = enclosing + type
        val fields = type.memberProperties
            .associate { property -> property.name to propertyShape(property, path) }
            .filterValues { !it.isEmpty }

        return SecretShape(fields = fields)
    }

    private fun propertyShape(property: KProperty1<*, *>, enclosing: Set<KClass<*>>): SecretShape {
        if (property.getter.findAnnotation<Sensitive>() != null) {
            check(property.returnType.classifier == String::class) { "Property '${property.name}' is marked @Sensitive but is not a String" }
            return SecretShape(sensitive = true)
        }

        val classifier = property.returnType.classifier as? KClass<*> ?: return SecretShape.EMPTY

        if (classifier == List::class) {
            val element = property.returnType.arguments.firstOrNull()?.type?.classifier as? KClass<*>
                ?: return SecretShape.EMPTY
            val elementShape = objectShape(element, enclosing)
            if (elementShape.isEmpty) {
                return SecretShape.EMPTY
            }

            val key = element.memberProperties.firstOrNull { it.getter.findAnnotation<SettingsKey>() != null }?.name
                ?: error("${element.simpleName} contains secrets inside a list but declares no @SettingsKey")
            return SecretShape(items = ItemShape(key, elementShape))
        }

        property.getter.findAnnotation<OneOf>()?.let { oneOf ->
            return oneOf.value
                .map { objectShape(it, enclosing) }
                .fold(SecretShape.EMPTY, ::union)
        }

        return if (classifier.isData) objectShape(classifier, enclosing) else SecretShape.EMPTY
    }

    /**
     * Combines the shapes of the alternatives of a `oneOf` property, so a placeholder is recognised for any of them.
     */
    private fun union(left: SecretShape, right: SecretShape): SecretShape {
        if (left.isEmpty) return right
        if (right.isEmpty) return left

        val fields = (left.fields.keys + right.fields.keys)
            .associateWith { name -> union(left.fields[name] ?: SecretShape.EMPTY, right.fields[name] ?: SecretShape.EMPTY) }
            .filterValues { !it.isEmpty }

        val items = when {
            left.items == null -> right.items
            right.items == null -> left.items
            else -> ItemShape(left.items.key, union(left.items.element, right.items.element))
        }

        return SecretShape(sensitive = left.sensitive || right.sensitive, fields = fields, items = items)
    }

}
