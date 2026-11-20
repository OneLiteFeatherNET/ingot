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

package net.onelitefeather.ingot.search.backend

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class SearchCapabilityTest {

    @Test
    fun `a backend that declares nothing has no capabilities`() {
        val backend: ArtifactSearchBackend = FakeArtifactSearchBackend()

        assertThat(backend.capabilities).`as`("capabilities of a backend that declares none").isEmpty()
    }

    @Test
    fun `the capabilities are attributes and the three attribute queries`() {
        assertThat(SearchCapability.entries)
            .`as`("declarable search capabilities")
            .containsExactly(
                SearchCapability.ATTRIBUTES,
                SearchCapability.ATTRIBUTE_FILTERS,
                SearchCapability.ATTRIBUTE_FACETS,
                SearchCapability.ATTRIBUTE_SORT,
            )
    }

}
