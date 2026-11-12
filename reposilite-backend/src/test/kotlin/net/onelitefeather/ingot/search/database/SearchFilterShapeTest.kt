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

package net.onelitefeather.ingot.search.database

import net.onelitefeather.ingot.search.ArtifactSearchQuery
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Which queries get a word match (search decision, per whitespace token). A token with a `.` or a `:` gets none, so a query
 * made only of such tokens keeps main's SQL. Pure: the tables are only described, not opened.
 */
internal class SearchFilterShapeTest {

    private fun hasWordMatch(q: String?): Boolean =
        SearchFilters(ArtifactSearchQuery(q = q), SearchArtifactTable(null), SearchTokenTable(null)).usesTokenTable

    @Test
    fun `a dotted query gets no word match`() {
        assertThat(hasWordMatch("org.spring")).`as`("word match of a dotted query").isFalse()
    }

    @Test
    fun `a colon query gets no word match`() {
        assertThat(hasWordMatch("org.apache:commons")).`as`("word match of a colon query").isFalse()
    }

    @Test
    fun `a plain word gets a word match`() {
        assertThat(hasWordMatch("core")).`as`("word match of a plain word").isTrue()
    }

    @Test
    fun `a plain token next to a dotted token gets a word match, the dotted token none`() {
        assertThat(hasWordMatch("org.spring core")).`as`("word match of a mixed query").isTrue()
    }

    @Test
    fun `a blank query gets no word match`() {
        assertThat(hasWordMatch("  ")).`as`("word match of a blank query").isFalse()
    }

    @Test
    fun `a missing query gets no word match`() {
        assertThat(hasWordMatch(null)).`as`("word match of a missing query").isFalse()
    }
}
