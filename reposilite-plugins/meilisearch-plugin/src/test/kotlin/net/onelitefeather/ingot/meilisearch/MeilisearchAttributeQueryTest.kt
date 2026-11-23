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

package net.onelitefeather.ingot.meilisearch

import com.fasterxml.jackson.databind.JsonNode
import com.reposilite.ReposiliteObjectMapper
import net.onelitefeather.ingot.plugin.ExperimentalIngotPluginApi
import net.onelitefeather.ingot.search.ArtifactSearchQuery
import net.onelitefeather.ingot.search.AttributeFilter
import net.onelitefeather.ingot.search.AttributeSort
import net.onelitefeather.ingot.search.FacetBucket
import net.onelitefeather.ingot.search.SearchUnavailableException
import net.onelitefeather.ingot.search.access.PathPredicate
import net.onelitefeather.ingot.search.access.RepositoryAccess
import net.onelitefeather.ingot.search.access.SearchAccess
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.backend.SearchCapability
import net.onelitefeather.ingot.search.enrichment.AttributeDeclaration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * Filters, facets and sorts on declared attributes at the Meilisearch backend (search attribute facets, D5): the settings derived
 * from the declarations, the pending state of a settings change, the filter string, the facet distribution without the facet's own
 * key and the sort. Against the in-process fake server; no sleeps, no network.
 */
internal class MeilisearchAttributeQueryTest {

    private val mapper = ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
    private val index = "ingot-artifacts"
    private val environment = mapOf("INGOT_MEILISEARCH_URL" to "https://search.example.test", "INGOT_MEILISEARCH_API_KEY" to "test-key")
    private val access = SearchAccess(listOf(RepositoryAccess("releases", PathPredicate.All)))

    private val platforms = AttributeDeclaration("portal.platforms", filterable = true, facetable = true, sortable = false)
    private val versions = AttributeDeclaration("portal.versions", filterable = true, facetable = false, sortable = true)
    private val declared = listOf(platforms, versions)

    private class Fixture(val backend: MeilisearchSearchBackend, val server: FakeMeilisearch)

    private fun fixture(
        waitStep: Duration = Duration.ZERO,
        declarations: List<AttributeDeclaration> = declared,
        setup: FakeMeilisearch.() -> Unit,
    ): Fixture {
        val clock = ManualClock()
        val server = FakeMeilisearch(clock).apply(setup)
        val backend = MeilisearchSearchBackend(
            MeilisearchSettings.read(environment, { error("no key file expected") }),
            clock,
            server,
            ManualWaiter(clock, waitStep),
            declarations = declarations,
        )
        return Fixture(backend, server)
    }

    private fun FakeMeilisearch.supported(settings: Map<String, Any>) {
        on("GET", "/version", body = """{"pkgVersion":"1.15.0"}""")
        on("GET", "/indexes/$index", body = """{"uid":"$index"}""")
        on("GET", "/indexes/$index/settings", body = mapper.writeValueAsString(settings))
    }

    private fun FakeMeilisearch.searchBodies(): List<JsonNode> = calls
        .filter { it.method == "POST" && it.uri.path == "/indexes/$index/search" }
        .map { mapper.readTree(it.body.orEmpty()) }

    @Test
    fun `should declare the three attribute queries next to the attribute storage`() {
        val fixture = fixture {}

        assertThat(fixture.backend.capabilities)
            .`as`("capabilities of the meilisearch backend")
            .containsExactlyInAnyOrder(
                SearchCapability.ATTRIBUTES,
                SearchCapability.ATTRIBUTE_FILTERS,
                SearchCapability.ATTRIBUTE_FACETS,
                SearchCapability.ATTRIBUTE_SORT,
            )
    }

    @Test
    fun `should make a filterable or facetable key filterable and only a sortable key sortable`() {
        val settings = MeilisearchIndexSettings.desiredFor(declared)

        assertThat(settings["filterableAttributes"] as List<*>)
            .`as`("filterable attributes with the declarations")
            .contains("attributes.portal.platforms", "attributes.portal.versions")
        assertThat(settings["sortableAttributes"] as List<*>)
            .`as`("sortable attributes with the declarations")
            .contains("attributes.portal.versions")
            .doesNotContain("attributes.portal.platforms")
    }

    @Test
    fun `should send one settings update when a declaration is new`() {
        val fixture = fixture {
            supported(MeilisearchIndexSettings.desired)
            on("PATCH", "/indexes/$index/settings", status = 202, body = """{"taskUid":7}""")
            onTask(7, "succeeded")
        }

        fixture.backend.open()

        val patches = fixture.server.calls.filter { it.method == "PATCH" }
        assertThat(patches).`as`("settings updates after a new declaration").hasSize(1)
        assertThat(patches.single().body.orEmpty()).`as`("body of the settings update").contains("attributes.portal.platforms")
    }

    @Test
    fun `should send no settings update when the settings already hold the declarations`() {
        val fixture = fixture { supported(MeilisearchIndexSettings.desiredFor(declared)) }

        fixture.backend.open()

        assertThat(fixture.server.calls.filter { it.method == "PATCH" })
            .`as`("settings updates when the settings already hold the declarations")
            .isEmpty()
    }

    @Test
    fun `should answer an attribute filter with the unavailable refusal while the settings change is pending`() {
        val fixture = fixture(waitStep = Duration.ofSeconds(25)) {
            supported(MeilisearchIndexSettings.desired)
            on("PATCH", "/indexes/$index/settings", status = 202, body = """{"taskUid":9}""")
            onTask(9, "processing")
        }
        fixture.backend.open()

        assertThatThrownBy {
            fixture.backend.search(ArtifactSearchQuery(attributeFilters = listOf(AttributeFilter("portal.platforms", listOf("Paper")))), access)
        }.isInstanceOf(SearchUnavailableException::class.java)
    }

    @Test
    fun `should filter a group window on one clause per key with the values of that key`() {
        val fixture = fixture { onSearch { """{"hits":[],"totalHits":0}""" } }

        fixture.backend.search(
            ArtifactSearchQuery(
                attributeFilters = listOf(
                    AttributeFilter("portal.platforms", listOf("Paper", "Folia")),
                    AttributeFilter("portal.versions", listOf("1.21")),
                    AttributeFilter("portal.platforms", listOf("paper")),
                ),
            ),
            access,
        )

        val filter = fixture.server.searchBodies().first().path("filter").asText()
        assertThat(filter)
            .`as`("filter of the group window")
            .contains("""attributes.portal.platforms IN ["Paper", "Folia"]""")
            .contains("""attributes.portal.versions IN ["1.21"]""")
    }

    @Test
    fun `should count a facet over the filters of the other keys and leave its own key out`() {
        val fixture = fixture {
            onSearch { body ->
                if (body.path("facets").toString().contains("attributes.portal.platforms")) {
                    """{"hits":[],"facetDistribution":{"attributes.portal.platforms":{"Folia":1,"Velocity":1,"Paper":2}}}"""
                } else {
                    """{"hits":[],"facetDistribution":{}}"""
                }
            }
        }

        val facets = fixture.backend.facets(
            ArtifactSearchQuery(
                attributeFilters = listOf(AttributeFilter("portal.platforms", listOf("Paper")), AttributeFilter("portal.versions", listOf("1.21"))),
                attributeFacets = setOf("portal.platforms"),
            ),
            access,
        )

        assertThat(facets.attributes?.get("portal.platforms"))
            .`as`("platform buckets while Paper is selected")
            .containsExactly(FacetBucket("Paper", 2), FacetBucket("Folia", 1), FacetBucket("Velocity", 1))
        val request = fixture.server.searchBodies().single { it.path("facets").toString().contains("attributes.portal.platforms") }
        assertThat(request.path("filter").asText())
            .`as`("filter of the platform facet")
            .contains("attributes.portal.versions")
            .doesNotContain("attributes.portal.platforms")
    }

    @Test
    fun `should leave the attribute facets out unless they are asked for`() {
        val fixture = fixture { onSearch { """{"hits":[],"facetDistribution":{}}""" } }

        val facets = fixture.backend.facets(ArtifactSearchQuery(), access)

        assertThat(facets.attributes).`as`("attribute facets of a query without attributeFacets").isNull()
    }

    @Test
    fun `should sort groups by the smallest value ascending with the groups without a value last`() {
        val fixture = sortFixture()

        val page = fixture.backend.search(ArtifactSearchQuery(attributeSort = AttributeSort("portal.versions", descending = false)), access)

        assertThat(page.items.map { it.artifactId })
            .`as`("artifacts by game version ascending")
            .containsExactly("alpha", "beta", "gamma", "delta")
    }

    @Test
    fun `should sort groups by the largest value descending with the groups without a value last`() {
        val fixture = sortFixture()

        val page = fixture.backend.search(ArtifactSearchQuery(attributeSort = AttributeSort("portal.versions", descending = true)), access)

        assertThat(page.items.map { it.artifactId })
            .`as`("artifacts by game version descending")
            .containsExactly("alpha", "gamma", "beta", "delta")
    }

    /** Four groups: alpha holds 1.21 and 1.19, beta 1.20, gamma 1.21 and delta nothing. */
    private fun sortFixture(): Fixture = fixture {
        onSearch { body ->
            when {
                body.has("distinct") -> """{"totalHits":4,"hits":[${listOf("alpha", "beta", "gamma", "delta").joinToString(",") { hit(it) }}]}"""
                body.has("attributesToRetrieve") -> """{"hits":[
                    ${file("alpha", """{"portal.versions":["1.21"]}""")},
                    ${file("alpha", """{"portal.versions":["1.19"]}""")},
                    ${file("beta", """{"portal.versions":["1.20"]}""")},
                    ${file("gamma", """{"portal.versions":["1.21"]}""")},
                    ${file("delta", "null")}]}"""
                else -> """{"hits":[],"facetDistribution":{"version":{"1.0":1}}}"""
            }
        }
    }

    private fun hit(artifact: String): String =
        """{"gav":"$artifact","group_id":"com.example","artifact_id":"$artifact","version":"1.0","group_id_lc":"com.example","artifact_id_lc":"$artifact","version_lc":"1.0"}"""

    private fun file(artifact: String, attributes: String): String =
        """{"gav":"$artifact","repository":"releases","origin":"HOSTED","last_modified":1000,"size_bytes":10,"attributes":$attributes}"""

}
