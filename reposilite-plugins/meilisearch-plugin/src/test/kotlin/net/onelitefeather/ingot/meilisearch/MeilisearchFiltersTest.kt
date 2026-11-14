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

package net.onelitefeather.ingot.meilisearch

import net.onelitefeather.ingot.search.access.PathPredicate
import net.onelitefeather.ingot.search.access.PrecedenceRule
import net.onelitefeather.ingot.search.access.RepositoryAccess
import net.onelitefeather.ingot.search.access.SearchAccess
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import net.onelitefeather.ingot.search.SearchUnavailableException

/** The golden filter strings of the access value (design D6). Every test pins one predicate to its rendered text. */
@OptIn(ExperimentalIngotSearchApi::class)
internal class MeilisearchFiltersTest {

    private fun access(path: PathPredicate): SearchAccess = SearchAccess(listOf(RepositoryAccess("releases", path)))

    @Test
    fun `should render a repository without path restriction as its repository equality`() {
        // when / then: all paths of the repository
        assertThat(MeilisearchFilters.access(access(PathPredicate.All)))
            .`as`("filter of an unrestricted repository")
            .isEqualTo("(repository = \"releases\")")
    }

    @Test
    fun `should render an exact path as the path itself or a directory that holds it`() {
        // when / then: the path itself, or the path as an ancestor directory of a row
        assertThat(MeilisearchFilters.access(access(PathPredicate.Exact("com/example/app"))))
            .`as`("filter of an exact path")
            .isEqualTo("(repository = \"releases\" AND (path = \"com/example/app\" OR directories = \"com/example/app\"))")
    }

    @Test
    fun `should render a subtree from its segments as the joined path`() {
        // when / then: the segments are joined with slashes
        assertThat(MeilisearchFilters.access(access(PathPredicate.Subtree(listOf("com", "example")))))
            .`as`("filter of a subtree")
            .isEqualTo("(repository = \"releases\" AND (path = \"com/example\" OR directories = \"com/example\"))")
    }

    @Test
    fun `should render nothing for a repository whose path predicate never matches`() {
        // when / then: a never predicate matches no row
        assertThat(MeilisearchFilters.access(access(PathPredicate.Never)))
            .`as`("filter of a never predicate")
            .isEqualTo("(repository = \"releases\" AND id NOT EXISTS)")
    }

    @Test
    fun `should escape quotes and backslashes of a path inside the quoted value`() {
        // when / then: the quote and the backslash are escaped as the filter syntax requires
        assertThat(MeilisearchFilters.access(access(PathPredicate.Exact("a\"b\\c"))))
            .`as`("filter of a path with a quote and a backslash")
            .contains("path = \"a\\\"b\\\\c\"")
    }

    @Test
    fun `should join the repositories of the access with or`() {
        // when: two repositories are visible
        val filter = MeilisearchFilters.access(
            SearchAccess(listOf(RepositoryAccess("releases", PathPredicate.All), RepositoryAccess("snapshots", PathPredicate.All))),
        )

        // then: a row of either repository matches
        assertThat(filter).`as`("filter of two repositories").isEqualTo("((repository = \"releases\") OR (repository = \"snapshots\"))")
    }

    @Test
    fun `should return no filter for an access without repositories`() {
        // when / then: nothing is visible, so the caller answers without a request
        assertThat(MeilisearchFilters.access(SearchAccess.NONE)).`as`("filter of an empty access").isNull()
    }

    @Test
    fun `should render the first match rule as the allowed condition and not the earlier ones`() {
        // given: the first rule denies the subtree, the second allows everything
        val predicate = PathPredicate.Precedence(
            listOf(
                PrecedenceRule(PathPredicate.Exact("secret"), allowed = false),
                PrecedenceRule(PathPredicate.All, allowed = true),
            ),
        )

        // when / then: an allowed row is one that the denial does not match
        assertThat(MeilisearchFilters.access(access(predicate)))
            .`as`("filter of a denial followed by an allowance")
            .isEqualTo(
                "(repository = \"releases\" AND (id EXISTS AND NOT ((path = \"secret\" OR directories = \"secret\"))))",
            )
    }

    @Test
    fun `should refuse a legacy grant with its reason and send no request`() {
        // when / then: a plain string prefix cannot be expressed in a filter
        assertThatThrownBy { MeilisearchFilters.access(access(PathPredicate.Legacy("com/ex"))) }
            .isInstanceOf(SearchUnavailableException::class.java)
            .hasMessage("legacy grants are not supported by the meilisearch backend")
    }

    @Test
    fun `should render the directories of a path as every ancestor directory`() {
        // when / then: the ancestors of a jar in a version directory, from the top down
        assertThat(MeilisearchFilters.directoriesOf("com/example/app/1.0/app-1.0.jar"))
            .`as`("ancestor directories of the path")
            .containsExactly("com", "com/example", "com/example/app", "com/example/app/1.0")
    }

}
