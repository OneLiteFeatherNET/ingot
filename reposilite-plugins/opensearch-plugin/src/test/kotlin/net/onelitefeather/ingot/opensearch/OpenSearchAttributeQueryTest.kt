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

package net.onelitefeather.ingot.opensearch

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import net.onelitefeather.ingot.opensearch.query.OpenSearchQueries
import net.onelitefeather.ingot.opensearch.query.FilterKind
import net.onelitefeather.ingot.opensearch.query.GroupPart
import net.onelitefeather.ingot.search.ArtifactSearchQuery
import net.onelitefeather.ingot.search.AttributeFilter
import net.onelitefeather.ingot.search.AttributeSort
import net.onelitefeather.ingot.search.gavKeyOf
import java.util.HexFormat
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
 * golden files under `golden/opensearch`, and the facet answer read back through a fake transport.
 */
internal class OpenSearchAttributeQueryTest {

    private val mapper = ObjectMapper()

    private val clock: Clock = Clock.fixed(Instant.parse("2026-11-21T10:00:00Z"), ZoneOffset.UTC)

    private val access = SearchAccess(listOf(RepositoryAccess("releases", PathPredicate.All)))

    private val environment = mapOf("INGOT_OPENSEARCH_URLS" to "https://search.test:9200")

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
        assertGolden("filter-attributes", OpenSearchQueries.queryFilter(query, access))
    }

    @Test
    fun `should leave the filter of its own key out of an attribute facet`() {
        // given: a query that filters the platforms and the versions
        val query = ArtifactSearchQuery(
            extensions = listOf("jar"),
            attributeFilters = listOf(AttributeFilter(PLATFORMS, listOf("Paper")), AttributeFilter(VERSIONS, listOf("1.21"))),
        )

        // then: the platforms facet counts under the versions filter, the fixed filters and the access value only
        assertGolden("facet-attribute", OpenSearchQueries.attributeFacet(PLATFORMS, query, access))
    }

    @Test
    fun `should narrow the listed groups by the attribute filters`() {
        // given: a query that filters the platforms
        val query = ArtifactSearchQuery(attributeFilters = listOf(AttributeFilter(PLATFORMS, listOf("Paper"))))

        // when: the bodies that count and list the groups are built
        val total = OpenSearchQueries.groupTotal(query, access, GroupPart.MAIN)
        val window = OpenSearchQueries.groupWindow(query, access, GroupPart.MAIN, window = 20)

        // then: both carry the attribute clause
        assertThat(fieldsOf(total.path("query"))).`as`("fields filtered by the group count").contains("attributes.$PLATFORMS")
        assertThat(fieldsOf(window.path("query"))).`as`("fields filtered by the group window").contains("attributes.$PLATFORMS")
    }

    @Test
    fun `should narrow the fixed facets by the attribute filters`() {
        // given: a query that filters the platforms
        val query = ArtifactSearchQuery(attributeFilters = listOf(AttributeFilter(PLATFORMS, listOf("Velocity"))))

        // when: the extension facet is built
        val facet = OpenSearchQueries.facet(FilterKind.EXTENSION, query, access)

        // then: it counts under the attribute filter
        assertThat(fieldsOf(facet.path("filter"))).`as`("fields filtered by the extension facet").contains("attributes.$PLATFORMS")
    }

    @Test
    fun `should count the asked attribute facets in one request and read them back in the order asked for`() {
        // given: a cluster that answers the facet request with buckets for both keys
        val exchange = FakeExchange { call -> facetAnswer(call) }
        val backend = OpenSearchSearchBackend(OpenSearchSettings.read(environment), clock, sslContext = null, exchange = exchange)

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
        val backend = OpenSearchSearchBackend(OpenSearchSettings.read(environment), clock, sslContext = null, exchange = exchange)

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
    fun `should declare attribute filters, facets and the attribute sort`() {
        // when: the backend is created
        val backend = OpenSearchSearchBackend(OpenSearchSettings.read(environment), clock, sslContext = null, exchange = FakeExchange())

        // then: the core lets all three attribute queries through
        assertThat(backend.capabilities)
            .`as`("capabilities")
            .contains(SearchCapability.ATTRIBUTE_FILTERS, SearchCapability.ATTRIBUTE_FACETS, SearchCapability.ATTRIBUTE_SORT)
    }

    @Test
    fun `should list the groups in the order the cluster sorted them by the attribute`() {
        // given: a cluster that sorts the groups beta, alpha, gamma and answers the group buckets in the reverse order
        val exchange = FakeExchange { call -> sortAnswer(call) }
        val backend = OpenSearchSearchBackend(OpenSearchSettings.read(environment), clock, sslContext = null, exchange = exchange)

        // when: a page sorted by the attribute is read
        val page = backend.search(ArtifactSearchQuery(attributeSort = AttributeSort(VERSIONS, descending = false)), access)

        // then: the items follow the order of the sorted hits, not the order of the buckets
        assertThat(page.items.map { it.artifactId }).`as`("artifacts by attribute").containsExactly("beta", "alpha", "gamma")
        assertThat(exchange.calls.map { call -> call.body?.let { mapper.readTree(it).has("collapse") } ?: false })
            .`as`("requests that collapse the files onto their group")
            .contains(true)
    }

    /** Answers a sorted read: the hits give the order of the GAV keys, the group buckets come in the opposite order. */
    private fun sortAnswer(call: HttpCall): HttpAnswer {
        val body = mapper.readTree(call.body)
        val answer = mapper.createObjectNode()
        when {
            body.has("collapse") -> {
                val hits = answer.putObject("hits").putArray("hits")
                sorted.forEach { name -> hits.addObject().putObject("fields").putArray("gav").add(gavOf(name)) }
            }
            body.at("/aggs/groups/terms/field").asText() == "gav" -> {
                val buckets = answer.putObject("aggregations").putObject("groups").putArray("buckets")
                sorted.reversed().forEach { name -> buckets.add(bucketOf(name)) }
            }
            body.has("aggs") && body.path("aggs").has("gavs") -> {
                val buckets = answer.putObject("aggregations").putObject("gavs").putArray("buckets")
                sorted.forEach { name ->
                    val bucket = buckets.addObject().put("key", gavOf(name))
                    bucket.putObject("repositories").putArray("buckets").addObject().put("key", "releases")
                        .putObject("origins").putArray("buckets").addObject().put("key", "HOSTED")
                }
            }
        }
        return HttpAnswer(200, emptyMap(), mapper.writeValueAsString(answer))
    }

    private fun gavOf(name: String): String = HexFormat.of().formatHex(gavKeyOf("com.example", name, "1.0"))

    private fun bucketOf(name: String): JsonNode {
        val bucket = mapper.createObjectNode().put("key", gavOf(name)).put("doc_count", 1)
        listOf("groupLc" to "com.example", "artifactLc" to name, "versionLc" to "1.0", "group" to "com.example", "artifact" to name, "version" to "1.0")
            .forEach { (field, value) -> bucket.putObject(field).putArray("buckets").addObject().put("key", value) }
        bucket.putObject("lastModified").put("value", 1L)
        bucket.putObject("sizeBytes").put("value", 1L)
        return bucket
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
        val url = requireNotNull(javaClass.classLoader.getResource("golden/opensearch/$name.json")) { "golden file $name is missing" }
        val expected = mapper.readTree(url.readText())
        val actual = mapper.readTree(mapper.writeValueAsString(body))
        assertThat(actual)
            .describedAs("the body differs from golden/opensearch/$name.json; actual body: $actual")
            .isEqualTo(expected)
    }

    private val sorted = listOf("beta", "alpha", "gamma")

    private companion object {
        const val PLATFORMS = "portal.platforms"
        const val VERSIONS = "portal.versions"
    }

}
