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

import net.onelitefeather.ingot.identity.model.NodePattern

/**
 * What one caller may see in the artifact search index (artifact search spec, 5.2). The value holds no evaluator types, so
 * an external engine can translate it without depending on the identity model. A caller gets one value per request,
 * compiled by [SearchAccessCompiler]; a repository missing from [repositories] is not visible at all.
 */
data class SearchAccess(val repositories: List<RepositoryAccess>) {

    /** The in-memory form of the row filter: whether a row of [repository] at [path] is returned. */
    fun matches(repository: String, path: String): Boolean =
        repositories.any { it.repository == repository && it.path.matches(path) }

    companion object {
        /** The value of a caller that sees nothing. */
        val NONE: SearchAccess = SearchAccess(emptyList())
    }
}

/** The rows of one repository the caller may see, as a predicate over the path inside that repository. */
data class RepositoryAccess(val repository: String, val path: PathPredicate)

/**
 * A condition on the path inside a repository. Every variant names how it is rendered to SQL and how it is evaluated in
 * memory, and the two agree on normalised paths, which is all the index stores.
 */
sealed interface PathPredicate {

    /** Every path. */
    data object All : PathPredicate

    /** No path. */
    data object Never : PathPredicate

    /** The path equals [prefix] or lies below it. Case-sensitive and segment-aware, as a new-model path grant. */
    data class Exact(val prefix: String) : PathPredicate

    /** The normalised path starts with [segments]: an artifact subtree (groupId and artifactId). Empty matches every path. */
    data class Subtree(val segments: List<String>) : PathPredicate

    /** The path starts with [prefix] ignoring case, with no segment boundary: a legacy route, kept on purpose (2.10). */
    data class Legacy(val prefix: String) : PathPredicate

    /** Every part holds. */
    data class Conjunction(val parts: List<PathPredicate>) : PathPredicate

    /**
     * Ordered decisions, the first rule whose condition holds decides. A path no condition holds for is denied. The rules
     * are ordered by the precedence of the evaluator: the most specific rule first, a denial before an allow of the same
     * specificity, and the closest precedence level before the farther ones.
     */
    data class Precedence(val rules: List<PrecedenceRule>) : PathPredicate
}

/** One step of [PathPredicate.Precedence]: when [condition] holds for the path, the path is [allowed] or denied. */
data class PrecedenceRule(val condition: PathPredicate, val allowed: Boolean)

/**
 * The in-memory form of a condition on a normalised path. It has the meaning of the SQL rendering: a [PathPredicate.Subtree]
 * compares segments, the other variants compare text as their rendering does.
 */
internal fun PathPredicate.matches(path: String): Boolean = when (this) {
    PathPredicate.All -> true
    PathPredicate.Never -> false
    is PathPredicate.Exact -> path == prefix || path.startsWith("$prefix/")
    is PathPredicate.Subtree -> NodePattern.segmentsOf(path)
        ?.let { it.size >= segments.size && it.subList(0, segments.size) == segments }
        ?: false
    is PathPredicate.Legacy -> path.startsWith(prefix, ignoreCase = true)
    is PathPredicate.Conjunction -> parts.all { it.matches(path) }
    is PathPredicate.Precedence -> rules.firstOrNull { it.condition.matches(path) }?.allowed ?: false
}
