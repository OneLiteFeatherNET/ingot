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

package net.onelitefeather.ingot.meilisearch

import com.fasterxml.jackson.databind.JsonNode

/**
 * The settings the plugin keeps on its own index (design D3, open question 6). [differences] lists the values that differ from the
 * stored settings, and each of them is logged with its old and new value before the settings are applied.
 */
internal object MeilisearchIndexSettings {

    /** The attributes of the filters of the access value and of the search. */
    val FILTERABLE: List<String> = listOf(
        "id", "repository", "path", "directories", "gav", "group_id", "artifact_id", "version", "group_id_lc", "artifact_id_lc",
        "version_lc", "artifact_key", "classifier", "extension", "snapshot", "origin", "source_host", "size_bytes", "last_modified",
        "indexed_at", "sha1",
    )

    val SORTABLE: List<String> = listOf("path", "repository", "group_id", "artifact_id", "version", "size_bytes", "last_modified")

    val SEARCHABLE: List<String> = listOf("group_id", "artifact_id", "version", "tokens")

    const val MAX_VALUES_PER_FACET: Int = 10_000
    const val MAX_TOTAL_HITS: Int = 100_000

    /** The settings as the server takes them in a request. Typo tolerance is off: the contract cannot hold with it (open question 7). */
    val desired: Map<String, Any> = mapOf(
        "filterableAttributes" to FILTERABLE,
        "sortableAttributes" to SORTABLE,
        "searchableAttributes" to SEARCHABLE,
        "typoTolerance" to mapOf("enabled" to false),
        "faceting" to mapOf("maxValuesPerFacet" to MAX_VALUES_PER_FACET),
        "pagination" to mapOf("maxTotalHits" to MAX_TOTAL_HITS),
    )

    /** One value that differs: its name, the stored value and the desired value, both as text. */
    data class Difference(val name: String, val stored: String, val desired: String)

    /** The differences between the stored settings of the index and [desired]. Attribute lists are compared as sets. */
    fun differences(stored: JsonNode): List<Difference> {
        val result = mutableListOf<Difference>()
        fun attributes(name: String, wanted: List<String>) {
            val current = stored.path(name).map { it.asText() }.sorted()
            val expected = wanted.sorted()
            if (current != expected) result += Difference(name, current.joinToString(), expected.joinToString())
        }
        fun value(name: String, current: JsonNode?, wanted: Any) {
            val actual = current?.toString() ?: "unset"
            if (actual != wanted.toString()) result += Difference(name, actual, wanted.toString())
        }
        attributes("filterableAttributes", FILTERABLE)
        attributes("sortableAttributes", SORTABLE)
        attributes("searchableAttributes", SEARCHABLE)
        value("typoTolerance.enabled", stored.path("typoTolerance").path("enabled").takeUnless { it.isMissingNode }, false)
        value("faceting.maxValuesPerFacet", stored.path("faceting").path("maxValuesPerFacet").takeUnless { it.isMissingNode }, MAX_VALUES_PER_FACET)
        value("pagination.maxTotalHits", stored.path("pagination").path("maxTotalHits").takeUnless { it.isMissingNode }, MAX_TOTAL_HITS)
        return result
    }

}
