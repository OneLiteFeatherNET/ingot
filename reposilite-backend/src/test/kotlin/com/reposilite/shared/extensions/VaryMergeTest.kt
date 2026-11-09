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

package com.reposilite.shared.extensions

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class VaryMergeTest {

    @Test
    fun `should vary on accept when the response has no vary value yet`() {
        // when: an error answer is written and no Vary header was set before
        val vary = mergeVary(emptyList(), listOf("Accept"))

        // then: the answer depends on the Accept request header
        assertThat(vary).describedAs("Vary without an earlier value").isEqualTo("Accept")
    }

    @Test
    fun `should keep an existing vary value and append accept`() {
        // when: the response already varies on Accept-Encoding
        val vary = mergeVary(listOf("Accept-Encoding"), listOf("Accept"))

        // then: the earlier value survives and Accept follows it
        assertThat(vary).describedAs("merged Vary").isEqualTo("Accept-Encoding, Accept")
    }

    @Test
    fun `should not repeat accept when the earlier value lists it in any letter case`() {
        // when: the earlier value already names Accept in lower case
        val vary = mergeVary(listOf("accept"), listOf("Accept"))

        // then: the earlier spelling is kept and nothing is added
        assertThat(vary).describedAs("merged Vary").isEqualTo("accept")
    }

    @Test
    fun `should keep a wildcard vary value unchanged`() {
        // when: the earlier value is the wildcard, which already covers every request header
        val vary = mergeVary(listOf("*"), listOf("Accept"))

        // then: the wildcard is not diluted into a list
        assertThat(vary).describedAs("merged Vary").isEqualTo("*")
    }

}
