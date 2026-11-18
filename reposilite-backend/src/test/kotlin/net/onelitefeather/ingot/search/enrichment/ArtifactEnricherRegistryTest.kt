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

package net.onelitefeather.ingot.search.enrichment

import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class ArtifactEnricherRegistryTest {

    private val warnings = mutableListOf<String>()

    private fun registry() = ArtifactEnricherRegistry(warn = warnings::add)

    @Test
    fun `an enricher of the current api version registers`() {
        val registry = registry()
        val enricher = FakeEnricher(id = "jar-manifest")

        val accepted = registry.register(enricher)

        assertThat(accepted).`as`("registration of an enricher of the current api version").isTrue()
        assertThat(registry.accepted()).`as`("accepted enrichers after the registration").containsExactly(enricher)
        assertThat(warnings).`as`("warnings of an accepted enricher").isEmpty()
    }

    @Test
    fun `an enricher of another api version is refused and the warning names its class and both versions`() {
        val registry = registry()
        val otherVersion = ARTIFACT_ENRICHER_API_VERSION + 1

        val accepted = registry.register(FakeEnricher(id = "jar-manifest", apiVersion = otherVersion))

        assertThat(accepted).`as`("registration of an enricher of another api version").isFalse()
        assertThat(warnings).`as`("warnings of the refused enricher").hasSize(1)
        assertThat(warnings.single()).`as`("warning text")
            .contains(FakeEnricher::class.java.name)
            .contains("apiVersion=$otherVersion")
            .contains("expected $ARTIFACT_ENRICHER_API_VERSION")
    }

    @Test
    fun `a duplicate id is refused and the warning names both classes`() {
        val registry = registry()
        registry.register(FakeEnricher(id = "jar-manifest"))

        val accepted = registry.register(AnotherFakeEnricher(id = "jar-manifest"))

        assertThat(accepted).`as`("registration of a duplicate id").isFalse()
        assertThat(warnings).`as`("warnings of the duplicate").hasSize(1)
        assertThat(warnings.single()).`as`("warning text")
            .contains(FakeEnricher::class.java.name)
            .contains(AnotherFakeEnricher::class.java.name)
    }

    @Test
    fun `the first enricher of an id is kept after a refused duplicate`() {
        val registry = registry()
        val first = FakeEnricher(id = "jar-manifest")
        registry.register(first)

        registry.register(AnotherFakeEnricher(id = "jar-manifest"))

        assertThat(registry.accepted()).`as`("accepted enrichers after a refused duplicate").containsExactly(first)
    }

    @Test
    fun `an id outside the pattern is refused and the warning names its class`() {
        val invalidIds = listOf("Jar-Manifest", "a", "-jar", "jar_manifest", "x".repeat(33))

        invalidIds.forEach { id ->
            val registry = registry()

            val accepted = registry.register(FakeEnricher(id = id))

            assertThat(accepted).`as`("registration of the id '$id' outside the pattern").isFalse()
            assertThat(registry.accepted()).`as`("accepted enrichers for the id '$id'").isEmpty()
        }
        assertThat(warnings).`as`("one warning per refused id").hasSize(invalidIds.size)
        assertThat(warnings).`as`("warnings name the class of the refused enricher")
            .allMatch { it.contains(FakeEnricher::class.java.name) }
    }

    @Test
    fun `a refused enricher is not among the accepted enrichers`() {
        val registry = registry()
        val refused = FakeEnricher(id = "jar-manifest", apiVersion = ARTIFACT_ENRICHER_API_VERSION + 1)

        registry.register(refused)

        assertThat(registry.accepted()).`as`("accepted enrichers after a refused registration").doesNotContain(refused)
    }

    @Test
    fun `the registration event registers the enricher in the registry`() {
        val registry = registry()
        val event = ArtifactEnricherRegistrationEvent(registry)
        val enricher = FakeEnricher(id = "jar-manifest")

        event.register(enricher)

        assertThat(registry.accepted()).`as`("accepted enrichers after registration through the event").containsExactly(enricher)
    }

    private open class FakeEnricher(
        override val id: String,
        override val apiVersion: Int = ARTIFACT_ENRICHER_API_VERSION,
    ) : ArtifactEnricher {

        override val version: String = "1"

        override val extensions: Set<String> = setOf("jar")

        override fun enrich(artifact: ArtifactEnrichmentRequest): EnrichmentResult = EnrichmentResult.Completed

    }

    private class AnotherFakeEnricher(id: String) : FakeEnricher(id = id)

}
