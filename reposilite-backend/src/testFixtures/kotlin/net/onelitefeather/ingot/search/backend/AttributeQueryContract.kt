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

@file:OptIn(net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi::class)

package net.onelitefeather.ingot.search.backend

import com.reposilite.audit.MutableClock
import net.onelitefeather.ingot.search.ArtifactPathParser
import net.onelitefeather.ingot.search.ArtifactPathResult
import net.onelitefeather.ingot.search.ArtifactSearchQuery
import net.onelitefeather.ingot.search.AttributeFilter
import net.onelitefeather.ingot.search.AttributeSort
import net.onelitefeather.ingot.search.FacetBucket
import net.onelitefeather.ingot.search.IndexRow
import net.onelitefeather.ingot.search.Provenance
import net.onelitefeather.ingot.search.access.PathPredicate
import net.onelitefeather.ingot.search.access.RepositoryAccess
import net.onelitefeather.ingot.search.access.SearchAccess
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Clock
import java.time.Instant

/**
 * The attribute queries every backend that declares [SearchCapability.ATTRIBUTE_FILTERS], [SearchCapability.ATTRIBUTE_FACETS]
 * and [SearchCapability.ATTRIBUTE_SORT] shares (search attribute facets, D2 to D4 and D7). Each test opens a fresh, empty
 * backend through [open] and writes the same fixture: four public artifacts and one in a private repository whose attributes
 * match every filter, so a count or a result that leaks it shows.
 */
abstract class AttributeQueryContract {

    @TempDir
    lateinit var directory: Path

    /** A backend and what closes it after its test. */
    protected class Opened(val backend: ArtifactSearchBackend, val close: () -> Unit)

    /** Opens a fresh, empty backend that reads time from [clock]. */
    protected abstract fun open(directory: Path, clock: Clock): Opened

    private fun withFixture(block: (ArtifactSearchBackend) -> Unit) {
        val opened = open(directory, MutableClock(Instant.parse(NOW)))
        try {
            opened.backend.apply(FIXTURE.map { (repository, path, attributes) -> IndexAction.Upsert(row(repository, path), attributes) })
            block(opened.backend)
        } finally {
            opened.close()
        }
    }

    @Test
    fun `the backend declares the three attribute queries`() = withFixture { backend ->
        assertThat(backend.capabilities)
            .`as`("capabilities of a backend that runs the attribute query contract")
            .contains(SearchCapability.ATTRIBUTE_FILTERS, SearchCapability.ATTRIBUTE_FACETS, SearchCapability.ATTRIBUTE_SORT)
    }

    @Test
    fun `a filter matches any of the values of its key, ignoring case`() = withFixture { backend ->
        val page = backend.search(ArtifactSearchQuery(attributeFilters = listOf(AttributeFilter(PLATFORMS, listOf("PAPER", "velocity")))), PUBLIC)

        assertThat(page.items.map { it.artifactId })
            .`as`("artifacts with the platform Paper or Velocity")
            .containsExactly("alpha", "beta", "gamma")
    }

    @Test
    fun `filters on two keys both have to match`() = withFixture { backend ->
        val query = ArtifactSearchQuery(
            attributeFilters = listOf(AttributeFilter(PLATFORMS, listOf("Paper")), AttributeFilter(VERSIONS, listOf("1.21"))),
        )

        val page = backend.search(query, PUBLIC)

        assertThat(page.items.map { it.artifactId }).`as`("artifacts for Paper and 1.21").containsExactly("alpha")
    }

    @Test
    fun `a filter never returns a row the viewer cannot read`() = withFixture { backend ->
        val page = backend.search(ArtifactSearchQuery(attributeFilters = listOf(AttributeFilter(PLATFORMS, listOf("Paper")))), PUBLIC)

        assertThat(page.items.map { it.artifactId }).`as`("artifacts for Paper seen anonymously").doesNotContain("secret")
    }

    @Test
    fun `a selected value keeps its alternatives in its own facet`() = withFixture { backend ->
        val query = ArtifactSearchQuery(
            attributeFilters = listOf(AttributeFilter(PLATFORMS, listOf("Paper"))),
            attributeFacets = setOf(PLATFORMS),
        )

        val facets = backend.facets(query, PUBLIC)

        assertThat(facets.attributes?.get(PLATFORMS))
            .`as`("platform buckets while Paper is selected")
            .containsExactly(FacetBucket("Paper", 2), FacetBucket("Folia", 1), FacetBucket("Velocity", 1))
    }

    @Test
    fun `a facet counts the rows that match the filters on the other keys`() = withFixture { backend ->
        val query = ArtifactSearchQuery(
            attributeFilters = listOf(AttributeFilter(PLATFORMS, listOf("Paper"))),
            attributeFacets = setOf(VERSIONS),
        )

        val facets = backend.facets(query, PUBLIC)

        assertThat(facets.attributes?.get(VERSIONS))
            .`as`("version buckets of the Paper artifacts")
            .containsExactly(FacetBucket("1.20", 1), FacetBucket("1.21", 1))
    }

    @Test
    fun `a facet count leaves out the rows the viewer cannot read`() = withFixture { backend ->
        val query = ArtifactSearchQuery(attributeFacets = setOf(PLATFORMS))

        val anonymous = backend.facets(query, PUBLIC)
        val member = backend.facets(query, EVERY_REPOSITORY)

        assertThat(anonymous.attributes?.get(PLATFORMS))
            .`as`("platform buckets seen anonymously")
            .contains(FacetBucket("Paper", 2))
        assertThat(member.attributes?.get(PLATFORMS))
            .`as`("platform buckets seen with access to the private repository")
            .contains(FacetBucket("Paper", 3))
    }

    @Test
    fun `an attribute filter narrows the fixed facets`() = withFixture { backend ->
        val query = ArtifactSearchQuery(attributeFilters = listOf(AttributeFilter(PLATFORMS, listOf("Velocity"))))

        val facets = backend.facets(query, PUBLIC)

        assertThat(facets.extension).`as`("extension buckets of the Velocity artifacts").containsExactly(FacetBucket("jar", 1))
    }

    @Test
    fun `the attribute facets are absent unless they are asked for`() = withFixture { backend ->
        val facets = backend.facets(ArtifactSearchQuery(), PUBLIC)

        assertThat(facets.attributes).`as`("attribute facets of a query without attributeFacets").isNull()
    }

    @Test
    fun `a sort puts the rows without a value last and breaks ties by the existing order`() = withFixture { backend ->
        val page = backend.search(ArtifactSearchQuery(attributeSort = AttributeSort(VERSIONS, descending = false)), PUBLIC)

        assertThat(page.items.map { it.artifactId })
            .`as`("artifacts by game version ascending")
            .containsExactly("beta", "alpha", "gamma", "delta")
    }

    @Test
    fun `a descending sort still puts the rows without a value last`() = withFixture { backend ->
        val page = backend.search(ArtifactSearchQuery(attributeSort = AttributeSort(VERSIONS, descending = true)), PUBLIC)

        assertThat(page.items.map { it.artifactId })
            .`as`("artifacts by game version descending")
            .containsExactly("alpha", "gamma", "beta", "delta")
    }

    private fun row(repository: String, path: String): IndexRow {
        val parsed = ArtifactPathParser.parse(repository, path) as ArtifactPathResult.Row
        return IndexRow(
            file = parsed.file,
            provenance = Provenance.HOSTED,
            sizeBytes = 10L,
            lastModified = NOW_MILLIS,
            sha1 = null,
            sha256 = null,
            indexedAt = NOW_MILLIS,
        )
    }

    private companion object {
        const val NOW = "2026-11-21T10:00:00Z"
        val NOW_MILLIS: Long = Instant.parse(NOW).toEpochMilli()
        const val PUBLIC_REPOSITORY = "releases"
        const val PRIVATE_REPOSITORY = "private"
        const val PLATFORMS = "portal.platforms"
        const val VERSIONS = "portal.versions"

        /** A viewer without an account: the public repository only. */
        val PUBLIC = SearchAccess(listOf(RepositoryAccess(PUBLIC_REPOSITORY, PathPredicate.All)))

        /** A member who may read the private repository too. */
        val EVERY_REPOSITORY = SearchAccess(
            listOf(RepositoryAccess(PUBLIC_REPOSITORY, PathPredicate.All), RepositoryAccess(PRIVATE_REPOSITORY, PathPredicate.All)),
        )

        /** alpha repeats Paper, which still counts the file once. delta has no attributes. secret matches every filter. */
        val FIXTURE: List<Triple<String, String, Map<String, List<String>>>> = listOf(
            Triple(
                PUBLIC_REPOSITORY,
                "com/example/alpha/1.0/alpha-1.0.jar",
                mapOf(PLATFORMS to listOf("Paper", "Folia", "Paper"), VERSIONS to listOf("1.21")),
            ),
            Triple(PUBLIC_REPOSITORY, "com/example/beta/1.0/beta-1.0.jar", mapOf(PLATFORMS to listOf("Paper"), VERSIONS to listOf("1.20"))),
            Triple(PUBLIC_REPOSITORY, "com/example/gamma/1.0/gamma-1.0.jar", mapOf(PLATFORMS to listOf("Velocity"), VERSIONS to listOf("1.21"))),
            Triple(PUBLIC_REPOSITORY, "com/example/delta/1.0/delta-1.0.jar", emptyMap()),
            Triple(PRIVATE_REPOSITORY, "com/example/secret/1.0/secret-1.0.jar", mapOf(PLATFORMS to listOf("Paper"), VERSIONS to listOf("1.21"))),
        )
    }

}
