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

import net.onelitefeather.ingot.search.access.PathPredicate
import net.onelitefeather.ingot.search.access.PrecedenceRule
import net.onelitefeather.ingot.search.access.SearchAccess
import org.jetbrains.exposed.v1.core.Case
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.intLiteral
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import java.util.Locale

/**
 * The row filter of [SearchAccess] as an Exposed condition over `search_artifact` (artifact search spec, 5.3). Every query
 * of the search engine starts with this condition. A repository with every path is one `IN` list; every other repository is
 * its name and its path condition. The path columns are `path`, for exact and subtree conditions, and `path_lc`, for legacy
 * text prefixes, which is what the index ranges on (2.10).
 */
internal fun SearchAccess.toCondition(table: SearchArtifactTable): Op<Boolean> {
    val blocks = mutableListOf<Op<Boolean>>()
    val wholeRepositories = repositories.filter { it.path == PathPredicate.All }.map { it.repository }
    if (wholeRepositories.isNotEmpty()) blocks += table.repository inList wholeRepositories
    repositories.filter { it.path != PathPredicate.All }.forEach { access ->
        blocks += (table.repository eq access.repository) and access.path.toOp(table)
    }
    return if (blocks.isEmpty()) Op.FALSE else blocks.reduce { acc, block -> acc or block }
}

private fun PathPredicate.toOp(table: SearchArtifactTable): Op<Boolean> = when (this) {
    PathPredicate.All -> Op.TRUE
    PathPredicate.Never -> Op.FALSE
    is PathPredicate.Exact -> segmentPrefix(table, prefix)
    is PathPredicate.Subtree -> if (segments.isEmpty()) Op.TRUE else segmentPrefix(table, segments.joinToString("/"))
    is PathPredicate.Legacy -> textPrefix(table, prefix)
    is PathPredicate.Conjunction -> parts.map { it.toOp(table) }.reduce { acc, part -> acc and part }
    is PathPredicate.Precedence -> precedence(table, rules)
}

/** The path equals [prefix] or lies below it: `path = p OR (path >= p/ AND path < p0)`, half-open as section 2.10 has it. */
private fun segmentPrefix(table: SearchArtifactTable, prefix: String): Op<Boolean> =
    (table.path eq prefix) or ((table.path greaterEq "$prefix/") and (table.path less "${prefix}0"))

/**
 * The path starts with [prefix] ignoring case, with no segment boundary, on the lower-cased column. The upper bound is the
 * prefix with its last character raised by one; a prefix ending in the largest character has no upper bound.
 */
private fun textPrefix(table: SearchArtifactTable, prefix: String): Op<Boolean> {
    val lower = prefix.lowercase(Locale.ROOT)
    val last = lower.last()
    if (last == Char.MAX_VALUE) return table.pathLc greaterEq lower
    val upper = lower.dropLast(1) + (last + 1)
    return (table.pathLc greaterEq lower) and (table.pathLc less upper)
}

/**
 * The first rule whose condition holds decides, a path no rule covers is denied. Rendered as one CASE that yields 1 for an
 * allowed path and 0 otherwise, so the size grows with the rules and not with their pairwise combinations.
 */
private fun precedence(table: SearchArtifactTable, rules: List<PrecedenceRule>): Op<Boolean> {
    if (rules.isEmpty()) return Op.FALSE
    val first = rules.first()
    val cases = rules.drop(1).fold(Case().When(first.condition.toOp(table), verdict(first))) { acc, rule ->
        acc.When(rule.condition.toOp(table), verdict(rule))
    }
    return cases.Else(intLiteral(0)) eq 1
}

private fun verdict(rule: PrecedenceRule) = intLiteral(if (rule.allowed) 1 else 0)
