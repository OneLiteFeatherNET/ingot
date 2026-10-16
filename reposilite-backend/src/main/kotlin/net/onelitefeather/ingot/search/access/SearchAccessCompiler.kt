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

import com.reposilite.maven.RepositoryVisibility
import net.onelitefeather.ingot.identity.evaluation.PermissionEvaluator
import net.onelitefeather.ingot.identity.model.AccessSubject
import net.onelitefeather.ingot.identity.model.Action
import net.onelitefeather.ingot.identity.model.Grant
import net.onelitefeather.ingot.identity.model.NodePattern
import net.onelitefeather.ingot.identity.model.NodeSpecificity
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.model.SourcedRule

/**
 * Compiles the grants of one caller into a [SearchAccess] (artifact search spec, 5.3). The compiler reads the subject the
 * evaluator reads, never the tables, and it decides the same question as [PermissionEvaluator.allows] for READ on every
 * row: the token narrows first, an instance admin then sees everything the token allows, and everyone else needs the
 * owner's rules and grants, decided level by level with the most specific rule first and a denial before an allow of
 * the same specificity. A null subject is the anonymous caller.
 */
internal object SearchAccessCompiler {

    /** The most terms one repository may carry before the request is refused (5.3). */
    const val MAX_TERMS_PER_REPOSITORY: Int = 200

    /** The most terms of all repositories together before the request is refused (5.3). */
    const val MAX_TERMS_TOTAL: Int = 1000

    private val ANONYMOUS = AccessSubject(
        principalId = null,
        principalName = null,
        kind = PrincipalKind.USER,
        disabled = false,
        levels = emptyList(),
        tokenRules = null,
    )

    /** An item of a precedence list: its specificity orders it, its rule decides the paths it covers. */
    private class Item(val specificity: NodeSpecificity, val rule: PrecedenceRule)

    /** The most specific item first; of equal specificity a denial first, so it wins the tie (the evaluator's rule). */
    private val BY_PRECEDENCE: Comparator<Item> =
        compareByDescending<Item> { it.specificity }.thenBy { if (it.rule.allowed) 1 else 0 }

    /** The row filter of [subject] over [repositories], keyed by repository name with its visibility. */
    fun compile(subject: AccessSubject?, repositories: Map<String, RepositoryVisibility>): SearchAccess {
        val caller = subject ?: ANONYMOUS
        if (caller.disabled) return SearchAccess.NONE

        val admin = PermissionEvaluator.isInstanceAdmin(caller)
        val entries = repositories.mapNotNull { (repository, visibility) ->
            val path = if (visibility == RepositoryVisibility.PUBLIC) PathPredicate.All else restrictedPath(caller, repository, admin)
            if (path == PathPredicate.Never) null else RepositoryAccess(repository, path)
        }
        enforceTermCap(entries)
        return SearchAccess(entries)
    }

    /** A HIDDEN or PRIVATE repository: the token narrows, and an instance admin needs nothing more. */
    private fun restrictedPath(caller: AccessSubject, repository: String, admin: Boolean): PathPredicate {
        val token = tokenPath(caller.tokenRules, repository)
        if (admin) return token
        return conjunction(token, ownerPath(caller, repository))
    }

    /** The node list of a token, one precedence level. No node list narrows nothing. */
    private fun tokenPath(rules: List<SourcedRule>?, repository: String): PathPredicate {
        if (rules == null) return PathPredicate.All
        return precedence(rules.mapNotNull { ruleItem(it, repository) }.sortedWith(BY_PRECEDENCE))
    }

    /** The owner's rules and grants. Each level is ordered on its own; the levels follow each other, nearest first. */
    private fun ownerPath(caller: AccessSubject, repository: String): PathPredicate {
        val items = caller.levels.flatMap { level ->
            val levelItems = level.rules.mapNotNull { ruleItem(it, repository) } + level.grants.mapNotNull { grantItem(it, repository) }
            levelItems.sortedWith(BY_PRECEDENCE)
        }
        return precedence(items)
    }

    /**
     * An artifact or repository node as an item for READ on [repository]. An artifact node covers its subtree; a repository
     * node covers every path, and a node of another repository, or an admin node, covers nothing.
     */
    private fun ruleItem(rule: SourcedRule, repository: String): Item? {
        val pattern = rule.pattern
        val covered = pattern.coveredSegments
        val condition = when {
            covered != null -> if (pattern.isArtifactNodeFor(repository, "read")) PathPredicate.Subtree(covered) else return null
            pattern.matches(NodePattern.repository(repository, "read")) -> PathPredicate.All
            else -> return null
        }
        return Item(pattern.rank, PrecedenceRule(condition, rule.allowed))
    }

    /** A role grant as an item. Only roles that include READ count; a grant is an allow with the specificity of a role. */
    private fun grantItem(grant: Grant, repository: String): Item? {
        if (Action.READ !in grant.role.actions) return null
        val condition = if (grant.role.legacy) {
            legacyCondition(grant.pathPrefix ?: return null, repository)
        } else {
            if (grant.repository != null && grant.repository != repository) return null
            newModelCondition(grant.pathPrefix)
        }
        if (condition == PathPredicate.Never) return null
        return Item(NodeSpecificity.ROLE_GRANT, PrecedenceRule(condition, allowed = true))
    }

    /** A new-model path grant: segment-aware and case-sensitive, a missing or root prefix covers the repository. */
    private fun newModelCondition(pathPrefix: String?): PathPredicate {
        val normalized = pathPrefix?.trim('/').orEmpty()
        return if (normalized.isEmpty()) PathPredicate.All else PathPredicate.Exact(normalized)
    }

    /**
     * A legacy route is matched against the full path (repository and path) by plain prefix, ignoring case and without a segment
     * boundary. The part of the route that names the repository is decided here, the rest stays a text prefix of the path.
     */
    private fun legacyCondition(route: String, repository: String): PathPredicate {
        val head = "/$repository/"
        return when {
            route.length <= head.length -> if (head.startsWith(route, ignoreCase = true)) PathPredicate.All else PathPredicate.Never
            route.substring(0, head.length).equals(head, ignoreCase = true) -> PathPredicate.Legacy(route.substring(head.length))
            else -> PathPredicate.Never
        }
    }

    /**
     * The first item whose condition holds decides. Items the first ones already cover can never decide and are dropped, so
     * the list keeps only what changes an answer. A list without any allow denies every path.
     */
    private fun precedence(items: List<Item>): PathPredicate {
        val kept = mutableListOf<PrecedenceRule>()
        for (item in items) {
            val condition = item.rule.condition
            if (condition == PathPredicate.Never || kept.any { covers(it.condition, condition) }) continue
            kept += item.rule
        }
        val first = kept.firstOrNull() ?: return PathPredicate.Never
        if (kept.none { it.allowed }) return PathPredicate.Never
        if (first.condition == PathPredicate.All) return if (first.allowed) PathPredicate.All else PathPredicate.Never
        return PathPredicate.Precedence(kept)
    }

    /** Whether every path that [specific] holds for is also held by [general]. Only comparable pairs of the same kind. */
    private fun covers(general: PathPredicate, specific: PathPredicate): Boolean = when {
        general == PathPredicate.All -> true
        general is PathPredicate.Exact && specific is PathPredicate.Exact ->
            specific.prefix == general.prefix || specific.prefix.startsWith("${general.prefix}/")
        general is PathPredicate.Subtree && specific is PathPredicate.Subtree ->
            specific.segments.size >= general.segments.size && specific.segments.subList(0, general.segments.size) == general.segments
        general is PathPredicate.Legacy && specific is PathPredicate.Legacy -> specific.prefix.startsWith(general.prefix, ignoreCase = true)
        else -> false
    }

    private fun conjunction(first: PathPredicate, second: PathPredicate): PathPredicate = when {
        first == PathPredicate.Never || second == PathPredicate.Never -> PathPredicate.Never
        first == PathPredicate.All -> second
        second == PathPredicate.All -> first
        else -> PathPredicate.Conjunction(listOf(first, second))
    }

    private fun enforceTermCap(entries: List<RepositoryAccess>) {
        val perRepository = entries.map { termsOf(it.path) }
        perRepository.forEach { terms ->
            if (terms > MAX_TERMS_PER_REPOSITORY) throw SearchGrantSetTooLargeException(terms, MAX_TERMS_PER_REPOSITORY)
        }
        val total = perRepository.sum()
        if (total > MAX_TERMS_TOTAL) throw SearchGrantSetTooLargeException(total, MAX_TERMS_TOTAL)
    }

    /** The number of path conditions a predicate carries, the unit of the term cap. */
    private fun termsOf(path: PathPredicate): Int = when (path) {
        PathPredicate.All, PathPredicate.Never -> 0
        is PathPredicate.Exact, is PathPredicate.Subtree, is PathPredicate.Legacy -> 1
        is PathPredicate.Conjunction -> path.parts.sumOf { termsOf(it) }
        is PathPredicate.Precedence -> path.rules.sumOf { termsOf(it.condition) }
    }

}

/**
 * The grants of a caller compile to more terms than the search accepts (artifact search spec, 5.3). The request fails with
 * 503 and the message "grant set too large for search"; the cap is a guard, far above any observed token.
 */
class SearchGrantSetTooLargeException(val terms: Int, val limit: Int) :
    RuntimeException("grant set too large for search ( terms, limit )")
