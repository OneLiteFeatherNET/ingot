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

package net.onelitefeather.ingot.meilisearch

import net.onelitefeather.ingot.search.ArtifactFileRecord
import net.onelitefeather.ingot.search.IndexRow
import net.onelitefeather.ingot.search.Provenance
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Word matching of the query (contract: each word of a query is a prefix of some token of the GAV, and every word must match).
 * Meilisearch prefix-matches only the last word, so the document carries every prefix of its tokens and each word becomes a filter.
 */
internal class MeilisearchWordTest {

    private fun springCore(): IndexRow = IndexRow(
        file = ArtifactFileRecord(
            repository = "releases",
            path = "org/springframework/spring-core/6.1.0/spring-core-6.1.0.jar",
            groupId = "org.springframework",
            artifactId = "spring-core",
            version = "6.1.0",
            classifier = null,
            extension = "jar",
            snapshot = false,
        ),
        provenance = Provenance.HOSTED,
        sizeBytes = 1,
        lastModified = 1,
        sha1 = null,
        sha256 = null,
        indexedAt = 1,
    )

    @Test
    fun `should give each word of a query its own prefix condition`() {
        // when / then: both words are kept, lower-cased, and each must match
        assertThat(MeilisearchFilters.wordConditions("Spri  Core"))
            .`as`("conditions of the words of the query")
            .containsExactly("word_prefixes = \"spri\"", "word_prefixes = \"core\"")
    }

    @Test
    fun `should give a dotted query no word conditions because it is a coordinate prefix`() {
        // when / then: the dotted query is matched by the groupId or the version prefix instead
        assertThat(MeilisearchFilters.wordConditions("spring.core"))
            .`as`("word conditions of a dotted query")
            .isEmpty()
    }

    @Test
    fun `should index every prefix of every token of the document`() {
        // when: the document of the row is built
        val document = MeilisearchDocument.of(springCore())
        val prefixes = document.path("word_prefixes").map { it.asText() }

        // then: a prefix of a token is in it, and a word that is no prefix of any token is not
        assertThat(prefixes).`as`("prefixes of the tokens").contains("spri", "spring", "core")
        assertThat(prefixes).`as`("prefixes of the tokens").doesNotContain("frame")
    }

}
