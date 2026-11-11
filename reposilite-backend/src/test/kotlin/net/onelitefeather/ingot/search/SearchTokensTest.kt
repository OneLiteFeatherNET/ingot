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

package net.onelitefeather.ingot.search

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The tokens of a GAV and the parts of a query word (full text change, D1 and D2). Pure: no database, no clock. */
internal class SearchTokensTest {

    @Test
    fun `splits a dotted groupId and a hyphenated artifactId into tokens`() {
        assertThat(SearchTokens.of("org.springframework", "spring-core", "6.1.0"))
            .`as`("tokens of the coordinates in the order groupId, artifactId, version")
            .containsExactly("org", "springframework", "spring", "core", "6", "1", "0")
    }

    @Test
    fun `keeps the qualifier of a snapshot version as a token`() {
        assertThat(SearchTokens.of("com.example", "lib", "1.0-SNAPSHOT"))
            .`as`("tokens of a snapshot version")
            .containsExactly("com", "example", "lib", "1", "0", "snapshot")
    }

    @Test
    fun `keeps letters outside ASCII as part of a token`() {
        assertThat(SearchTokens.of("de.grün", "café-ünit", "1"))
            .`as`("tokens with Unicode letters")
            .containsExactly("de", "grün", "café", "ünit", "1")
    }

    @Test
    fun `lower-cases the tokens`() {
        assertThat(SearchTokens.of("ORG.Example", "Lib", "1.0"))
            .`as`("lower-cased tokens")
            .containsExactly("org", "example", "lib", "1", "0")
    }

    @Test
    fun `keeps one token once when it occurs in several coordinates`() {
        assertThat(SearchTokens.of("org.spring", "spring-spring", "1.0.1"))
            .`as`("tokens without duplicates")
            .containsExactly("org", "spring", "1", "0")
    }

    @Test
    fun `drops a token of 65 characters and keeps one of 64`() {
        val longest = "a".repeat(64)
        val tooLong = "b".repeat(65)

        assertThat(SearchTokens.of("com.example", "$longest-$tooLong", "1"))
            .`as`("tokens with a 64 and a 65 character token")
            .containsExactly("com", "example", longest, "1")
    }

    @Test
    fun `keeps the first 32 tokens of a GAV with 33`() {
        val groupId = (1..33).joinToString(".") { "t$it" }

        val tokens = SearchTokens.of(groupId, "lib", "1")

        assertThat(tokens).`as`("tokens of a GAV with 33 distinct tokens").hasSize(32)
        assertThat(tokens.first()).`as`("first token kept").isEqualTo("t1")
        assertThat(tokens.last()).`as`("last token kept, the 33rd is dropped").isEqualTo("t32")
    }

    @Test
    fun `a GAV gives the same key whatever the case of its coordinates`() {
        assertThat(gavKeyOf("Org.Example", "Lib", "1.0"))
            .`as`("key of the upper-case coordinates")
            .isEqualTo(gavKeyOf("org.example", "lib", "1.0"))
    }

    @Test
    fun `the key of a GAV is 64 lower-case hexadecimal characters`() {
        assertThat(gavKeyOf("org.example", "lib", "1.0"))
            .`as`("key of a GAV")
            .matches("[0-9a-f]{64}")
    }

    @Test
    fun `a word splits into its letter and digit runs`() {
        assertThat(SearchTokens.partsOf("spring-core"))
            .`as`("parts of a hyphenated word")
            .containsExactly("spring", "core")
    }

    @Test
    fun `a word without separators is one part`() {
        assertThat(SearchTokens.partsOf("core"))
            .`as`("parts of a word without separators")
            .containsExactly("core")
    }

    @Test
    fun `quotes are not part of a word`() {
        assertThat(SearchTokens.partsOf("\"core\""))
            .`as`("parts of a quoted word")
            .containsExactly("core")
    }

    @Test
    fun `a word of punctuation alone has no parts`() {
        assertThat(SearchTokens.partsOf("*"))
            .`as`("parts of a wildcard character")
            .isEmpty()
    }

    @Test
    fun `parts are lower-cased`() {
        assertThat(SearchTokens.partsOf("Spring.Core"))
            .`as`("parts of a mixed-case word")
            .containsExactly("spring", "core")
    }
}
