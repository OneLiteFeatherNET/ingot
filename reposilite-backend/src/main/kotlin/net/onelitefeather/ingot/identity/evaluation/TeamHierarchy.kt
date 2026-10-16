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

/** Why a set of team parents is refused. The team ids are the storage ids; the facade names them. */
sealed interface HierarchyProblem {
    /** A team inherits from itself, directly or through its parents. */
    data class Cycle(val teamId: Int) : HierarchyProblem

    /** A chain of inheritance is longer than [TeamHierarchy.MAX_DEPTH] levels above [teamId]. */
    data class TooDeep(val teamId: Int, val depth: Int) : HierarchyProblem
}

/**
 * Checks the inheritance graph between teams (identity model, permission nodes, decision D3). Pure: it reads nothing and
 * holds no state. Depth counts the inheritance edges from a team to its furthest ancestor, so a team with one parent
 * has depth 1, and a chain of eight edges is the deepest that is allowed.
 */
object TeamHierarchy {

    const val MAX_DEPTH: Int = 8

    /**
     * Returns the first problem in the graph, or null when every team is acyclic and no chain is deeper than [MAX_DEPTH].
     * [parentsByTeam] maps a team id to its parent ids; a missing key means no parents.
     */
    fun problemOf(parentsByTeam: Map<Int, Set<Int>>): HierarchyProblem? {
        val depths = HashMap<Int, Int>()
        val onPath = HashSet<Int>()
        val nodes = (parentsByTeam.keys + parentsByTeam.values.flatten()).sorted()
        for (team in nodes) {
            val problem = depthOf(team, parentsByTeam, depths, onPath)
            if (problem != null) return problem
        }
        return null
    }

    /**
     * The teams a principal reaches, grouped by distance: the direct teams at distance 0, their parents at 1, and so on up to
     * [MAX_DEPTH]. A team sits at the smallest distance it is reached at. Levels with no team are not listed, so the list
     * ends at the last distance that holds a team.
     */
    fun levelsFrom(direct: Set<Int>, parents: Map<Int, Set<Int>>): List<Pair<Int, Set<Int>>> {
        val levels = mutableListOf<Pair<Int, Set<Int>>>()
        val seen = HashSet<Int>()
        var frontier = direct
        var depth = 0
        while (frontier.isNotEmpty() && depth <= MAX_DEPTH) {
            val fresh = frontier.filter { seen.add(it) }.toSet()
            if (fresh.isEmpty()) break
            levels += depth to fresh
            frontier = fresh.flatMap { parents[it].orEmpty() }.toSet()
            depth++
        }
        return levels
    }

    /** Depth of [team] by a walk up its parents, or the problem found on the way. */
    private fun depthOf(
        team: Int,
        parentsByTeam: Map<Int, Set<Int>>,
        depths: MutableMap<Int, Int>,
        onPath: MutableSet<Int>,
    ): HierarchyProblem? {
        depths[team]?.let { known -> return if (known > MAX_DEPTH) HierarchyProblem.TooDeep(team, known) else null }
        if (!onPath.add(team)) return HierarchyProblem.Cycle(team)

        var depth = 0
        for (parent in parentsByTeam[team].orEmpty().sorted()) {
            val problem = depthOf(parent, parentsByTeam, depths, onPath)
            if (problem != null) return problem
            depth = maxOf(depth, depths.getValue(parent) + 1)
        }
        onPath.remove(team)
        depths[team] = depth
        return if (depth > MAX_DEPTH) HierarchyProblem.TooDeep(team, depth) else null
    }
}
