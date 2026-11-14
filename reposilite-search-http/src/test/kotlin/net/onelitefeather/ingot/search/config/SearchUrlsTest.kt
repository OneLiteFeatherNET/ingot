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

package net.onelitefeather.ingot.search.config

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/** The single server URL that a product with one server, such as Meilisearch, reads. */
internal class SearchUrlsTest {

    private fun variables(vararg entries: Pair<String, String>) = SearchVariables("MEILISEARCH", mapOf(*entries))

    @Test
    fun `should read an https url from the ingot variable`() {
        val uri = variables("INGOT_MEILISEARCH_URL" to "https://search.example.org:7700")
            .singleUrl(allowHttp = false, productName = "Meilisearch")

        assertThat(uri.toString()).`as`("url").isEqualTo("https://search.example.org:7700")
    }

    @Test
    fun `should fall back to the legacy variable`() {
        val uri = variables("REPOSILITE_MEILISEARCH_URL" to "https://legacy.example.org")
            .singleUrl(allowHttp = false, productName = "Meilisearch")

        assertThat(uri.host).`as`("host").isEqualTo("legacy.example.org")
    }

    @Test
    fun `should refuse a missing url and name the variable`() {
        assertThatThrownBy { variables().singleUrl(allowHttp = false, productName = "Meilisearch") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_MEILISEARCH_URL")
    }

    @Test
    fun `should refuse a plain http url unless it is allowed`() {
        val variables = variables("INGOT_MEILISEARCH_URL" to "http://search.example.org")

        assertThatThrownBy { variables.singleUrl(allowHttp = false, productName = "Meilisearch") }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThat(variables.singleUrl(allowHttp = true, productName = "Meilisearch").scheme).`as`("scheme").isEqualTo("http")
    }

    @Test
    fun `should refuse user information without echoing it or naming another product`() {
        val variables = variables("INGOT_MEILISEARCH_URL" to "https://admin:hunter2@search.example.org")

        assertThatThrownBy { variables.singleUrl(allowHttp = false, productName = "Meilisearch") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_MEILISEARCH_URL")
            .hasMessageNotContaining("hunter2")
            .hasMessageNotContaining("PASSWORD")
    }
}
