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
import net.onelitefeather.ingot.search.ArtifactSearchQuery
import net.onelitefeather.ingot.search.AttributeFilter
import net.onelitefeather.ingot.search.AttributeSort
import net.onelitefeather.ingot.search.InvalidSearchQueryException
import net.onelitefeather.ingot.search.enrichment.AttributeDeclaration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * The refusals of an attribute query (search attribute facets, D1, D2 and D6). A query may only use a declared key with the
 * matching flag, within the bounds, and only a feature the backend declares; every refusal is a 400 that names its cause.
 */
internal class AttributeQueryRulesTest {

    private val catalogue = listOf(
        AttributeDeclaration("demo.loader", filterable = true, facetable = true, sortable = true),
        AttributeDeclaration("demo.label", filterable = false, facetable = false, sortable = false),
    ).associateBy { it.key }

    private val everything = setOf(
        SearchCapability.ATTRIBUTES,
        SearchCapability.ATTRIBUTE_FILTERS,
        SearchCapability.ATTRIBUTE_FACETS,
        SearchCapability.ATTRIBUTE_SORT,
    )

    private fun refusal(query: ArtifactSearchQuery, capabilities: Set<SearchCapability> = everything): InvalidSearchQueryException =
        assertThatThrownBy { validateAttributeQuery(query, catalogue, capabilities) }
            .isInstanceOf(InvalidSearchQueryException::class.java)
            .actual() as InvalidSearchQueryException

    @Test
    fun `a query without attribute parameters is allowed on a backend that declares nothing`() {
        assertThatCode { validateAttributeQuery(ArtifactSearchQuery(q = "app"), emptyMap(), emptySet()) }
            .`as`("a query of the fixed fields only").doesNotThrowAnyException()
    }

    @Test
    fun `a filter, sort and facet on declared keys with their flags is allowed`() {
        val query = ArtifactSearchQuery(
            attributeFilters = listOf(AttributeFilter("demo.loader", listOf("fabric"))),
            attributeSort = AttributeSort("demo.loader", descending = true),
            attributeFacets = setOf("demo.loader"),
        )

        assertThatCode { validateAttributeQuery(query, catalogue, everything) }
            .`as`("a query on a fully declared key").doesNotThrowAnyException()
    }

    @Test
    fun `a filter on an undeclared key is refused as undeclared-attribute naming the key`() {
        val refused = refusal(ArtifactSearchQuery(attributeFilters = listOf(AttributeFilter("demo.missing", listOf("x")))))

        assertThat(refused.problemType).`as`("problem type").isEqualTo(UNDECLARED_ATTRIBUTE_PROBLEM_TYPE)
        assertThat(refused.parameter).`as`("parameter").isEqualTo("attribute")
        assertThat(refused.message).`as`("message").contains("demo.missing")
    }

    @Test
    fun `a sort on an undeclared key is refused as undeclared-attribute naming the key`() {
        val refused = refusal(ArtifactSearchQuery(attributeSort = AttributeSort("demo.missing", descending = false)))

        assertThat(refused.problemType).`as`("problem type").isEqualTo(UNDECLARED_ATTRIBUTE_PROBLEM_TYPE)
        assertThat(refused.parameter).`as`("parameter").isEqualTo("attributeSort")
        assertThat(refused.message).`as`("message").contains("demo.missing")
    }

    @Test
    fun `a facet on an undeclared key is refused as undeclared-attribute naming the key`() {
        val refused = refusal(ArtifactSearchQuery(attributeFacets = setOf("demo.missing")))

        assertThat(refused.problemType).`as`("problem type").isEqualTo(UNDECLARED_ATTRIBUTE_PROBLEM_TYPE)
        assertThat(refused.parameter).`as`("parameter").isEqualTo("attributeFacets")
        assertThat(refused.message).`as`("message").contains("demo.missing")
    }

    @Test
    fun `a filter on a key declared without filterable is refused`() {
        val refused = refusal(ArtifactSearchQuery(attributeFilters = listOf(AttributeFilter("demo.label", listOf("x")))))

        assertThat(refused.problemType).`as`("problem type").isEqualTo(UNDECLARED_ATTRIBUTE_PROBLEM_TYPE)
        assertThat(refused.message).`as`("message").contains("demo.label").contains("filterable")
    }

    @Test
    fun `a sort on a key declared without sortable is refused`() {
        val refused = refusal(ArtifactSearchQuery(attributeSort = AttributeSort("demo.label", descending = false)))

        assertThat(refused.message).`as`("message").contains("demo.label").contains("sortable")
    }

    @Test
    fun `a facet on a key declared without facetable is refused`() {
        val refused = refusal(ArtifactSearchQuery(attributeFacets = setOf("demo.label")))

        assertThat(refused.message).`as`("message").contains("demo.label").contains("facetable")
    }

    @Test
    fun `more than 4 filter keys are refused`() {
        val keys = (1..5).map { "demo.key$it" }
        val wide = keys.associateWith { AttributeDeclaration(it, filterable = true, facetable = false, sortable = false) }
        val query = ArtifactSearchQuery(attributeFilters = keys.map { AttributeFilter(it, listOf("x")) })

        assertThatThrownBy { validateAttributeQuery(query, wide, everything) }
            .`as`("five filter keys")
            .isInstanceOf(InvalidSearchQueryException::class.java)
            .hasMessageContaining("4")
    }

    @Test
    fun `more than 16 values of one filter key are refused`() {
        val refused = refusal(ArtifactSearchQuery(attributeFilters = listOf(AttributeFilter("demo.loader", (1..17).map { "v$it" }))))

        assertThat(refused.parameter).`as`("parameter").isEqualTo("attribute")
        assertThat(refused.message).`as`("message").contains("16")
    }

    @Test
    fun `more than 8 facet keys are refused`() {
        val keys = (1..9).map { "demo.key$it" }
        val wide = keys.associateWith { AttributeDeclaration(it, filterable = false, facetable = true, sortable = false) }

        assertThatThrownBy { validateAttributeQuery(ArtifactSearchQuery(attributeFacets = keys.toSet()), wide, everything) }
            .`as`("nine facet keys")
            .isInstanceOf(InvalidSearchQueryException::class.java)
            .hasMessageContaining("8")
    }

    @Test
    fun `a filter on a backend without ATTRIBUTE_FILTERS is refused as unsupported-search-capability`() {
        val query = ArtifactSearchQuery(attributeFilters = listOf(AttributeFilter("demo.loader", listOf("fabric"))))

        val refused = refusal(query, everything - SearchCapability.ATTRIBUTE_FILTERS)

        assertThat(refused.problemType).`as`("problem type").isEqualTo(UNSUPPORTED_SEARCH_CAPABILITY_PROBLEM_TYPE)
        assertThat(refused.message).`as`("message").contains("ATTRIBUTE_FILTERS")
    }

    @Test
    fun `a sort on a backend without ATTRIBUTE_SORT is refused as unsupported-search-capability`() {
        val refused = refusal(ArtifactSearchQuery(attributeSort = AttributeSort("demo.loader", descending = false)), everything - SearchCapability.ATTRIBUTE_SORT)

        assertThat(refused.problemType).`as`("problem type").isEqualTo(UNSUPPORTED_SEARCH_CAPABILITY_PROBLEM_TYPE)
        assertThat(refused.message).`as`("message").contains("ATTRIBUTE_SORT")
    }

    @Test
    fun `a facet on a backend without ATTRIBUTE_FACETS is refused as unsupported-search-capability`() {
        val refused = refusal(ArtifactSearchQuery(attributeFacets = setOf("demo.loader")), everything - SearchCapability.ATTRIBUTE_FACETS)

        assertThat(refused.problemType).`as`("problem type").isEqualTo(UNSUPPORTED_SEARCH_CAPABILITY_PROBLEM_TYPE)
        assertThat(refused.message).`as`("message").contains("ATTRIBUTE_FACETS")
    }

}
