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
 * a placeholder takes the stored value at the same place, and lists are matched by their [SettingsKey]. A placeholder without a
 * stored counterpart is rejected, so a secret is never handed to a different entry.
 *
 * A matched list entry also keeps the secrets its body leaves out: a credential or a secret that is absent from the incoming entry
 * keeps the stored value, so a client that rebuilds an entry from part of it cannot erase the secret. An explicit `null` is still a
 * removal. When several stored entries share the key of the incoming one, no entry can be told apart from the others, so a secret
 * that would have to come from one of them is rejected instead of guessed.
 */
internal object SettingsSecrets {

    fun mask(type: Class<*>, settings: JsonNode): JsonNode =
        maskNode(settings.deepCopy(), shapeOf(type))

    /**
     * Restores the secrets of [incoming] from [stored]. A placeholder is replaced only when [placeholders] is set, which is the case
     * while secrets are hidden. Absent secrets of matched entries are kept either way.
     */
    fun restore(type: Class<*>, incoming: JsonNode, stored: JsonNode, placeholders: Boolean = true): Result<JsonNode, String> =
        restoreNode(incoming, listOf(stored), shapeOf(type), path = "", placeholders = placeholders, inEntry = false)

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

    /**
     * Restores one node. [stored] holds the stored nodes it may take values from: one for a node at a known place, one per entry
     * that shares the key for a list entry, and none for an entry that is new. [inEntry] is set below a matched list entry.
     */
    private fun restoreNode(
        incoming: JsonNode,
        stored: List<JsonNode?>,
        shape: SecretShape,
        path: String,
        placeholders: Boolean,
        inEntry: Boolean,
    ): Result<JsonNode, String> {
        if (shape.sensitive) {
            if (!placeholders || !incoming.isTextual || incoming.textValue() != HIDDEN_SECRET) {
                return incoming.asSuccess()
            }

            val counterpart = stored.singleOrNull()?.takeIf { it.isTextual }
            return counterpart?.asSuccess()
                ?: "Cannot keep the hidden value of '$path': no stored value matches this entry. Send the actual value instead of $HIDDEN_SECRET.".asError()
        }

        if (incoming is ObjectNode && shape.fields.isNotEmpty()) {
            val restored = incoming.deepCopy()
            for ((name, child) in shape.fields) {
                val childPath = if (path.isEmpty()) name else "$path.$name"
                val value = incoming.get(name)
                if (value == null) {
                    // A secret that the body leaves out of a matched entry keeps the stored one.
                    // A list is not a secret, so an absent list is not restored.
                    if (inEntry && child.items == null) {
                        val kept = stored.mapNotNull { it?.get(name) }.filterNot { it.isNull }
                        if (kept.isNotEmpty()) {
                            if (stored.size != 1) {
                                return "Cannot keep the stored value of '$childPath': several mirrored entries share its key. Send the value with the entry.".asError()
                            }
                            restored.set<JsonNode>(name, kept.single())
                        }
                    }
                    continue
                }

                val result = restoreNode(value, stored.map { it?.get(name) }, child, childPath, placeholders, inEntry)
                if (result.isErr) {
                    return result
                }
                restored.set<JsonNode>(name, result.get())
            }
            return restored.asSuccess()
        }

        val items = shape.items
        if (incoming is ArrayNode && items != null) {
            val storedEntries = stored.filterIsInstance<ArrayNode>().flatten()
            val restored = JsonNodeFactory.instance.arrayNode()
            incoming.forEachIndexed { index, entry ->
                val key = entry.get(items.key)?.asText()
                val counterparts = storedEntries.filter { key != null && it.get(items.key)?.asText() == key }
                val result = restoreNode(entry, counterparts, items.element, "$path[${key ?: index}]", placeholders, inEntry = true)
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
