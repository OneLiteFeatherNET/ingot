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

import net.onelitefeather.ingot.identity.model.AccessSubject
import net.onelitefeather.ingot.identity.model.Action
import net.onelitefeather.ingot.identity.model.DecisionSource
import net.onelitefeather.ingot.identity.model.Grant
import net.onelitefeather.ingot.identity.model.NodePattern
import net.onelitefeather.ingot.identity.model.SourcedRule

/** What a request asks for: a full path as `/repository/path/in/repository`, and the action. */
data class AccessRequest(val fullPath: String, val action: Action)

/**
 * One rule or grant the decision looked at. [matched] says whether it covers the requested node; [specificity] is the
 * literal count that orders the matches of a level.
 */
data class Candidate(
    val source: DecisionSource,
    val subject: String,
    val depth: Int,
    val node: String,
    val allowed: Boolean,
    val matched: Boolean,
    val reason: String,
)

/** The answer for one node, with every candidate of every level it consulted. */
data class Decision(
    val source: DecisionSource,
    val subject: String?,
    val node: String,
    val allowed: Boolean,
    val depth: Int,
    val trace: List<Candidate>,
)

/**
 * Decides what a subject may do. Pure: it reads nothing but its arguments.
 *
 * The rules are those of the permission model (identity model, permission nodes, decisions D3 to D6). The sources are
 * consulted in precedence order. Within the first source that holds a matching rule, the most specific match decides, and
 * a denial wins a tie. No matching rule denies. A token with its own node list is then applied as an intersection, so it can
 * only remove rights. Legacy route grants and path grants take part at the level of their team, with the matching rules
 * they had before, so migrated tokens keep their exact rights.
 */
object PermissionEvaluator {

    /** Whether the request is allowed. Disabled subjects are denied everything. */
    fun allows(subject: AccessSubject, request: AccessRequest): Boolean {
        if (subject.disabled) return false
        if (request.action == Action.INSTANCE_ADMIN) return isInstanceAdmin(subject)

        val (repository, pathInRepository) = splitPath(request.fullPath)
        val node = NodePattern.repository(repository, request.action.repositoryNodeAction())
        if (!tokenAllows(subject, node)) return false
        if (isInstanceAdmin(subject)) return true

        return ownerDecision(subject, node) { grant -> pathGrantMatch(grant, request, repository, pathInRepository) }.allowed
    }

    /**
     * Whether the subject is an instance administrator: it is allowed `ingot.admin.*`, through its own nodes or the nodes of
     * its teams, and its token narrowing (if any) allows it too. The former instance role and the legacy MANAGER permission.
     */
    fun isInstanceAdmin(subject: AccessSubject): Boolean {
        if (subject.disabled) return false
        return ownerDecision(subject, NodePattern.INSTANCE_ADMIN) { GrantMatch(false, "no grant covers instance admin") }.allowed &&
            tokenAllows(subject, NodePattern.INSTANCE_ADMIN)
    }

    /**
     * Decides one concrete node, for the permission check of the identity API. Grants count only where they cover the whole
     * repository: a path-scoped grant has no answer for a node, and the trace says so.
     */
    fun decide(subject: AccessSubject, node: NodePattern): Decision {
        if (subject.disabled) {
            return Decision(DecisionSource.DEFAULT, null, node.text, allowed = false, depth = 0, trace = emptyList())
        }
        val owner = ownerDecision(subject, node) { grant -> nodeGrantMatch(grant, node, includeLegacy = true) }
        val tokenRules = subject.tokenRules ?: return owner
        val token = decideRules(DecisionSource.TOKEN, subject.principalName.orEmpty(), 0, tokenRules, node)
        val trace = owner.trace + token.trace
        return when {
            !owner.allowed -> owner.copy(trace = trace)
            !token.allowed -> token.copy(trace = trace)
            else -> owner.copy(trace = trace)
        }
    }

    /**
     * Whether the subject may see the repository named by [fragment], a repository fragment such as `/releases`. The legacy
     * part is the old `AccessTokenFacade.canSee`, unchanged. Nodes and path grants add visibility on top of it.
     */
    fun canSee(subject: AccessSubject, fragment: String): Boolean {
        if (subject.disabled) return false
        val repository = fragment.removePrefix("/")
        if (!tokenAllows(subject, NodePattern.repository(repository, "read"))) return false
        if (isInstanceAdmin(subject)) return true
        if (subject.levels.any { level -> level.grants.any { grantReaches(it, fragment) } }) return true

        return ownerDecision(subject, NodePattern.repository(repository, "read")) { grant ->
            nodeGrantMatch(grant, NodePattern.repository(repository, "read"), includeLegacy = true)
        }.allowed
    }

    /**
     * Whether a grant reaches the repository of [fragment] at all, whatever its actions. Used by [canSee] and by the access
     * explanation of the identity API, so both answer the same question.
     */
    fun grantReaches(grant: Grant, fragment: String): Boolean =
        if (grant.role.legacy) {
            val path = grant.pathPrefix
            path != null && (path.startsWith(fragment, ignoreCase = true) || fragment.startsWith(path))
        } else {
            grant.repository == null || grant.repository == fragment.removePrefix("/")
        }

    /**
     * Whether a HIDDEN repository is listed for the subject: instance admins, and subjects that may read it through a node or
     * a path grant of role READ or higher. Legacy grants never list a HIDDEN repository, as before.
     */
    fun canListHidden(subject: AccessSubject, repository: String): Boolean {
        if (subject.disabled) return false
        val read = NodePattern.repository(repository, "read")
        if (!tokenAllows(subject, read)) return false
        if (isInstanceAdmin(subject)) return true
        if (ownerDecision(subject, read) { grant -> nodeGrantMatch(grant, read, includeLegacy = false) }.allowed) return true

        return subject.levels.any { level ->
            level.grants.any { grant ->
                !grant.role.legacy && Action.READ in grant.role.actions && (grant.repository == null || grant.repository == repository)
            }
        }
    }

    /**
     * The node patterns the subject is allowed, for the user interface to gate its views. Every distinct rule pattern is
     * decided as if it were requested, token narrowing included. The exact answer for one node is [decide].
     */
    fun effectiveNodes(subject: AccessSubject): List<String> {
        if (subject.disabled) return emptyList()
        return subject.levels.flatMap { level -> level.rules.map { it.pattern.text } }
            .plus(subject.tokenRules.orEmpty().map { it.pattern.text })
            .distinct()
            .filter { text -> decide(subject, NodePattern.of(text)).allowed }
            .sorted()
    }

    // internals

    /** The answer of the rules of the owner, without the token. */
    private fun ownerDecision(subject: AccessSubject, node: NodePattern, grantMatch: (Grant) -> GrantMatch): Decision {
        val trace = mutableListOf<Candidate>()
        var decided: Decision? = null
        for (level in subject.levels) {
            val scored = mutableListOf<Scored>()
            level.rules.forEach { rule ->
                val matched = rule.pattern.matches(node)
                val candidate = Candidate(
                    source = level.source,
                    subject = rule.subject,
                    depth = level.depth,
                    node = rule.pattern.text,
                    allowed = rule.allowed,
                    matched = matched,
                    reason = if (matched) "matches with specificity ${rule.pattern.specificity}" else "does not match ${node.text}",
                )
                trace += candidate
                if (matched) scored += Scored(candidate, rule.pattern.specificity)
            }
            level.grants.forEach { grant ->
                val match = grantMatch(grant)
                val source = if (grant.role.legacy) DecisionSource.LEGACY else level.source
                val candidate = Candidate(
                    source = source,
                    subject = grant.team,
                    depth = level.depth,
                    node = node.text,
                    allowed = true,
                    matched = match.matched,
                    reason = match.reason,
                )
                trace += candidate
                if (match.matched) scored += Scored(candidate, node.specificity)
            }
            if (decided == null && scored.isNotEmpty()) {
                decided = decideLevel(level.depth, scored, node)
            }
        }
        return decided?.copy(trace = trace) ?: Decision(DecisionSource.DEFAULT, null, node.text, allowed = false, depth = 0, trace = trace)
    }

    /** Decides a set of rules as one level, for the token. */
    private fun decideRules(source: DecisionSource, subject: String, depth: Int, rules: List<SourcedRule>, node: NodePattern): Decision {
        val trace = rules.map { rule ->
            Candidate(
                source = source,
                subject = subject,
                depth = depth,
                node = rule.pattern.text,
                allowed = rule.allowed,
                matched = rule.pattern.matches(node),
                reason = if (rule.pattern.matches(node)) "token node matches with specificity ${rule.pattern.specificity}" else "does not match ${node.text}",
            )
        }
        val scored = trace.zip(rules).filter { (candidate, _) -> candidate.matched }
            .map { (candidate, rule) -> Scored(candidate, rule.pattern.specificity) }
        val decision = if (scored.isEmpty()) {
            Decision(source, subject, node.text, allowed = false, depth = depth, trace = emptyList())
        } else {
            decideLevel(depth, scored, node).copy(source = source)
        }
        return decision.copy(trace = trace)
    }

    /** The most specific matches decide. A denial among them wins the tie. */
    private fun decideLevel(depth: Int, scored: List<Scored>, node: NodePattern): Decision {
        val best = scored.maxOf { it.specificity }
        val winners = scored.filter { it.specificity == best }
        val denial = winners.firstOrNull { !it.candidate.allowed }
        val winner = denial ?: winners.first()
        return Decision(
            source = winner.candidate.source,
            subject = winner.candidate.subject,
            node = node.text,
            allowed = denial == null,
            depth = depth,
            trace = emptyList(),
        )
    }

    /** Whether the token narrows its owner away from the node. A token without a node list narrows nothing. */
    private fun tokenAllows(subject: AccessSubject, node: NodePattern): Boolean {
        val rules = subject.tokenRules ?: return true
        return decideRules(DecisionSource.TOKEN, subject.principalName.orEmpty(), 0, rules, node).allowed
    }

    private class Scored(val candidate: Candidate, val specificity: Int)

    /** Whether a grant covers the requested node, with the reason a client can read in the trace. */
    private data class GrantMatch(val matched: Boolean, val reason: String)

    /** A grant as a node: it covers a repository action of its repository when it is repository-wide. */
    private fun nodeGrantMatch(grant: Grant, node: NodePattern, includeLegacy: Boolean): GrantMatch {
        val repository = node.repository ?: return GrantMatch(false, "a grant covers repository nodes only")
        val action = node.action?.let(::nodeAction) ?: return GrantMatch(false, "a grant covers repository nodes only")
        if (grant.role.legacy && !includeLegacy) return GrantMatch(false, "legacy grants never list this node")
        if (action !in grant.role.actions) return GrantMatch(false, "role ${grant.role.name} does not include ${node.action}")
        if (grant.repository != null && grant.repository != repository) return GrantMatch(false, "grant is for repository ${grant.repository}")
        if (grant.role.legacy) {
            val route = grant.pathPrefix?.trim('/').orEmpty()
            return if (route.isEmpty() || route.equals(repository, ignoreCase = true)) {
                GrantMatch(true, "legacy route covers the whole repository")
            } else {
                GrantMatch(false, "legacy route is path-scoped, use a path check")
            }
        }
        return if (grant.pathPrefix.isNullOrBlank()) {
            GrantMatch(true, "role ${grant.role.name} covers the whole repository")
        } else {
            GrantMatch(false, "path-scoped grant, use a path check")
        }
    }

    /** A grant as a path: the old legacy rule, or a new role within its repository and path prefix. */
    private fun pathGrantMatch(grant: Grant, request: AccessRequest, repository: String, pathInRepository: String): GrantMatch {
        if (request.action !in grant.role.actions) return GrantMatch(false, "role does not include ${request.action}")
        val matched = if (grant.role.legacy) {
            // Legacy routes match the full path by plain case-insensitive prefix, without a segment boundary.
            val prefix = grant.pathPrefix
            prefix != null && request.fullPath.startsWith(prefix, ignoreCase = true)
        } else {
            (grant.repository == null || grant.repository == repository) && withinPrefix(grant.pathPrefix, pathInRepository)
        }
        return GrantMatch(matched, if (matched) "path grant of ${grant.role.name} covers the path" else "path grant does not cover the path")
    }

    /** A new-model prefix matches on whole segments, case-sensitively. A missing or root prefix covers the repository. */
    private fun withinPrefix(prefix: String?, pathInRepository: String): Boolean {
        val normalized = prefix?.trim('/').orEmpty()
        if (normalized.isEmpty()) return true
        return pathInRepository == normalized || pathInRepository.startsWith("$normalized/")
    }

    /** Maps the node action of a repository node to the action the role table uses. */
    private fun nodeAction(action: String): Action? =
        when (action) {
            "read" -> Action.READ
            "deploy" -> Action.WRITE
            "delete" -> Action.DELETE
            "manage" -> Action.MANAGE_REPOSITORY
            else -> null
        }

    /** The repository node action of a request action. */
    private fun Action.repositoryNodeAction(): String =
        when (this) {
            Action.READ -> "read"
            Action.WRITE -> "deploy"
            Action.DELETE -> "delete"
            Action.MANAGE_REPOSITORY -> "manage"
            Action.INSTANCE_ADMIN -> error("INSTANCE_ADMIN is not a repository action")
        }

    /** Splits `/repository/path/in/repository` into the repository and the path inside it. */
    internal fun splitPath(fullPath: String): Pair<String, String> {
        val trimmed = fullPath.removePrefix("/")
        return trimmed.substringBefore('/') to trimmed.substringAfter('/', "")
    }
}
