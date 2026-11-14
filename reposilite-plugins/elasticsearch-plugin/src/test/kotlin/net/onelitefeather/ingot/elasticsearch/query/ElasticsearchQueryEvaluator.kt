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
package net.onelitefeather.ingot.elasticsearch.query

import com.fasterxml.jackson.databind.JsonNode
import java.util.Locale

/**
 * Evaluates the subset of the query DSL that the access filter uses, against one in-memory document. It lets the translation
 * tests run without a cluster: the same document fields the index stores (`repository`, `path`, `path_lc`) are read from a map.
 * Any clause outside the subset fails the test instead of passing silently.
 */
internal object ElasticsearchQueryEvaluator {

    fun matches(query: JsonNode, document: Map<String, String>): Boolean {
        return when {
            query.has("match_all") -> true
            query.has("match_none") -> false
            query.has("term") -> single(query, "term").let { (field, value) -> document[field] == value.asText() }
            query.has("terms") -> single(query, "terms").let { (field, values) ->
                values.any { document[field] == it.asText() }
            }
            query.has("prefix") -> single(query, "prefix").let { (field, value) ->
                document[field]?.startsWith(value.asText()) == true
            }
            query.has("bool") -> bool(query.get("bool"), document)
            else -> error("clause outside the evaluated subset: $query")
        }
    }

    private fun bool(node: JsonNode, document: Map<String, String>): Boolean {
        val filter = node.get("filter")?.toList().orEmpty()
        val mustNot = node.get("must_not")?.toList().orEmpty()
        val should = node.get("should")?.toList().orEmpty()
        if (!filter.all { matches(it, document) }) return false
        if (mustNot.any { matches(it, document) }) return false
        if (should.isEmpty()) return true
        val required = node.get("minimum_should_match")?.asInt() ?: 1
        return should.count { matches(it, document) } >= required
    }

    private fun single(query: JsonNode, kind: String): Pair<String, JsonNode> {
        val fields = query.get(kind).fields()
        require(fields.hasNext()) { "$kind clause without a field: $query" }
        val (field, value) = fields.next()
        return field to value
    }

    /** The document of one row, with the fields the access filter reads. */
    fun document(repository: String, path: String): Map<String, String> = mapOf(
        "repository" to repository,
        "path" to path,
        "path_lc" to path.lowercase(Locale.ROOT),
    )

}
