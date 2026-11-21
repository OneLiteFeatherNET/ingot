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

@file:OptIn(ExperimentalIngotSearchApi::class, ExperimentalIngotPluginApi::class)

package net.onelitefeather.ingot.search.backend

import net.onelitefeather.ingot.plugin.ExperimentalIngotPluginApi
import net.onelitefeather.ingot.search.ArtifactSearchEngine
import net.onelitefeather.ingot.search.ArtifactSearchPage
import net.onelitefeather.ingot.search.ArtifactSearchQuery
import net.onelitefeather.ingot.search.InvalidSearchQueryException
import net.onelitefeather.ingot.search.SearchFacets
import net.onelitefeather.ingot.search.access.SearchAccess
import net.onelitefeather.ingot.search.enrichment.AttributeDeclaration

/** The problem type of a query on a key that is not declared, or not declared for that use (search attribute facets, D1). */
internal const val UNDECLARED_ATTRIBUTE_PROBLEM_TYPE = "urn:ingot:problem:undeclared-attribute"

/** The problem type of a query that needs a capability the backend does not declare (search attribute facets, D6). */
internal const val UNSUPPORTED_SEARCH_CAPABILITY_PROBLEM_TYPE = "urn:ingot:problem:unsupported-search-capability"

private const val MAX_FILTER_KEYS = 4
private const val MAX_FILTER_VALUES = 16
private const val MAX_FACET_KEYS = 8

private const val FILTER_PARAMETER = "attribute"
private const val SORT_PARAMETER = "attributeSort"
private const val FACETS_PARAMETER = "attributeFacets"

/**
 * Refuses an attribute query with an [InvalidSearchQueryException] (search attribute facets, D1, D2 and D6). The bounds are checked
 * first, then every key against [catalogue] and its flag, then the features against the [capabilities] of the backend. A query
 * without attribute parameters is always allowed.
 */
internal fun validateAttributeQuery(
    query: ArtifactSearchQuery,
    catalogue: Map<String, AttributeDeclaration>,
    capabilities: Set<SearchCapability>,
) {
    val filterKeys = query.attributeFilters.map { it.key }.distinct()
    if (filterKeys.size > MAX_FILTER_KEYS) {
        throw InvalidSearchQueryException(FILTER_PARAMETER, "$FILTER_PARAMETER names ${filterKeys.size} keys, at most $MAX_FILTER_KEYS are allowed")
    }
    query.attributeFilters.firstOrNull { it.values.distinct().size > MAX_FILTER_VALUES }?.let {
        throw InvalidSearchQueryException(FILTER_PARAMETER, "$FILTER_PARAMETER gives ${it.key} more than $MAX_FILTER_VALUES values")
    }
    if (query.attributeFacets.size > MAX_FACET_KEYS) {
        throw InvalidSearchQueryException(FACETS_PARAMETER, "$FACETS_PARAMETER names ${query.attributeFacets.size} keys, at most $MAX_FACET_KEYS are allowed")
    }

    filterKeys.forEach { declared(catalogue, it, FILTER_PARAMETER, "filterable") { declaration -> declaration.filterable } }
    query.attributeSort?.let { sort -> declared(catalogue, sort.key, SORT_PARAMETER, "sortable") { it.sortable } }
    query.attributeFacets.forEach { declared(catalogue, it, FACETS_PARAMETER, "facetable") { declaration -> declaration.facetable } }

    if (filterKeys.isNotEmpty()) supported(capabilities, SearchCapability.ATTRIBUTE_FILTERS, FILTER_PARAMETER)
    if (query.attributeSort != null) supported(capabilities, SearchCapability.ATTRIBUTE_SORT, SORT_PARAMETER)
    if (query.attributeFacets.isNotEmpty()) supported(capabilities, SearchCapability.ATTRIBUTE_FACETS, FACETS_PARAMETER)
}

private fun declared(
    catalogue: Map<String, AttributeDeclaration>,
    key: String,
    parameter: String,
    flag: String,
    allows: (AttributeDeclaration) -> Boolean,
) {
    val declaration = catalogue[key]
        ?: throw InvalidSearchQueryException(parameter, "The attribute $key is not declared by any enricher", UNDECLARED_ATTRIBUTE_PROBLEM_TYPE)
    if (!allows(declaration)) {
        throw InvalidSearchQueryException(parameter, "The attribute $key is not declared as $flag", UNDECLARED_ATTRIBUTE_PROBLEM_TYPE)
    }
}

private fun supported(capabilities: Set<SearchCapability>, capability: SearchCapability, parameter: String) {
    if (capability !in capabilities) {
        throw InvalidSearchQueryException(
            parameter,
            "The search backend does not support $parameter: it does not declare $capability",
            UNSUPPORTED_SEARCH_CAPABILITY_PROBLEM_TYPE,
        )
    }
}

/**
 * Checks the attribute parameters of every search and facet read before [delegate] sees them (search attribute facets, D6). A
 * refused query never reaches the backend, so a missing feature is a 400 and never an empty result. [catalogue] and
 * [capabilities] are read per call.
 */
internal class AttributeCheckedSearchEngine(
    private val delegate: ArtifactSearchEngine,
    private val catalogue: () -> Map<String, AttributeDeclaration>,
    private val capabilities: () -> Set<SearchCapability>,
) : ArtifactSearchEngine by delegate {

    override fun search(query: ArtifactSearchQuery, access: SearchAccess): ArtifactSearchPage {
        validateAttributeQuery(query, catalogue(), capabilities())
        return delegate.search(query, access)
    }

    override fun facets(query: ArtifactSearchQuery, access: SearchAccess): SearchFacets {
        validateAttributeQuery(query, catalogue(), capabilities())
        return delegate.facets(query, access)
    }

}
