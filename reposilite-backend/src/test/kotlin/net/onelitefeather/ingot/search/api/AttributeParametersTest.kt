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

import net.onelitefeather.ingot.search.AttributeFilter
import net.onelitefeather.ingot.search.AttributeSort
import net.onelitefeather.ingot.search.InvalidSearchQueryException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * The attribute parameters of the search route (search attribute facets, D2): `attribute=<key>:<value>`, repeatable, an OR within a
 * key; `attributeSort=<key>:asc|desc`; `attributeFacets=<key>,<key>`. A value that does not parse is a 400 naming the parameter.
 */
internal class AttributeParametersTest {

    @Test
    fun `no attribute parameters give no filters, no sort and no facets`() {
        val parsed = attributeParameters(emptyList(), emptyList(), emptyList())

        assertThat(parsed).`as`("parameters of a request without attributes").isEqualTo(AttributeParameters(emptyList(), null, emptySet()))
    }

    @Test
    fun `repeated values of one key become one filter in the order given`() {
        val parsed = attributeParameters(listOf("demo.loader:fabric", "demo.version:1.21", "demo.loader:quilt"), emptyList(), emptyList())

        assertThat(parsed.filters).`as`("filters").containsExactly(
            AttributeFilter("demo.loader", listOf("fabric", "quilt")),
            AttributeFilter("demo.version", listOf("1.21")),
        )
    }

    @Test
    fun `the value is everything after the first colon`() {
        val parsed = attributeParameters(listOf("demo.ref:a:b"), emptyList(), emptyList())

        assertThat(parsed.filters).`as`("filters").containsExactly(AttributeFilter("demo.ref", listOf("a:b")))
    }

    @Test
    fun `a filter without a colon or with an empty value is refused naming attribute`() {
        listOf("demo.loader", "demo.loader:", ":fabric").forEach { raw ->
            assertThatThrownBy { attributeParameters(listOf(raw), emptyList(), emptyList()) }
                .`as`("attribute=%s", raw)
                .isInstanceOf(InvalidSearchQueryException::class.java)
                .extracting { (it as InvalidSearchQueryException).parameter }
                .isEqualTo("attribute")
        }
    }

    @Test
    fun `a sort names a key and a direction`() {
        assertThat(attributeParameters(emptyList(), listOf("demo.loader:desc"), emptyList()).sort)
            .`as`("descending sort").isEqualTo(AttributeSort("demo.loader", descending = true))
        assertThat(attributeParameters(emptyList(), listOf("demo.loader:ASC"), emptyList()).sort)
            .`as`("ascending sort").isEqualTo(AttributeSort("demo.loader", descending = false))
    }

    @Test
    fun `a sort without a known direction or given twice is refused naming attributeSort`() {
        listOf(listOf("demo.loader"), listOf("demo.loader:up"), listOf("demo.a:asc", "demo.b:asc")).forEach { raw ->
            assertThatThrownBy { attributeParameters(emptyList(), raw, emptyList()) }
                .`as`("attributeSort=%s", raw)
                .isInstanceOf(InvalidSearchQueryException::class.java)
                .extracting { (it as InvalidSearchQueryException).parameter }
                .isEqualTo("attributeSort")
        }
    }

    @Test
    fun `facet keys are split at commas, trimmed and repeatable`() {
        val parsed = attributeParameters(emptyList(), emptyList(), listOf("demo.loader, demo.version", "demo.loader,,"))

        assertThat(parsed.facets).`as`("facet keys").containsExactly("demo.loader", "demo.version")
    }

}
