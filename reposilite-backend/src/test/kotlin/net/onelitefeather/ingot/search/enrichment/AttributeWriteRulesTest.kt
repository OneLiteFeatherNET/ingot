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

@file:OptIn(ExperimentalIngotSearchApi::class, ExperimentalIngotPluginApi::class)

package net.onelitefeather.ingot.search.enrichment

import net.onelitefeather.ingot.plugin.ExperimentalIngotPluginApi
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.backend.SearchCapability
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class AttributeWriteRulesTest {

    private val attributes = EnrichmentAttributes(mapOf("portal.loader" to listOf("fabric")))

    @Test
    fun `a backend without attributes drops the set as skipped by an unsupported backend`() {
        val result = attributes.atBoundary(emptySet())

        assertThat(result).`as`("attribute set at a backend without ATTRIBUTES").isEqualTo(EnrichmentSkipped("unsupported-backend"))
    }

    @Test
    fun `a backend with attributes keeps the set`() {
        val result = attributes.atBoundary(setOf(SearchCapability.ATTRIBUTES))

        assertThat(result).`as`("attribute set at a backend with ATTRIBUTES").isEqualTo(attributes)
    }

    @Test
    fun `a result without attributes passes a backend without attributes unchanged`() {
        val results = listOf(EnrichmentCompleted, EnrichmentSkipped("not-a-mod"), EnrichmentFailed("broken jar"))

        assertThat(results.map { it.atBoundary(emptySet()) })
            .`as`("results without attributes at a backend without ATTRIBUTES")
            .isEqualTo(results)
    }

    @Test
    fun `a changed file holds the union of the results and the version of every enricher that completed`() {
        val written = attributesOfChangedFile(
            listOf(
                EnricherOutcome("portal", "2", attributes),
                EnricherOutcome("license", "1", EnrichmentAttributes(mapOf("license.spdx" to listOf("MIT", "Apache-2.0")))),
                EnricherOutcome("marker", "3", EnrichmentCompleted),
            ),
        )

        assertThat(written).`as`("attributes of a changed file whose enrichers all completed").isEqualTo(
            mapOf(
                "portal.loader" to listOf("fabric"),
                "core.enricher.portal" to listOf("2"),
                "license.spdx" to listOf("MIT", "Apache-2.0"),
                "core.enricher.license" to listOf("1"),
                "core.enricher.marker" to listOf("3"),
            ),
        )
    }

    @Test
    fun `a failed enricher leaves neither its keys nor its version while the others keep their results`() {
        val written = attributesOfChangedFile(
            listOf(
                EnricherOutcome("portal", "2", EnrichmentFailed("broken jar")),
                EnricherOutcome("license", "1", EnrichmentAttributes(mapOf("license.spdx" to listOf("MIT")))),
            ),
        )

        assertThat(written).`as`("attributes of a changed file with a failed enricher").isEqualTo(
            mapOf("license.spdx" to listOf("MIT"), "core.enricher.license" to listOf("1")),
        )
    }

    @Test
    fun `a skipped enricher leaves neither its keys nor its version`() {
        val written = attributesOfChangedFile(listOf(EnricherOutcome("portal", "2", EnrichmentSkipped("unsupported-backend"))))

        assertThat(written).`as`("attributes of a changed file whose only enricher skipped").isEmpty()
    }

    @Test
    fun `a changed file without matching enrichers holds no attributes`() {
        assertThat(attributesOfChangedFile(emptyList())).`as`("attributes of a changed file without enrichers").isEmpty()
    }

}
