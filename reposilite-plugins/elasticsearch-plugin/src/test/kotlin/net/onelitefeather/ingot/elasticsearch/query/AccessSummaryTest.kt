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
package net.onelitefeather.ingot.elasticsearch.query

import net.onelitefeather.ingot.search.access.PathPredicate
import net.onelitefeather.ingot.search.access.RepositoryAccess
import net.onelitefeather.ingot.search.access.SearchAccess
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The access value that goes to the log: the counts only, never a path. */
internal class AccessSummaryTest {

    @Test
    fun `should describe the access value by its repository and term counts`() {
        val access = SearchAccess(
            listOf(
                RepositoryAccess("releases", PathPredicate.All),
                RepositoryAccess("private", PathPredicate.Exact("net/onelitefeather/secret")),
            ),
        )

        assertThat(ElasticsearchQueries.describe(access))
            .describedAs("the summary must count the repositories and the terms")
            .isEqualTo("repositories=2 terms=1")
    }

    @Test
    fun `should leave every path out of the description`() {
        val access = SearchAccess(listOf(RepositoryAccess("private", PathPredicate.Exact("net/onelitefeather/secret"))))

        assertThat(ElasticsearchQueries.describe(access))
            .describedAs("the description must not carry a path")
            .doesNotContain("net", "secret", "onelitefeather")
    }

}
