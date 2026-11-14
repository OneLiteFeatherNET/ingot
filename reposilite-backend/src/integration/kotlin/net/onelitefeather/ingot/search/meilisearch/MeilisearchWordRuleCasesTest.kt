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

package net.onelitefeather.ingot.search.meilisearch

import net.onelitefeather.ingot.search.ArtifactPathParser
import net.onelitefeather.ingot.search.ArtifactPathResult
import net.onelitefeather.ingot.search.ArtifactSearchQuery
import net.onelitefeather.ingot.search.IndexRow
import net.onelitefeather.ingot.search.Provenance
import net.onelitefeather.ingot.search.access.PathPredicate
import net.onelitefeather.ingot.search.access.RepositoryAccess
import net.onelitefeather.ingot.search.access.SearchAccess
import net.onelitefeather.ingot.search.backend.ArtifactSearchBackend
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.backend.IndexAction
import net.onelitefeather.ingot.search.opensearch.newIndexName
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * The word rule of a query on the Meilisearch backend, pinned to the database rule (one rule for every backend). A word that is not
 * the last one matches a whole token of the coordinates, or the groupId, the artifactId or the version as the main rule does. The
 * last word also matches a prefix of a token. The shared contract is not changed for these cases: they live here because they name
 * the Meilisearch divergence that was found after the contract was written.
 */
internal class MeilisearchWordRuleCasesTest {

    @Test
    fun `a word before the last one that prefixes a token outside the groupId and the artifactId finds nothing`() {
        withIndexOf(SPRING_CORE) { backend ->
            assertThat(searchNamed(backend, "springf core"))
                .`as`("groups of q=springf core: springf is no prefix of the groupId or the artifactId and no whole token")
                .isEmpty()
        }
    }

    @Test
    fun `a word before the last one that prefixes the artifactId still finds the group`() {
        withIndexOf(SPRING_CORE) { backend ->
            assertThat(searchNamed(backend, "spri core"))
                .`as`("groups of q=spri core: spri prefixes the artifactId")
                .containsExactly("org.springframework")
        }
    }

    @Test
    fun `a word before the last one that is a whole token still finds the group`() {
        withIndexOf(SPRING_CORE) { backend ->
            assertThat(searchNamed(backend, "spring cor"))
                .`as`("groups of q=spring cor: spring is a whole token")
                .containsExactly("org.springframework")
        }
    }

    @Test
    fun `a word before the last one that prefixes the groupId still finds the group`() {
        withIndexOf(SPRING_CORE) { backend ->
            assertThat(searchNamed(backend, "org core"))
                .`as`("groups of q=org core: org prefixes the groupId")
                .containsExactly("org.springframework")
        }
    }

    @Test
    fun `the last word that prefixes a token outside the groupId and the artifactId still finds the group`() {
        withIndexOf(SPRING_CORE) { backend ->
            assertThat(searchNamed(backend, "core springf"))
                .`as`("groups of q=core springf: the last word is a prefix of the token springframework")
                .containsExactly("org.springframework")
        }
    }

    private fun withIndexOf(path: String, block: (ArtifactSearchBackend) -> Unit) {
        val index = newIndexName()
        val backend = createMeilisearchBackend(meilisearchTestEnvironment(shared.url(), index))
        backend.open()
        try {
            backend.apply(listOf(upsertOf(path)))
            block(backend)
        } finally {
            backend.close()
            MeilisearchAdminClient(shared).deleteIndex(index)
        }
    }

    private fun searchNamed(backend: ArtifactSearchBackend, q: String): List<String> =
        backend.search(ArtifactSearchQuery(q = q), ALL_REPOSITORIES).items.map { it.groupId }

    private fun upsertOf(path: String): IndexAction.Upsert {
        val parsed = ArtifactPathParser.parse(REPOSITORY, path) as ArtifactPathResult.Row
        return IndexAction.Upsert(
            IndexRow(
                file = parsed.file,
                provenance = Provenance.HOSTED,
                sizeBytes = 10L,
                lastModified = Instant.parse(NOW).toEpochMilli(),
                sha1 = null,
                sha256 = null,
                indexedAt = Instant.parse(NOW).toEpochMilli(),
            ),
        )
    }

    private companion object {
        const val NOW = "2026-10-17T10:00:00Z"
        const val REPOSITORY = "releases"
        const val SPRING_CORE = "org/springframework/spring-core/6.1.0/spring-core-6.1.0.jar"

        /** One server for the class: every case writes to an index of its own and deletes it. */
        val shared: StartedMeilisearch by lazy { startMeilisearch() }

        val ALL_REPOSITORIES = SearchAccess(listOf(RepositoryAccess(REPOSITORY, PathPredicate.All)))
    }

}
