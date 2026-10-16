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
package net.onelitefeather.ingot.identity.evaluation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** Cycles and the depth limit of team inheritance (identity model, permission nodes, decision D3). Pure, no storage. */
internal class TeamHierarchyTest {

    @Test
    fun `an empty graph has no problem`() {
        assertThat(TeamHierarchy.problemOf(emptyMap())).describedAs("problem").isNull()
    }

    @Test
    fun `a team cannot inherit from itself`() {
        assertThat(TeamHierarchy.problemOf(mapOf(1 to setOf(1)))).isEqualTo(HierarchyProblem.Cycle(1))
    }

    @Test
    fun `a cycle through two teams is found`() {
        assertThat(TeamHierarchy.problemOf(mapOf(1 to setOf(2), 2 to setOf(1)))).isInstanceOf(HierarchyProblem.Cycle::class.java)
    }

    @Test
    fun `a cycle further up the chain is found`() {
        val graph = mapOf(1 to setOf(2), 2 to setOf(3), 3 to setOf(4), 4 to setOf(2))
        assertThat(TeamHierarchy.problemOf(graph)).isInstanceOf(HierarchyProblem.Cycle::class.java)
    }

    @Test
    fun `a chain of exactly the maximum depth is accepted`() {
        val graph = (1..8).associateWith { setOf(it + 1) }
        assertThat(TeamHierarchy.problemOf(graph)).describedAs("eight edges").isNull()
    }

    @Test
    fun `a chain one level deeper than the maximum is refused`() {
        val graph = (1..9).associateWith { setOf(it + 1) }
        assertThat(TeamHierarchy.problemOf(graph)).isEqualTo(HierarchyProblem.TooDeep(teamId = 1, depth = 9))
    }

    @Test
    fun `depth is the longest chain, so a long branch of a diamond counts`() {
        // 1 has two parents: 2 (one edge to the top) and 3, which leads to a chain of nine edges through 4..12.
        val graph = mapOf(1 to setOf(2, 3)) + (3..11).associateWith { setOf(it + 1) }
        assertThat(TeamHierarchy.problemOf(graph)).isInstanceOf(HierarchyProblem.TooDeep::class.java)
    }

    @Test
    fun `a diamond within the limit is accepted`() {
        val graph = mapOf(1 to setOf(2, 3), 2 to setOf(4), 3 to setOf(4))
        assertThat(TeamHierarchy.problemOf(graph)).isNull()
    }

    @Test
    fun `levels group the inherited teams by distance, nearest first`() {
        val levels = TeamHierarchy.levelsFrom(setOf(1), mapOf(1 to setOf(2, 3), 2 to setOf(4), 3 to setOf(4)))

        assertThat(levels).containsExactly(0 to setOf(1), 1 to setOf(2, 3), 2 to setOf(4))
    }

    @Test
    fun `a team reached by a short and a long path lands on the short distance`() {
        val levels = TeamHierarchy.levelsFrom(setOf(1), mapOf(1 to setOf(2, 3), 2 to setOf(3)))

        assertThat(levels).containsExactly(0 to setOf(1), 1 to setOf(2, 3))
    }

    @Test
    fun `levels stop at the maximum depth`() {
        val parents = (1..12).associateWith { setOf(it + 1) }

        val levels = TeamHierarchy.levelsFrom(setOf(1), parents)

        assertThat(levels.last().first).describedAs("deepest level").isEqualTo(TeamHierarchy.MAX_DEPTH)
    }
}
