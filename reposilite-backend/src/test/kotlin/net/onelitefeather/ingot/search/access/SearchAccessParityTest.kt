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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * The in-memory row filter agrees with the permission evaluator (artifact search spec, 5.7). For every caller and every
 * fixed row, [SearchAccess.matches] returns what [PermissionEvaluator.allows] decides for READ on the row's path.
 * Each caller is compiled from its own fixture, so the cases share no state.
 */
internal class SearchAccessParityTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("net.onelitefeather.ingot.search.access.SearchAccessFixtures#subjects")
    fun `the row filter returns exactly the rows the evaluator allows to browse`(case: SubjectCase) {
        val access = SearchAccessCompiler.compile(case.subject, SearchAccessFixtures.repositories)

        val disagreements = SearchAccessFixtures.rows.filter { row ->
            access.matches(row.repository, row.path) != SearchAccessFixtures.evaluatorAllows(case.subject, row)
        }

        assertThat(disagreements)
            .describedAs("rows where the row filter and the evaluator disagree for '${case.name}'")
            .isEmpty()
    }

}
