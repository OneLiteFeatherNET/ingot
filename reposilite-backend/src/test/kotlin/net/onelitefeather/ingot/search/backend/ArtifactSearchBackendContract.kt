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

package net.onelitefeather.ingot.search.backend

import com.reposilite.audit.MutableClock
import net.onelitefeather.ingot.search.ArtifactFilesQuery
import net.onelitefeather.ingot.search.ArtifactPathParser
import net.onelitefeather.ingot.search.ArtifactPathResult
import net.onelitefeather.ingot.search.ArtifactSearchQuery
import net.onelitefeather.ingot.search.IndexRow
import net.onelitefeather.ingot.search.Origin
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
import kotlin.random.Random

/**
 * The behaviour every [ArtifactSearchBackend] shares (search backend seam, D10). Each test opens a fresh, empty backend
 * through [open], so no test sees another test's rows. The subclasses pick the implementation: the in-memory fake as the
 * reference, and the database backend on each vendor that runs in the unit suite.
 */
internal abstract class ArtifactSearchBackendContract {

    @TempDir
    lateinit var directory: Path

    /** A backend and what closes it after its test. */
    protected class Opened(val backend: ArtifactSearchBackend, val close: () -> Unit)

    /** Opens a fresh, empty backend that reads time from [clock]. */
    protected abstract fun open(directory: Path, clock: Clock): Opened

    private fun withBackend(block: (ArtifactSearchBackend) -> Unit) {
        val opened = open(directory, MutableClock(Instant.parse(NOW)))
        try {
            block(opened.backend)
        } finally {
            opened.close()
        }
    }

    @Test
    fun `an upsert replayed twice leaves one row`() = withBackend { backend ->
        val upsert = IndexAction.Upsert(row(REPOSITORY, JAR))

        backend.apply(listOf(upsert))
        backend.apply(listOf(upsert))

        assertThat(backend.count()).`as`("rows after a replayed upsert").isEqualTo(1L)
    }

    @Test
    fun `removing a path removes only that path`() = withBackend { backend ->
        backend.apply(listOf(IndexAction.Upsert(row(REPOSITORY, JAR)), IndexAction.Upsert(row(REPOSITORY, SIBLING))))

        backend.apply(listOf(IndexAction.RemovePath(REPOSITORY, JAR)))

        assertThat(backend.existing(REPOSITORY, listOf(JAR, SIBLING)).keys)
            .`as`("paths still stored")
            .containsExactly(SIBLING)
    }

    @Test
    fun `a prefix removal keeps the rows that were indexed after its cut-off`() = withBackend { backend ->
        backend.apply(
            listOf(
                IndexAction.Upsert(row(REPOSITORY, JAR, indexedAt = BEFORE_CUTOFF)),
                IndexAction.Upsert(row(REPOSITORY, SOURCES, indexedAt = AFTER_CUTOFF)),
            ),
        )

        backend.apply(listOf(IndexAction.RemovePrefix(REPOSITORY, DIRECTORY, notAfter = Instant.ofEpochMilli(CUTOFF))))

        assertThat(backend.existing(REPOSITORY, listOf(JAR, SOURCES)).keys)
            .`as`("paths left after the removal")
            .containsExactly(SOURCES)
    }

    @Test
    fun `a reorigin changes the origin of the rows below its directory and of no other`() = withBackend { backend ->
        backend.apply(
            listOf(
                IndexAction.Upsert(row(REPOSITORY, MIRRORED_JAR)),
                IndexAction.Upsert(row(REPOSITORY, HOSTED_JAR)),
            ),
        )

        backend.apply(listOf(IndexAction.Reorigin(REPOSITORY, MIRRORED_DIRECTORY, Provenance(Origin.MIRROR, UPSTREAM))))

        val mirrored = backend.files(ArtifactFilesQuery("com.example", "mirrored", "1.0"), ALL_REPOSITORIES).items.single()
        val hosted = backend.files(ArtifactFilesQuery("com.example", "hosted", "1.0"), ALL_REPOSITORIES).items.single()
        assertThat(mirrored.origin).`as`("origin of the reoriginated row").isEqualTo(Origin.MIRROR)
        assertThat(mirrored.sourceHost).`as`("upstream host of the reoriginated row").isEqualTo(UPSTREAM)
        assertThat(hosted.origin).`as`("origin of a row in another directory").isEqualTo(Origin.HOSTED)
    }

    @Test
    fun `the pages of pathsAt are stable and cover every path once`() = withBackend { backend ->
        val paths = (1..5).map { "com/example/lib/1.0.$it/lib-1.0.$it.jar" }
        backend.apply(paths.map { IndexAction.Upsert(row(REPOSITORY, it)) })

        val first = backend.pathsAt(REPOSITORY, "com/example/lib", after = null, limit = 2)
        val second = backend.pathsAt(REPOSITORY, "com/example/lib", after = first.last(), limit = 2)
        val third = backend.pathsAt(REPOSITORY, "com/example/lib", after = second.last(), limit = 2)

        assertThat(first + second + third)
            .`as`("paths of the three pages in path order")
            .containsExactlyElementsOf(paths.sorted())
    }

    @Test
    fun `search, facets and files never return a row outside the caller's access`() = withBackend { backend ->
        backend.apply(listOf(IndexAction.Upsert(row(REPOSITORY, JAR)), IndexAction.Upsert(row(HIDDEN_REPOSITORY, HIDDEN_JAR))))
        val visible = SearchAccess(listOf(RepositoryAccess(REPOSITORY, PathPredicate.All)))

        val page = backend.search(ArtifactSearchQuery(), visible)
        val facets = backend.facets(ArtifactSearchQuery(), visible)
        val hidden = backend.files(ArtifactFilesQuery("com.example", "hidden", "1.0"), visible)

        assertThat(page.items.flatMap { item -> item.repositories.map { it.name } })
            .`as`("repositories named by the search results")
            .containsOnly(REPOSITORY)
        assertThat(facets.repository.map { it.value })
            .`as`("repository facet buckets")
            .containsOnly(REPOSITORY)
        assertThat(hidden.items).`as`("files of a repository outside the access").isEmpty()
    }

    @Test
    fun `a sample returns rows that exist and hands back a cursor`() = withBackend { backend ->
        val paths = (1..3).map { "com/example/lib/1.0.$it/lib-1.0.$it.jar" }
        backend.apply(paths.map { IndexAction.Upsert(row(REPOSITORY, it)) })

        val start = backend.sampleStart(Random(SEED))
        val page = backend.sample(start, limit = 2)

        assertThat(start).`as`("start of a sample over a non-empty index").isNotNull()
        assertThat(page.rows).`as`("rows of the sample").hasSize(2)
        assertThat(backend.existing(REPOSITORY, page.rows.map { it.path }).keys)
            .`as`("sampled rows that are still stored")
            .containsExactlyInAnyOrderElementsOf(page.rows.map { it.path })
        assertThat(page.next).`as`("cursor after the sample").isNotNull()
        assertThat(backend.sample(page.next, limit = 1).rows)
            .`as`("rows read with the returned cursor")
            .hasSize(1)
    }

    private fun row(repository: String, path: String, indexedAt: Long = NOW_MILLIS): IndexRow {
        val parsed = ArtifactPathParser.parse(repository, path) as ArtifactPathResult.Row
        return IndexRow(
            file = parsed.file,
            provenance = Provenance.HOSTED,
            sizeBytes = 10L,
            lastModified = NOW_MILLIS,
            sha1 = null,
            sha256 = null,
            indexedAt = indexedAt,
        )
    }

    private companion object {
        const val NOW = "2026-10-17T10:00:00Z"
        val NOW_MILLIS: Long = Instant.parse(NOW).toEpochMilli()
        const val SEED = 7
        const val REPOSITORY = "releases"
        const val HIDDEN_REPOSITORY = "hidden"
        const val DIRECTORY = "com/example/app/1.0"
        const val JAR = "com/example/app/1.0/app-1.0.jar"
        const val SOURCES = "com/example/app/1.0/app-1.0-sources.jar"
        const val SIBLING = "com/example/app/1.0/app-1.0.pom"
        const val HIDDEN_JAR = "com/example/hidden/1.0/hidden-1.0.jar"
        const val MIRRORED_DIRECTORY = "com/example/mirrored/1.0"
        const val MIRRORED_JAR = "com/example/mirrored/1.0/mirrored-1.0.jar"
        const val HOSTED_JAR = "com/example/hosted/1.0/hosted-1.0.jar"
        const val UPSTREAM = "upstream.example"
        val CUTOFF: Long = Instant.parse("2026-10-17T12:00:00Z").toEpochMilli()
        val BEFORE_CUTOFF: Long = Instant.parse("2026-10-17T11:00:00Z").toEpochMilli()
        val AFTER_CUTOFF: Long = Instant.parse("2026-10-17T13:00:00Z").toEpochMilli()
        val ALL_REPOSITORIES = SearchAccess(listOf(RepositoryAccess(REPOSITORY, PathPredicate.All)))
    }

}
