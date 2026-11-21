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
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
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

    @Test
    fun `an enricher without declarations adds nothing to the catalogue`() {
        val registry = registry()

        registry.register(FakeEnricher(id = "jar-manifest"))

        assertThat(registry.catalogue()).`as`("catalogue of an enricher that declares nothing").isEmpty()
        assertThat(warnings).`as`("warnings of an enricher that declares nothing").isEmpty()
    }

    @Test
    fun `a declared key is in the catalogue with its three flags`() {
        val registry = registry()
        val loader = AttributeDeclaration(key = "minecraft-portal.loader", filterable = true, facetable = true, sortable = false)
        val name = AttributeDeclaration(key = "minecraft-portal.name", filterable = false, facetable = false, sortable = true)

        registry.register(FakeEnricher(id = "minecraft-portal", declaredAttributes = listOf(loader, name)))

        assertThat(registry.catalogue()).`as`("catalogue of the declared keys")
            .containsExactly(entry("minecraft-portal.loader", loader), entry("minecraft-portal.name", name))
        assertThat(warnings).`as`("warnings of valid declarations").isEmpty()
    }

    @Test
    fun `a declaration outside the enricher namespace is refused and the warning names the key and the class`() {
        val registry = registry()
        val own = AttributeDeclaration(key = "minecraft-portal.loader", filterable = true, facetable = true, sortable = false)
        val foreign = AttributeDeclaration(key = "gradle-portal.plugin_id", filterable = true, facetable = false, sortable = false)

        val accepted = registry.register(FakeEnricher(id = "minecraft-portal", declaredAttributes = listOf(own, foreign)))

        assertThat(accepted).`as`("registration of an enricher with one foreign declaration").isTrue()
        assertThat(registry.catalogue()).`as`("catalogue without the foreign declaration")
            .containsExactly(entry("minecraft-portal.loader", own))
        assertThat(warnings).`as`("warnings of the foreign declaration").hasSize(1)
        assertThat(warnings.single()).`as`("warning text")
            .contains("gradle-portal.plugin_id")
            .contains(FakeEnricher::class.java.name)
    }

    @Test
    fun `a declaration whose name breaks the attribute key rules is refused`() {
        val invalidKeys = listOf("minecraft-portal", "minecraft-portal.", "minecraft-portal.Loader", "minecraft-portal.game-version")

        invalidKeys.forEach { key ->
            val registry = registry()
            val declaration = AttributeDeclaration(key = key, filterable = true, facetable = true, sortable = true)

            registry.register(FakeEnricher(id = "minecraft-portal", declaredAttributes = listOf(declaration)))

            assertThat(registry.catalogue()).`as`("catalogue for the invalid key '$key'").isEmpty()
        }
        assertThat(warnings).`as`("one warning per refused key").hasSize(invalidKeys.size)
    }

    @Test
    fun `a key declared twice is refused naming both classes and the first declaration is kept`() {
        val registry = registry()
        val first = AttributeDeclaration(key = "minecraft-portal.loader", filterable = true, facetable = true, sortable = false)
        val second = AttributeDeclaration(key = "minecraft-portal.loader", filterable = false, facetable = false, sortable = true)

        registry.register(FakeEnricher(id = "minecraft-portal", declaredAttributes = listOf(first, second)))

        assertThat(registry.catalogue()).`as`("catalogue after a duplicate declaration")
            .containsExactly(entry("minecraft-portal.loader", first))
        assertThat(warnings).`as`("warnings of the duplicate declaration").hasSize(1)
        assertThat(warnings.single()).`as`("warning text")
            .contains("minecraft-portal.loader")
            .contains(FakeEnricher::class.java.name)
    }

    @Test
    fun `a refused enricher adds nothing to the catalogue`() {
        val registry = registry()
        val declaration = AttributeDeclaration(key = "minecraft-portal.loader", filterable = true, facetable = true, sortable = false)
        registry.register(FakeEnricher(id = "minecraft-portal"))

        registry.register(AnotherFakeEnricher(id = "minecraft-portal", declaredAttributes = listOf(declaration)))

        assertThat(registry.catalogue()).`as`("catalogue after a refused enricher").isEmpty()
    }

    private open class FakeEnricher(
        override val id: String,
        override val apiVersion: Int = ARTIFACT_ENRICHER_API_VERSION,
        override val declaredAttributes: List<AttributeDeclaration> = emptyList(),
    ) : ArtifactEnricher {

        override val version: String = "1"

        override val extensions: Set<String> = setOf("jar")

        override fun enrich(artifact: ArtifactEnrichmentRequest): EnrichmentResult = EnrichmentCompleted

    }

    private class AnotherFakeEnricher(
        id: String,
        declaredAttributes: List<AttributeDeclaration> = emptyList(),
    ) : FakeEnricher(id = id, declaredAttributes = declaredAttributes)

}
