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
import net.onelitefeather.ingot.search.ArtifactFilePage
import net.onelitefeather.ingot.search.ArtifactFilesQuery
import net.onelitefeather.ingot.search.ArtifactSearchEngine
import net.onelitefeather.ingot.search.ArtifactSearchPage
import net.onelitefeather.ingot.search.ArtifactSearchQuery
import net.onelitefeather.ingot.search.AttributeFilter
import net.onelitefeather.ingot.search.IndexState
import net.onelitefeather.ingot.search.IndexStatus
import net.onelitefeather.ingot.search.InvalidSearchQueryException
import net.onelitefeather.ingot.search.SearchFacets
import net.onelitefeather.ingot.search.access.SearchAccess
import net.onelitefeather.ingot.search.enrichment.AttributeDeclaration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * The core refuses an attribute query before the backend sees it (search attribute facets, D6): a missing capability is a 400,
 * never an empty result, and a query without attribute parameters reaches the backend unchanged.
 */
internal class AttributeCheckedSearchEngineTest {

    private val catalogue = mapOf("demo.loader" to AttributeDeclaration("demo.loader", filterable = true, facetable = true, sortable = true))

    private val filtered = ArtifactSearchQuery(attributeFilters = listOf(AttributeFilter("demo.loader", listOf("fabric"))))

    @Test
    fun `a filter on a backend without the capability is refused before the backend is called`() {
        val backend = RecordingEngine()
        val checked = AttributeCheckedSearchEngine(backend, { catalogue }, { setOf(SearchCapability.ATTRIBUTES) })

        assertThatThrownBy { checked.search(filtered, SearchAccess.NONE) }
            .`as`("search with an attribute filter on a backend without ATTRIBUTE_FILTERS")
            .isInstanceOf(InvalidSearchQueryException::class.java)
            .extracting { (it as InvalidSearchQueryException).problemType }
            .isEqualTo(UNSUPPORTED_SEARCH_CAPABILITY_PROBLEM_TYPE)
        assertThatThrownBy { checked.facets(filtered, SearchAccess.NONE) }
            .`as`("facets with an attribute filter on a backend without ATTRIBUTE_FILTERS")
            .isInstanceOf(InvalidSearchQueryException::class.java)
        assertThat(backend.calls).`as`("calls that reached the backend").isEmpty()
    }

    @Test
    fun `a query without attribute parameters reaches the backend unchanged`() {
        val backend = RecordingEngine()
        val checked = AttributeCheckedSearchEngine(backend, { emptyMap() }, { emptySet() })
        val query = ArtifactSearchQuery(q = "app")

        checked.search(query, SearchAccess.NONE)
        checked.facets(query, SearchAccess.NONE)

        assertThat(backend.calls).`as`("calls that reached the backend").containsExactly("search" to query, "facets" to query)
    }

    @Test
    fun `an allowed attribute query reaches the backend unchanged`() {
        val backend = RecordingEngine()
        val checked = AttributeCheckedSearchEngine(backend, { catalogue }, { setOf(SearchCapability.ATTRIBUTE_FILTERS) })

        checked.search(filtered, SearchAccess.NONE)

        assertThat(backend.calls).`as`("calls that reached the backend").containsExactly("search" to filtered)
    }

    /** Records every read; answers empty pages. */
    private class RecordingEngine : ArtifactSearchEngine {

        val calls = mutableListOf<Pair<String, ArtifactSearchQuery>>()

        private val status = IndexStatus(IndexState.READY, 0, null, 0, 0)

        override val id: String = "recording"

        override fun search(query: ArtifactSearchQuery, access: SearchAccess): ArtifactSearchPage {
            calls += "search" to query
            return ArtifactSearchPage(emptyList(), query.page, query.size, 0, false, status)
        }

        override fun facets(query: ArtifactSearchQuery, access: SearchAccess): SearchFacets {
            calls += "facets" to query
            return SearchFacets(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        }

        override fun files(query: ArtifactFilesQuery, access: SearchAccess): ArtifactFilePage =
            ArtifactFilePage(emptyList(), query.page, query.size, 0, status)

        override fun status(): IndexStatus = status

    }

}
