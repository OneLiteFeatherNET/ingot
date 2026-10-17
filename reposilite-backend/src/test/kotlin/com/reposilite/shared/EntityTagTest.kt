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

package com.reposilite.shared

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class EntityTagTest {

    @Test
    fun `should allow the write when no if-match header is sent`() {
        assertThat(EntityTag.matches(ifMatch = null, current = "\"abc\"")).isTrue
        assertThat(EntityTag.matches(ifMatch = "  ", current = "\"abc\"")).isTrue
    }

    @Test
    fun `should allow the write when if-match equals the current entity tag`() {
        assertThat(EntityTag.matches(ifMatch = "\"abc\"", current = "\"abc\"")).isTrue
    }

    @Test
    fun `should reject the write when if-match differs from the current entity tag`() {
        assertThat(EntityTag.matches(ifMatch = "\"old\"", current = "\"new\"")).isFalse
    }

    @Test
    fun `should accept the write when one of the listed entity tags matches`() {
        assertThat(EntityTag.matches(ifMatch = "\"old\", \"abc\"", current = "\"abc\"")).isTrue
    }

    @Test
    fun `should accept a weak entity tag of the current state`() {
        // a compressing proxy turns the strong tag into a weak one, the opaque tag is still the same state
        assertThat(EntityTag.matches(ifMatch = "W/\"abc\"", current = "\"abc\"")).isTrue
    }

    @Test
    fun `should reject a weak entity tag of another state with a mismatch`() {
        assertThat(EntityTag.matches(ifMatch = "W/\"old\"", current = "\"abc\"")).isFalse
    }

    @Test
    fun `should accept a list that holds a weak entity tag of the current state`() {
        assertThat(EntityTag.matches(ifMatch = "\"old\", W/\"abc\"", current = "\"abc\"")).isTrue
    }

    @Test
    fun `should accept any existing resource for a wildcard if-match`() {
        assertThat(EntityTag.matches(ifMatch = "*", current = "\"abc\"")).isTrue
    }

    @Test
    fun `should reject a precondition on a resource that does not exist`() {
        assertThat(EntityTag.matches(ifMatch = "*", current = null)).isFalse
        assertThat(EntityTag.matches(ifMatch = "\"abc\"", current = null)).isFalse
    }

}
