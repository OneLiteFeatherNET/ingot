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
package net.onelitefeather.ingot.opensearch.query

import net.onelitefeather.ingot.search.access.FixtureRow
import net.onelitefeather.ingot.search.access.PathPredicate
import net.onelitefeather.ingot.search.access.PrecedenceRule
import net.onelitefeather.ingot.search.access.RepositoryAccess
import net.onelitefeather.ingot.search.access.SearchAccess
import net.onelitefeather.ingot.search.access.SearchAccessCompiler
import net.onelitefeather.ingot.search.access.SearchAccessFixtures
import net.onelitefeather.ingot.search.access.SubjectCase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * The access filter of the OpenSearch query returns the rows that [SearchAccess.matches] returns, which the database engine
 * is pinned to (artifact search spec, 5.7). Each test evaluates the translated filter on fixed rows and compares the result.
 */
internal class OpenSearchAccessTranslationTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("net.onelitefeather.ingot.search.access.SearchAccessFixtures#subjects")
    fun `the translated access returns the rows the search access returns for each parity caller`(case: SubjectCase) {
        val access = SearchAccessCompiler.compile(case.subject, SearchAccessFixtures.repositories)

        assertTranslationAgrees(access, "caller '${case.name}'")
    }

    @Test
    fun `an all path shows every row of its repository`() {
        val access = SearchAccess(listOf(RepositoryAccess("releases", PathPredicate.All)))

        assertThat(visiblePaths(access, "releases"))
            .describedAs("every path of the repository must be visible")
            .containsExactlyElementsOf(SearchAccessFixtures.paths)
    }

    @Test
    fun `an all path hides the rows of the repositories it does not name`() {
        val access = SearchAccess(listOf(RepositoryAccess("releases", PathPredicate.All)))

        assertThat(visiblePaths(access, "private"))
            .describedAs("rows of another repository must stay hidden")
            .isEmpty()
    }

    @Test
    fun `an exact path shows the path itself and the paths below it`() {
        val access = SearchAccess(listOf(RepositoryAccess("releases", PathPredicate.Exact("com/example"))))

        assertThat(visiblePaths(access, "releases"))
            .describedAs("exact path must match the path and its children, not a sibling with the same text")
            .contains("com/example", "com/example/", "com/example/app/1.0/app-1.0.jar")
            .doesNotContain("com/examples/app/1.0/app-1.0.jar")
    }

    @Test
    fun `a subtree shows the artifacts below its segments and not the ones with a longer name`() {
        val access = SearchAccess(listOf(RepositoryAccess("releases", PathPredicate.Subtree(listOf("net", "onelitefeather")))))

        assertThat(visiblePaths(access, "releases"))
            .describedAs("subtree must compare whole segments")
            .contains("net/onelitefeather/mycelium/2.0/mycelium-2.0.jar")
            .doesNotContain("net/onelitefeatherX/lib/1.0/lib-1.0.jar")
    }

    @Test
    fun `a legacy path matches its text prefix ignoring case and without a segment boundary`() {
        val access = SearchAccess(listOf(RepositoryAccess("releases", PathPredicate.Legacy("com/example"))))

        assertThat(visiblePaths(access, "releases"))
            .describedAs("legacy prefix must ignore case and cross segment boundaries")
            .contains("com/examples/app/1.0/app-1.0.jar", "com/Example/app/1.0/app-1.0.jar")
            .doesNotContain("org/other/lib/1.0/lib-1.0.jar")
    }

    @Test
    fun `a conjunction shows only the rows that every part holds`() {
        val conjunction = PathPredicate.Conjunction(
            listOf(PathPredicate.Subtree(listOf("net", "onelitefeather")), PathPredicate.Exact("net/onelitefeather/mycelium")),
        )
        val access = SearchAccess(listOf(RepositoryAccess("private", conjunction)))

        assertThat(visiblePaths(access, "private"))
            .describedAs("only the rows inside both the subtree and the exact path are visible")
            .containsExactly("net/onelitefeather/mycelium/2.0/mycelium-2.0.jar")
    }

    @Test
    fun `a never path shows no row of its repository`() {
        val access = SearchAccess(listOf(RepositoryAccess("private", PathPredicate.Never)))

        assertThat(visiblePaths(access, "private"))
            .describedAs("a never path must hide every row")
            .isEmpty()
    }

    @Test
    fun `an empty access value hides every row`() {
        assertThat(visiblePaths(SearchAccess.NONE, "releases"))
            .describedAs("the value of a caller that sees nothing must hide every row")
            .isEmpty()
    }

    @Test
    fun `a precedence list hides the path that an earlier denial covers even when a later allow covers it too`() {
        val precedence = PathPredicate.Precedence(
            listOf(
                PrecedenceRule(PathPredicate.Subtree(listOf("net", "onelitefeather", "mycelium-bom")), allowed = false),
                PrecedenceRule(PathPredicate.Subtree(listOf("net", "onelitefeather")), allowed = true),
            ),
        )
        val access = SearchAccess(listOf(RepositoryAccess("private", precedence)))

        assertThat(visiblePaths(access, "private"))
            .describedAs("the denial comes first, so the mycelium-bom artifact must stay hidden")
            .doesNotContain("net/onelitefeather/mycelium-bom/1.0/mycelium-bom-1.0.jar")
            .contains("net/onelitefeather/mycelium/2.0/mycelium-2.0.jar")
    }

    private fun assertTranslationAgrees(access: SearchAccess, subject: String) {
        val filter = OpenSearchQueries.accessFilter(access)
        val disagreements = SearchAccessFixtures.rows.filter { row: FixtureRow ->
            OpenSearchQueryEvaluator.matches(filter, OpenSearchQueryEvaluator.document(row.repository, row.path)) !=
                access.matches(row.repository, row.path)
        }

        assertThat(disagreements)
            .describedAs("rows where the translated access and SearchAccess.matches disagree for $subject")
            .isEmpty()
    }

    private fun visiblePaths(access: SearchAccess, repository: String): List<String> {
        val filter = OpenSearchQueries.accessFilter(access)
        return SearchAccessFixtures.paths.filter { path ->
            OpenSearchQueryEvaluator.matches(filter, OpenSearchQueryEvaluator.document(repository, path))
        }
    }

}
