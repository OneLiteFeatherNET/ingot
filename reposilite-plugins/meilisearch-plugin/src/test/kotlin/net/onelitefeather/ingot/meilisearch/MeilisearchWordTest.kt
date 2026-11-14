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
    fun `should give each word of a query its own condition, the last word by prefix`() {
        // when / then: both words are kept, lower-cased, and each must match
        assertThat(MeilisearchFilters.wordConditions("Spri  Core"))
            .`as`("conditions of the words of the query")
            .containsExactly(
                "(word_heads = \"spri\" OR tokens = \"spri\")",
                "(word_heads = \"core\" OR word_prefixes = \"core\")",
            )
    }

    @Test
    fun `should match a word before the last one by a whole token only when it is no main prefix`() {
        // when / then: a word before the last one has no prefix branch over the tokens, as the database rule has none
        assertThat(MeilisearchFilters.wordConditions("springf core").first())
            .`as`("condition of the word springf, which is no main prefix and before the last word")
            .doesNotContain("word_prefixes")
    }

    @Test
    fun `should match the parts of a word with a separator one by one, the last part by prefix`() {
        // when / then: the groupId or artifactId prefix matches the whole word, a token branch matches the parts
        assertThat(MeilisearchFilters.wordConditions("spring-c"))
            .`as`("condition of a last word of two parts")
            .containsExactly("(word_heads = \"spring-c\" OR (tokens = \"spring\" AND word_prefixes = \"c\"))")
    }

    @Test
    fun `should give a word with more parts than the database allows no token branch`() {
        // when / then: the database drops the token branch above eight parts, and so does the condition
        assertThat(MeilisearchFilters.wordConditions("a-b-c-d-e-f-g-h-i"))
            .`as`("condition of a word of nine parts")
            .containsExactly("word_heads = \"a-b-c-d-e-f-g-h-i\"")
    }

    @Test
    fun `should give a dotted word the main condition only, and a plain word in the same query its word condition`() {
        // when / then: a dotted word has no token branch, as on the database, and the plain word keeps its own
        assertThat(MeilisearchFilters.wordConditions("org.spring core"))
            .`as`("conditions of a dotted word followed by a plain word")
            .containsExactly("word_heads = \"org.spring\"", "(word_heads = \"core\" OR word_prefixes = \"core\")")
    }

    @Test
    fun `should give a dotted word the main condition only when it is the whole query`() {
        // when / then: the groupId, artifactId or whole version prefix, and no token of the coordinates
        assertThat(MeilisearchFilters.wordConditions("spring.core"))
            .`as`("conditions of a dotted word")
            .containsExactly("word_heads = \"spring.core\"")
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

    @Test
    fun `should index the prefixes of the groupId and the artifactId, and the whole version, as word heads`() {
        // when: the document of the row is built
        val heads = MeilisearchDocument.of(springCore()).path("word_heads").map { it.asText() }

        // then: the main rule matches a prefix of the groupId or the artifactId, and the version as a whole
        assertThat(heads).`as`("word heads of the document").contains("org", "org.spring", "spring-c", "spring-core", "6.1.0")
        assertThat(heads).`as`("word heads of the document: no token that starts neither the groupId nor the artifactId").doesNotContain("springf")
    }

}
