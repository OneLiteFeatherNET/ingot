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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The half-open upper bound of a prefix range (artifact search spec, 2.10). Pure: no database. */
internal class PrefixUpperBoundTest {

    @Test
    fun `increments the last character of a prefix`() {
        assertThat(prefixUpperBound("com.exa")).isEqualTo("com.exb")
    }

    @Test
    fun `increments one supplementary code point as a single character`() {
        assertThat(prefixUpperBound("a😀")).isEqualTo("a😁")
    }

    @Test
    fun `drops a trailing top code point and increments the one before it`() {
        assertThat(prefixUpperBound("a􏿿")).isEqualTo("b")
    }

    @Test
    fun `has no upper bound when every code point is the top one`() {
        assertThat(prefixUpperBound("􏿿")).isNull()
    }

    @Test
    fun `has no upper bound for the empty prefix`() {
        assertThat(prefixUpperBound("")).isNull()
    }

}
