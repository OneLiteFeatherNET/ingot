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

package net.onelitefeather.ingot.search.access

import net.onelitefeather.ingot.search.ArtifactFileItem
import net.onelitefeather.ingot.search.ArtifactFilePage
import net.onelitefeather.ingot.search.ArtifactFilesQuery
import net.onelitefeather.ingot.search.ArtifactSearchEngine
import net.onelitefeather.ingot.search.ArtifactSearchItem
import net.onelitefeather.ingot.search.ArtifactSearchPage
import net.onelitefeather.ingot.search.ArtifactSearchQuery
import net.onelitefeather.ingot.search.IndexState
import net.onelitefeather.ingot.search.IndexStatus
import net.onelitefeather.ingot.search.Origin
import net.onelitefeather.ingot.search.RepositoryPresence
import net.onelitefeather.ingot.search.SearchFacets
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * The access check of the core (search backend registration, D6). A backend that returns a row the caller may not see
 * must not leak it: the row is dropped and reported, whatever engine answered.
 */
internal class AccessCheckedSearchEngineTest {

    private val access = SearchAccess(listOf(RepositoryAccess("releases", PathPredicate.Exact("com/example"))))
    private val reported = mutableListOf<Pair<String, String>>()

    private fun checked(engine: ArtifactSearchEngine) = AccessCheckedSearchEngine(engine) { repository, location ->
        reported += repository to location
    }

    @Test
    fun `a file inside the access value is returned and nothing is reported`() {
        val engine = LeakingEngine(files = listOf(file("releases", "com/example/app/1.0/app-1.0.jar")))

        val page = checked(engine).files(filesQuery(), access)

        assertThat(page.items.map { it.path }).`as`("paths of the files returned").containsExactly("com/example/app/1.0/app-1.0.jar")
        assertThat(reported).`as`("rows reported as access mismatches").isEmpty()
    }

    @Test
    fun `a file outside the access value is dropped from the files response and reported`() {
        val engine = LeakingEngine(
            files = listOf(
                file("releases", "com/example/app/1.0/app-1.0.jar"),
                file("releases", "com/other/app/1.0/app-1.0.jar"),
            ),
        )

        val page = checked(engine).files(filesQuery(), access)

        assertThat(page.items.map { it.path }).`as`("paths of the files returned").containsExactly("com/example/app/1.0/app-1.0.jar")
        assertThat(reported).`as`("rows reported as access mismatches")
            .containsExactly("releases" to "com/other/app/1.0/app-1.0.jar")
    }

    @Test
    fun `a repository outside the access value is removed from a group and reported`() {
        val engine = LeakingEngine(groups = listOf(group("releases", "secret")))

        val page = checked(engine).search(ArtifactSearchQuery(), access)

        assertThat(page.items.single().repositories.map { it.name }).`as`("repositories of the group returned").containsExactly("releases")
        assertThat(reported).`as`("rows reported as access mismatches")
            .containsExactly("secret" to "com.example:app:1.0")
    }

    @Test
    fun `a group whose repositories are all outside the access value is dropped`() {
        val engine = LeakingEngine(groups = listOf(group("secret")))

        val page = checked(engine).search(ArtifactSearchQuery(), access)

        assertThat(page.items).`as`("groups returned").isEmpty()
        assertThat(reported).`as`("rows reported as access mismatches").hasSize(1)
    }

    private fun filesQuery() = ArtifactFilesQuery(groupId = "com.example", artifactId = "app", version = "1.0")

    private fun file(repository: String, path: String) = ArtifactFileItem(
        repository = repository,
        path = path,
        name = path.substringAfterLast('/'),
        classifier = null,
        extension = "jar",
        size = 1L,
        lastModified = Instant.EPOCH,
        origin = Origin.HOSTED,
        sourceHost = null,
        sha1 = null,
        browseLink = "/browse/$repository/$path",
    )

    private fun group(vararg repositories: String) = ArtifactSearchItem(
        groupId = "com.example",
        artifactId = "app",
        version = "1.0",
        latestVersion = null,
        snapshot = false,
        repositories = repositories.map { RepositoryPresence(it, Origin.HOSTED) },
        fileCount = 1L,
        lastModified = Instant.EPOCH,
        browseLink = "/browse/${repositories.first()}/com/example/app/1.0",
    )

    /** An engine that answers with what it was given, whatever the access value says: the leak the core must catch. */
    private class LeakingEngine(
        private val files: List<ArtifactFileItem> = emptyList(),
        private val groups: List<ArtifactSearchItem> = emptyList(),
    ) : ArtifactSearchEngine {

        override val id: String = "leaking"

        override fun search(query: ArtifactSearchQuery, access: SearchAccess): ArtifactSearchPage = ArtifactSearchPage(
            items = groups,
            page = 0,
            size = 20,
            total = groups.size.toLong(),
            totalCapped = false,
            indexStatus = status(),
        )

        override fun facets(query: ArtifactSearchQuery, access: SearchAccess): SearchFacets =
            SearchFacets(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

        override fun files(query: ArtifactFilesQuery, access: SearchAccess): ArtifactFilePage = ArtifactFilePage(
            items = files,
            page = 0,
            size = 50,
            total = files.size.toLong(),
            indexStatus = status(),
        )

        override fun status(): IndexStatus = IndexStatus(
            state = IndexState.READY,
            lagSeconds = 0L,
            lastFullRebuild = null,
            deadLetters = 0L,
            droppedEnqueues = 0L,
        )

    }

}
