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

package net.onelitefeather.ingot.search.api

import net.onelitefeather.ingot.search.enrichment.AttributeDeclaration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The facetable key list (search attribute facets, D9): built from the declarations only, so it reads no backend and no value.
 */
internal class FacetableAttributesTest {

    private fun declaration(key: String, facetable: Boolean, label: String = key) =
        AttributeDeclaration(key = key, filterable = true, facetable = facetable, sortable = false, label = label)

    @Test
    fun `only keys declared facetable are listed, each with its label`() {
        val catalogue = listOf(
            declaration("minecraft-portal.loader", facetable = true, label = "Loader"),
            declaration("minecraft-portal.name", facetable = false, label = "Name"),
        )

        assertThat(facetableAttributes(catalogue)).`as`("facetable keys")
            .containsExactly(FacetableAttribute("minecraft-portal.loader", "Loader"))
    }

    @Test
    fun `a declaration without a label shows its key as the label`() {
        val catalogue = listOf(AttributeDeclaration(key = "demo.loader", filterable = false, facetable = true, sortable = false))

        assertThat(facetableAttributes(catalogue)).`as`("label of a declaration without one")
            .containsExactly(FacetableAttribute("demo.loader", "demo.loader"))
    }

    @Test
    fun `an empty catalogue gives an empty list`() {
        assertThat(facetableAttributes(emptyList())).`as`("list of an empty catalogue").isEmpty()
    }

    @Test
    fun `the list is ordered by key whatever the order of registration`() {
        val catalogue = listOf(declaration("demo.version", true), declaration("alpha.loader", true), declaration("demo.loader", true))

        assertThat(facetableAttributes(catalogue).map { it.key }).`as`("keys in answer order")
            .containsExactly("alpha.loader", "demo.loader", "demo.version")
    }
}
