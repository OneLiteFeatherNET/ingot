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

package net.onelitefeather.ingot.search.database

import net.onelitefeather.ingot.search.ArtifactPathParser
import net.onelitefeather.ingot.search.ArtifactPathResult
import net.onelitefeather.ingot.search.ArtifactSearchQuery
import net.onelitefeather.ingot.search.IndexRow
import net.onelitefeather.ingot.search.InvalidSearchQueryException
import net.onelitefeather.ingot.search.Provenance
import net.onelitefeather.ingot.search.access.PathPredicate
import net.onelitefeather.ingot.search.access.RepositoryAccess
import net.onelitefeather.ingot.search.access.SearchAccess
import net.onelitefeather.ingot.search.backend.ArtifactSearchBackend
import net.onelitefeather.ingot.search.backend.IndexAction
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The word rule of the database backend, pinned by its results. The Meilisearch backend runs the same cases and must answer the same
 * way, so a switch of backend gives the same hits. Each case states what the database answers, not what the rule text says.
 */
internal class DatabaseWordRuleCasesTest {

    @TempDir
    lateinit var directory: Path

    @Test
    fun `a coordinate word with a groupId prefix and an artifactId prefix finds the group`() = withIndexOf(SPRING_CORE) { backend ->
        assertThat(searchNamed(backend, "org:spring-core"))
            .`as`("groups of q=org:spring-core: the groupId prefix and the artifactId prefix")
            .containsExactly("org.springframework")
    }

    @Test
    fun `a coordinate word whose artifactId is no prefix of the artifactId finds nothing`() = withIndexOf(SPRING_CORE) { backend ->
        assertThat(searchNamed(backend, "org:core"))
            .`as`("groups of q=org:core: core is no prefix of the artifactId spring-core")
            .isEmpty()
    }

    @Test
    fun `a coordinate word with an exact version finds the group, and a version prefix finds nothing`() = withIndexOf(SPRING_CORE) { backend ->
        assertThat(searchNamed(backend, "org:spring:6.1.0"))
            .`as`("groups of q=org:spring:6.1.0: the exact version")
            .containsExactly("org.springframework")
        assertThat(searchNamed(backend, "org:spring:6.1"))
            .`as`("groups of q=org:spring:6.1: the version must be exact")
            .isEmpty()
    }

    @Test
    fun `a coordinate word with a plain word after it finds the group when both match`() = withIndexOf(SPRING_CORE) { backend ->
        assertThat(searchNamed(backend, "org:spring core"))
            .`as`("groups of q=org:spring core: the coordinate matches, core is a token")
            .containsExactly("org.springframework")
    }

    @Test
    fun `more than five words is refused with the database message`() = withIndexOf(SPRING_CORE) { backend ->
        assertThatThrownBy { backend.search(ArtifactSearchQuery(q = "a b c d e f"), ALL_REPOSITORIES) }
            .`as`("search of six words")
            .isInstanceOf(InvalidSearchQueryException::class.java)
            .hasMessage("q is limited to 5 tokens")
    }

    @Test
    fun `more than two hundred characters is refused with the database message`() = withIndexOf(SPRING_CORE) { backend ->
        assertThatThrownBy { backend.search(ArtifactSearchQuery(q = "a".repeat(201)), ALL_REPOSITORIES) }
            .`as`("search of 201 characters")
            .isInstanceOf(InvalidSearchQueryException::class.java)
            .hasMessage("q is limited to 200 characters")
    }

    @Test
    fun `a coordinate word with four parts is refused with the database message`() = withIndexOf(SPRING_CORE) { backend ->
        assertThatThrownBy { backend.search(ArtifactSearchQuery(q = "a:b:c:d"), ALL_REPOSITORIES) }
            .`as`("search of four coordinate parts")
            .isInstanceOf(InvalidSearchQueryException::class.java)
            .hasMessage("a coordinates token has at most groupId:artifactId:version")
    }

    @Test
    fun `a coordinate word without any part is refused with the database message`() = withIndexOf(SPRING_CORE) { backend ->
        assertThatThrownBy { backend.search(ArtifactSearchQuery(q = ":"), ALL_REPOSITORIES) }
            .`as`("search of a colon alone")
            .isInstanceOf(InvalidSearchQueryException::class.java)
            .hasMessage("a coordinates token needs a groupId, an artifactId or a version")
    }

    private fun withIndexOf(path: String, block: (ArtifactSearchBackend) -> Unit) {
        val sqlite = SqliteFileDatabase(directory.resolve("search.db"))
        try {
            val clock = Clock.fixed(Instant.parse(NOW), ZoneOffset.UTC)
            SearchSchema.ensure(sqlite.database, clock)
            val backend = DatabaseSearchBackend(sqlite.database, clock)
            backend.apply(listOf(upsertOf(path)))
            block(backend)
        } finally {
            sqlite.close()
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
        val ALL_REPOSITORIES = SearchAccess(listOf(RepositoryAccess(REPOSITORY, PathPredicate.All)))
    }

}
