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

@file:OptIn(ExperimentalIngotSearchApi::class)

package net.onelitefeather.ingot.elasticsearch

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import net.onelitefeather.ingot.elasticsearch.query.ElasticsearchQueries
import net.onelitefeather.ingot.elasticsearch.query.FilterKind
import net.onelitefeather.ingot.elasticsearch.query.GroupPart
import net.onelitefeather.ingot.search.ArtifactSearchQuery
import net.onelitefeather.ingot.search.AttributeFilter
import net.onelitefeather.ingot.search.FacetBucket
import net.onelitefeather.ingot.search.access.PathPredicate
import net.onelitefeather.ingot.search.access.RepositoryAccess
import net.onelitefeather.ingot.search.access.SearchAccess
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.backend.SearchCapability
import net.onelitefeather.ingot.search.http.HttpAnswer
import net.onelitefeather.ingot.search.http.HttpCall
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Filters and facets on plugin attributes (search attribute facets, D3 and D5): the bodies sent to the cluster, compared with the
 * golden files under `golden/elasticsearch`, and the facet answer read back through a fake transport.
 */
internal class ElasticsearchAttributeQueryTest {

    private val mapper = ObjectMapper()

    private val clock: Clock = Clock.fixed(Instant.parse("2026-11-21T10:00:00Z"), ZoneOffset.UTC)

    private val access = SearchAccess(listOf(RepositoryAccess("releases", PathPredicate.All)))

    private val environment = mapOf("INGOT_ELASTICSEARCH_URLS" to "https://search.test:9200")

    @Test
    fun `should match any value of one key ignoring case and every filtered key`() {
        // given: two filters on the platforms, which are one OR, and one on the versions
        val query = ArtifactSearchQuery(
            extensions = listOf("jar"),
            attributeFilters = listOf(
                AttributeFilter(PLATFORMS, listOf("Paper", "velocity")),
                AttributeFilter(VERSIONS, listOf("1.21")),
                AttributeFilter(PLATFORMS, listOf("Folia", "paper")),
            ),
        )

        // then: one clause per key, after the fixed filters and before the access value, each value once
        assertGolden("filter-attributes", ElasticsearchQueries.queryFilter(query, access))
    }

    @Test
    fun `should leave the filter of its own key out of an attribute facet`() {
        // given: a query that filters the platforms and the versions
        val query = ArtifactSearchQuery(
            extensions = listOf("jar"),
            attributeFilters = listOf(AttributeFilter(PLATFORMS, listOf("Paper")), AttributeFilter(VERSIONS, listOf("1.21"))),
        )

        // then: the platforms facet counts under the versions filter, the fixed filters and the access value only
        assertGolden("facet-attribute", ElasticsearchQueries.attributeFacet(PLATFORMS, query, access))
    }

    @Test
    fun `should narrow the listed groups by the attribute filters`() {
        // given: a query that filters the platforms
        val query = ArtifactSearchQuery(attributeFilters = listOf(AttributeFilter(PLATFORMS, listOf("Paper"))))

        // when: the bodies that count and list the groups are built
        val total = ElasticsearchQueries.groupTotal(query, access, GroupPart.MAIN)
        val window = ElasticsearchQueries.groupWindow(query, access, GroupPart.MAIN, window = 20)

        // then: both carry the attribute clause
        assertThat(fieldsOf(total.path("query"))).`as`("fields filtered by the group count").contains("attributes.$PLATFORMS")
        assertThat(fieldsOf(window.path("query"))).`as`("fields filtered by the group window").contains("attributes.$PLATFORMS")
    }

    @Test
    fun `should narrow the fixed facets by the attribute filters`() {
        // given: a query that filters the platforms
        val query = ArtifactSearchQuery(attributeFilters = listOf(AttributeFilter(PLATFORMS, listOf("Velocity"))))

        // when: the extension facet is built
        val facet = ElasticsearchQueries.facet(FilterKind.EXTENSION, query, access)

        // then: it counts under the attribute filter
        assertThat(fieldsOf(facet.path("filter"))).`as`("fields filtered by the extension facet").contains("attributes.$PLATFORMS")
    }

    @Test
    fun `should count the asked attribute facets in one request and read them back in the order asked for`() {
        // given: a cluster that answers the facet request with buckets for both keys
        val exchange = FakeExchange { call -> facetAnswer(call) }
        val backend = ElasticsearchSearchBackend(ElasticsearchSettings.read(environment), clock, sslContext = null, exchange = exchange)

        // when: the facets of a query with two attribute facets are read
        val facets = backend.facets(ArtifactSearchQuery(attributeFacets = linkedSetOf(VERSIONS, PLATFORMS)), access)

        // then: one search request carries both aggregations, and each key gets its own buckets
        assertThat(exchange.calls).`as`("requests of one facet read").hasSize(1)
        assertThat(facets.attributes?.keys).`as`("attribute facet keys").containsExactly(VERSIONS, PLATFORMS)
        assertThat(facets.attributes?.get(VERSIONS)).`as`("version buckets").containsExactly(FacetBucket("1.21", 2))
        assertThat(facets.attributes?.get(PLATFORMS))
            .`as`("platform buckets")
            .containsExactly(FacetBucket("Paper", 2), FacetBucket("Folia", 1))
    }

    @Test
    fun `should leave the attribute facets out unless they are asked for`() {
        // given: a cluster that answers every facet request
        val exchange = FakeExchange { call -> facetAnswer(call) }
        val backend = ElasticsearchSearchBackend(ElasticsearchSettings.read(environment), clock, sslContext = null, exchange = exchange)

        // when: the facets of a query without attribute facets are read
        val facets = backend.facets(ArtifactSearchQuery(), access)

        // then: neither the request nor the answer has attribute facets
        val body = mapper.readTree(exchange.calls.single().body)
        assertThat(body.path("aggs").fieldNames().asSequence().toList())
            .`as`("aggregations of a facet read without attribute facets")
            .containsExactlyInAnyOrder("repository", "extension", "classifier", "origin", "snapshot")
        assertThat(facets.attributes).`as`("attribute facets").isNull()
    }

    @Test
    fun `should declare attribute filters and facets but not the attribute sort`() {
        // when: the backend is created
        val backend = ElasticsearchSearchBackend(ElasticsearchSettings.read(environment), clock, sslContext = null, exchange = FakeExchange())

        // then: the core lets filters and facets through, and refuses a sort before the backend sees it
        assertThat(backend.capabilities)
            .`as`("capabilities")
            .contains(SearchCapability.ATTRIBUTE_FILTERS, SearchCapability.ATTRIBUTE_FACETS)
            .doesNotContain(SearchCapability.ATTRIBUTE_SORT)
    }

    /** Answers a facet read: each attribute aggregation with the buckets of the field it counts. */
    private fun facetAnswer(call: HttpCall): HttpAnswer {
        val aggregations = mapper.createObjectNode()
        mapper.readTree(call.body).path("aggs").properties().forEach { (name, aggregation) ->
            val buckets = when (aggregation.at("/aggs/buckets/terms/field").asText()) {
                "attributes.$VERSIONS" -> listOf("1.21" to 2)
                "attributes.$PLATFORMS" -> listOf("Paper" to 2, "Folia" to 1)
                else -> emptyList()
            }
            val list = aggregations.putObject(name).putObject("buckets").putArray("buckets")
            buckets.forEach { (key, count) -> list.addObject().put("key", key).put("doc_count", count) }
        }
        val answer = mapper.createObjectNode()
        answer.set<JsonNode>("aggregations", aggregations)
        return HttpAnswer(200, emptyMap(), mapper.writeValueAsString(answer))
    }

    /** Every field name that a `term` or `terms` clause below [node] filters on. */
    private fun fieldsOf(node: JsonNode): List<String> =
        (node.findValues("term") + node.findValues("terms")).flatMap { it.fieldNames().asSequence().toList() }

    private fun assertGolden(name: String, body: JsonNode) {
        val url = requireNotNull(javaClass.classLoader.getResource("golden/elasticsearch/$name.json")) { "golden file $name is missing" }
        val expected = mapper.readTree(url.readText())
        val actual = mapper.readTree(mapper.writeValueAsString(body))
        assertThat(actual)
            .describedAs("the body differs from golden/elasticsearch/$name.json; actual body: $actual")
            .isEqualTo(expected)
    }

    private companion object {
        const val PLATFORMS = "portal.platforms"
        const val VERSIONS = "portal.versions"
    }

}
