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

package net.onelitefeather.ingot.search.database

import net.onelitefeather.ingot.search.ArtifactFilesQuery
import net.onelitefeather.ingot.search.ArtifactPathParser
import net.onelitefeather.ingot.search.ArtifactPathResult
import net.onelitefeather.ingot.search.ArtifactSearchEngine
import net.onelitefeather.ingot.search.ArtifactSearchPage
import net.onelitefeather.ingot.search.ArtifactSearchQuery
import net.onelitefeather.ingot.search.FacetBucket
import net.onelitefeather.ingot.search.IndexState
import net.onelitefeather.ingot.search.InvalidSearchQueryException
import net.onelitefeather.ingot.search.Origin
import net.onelitefeather.ingot.search.OriginFilter
import net.onelitefeather.ingot.search.RepositoryPresence
import net.onelitefeather.ingot.search.SearchOrder
import net.onelitefeather.ingot.search.SearchSort
import net.onelitefeather.ingot.search.SnapshotFilter
import net.onelitefeather.ingot.search.access.PathPredicate
import net.onelitefeather.ingot.search.access.RepositoryAccess
import net.onelitefeather.ingot.search.access.SearchAccess
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The read side of the database engine against a seeded database (artifact search spec, 4 and 10.2). Every test builds its
 * own database in its own temporary directory and drives the engine with a fixed clock. Subclasses choose the dialect.
 */
internal abstract class DatabaseArtifactSearchEngineTest {

    /** A connection and the way to close it. */
    protected class OpenDatabase(val database: Database, private val closer: AutoCloseable) : AutoCloseable {
        override fun close() = closer.close()
    }

    @TempDir
    lateinit var directory: Path

    private lateinit var opened: OpenDatabase
    private lateinit var database: Database
    private lateinit var engine: ArtifactSearchEngine
    private lateinit var fixture: IndexFixture

    private val clock: Clock = Clock.fixed(NOW, ZoneOffset.UTC)

    /** Opens a fresh database of the dialect under test in [directory]. */
    protected abstract fun open(directory: Path): OpenDatabase

    @BeforeEach
    fun setUp() {
        opened = open(directory)
        database = opened.database
        SearchSchema.ensure(database, clock)
        engine = DatabaseArtifactSearchEngine(database, clock)
        fixture = IndexFixture(database, clock)
    }

    @AfterEach
    fun tearDown() {
        opened.close()
    }

    // ---- filters -------------------------------------------------------------------------------------------------

    @Test
    fun `groups the files of one version into one item with the file count`() {
        seedFile(artifactId = "commons-lang3", version = "3.14.0", groupId = "org.apache.commons", extension = "jar")
        seedFile(artifactId = "commons-lang3", version = "3.14.0", groupId = "org.apache.commons", extension = "pom")
        seedFile(artifactId = "commons-lang3", version = "3.14.0", groupId = "org.apache.commons", classifier = "sources")

        val page = search(ArtifactSearchQuery())

        assertThat(page.items).`as`("groups").hasSize(1)
        assertThat(page.items.single().fileCount).`as`("files of the version").isEqualTo(3)
        assertThat(page.total).`as`("total groups").isEqualTo(1)
    }

    @Test
    fun `matches the groupId prefix case-insensitively`() {
        seedFile(groupId = "com.example", artifactId = "app")
        seedFile(groupId = "com.other", artifactId = "lib")
        seedFile(groupId = "org.apache", artifactId = "core")

        val page = search(ArtifactSearchQuery(groupId = "COM.EXA"))

        assertThat(page.items.map { it.groupId }).`as`("groups under COM.EXA").containsExactly("com.example")
    }

    @Test
    fun `does not match a groupId prefix in the middle of the value`() {
        seedFile(groupId = "org.apache", artifactId = "core")

        val page = search(ArtifactSearchQuery(groupId = "apache"))

        assertThat(page.items).`as`("groups for the infix apache").isEmpty()
    }

    @Test
    fun `matches the artifactId prefix and not a word inside the name`() {
        seedFile(groupId = "org.example", artifactId = "core-api")
        seedFile(groupId = "org.example", artifactId = "spring-core")

        val page = search(ArtifactSearchQuery(artifactId = "core"))

        assertThat(page.items.map { it.artifactId }).`as`("artifacts starting with core").containsExactly("core-api")
    }

    @Test
    fun `matches the version exactly`() {
        seedFile(version = "1.0.0")
        seedFile(version = "1.0.0.1")

        val page = search(ArtifactSearchQuery(version = "1.0.0"))

        assertThat(page.items.map { it.version }).`as`("versions equal to 1.0.0").containsExactly("1.0.0")
    }

    @Test
    fun `compares the exact version case-insensitively and returns the stored case`() {
        seedFile(version = "1.0.0-Beta")

        val page = search(ArtifactSearchQuery(version = "1.0.0-BETA"))

        assertThat(page.items.map { it.version }).`as`("version in stored case").containsExactly("1.0.0-Beta")
    }

    @Test
    fun `matches the version prefix`() {
        seedFile(version = "1.1.0")
        seedFile(version = "1.10.0")
        seedFile(version = "1.2.0")

        val page = search(ArtifactSearchQuery(versionPrefix = "1.1"))

        assertThat(page.items.map { it.version }).`as`("versions starting with 1.1").containsExactlyInAnyOrder("1.1.0", "1.10.0")
    }

    @Test
    fun `refuses a version and a version prefix together`() {
        assertThatThrownBy { search(ArtifactSearchQuery(version = "1.0.0", versionPrefix = "1.")) }
            .isInstanceOfSatisfying(InvalidSearchQueryException::class.java) { assertThat(it.parameter).isEqualTo("versionPrefix") }
    }

    @Test
    fun `matches any of several repositories`() {
        seedFile(repository = "releases", artifactId = "a")
        seedFile(repository = "snapshots", artifactId = "b")
        seedFile(repository = "hidden", artifactId = "c")

        val page = search(ArtifactSearchQuery(repositories = listOf("releases", "snapshots")))

        assertThat(page.items.map { it.artifactId }).`as`("artifacts in releases or snapshots").containsExactlyInAnyOrder("a", "b")
    }

    @Test
    fun `counts only the files that match the extension filter`() {
        seedFile(artifactId = "app", extension = "jar")
        seedFile(artifactId = "app", extension = "pom")
        seedFile(artifactId = "lib", version = "2.0.0", extension = "jar")

        val page = search(ArtifactSearchQuery(extensions = listOf("POM")))

        assertThat(page.items.map { it.artifactId }).`as`("groups with a pom").containsExactly("app")
        assertThat(page.items.single().fileCount).`as`("matching pom files").isEqualTo(1)
    }

    @Test
    fun `selects files without a classifier with the dash value`() {
        seedFile(artifactId = "app", classifier = null)
        seedFile(artifactId = "app", classifier = "sources")

        val page = search(ArtifactSearchQuery(classifiers = listOf("-")))

        assertThat(page.items.single().fileCount).`as`("files without classifier").isEqualTo(1)
    }

    @Test
    fun `selects a named classifier together with the files without one`() {
        seedFile(artifactId = "app", classifier = null)
        seedFile(artifactId = "app", classifier = "sources")
        seedFile(artifactId = "app", classifier = "javadoc")

        val page = search(ArtifactSearchQuery(classifiers = listOf("-", "sources")))

        assertThat(page.items.single().fileCount).`as`("files with - or sources").isEqualTo(2)
    }

    @Test
    fun `matches the checksum exactly, ignoring the case of the request`() {
        seedFile(artifactId = "app", sha1 = SHA1_A)
        seedFile(artifactId = "lib", sha1 = SHA1_B)

        val page = search(ArtifactSearchQuery(sha1 = SHA1_A.uppercase()))

        assertThat(page.items.map { it.artifactId }).`as`("groups with the checksum").containsExactly("app")
    }

    @Test
    fun `refuses a checksum that is not 40 hexadecimal characters`() {
        assertThatThrownBy { search(ArtifactSearchQuery(sha1 = "not-a-checksum")) }
            .isInstanceOfSatisfying(InvalidSearchQueryException::class.java) { assertThat(it.parameter).isEqualTo("sha1") }
    }

    @Test
    fun `keeps only snapshots when asked for snapshots`() {
        seedFile(version = "1.0.0")
        seedFile(version = "1.1-SNAPSHOT")

        val snapshots = search(ArtifactSearchQuery(snapshot = SnapshotFilter.TRUE))

        assertThat(snapshots.items.map { it.version }).`as`("snapshots").containsExactly("1.1-SNAPSHOT")
    }

    @Test
    fun `keeps only releases when asked for releases`() {
        seedFile(version = "1.0.0")
        seedFile(version = "1.1-SNAPSHOT")

        val releases = search(ArtifactSearchQuery(snapshot = SnapshotFilter.FALSE))

        assertThat(releases.items.map { it.version }).`as`("releases").containsExactly("1.0.0")
    }

    @Test
    fun `filters by origin`() {
        seedFile(repository = "releases", artifactId = "app", origin = Origin.HOSTED)
        seedFile(repository = "central-proxy", artifactId = "app", origin = Origin.MIRROR, sourceHost = "repo.maven.apache.org")
        seedFile(repository = "releases", artifactId = "lib", origin = Origin.HOSTED)

        val mirror = search(ArtifactSearchQuery(origin = OriginFilter.MIRROR))

        assertThat(mirror.items.map { it.artifactId }).`as`("groups with a mirror copy").containsExactly("app")
        assertThat(mirror.items.single().fileCount).`as`("mirror files").isEqualTo(1)
    }

    @Test
    fun `includes the modification time at the lower bound`() {
        seedFile(artifactId = "app", lastModified = T1)
        seedFile(artifactId = "lib", lastModified = T0)

        val page = search(ArtifactSearchQuery(modifiedAfter = T1))

        assertThat(page.items.map { it.artifactId }).`as`("modified at or after T1").containsExactly("app")
    }

    @Test
    fun `excludes the modification time at the upper bound`() {
        seedFile(artifactId = "app", lastModified = T1)
        seedFile(artifactId = "lib", lastModified = T2)

        val page = search(ArtifactSearchQuery(modifiedBefore = T2))

        assertThat(page.items.map { it.artifactId }).`as`("modified before T2").containsExactly("app")
    }

    @Test
    fun `keeps the size bounds inclusive`() {
        seedFile(artifactId = "small", sizeBytes = 100)
        seedFile(artifactId = "medium", sizeBytes = 200)
        seedFile(artifactId = "large", sizeBytes = 300)
        seedFile(artifactId = "huge", sizeBytes = 400)

        val page = search(ArtifactSearchQuery(sizeMin = 200, sizeMax = 300))

        assertThat(page.items.map { it.artifactId }).`as`("sizes from 200 to 300").containsExactlyInAnyOrder("medium", "large")
    }

    // ---- free text -----------------------------------------------------------------------------------------------

    @Test
    fun `matches a free text token against the groupId prefix, the artifactId prefix or the version`() {
        seedFile(groupId = "org.apache", artifactId = "commons-lang3", version = "3.14.0")
        seedFile(groupId = "com.example", artifactId = "app", version = "3.14.0")

        val byGroup = search(ArtifactSearchQuery(q = "org.apa"))
        val byArtifact = search(ArtifactSearchQuery(q = "commons"))
        val byVersion = search(ArtifactSearchQuery(q = "3.14.0"))

        assertThat(byGroup.items.map { it.artifactId }).`as`("groupId prefix").containsExactly("commons-lang3")
        assertThat(byArtifact.items.map { it.artifactId }).`as`("artifactId prefix").containsExactly("commons-lang3")
        assertThat(byVersion.items.map { it.artifactId }).`as`("exact version").containsExactlyInAnyOrder("commons-lang3", "app")
    }

    @Test
    fun `requires every free text token to match`() {
        seedFile(groupId = "org.apache", artifactId = "core")
        seedFile(groupId = "org.apache", artifactId = "commons")

        val page = search(ArtifactSearchQuery(q = "org core"))

        assertThat(page.items.map { it.artifactId }).`as`("groups matching both tokens").containsExactly("core")
    }

    @Test
    fun `reads a coordinates token as groupId, artifactId and version parts`() {
        seedFile(groupId = "org.apache.commons", artifactId = "commons-lang3", version = "3.14.0")
        seedFile(groupId = "org.apache.commons", artifactId = "commons-lang3", version = "3.13.0")

        val page = search(ArtifactSearchQuery(q = "org.apache.commons:commons-lang3:3.14.0"))

        assertThat(page.items.map { it.version }).`as`("the version of the coordinates").containsExactly("3.14.0")
    }

    @Test
    fun `reads an empty leading part of a coordinates token as no groupId filter`() {
        seedFile(groupId = "org.apache", artifactId = "commons-lang3")
        seedFile(groupId = "com.example", artifactId = "commons-io")

        val page = search(ArtifactSearchQuery(q = ":commons-lang"))

        assertThat(page.items.map { it.artifactId }).`as`("artifactId prefix only").containsExactly("commons-lang3")
    }

    @Test
    fun `refuses more than five free text tokens`() {
        assertThatThrownBy { search(ArtifactSearchQuery(q = "a b c d e f")) }
            .isInstanceOfSatisfying(InvalidSearchQueryException::class.java) { assertThat(it.parameter).isEqualTo("q") }
    }

    @Test
    fun `refuses a free text longer than 200 characters`() {
        assertThatThrownBy { search(ArtifactSearchQuery(q = "a".repeat(201))) }
            .isInstanceOfSatisfying(InvalidSearchQueryException::class.java) { assertThat(it.parameter).isEqualTo("q") }
    }

    // ---- sorting and paging --------------------------------------------------------------------------------------

    @Test
    fun `sorts by the total size of the matching files, largest first`() {
        seedFile(artifactId = "small", sizeBytes = 10)
        seedFile(artifactId = "big", sizeBytes = 500)
        seedFile(artifactId = "big", sizeBytes = 500, extension = "pom")

        val page = search(ArtifactSearchQuery(sort = SearchSort.SIZE, order = SearchOrder.DESC))

        assertThat(page.items.map { it.artifactId }).containsExactly("big", "small")
    }

    @Test
    fun `sorts by the latest modification of the matching files, oldest first`() {
        seedFile(artifactId = "recent", lastModified = T2)
        seedFile(artifactId = "old", lastModified = T0)

        val page = search(ArtifactSearchQuery(sort = SearchSort.MODIFIED, order = SearchOrder.ASC))

        assertThat(page.items.map { it.artifactId }).containsExactly("old", "recent")
    }

    @Test
    fun `pages through the groups without overlap and in a stable order`() {
        (1..25).forEach { seedFile(artifactId = "lib-%02d".format(it)) }

        val pages = (0..2).map { search(ArtifactSearchQuery(size = 10, page = it)) }
        val again = search(ArtifactSearchQuery(size = 10, page = 1))

        val names = pages.flatMap { page -> page.items.map { it.artifactId } }
        assertThat(names).`as`("all groups across the pages, in order")
            .containsExactlyElementsOf((1..25).map { "lib-%02d".format(it) })
        assertThat(pages.map { it.total }).`as`("total on every page").containsOnly(25L)
        assertThat(again.items).`as`("the same page asked twice").isEqualTo(pages[1].items)
    }

    @Test
    fun `pages groups that tie on the sort key without repeating or skipping one`() {
        (1..6).forEach { seedFile(artifactId = "tie-$it", version = "1.0.0") }

        val first = search(ArtifactSearchQuery(sort = SearchSort.VERSION, size = 4, page = 0))
        val second = search(ArtifactSearchQuery(sort = SearchSort.VERSION, size = 4, page = 1))

        assertThat(first.items).hasSize(4)
        assertThat(second.items).hasSize(2)
        assertThat((first.items + second.items).map { it.artifactId }).`as`("every group once across the pages")
            .doesNotHaveDuplicates()
            .hasSize(6)
    }

    @Test
    fun `returns an empty page beyond the last group and still counts the total`() {
        seedFile(artifactId = "app")

        val page = search(ArtifactSearchQuery(page = 3, size = 20))

        assertThat(page.items).`as`("items beyond the end").isEmpty()
        assertThat(page.total).`as`("total is still counted").isEqualTo(1)
    }

    @Test
    fun `allows the last page inside the 10000 entry window`() {
        seedFile(artifactId = "app")

        val page = search(ArtifactSearchQuery(page = 499, size = 20))

        assertThat(page.items).isEmpty()
    }

    @Test
    fun `refuses a page that ends beyond the 10000 entry window`() {
        assertThatThrownBy { search(ArtifactSearchQuery(page = 500, size = 20)) }
            .isInstanceOfSatisfying(InvalidSearchQueryException::class.java) { assertThat(it.parameter).isEqualTo("page") }
    }

    @Test
    fun `refuses a page size above 100`() {
        assertThatThrownBy { search(ArtifactSearchQuery(size = 101)) }
            .isInstanceOfSatisfying(InvalidSearchQueryException::class.java) { assertThat(it.parameter).isEqualTo("size") }
    }

    @Test
    fun `caps the total at 10001 groups and says so`() {
        fixture.insertGroups(count = ArtifactSearchPage.TOTAL_CAP.toInt() + 1, lastModified = T0.toEpochMilli(), indexedAt = clock.millis())

        val page = search(ArtifactSearchQuery(size = 1))

        assertThat(page.total).`as`("total up to the cap").isEqualTo(ArtifactSearchPage.TOTAL_CAP)
        assertThat(page.totalCapped).`as`("capped flag").isTrue()
    }

    // ---- items: repositories, links, latest version --------------------------------------------------------------

    @Test
    fun `lists every visible repository that holds the version with its origin`() {
        seedFile(repository = "releases", artifactId = "app", origin = Origin.HOSTED)
        seedFile(repository = "central-proxy", artifactId = "app", origin = Origin.MIRROR, sourceHost = "repo.maven.apache.org")

        val item = search(ArtifactSearchQuery()).items.single()

        assertThat(item.repositories).`as`("repositories of the version")
            .containsExactly(RepositoryPresence("central-proxy", Origin.MIRROR), RepositoryPresence("releases", Origin.HOSTED))
    }

    @Test
    fun `links the item to the directory of its first repository`() {
        seedFile(repository = "releases", groupId = "org.apache.commons", artifactId = "commons-lang3", version = "3.14.0")

        val item = search(ArtifactSearchQuery()).items.single()

        assertThat(item.browseLink).isEqualTo("/browse/releases/org/apache/commons/commons-lang3/3.14.0")
    }

    @Test
    fun `reports the latest version in Maven version order and not in text order`() {
        seedFile(version = "1.9.0")
        seedFile(version = "1.10.0")
        seedFile(version = "2.0.0-SNAPSHOT")

        val item = search(ArtifactSearchQuery(version = "1.9.0")).items.single()

        assertThat(item.latestVersion).`as`("latest release, ignoring the filter").isEqualTo("1.10.0")
    }

    @Test
    fun `reports the latest snapshot when an artifact has only snapshots`() {
        seedFile(artifactId = "lib", version = "1.0-SNAPSHOT")
        seedFile(artifactId = "lib", version = "1.1-SNAPSHOT")

        val items = search(ArtifactSearchQuery(artifactId = "lib")).items

        assertThat(items.map { it.version }).`as`("one group per snapshot").containsExactlyInAnyOrder("1.0-SNAPSHOT", "1.1-SNAPSHOT")
        assertThat(items.map { it.latestVersion }).`as`("latest snapshot of every group").containsOnly("1.1-SNAPSHOT")
    }

    @Test
    fun `takes the latest version only from the repositories the caller may see`() {
        seedFile(repository = "releases", artifactId = "app", version = "1.0.0")
        seedFile(repository = "hidden", artifactId = "app", version = "2.0.0")

        val page = engine.search(ArtifactSearchQuery(), releasesOnly)

        assertThat(page.items.single().latestVersion).`as`("latest visible version").isEqualTo("1.0.0")
    }

    // ---- facets --------------------------------------------------------------------------------------------------

    @Test
    fun `counts files in every facet over the visible rows`() {
        seedFile(repository = "releases", artifactId = "app", extension = "jar")
        seedFile(repository = "releases", artifactId = "app", extension = "pom")
        seedFile(repository = "releases", artifactId = "lib", version = "2.0.0", extension = "jar")
        seedFile(repository = "central-proxy", artifactId = "app", extension = "jar", origin = Origin.MIRROR, sourceHost = "repo.maven.apache.org")

        val facets = engine.facets(ArtifactSearchQuery(), allRepositories)

        assertThat(facets.repository).containsExactly(FacetBucket("releases", 3), FacetBucket("central-proxy", 1))
        assertThat(facets.extension).containsExactly(FacetBucket("jar", 3), FacetBucket("pom", 1))
        assertThat(facets.origin).containsExactly(FacetBucket("HOSTED", 3), FacetBucket("MIRROR", 1))
        assertThat(facets.snapshot).containsExactly(FacetBucket("false", 4))
    }

    @Test
    fun `counts a facet without its own filter but with the others`() {
        seedFile(repository = "releases", artifactId = "app", extension = "jar")
        seedFile(repository = "releases", artifactId = "app", extension = "pom")
        seedFile(repository = "central-proxy", artifactId = "app", extension = "jar", origin = Origin.MIRROR, sourceHost = "repo.maven.apache.org")

        val facets = engine.facets(ArtifactSearchQuery(repositories = listOf("releases")), allRepositories)

        assertThat(facets.repository).`as`("repository facet ignores the repository filter")
            .containsExactly(FacetBucket("releases", 2), FacetBucket("central-proxy", 1))
        assertThat(facets.extension).`as`("extension facet keeps the repository filter")
            .containsExactly(FacetBucket("jar", 1), FacetBucket("pom", 1))
    }

    @Test
    fun `counts files without a classifier as the dash bucket`() {
        seedFile(artifactId = "app", classifier = null)
        seedFile(artifactId = "app", classifier = "sources")

        val facets = engine.facets(ArtifactSearchQuery(), allRepositories)

        assertThat(facets.classifier).containsExactlyInAnyOrder(FacetBucket("-", 1), FacetBucket("sources", 1))
    }

    // ---- files of a version --------------------------------------------------------------------------------------

    @Test
    fun `lists the files of a version ordered by path`() {
        seedFile(artifactId = "app", extension = "pom")
        seedFile(artifactId = "app", extension = "jar")
        seedFile(artifactId = "app", classifier = "sources")

        val page = engine.files(filesOf(artifactId = "app"), allRepositories)

        assertThat(page.items.map { it.path }).`as`("paths in path order").isEqualTo(page.items.map { it.path }.sorted())
        assertThat(page.total).isEqualTo(3)
    }

    @Test
    fun `filters the files of a version by origin and names the upstream host of a copy`() {
        seedFile(repository = "releases", artifactId = "app")
        seedFile(repository = "central-proxy", artifactId = "app", origin = Origin.MIRROR, sourceHost = "repo.maven.apache.org")

        val page = engine.files(filesOf(artifactId = "app", origin = OriginFilter.MIRROR), allRepositories)

        assertThat(page.items.map { it.repository }).containsExactly("central-proxy")
        assertThat(page.items.single().sourceHost).`as`("upstream host of the copy").isEqualTo("repo.maven.apache.org")
    }

    @Test
    fun `filters the files of a version by repository and pages them without overlap`() {
        seedFile(repository = "releases", artifactId = "app", extension = "jar")
        seedFile(repository = "releases", artifactId = "app", extension = "pom")
        seedFile(repository = "releases", artifactId = "app", classifier = "sources")
        seedFile(repository = "other", artifactId = "app")

        val first = engine.files(filesOf(artifactId = "app", repositories = listOf("releases"), size = 2, page = 0), allRepositories)
        val second = engine.files(filesOf(artifactId = "app", repositories = listOf("releases"), size = 2, page = 1), allRepositories)

        assertThat(first.items).hasSize(2)
        assertThat(second.items).hasSize(1)
        assertThat(first.total).`as`("files in releases").isEqualTo(3)
        assertThat((first.items + second.items).map { it.path }).doesNotHaveDuplicates()
    }

    @Test
    fun `refuses a files request without a groupId`() {
        val query = ArtifactFilesQuery(groupId = "", artifactId = "app", version = "1.0.0")

        assertThatThrownBy { engine.files(query, allRepositories) }
            .isInstanceOfSatisfying(InvalidSearchQueryException::class.java) { assertThat(it.parameter).isEqualTo("groupId") }
    }

    // ---- access --------------------------------------------------------------------------------------------------

    @Test
    fun `returns no item, no facet bucket and no file when the caller may see no row`() {
        seedFile(repository = RELEASES, artifactId = "app")

        val page = engine.search(ArtifactSearchQuery(), SearchAccess.NONE)
        val facets = engine.facets(ArtifactSearchQuery(), SearchAccess.NONE)
        val files = engine.files(filesOf(artifactId = "app"), SearchAccess.NONE)

        assertThat(page.items).isEmpty()
        assertThat(page.total).isZero()
        assertThat(facets.repository).isEmpty()
        assertThat(files.items).isEmpty()
        assertThat(page.indexStatus).`as`("index status is still reported").isNotNull()
    }

    @Test
    fun `leaves a repository the caller may not see out of the results and the facets`() {
        seedFile(repository = "releases", artifactId = "app")
        seedFile(repository = "hidden", artifactId = "secret")

        val page = engine.search(ArtifactSearchQuery(), releasesOnly)
        val facets = engine.facets(ArtifactSearchQuery(), releasesOnly)

        assertThat(page.items.map { it.artifactId }).containsExactly("app")
        assertThat(facets.repository).containsExactly(FacetBucket("releases", 1))
    }

    // ---- index status --------------------------------------------------------------------------------------------

    @Test
    fun `reports a missing index when nothing is stored and no rebuild ran`() {
        assertThat(engine.status().state).isEqualTo(IndexState.MISSING)
    }

    @Test
    fun `reports a building index when rows exist before the first full rebuild`() {
        seedFile(artifactId = "app")

        assertThat(engine.status().state).isEqualTo(IndexState.BUILDING)
    }

    @Test
    fun `reports a ready index after a full rebuild completed`() {
        seedFile(artifactId = "app")
        markRebuildCompleted()

        val status = engine.status()

        assertThat(status.state).isEqualTo(IndexState.READY)
        assertThat(status.lastFullRebuild).`as`("last full rebuild").isEqualTo(NOW)
    }

    @Test
    fun `carries the index status in every search response`() {
        seedFile(artifactId = "app")
        markRebuildCompleted()

        val page = search(ArtifactSearchQuery())

        assertThat(page.indexStatus.state).isEqualTo(IndexState.READY)
    }

    @Test
    fun `reports the age of the oldest pending outbox row as the lag and degrades the index`() {
        seedFile(artifactId = "app")
        markRebuildCompleted()
        fixture.enqueue(createdAtMillis = NOW.minusSeconds(600).toEpochMilli(), attempts = 0)

        val status = engine.status()

        assertThat(status.lagSeconds).`as`("lag in seconds").isEqualTo(600)
        assertThat(status.state).`as`("state with a 10 minute lag").isEqualTo(IndexState.DEGRADED)
    }

    @Test
    fun `counts dead letters and leaves them out of the lag`() {
        seedFile(artifactId = "app")
        markRebuildCompleted()
        fixture.enqueue(createdAtMillis = NOW.minusSeconds(3600).toEpochMilli(), attempts = 10)

        val status = engine.status()

        assertThat(status.deadLetters).isEqualTo(1)
        assertThat(status.lagSeconds).`as`("dead letters are not pending").isZero()
        assertThat(status.state).isEqualTo(IndexState.DEGRADED)
    }

    @Test
    fun `reports dropped enqueues since the last rebuild as degraded`() {
        seedFile(artifactId = "app")
        markRebuildCompleted()
        fixture.putState(SearchStateKey.DROPPED_ENQUEUES, "2")

        val status = engine.status()

        assertThat(status.droppedEnqueues).isEqualTo(2)
        assertThat(status.state).isEqualTo(IndexState.DEGRADED)
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    /** Sees only the repository `releases`, as a caller with one grant would. */
    private val releasesOnly = SearchAccess(listOf(RepositoryAccess(RELEASES, PathPredicate.All)))

    /** Sees every repository the tests seed, with every path. */
    private val allRepositories = SearchAccess(
        listOf(RELEASES, "snapshots", "hidden", "central-proxy", "other").map { RepositoryAccess(it, PathPredicate.All) },
    )

    private fun search(query: ArtifactSearchQuery) = engine.search(query, allRepositories)

    private fun filesOf(
        artifactId: String,
        origin: OriginFilter = OriginFilter.ALL,
        repositories: List<String> = emptyList(),
        size: Int = 50,
        page: Int = 0,
    ) = ArtifactFilesQuery(
        groupId = "com.example",
        artifactId = artifactId,
        version = "1.0.0",
        origin = origin,
        repositories = repositories,
        size = size,
        page = page,
    )

    /** Stores one file of a version. The path is built from the coordinates and parsed by the production parser. */
    private fun seedFile(
        repository: String = RELEASES,
        groupId: String = "com.example",
        artifactId: String = "app",
        version: String = "1.0.0",
        classifier: String? = null,
        extension: String = "jar",
        origin: Origin = Origin.HOSTED,
        sourceHost: String? = null,
        sizeBytes: Long = 100,
        lastModified: Instant = T0,
        sha1: String? = null,
    ) {
        val fileName = listOfNotNull(artifactId, version, classifier).joinToString("-") + ".$extension"
        val path = "${groupId.replace('.', '/')}/$artifactId/$version/$fileName"
        val file = (ArtifactPathParser.parse(repository, path) as? ArtifactPathResult.Row)?.file
            ?: throw IllegalStateException("the test path $path does not parse as a stored file")
        fixture.insertFile(
            file = file,
            origin = origin,
            sourceHost = sourceHost,
            sizeBytes = sizeBytes,
            lastModified = lastModified.toEpochMilli(),
            sha1 = sha1,
            indexedAt = clock.millis(),
        )
    }

    private fun markRebuildCompleted() {
        fixture.putState(SearchStateKey.LAST_FULL_REBUILD, NOW.toEpochMilli().toString())
    }

    protected companion object {
        const val RELEASES = "releases"
        const val SHA1_A = "a94a8fe5ccb19ba61c4c0873d391e987982fbbd3"
        const val SHA1_B = "da39a3ee5e6b4b0d3255bfef95601890afd80709"
        val NOW: Instant = Instant.parse("2026-10-16T10:00:00Z")
        val T0: Instant = Instant.parse("2026-01-01T00:00:00Z")
        val T1: Instant = Instant.parse("2026-06-01T00:00:00Z")
        val T2: Instant = Instant.parse("2026-09-01T00:00:00Z")
    }

}
