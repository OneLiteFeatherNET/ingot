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

import net.onelitefeather.ingot.identity.model.AccessLevel
import net.onelitefeather.ingot.identity.model.AccessSubject
import net.onelitefeather.ingot.identity.model.DecisionSource
import net.onelitefeather.ingot.identity.model.NodePattern
import net.onelitefeather.ingot.identity.model.NodeRule
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.model.SourcedRule
import java.time.Instant

/**
 * The last-admin rule (identity model, section 7.4 and decision D12). A usable admin is an enabled principal with an
 * unexpired persistent token whose node list allows `ingot.admin.*` (or has none), and that is an instance admin through its
 * own nodes or the nodes of its teams. Pure: the data and the clock are arguments, so every change can be simulated before
 * it is written.
 */
object AdminReachability {

    data class Principal(val id: Int, val disabled: Boolean)

    data class Membership(val teamId: Int, val principalId: Int)

    /** A persistent token. [nodes] is null when the token does not narrow its owner. */
    data class Token(val id: Int, val principalId: Int, val expiresAt: Instant?, val nodes: List<NodeRule>?)

    /** The data of the rule. Rules of principals and teams are keyed by their own ids. */
    data class Input(
        val principals: List<Principal>,
        val principalRules: Map<Int, List<NodeRule>>,
        val teamRules: Map<Int, List<NodeRule>>,
        val parents: Map<Int, Set<Int>>,
        val memberships: List<Membership>,
        val tokens: List<Token>,
    )

    /** The principal ids that are usable instance admins at [now]. */
    fun usableAdmins(input: Input, now: Instant): Set<Int> {
        val membershipsByPrincipal = input.memberships.groupBy { it.principalId }
        val tokensByPrincipal = input.tokens.groupBy { it.principalId }
        return input.principals
            .filter { !it.disabled }
            .filter { principal ->
                val owner = AccessSubject(
                    principalId = principal.id,
                    principalName = null,
                    kind = PrincipalKind.USER,
                    disabled = false,
                    levels = levelsOf(principal.id, input, membershipsByPrincipal[principal.id].orEmpty()),
                    tokenRules = null,
                )
                tokensByPrincipal[principal.id].orEmpty()
                    .filter { token -> token.expiresAt == null || !now.isAfter(token.expiresAt) }
                    .any { token -> PermissionEvaluator.isInstanceAdmin(owner.copy(tokenRules = tokenRulesOf(token.nodes))) }
            }
            .map { it.id }
            .toSet()
    }

    /**
     * The principal ids that are instance admins through their nodes, token narrowing aside. Disabled principals are not
     * admins. Read by the administration API for the admin flag of every row.
     */
    fun effectiveAdmins(input: Input): Set<Int> {
        val membershipsByPrincipal = input.memberships.groupBy { it.principalId }
        return input.principals
            .filter { principal ->
                PermissionEvaluator.isInstanceAdmin(
                    AccessSubject(
                        principalId = principal.id,
                        principalName = null,
                        kind = PrincipalKind.USER,
                        disabled = principal.disabled,
                        levels = levelsOf(principal.id, input, membershipsByPrincipal[principal.id].orEmpty()),
                        tokenRules = null,
                    ),
                )
            }
            .map { it.id }
            .toSet()
    }

    /** Whether the team is an instance admin through its own nodes or the nodes of the teams it inherits. */
    fun teamIsAdmin(teamId: Int, input: Input): Boolean {
        val levels = TeamHierarchy.levelsFrom(setOf(teamId), input.parents).map { (depth, ids) ->
            AccessLevel(
                source = if (depth == 0) DecisionSource.TEAM else DecisionSource.INHERITED_TEAM,
                depth = depth,
                rules = ids.flatMap { id -> input.teamRules[id].orEmpty().mapNotNull(::sourcedRule) },
                grants = emptyList(),
            )
        }
        return PermissionEvaluator.isInstanceAdmin(
            AccessSubject(principalId = null, principalName = null, kind = PrincipalKind.USER, disabled = false, levels = levels, tokenRules = null),
        )
    }

    /** The precedence levels of a principal: its own nodes, its teams, and the teams they inherit. */
    private fun levelsOf(principalId: Int, input: Input, memberships: List<Membership>): List<AccessLevel> {
        val own = AccessLevel(
            source = DecisionSource.USER,
            depth = 0,
            rules = input.principalRules[principalId].orEmpty().mapNotNull(::sourcedRule),
            grants = emptyList(),
        )
        val teams = TeamHierarchy.levelsFrom(memberships.map { it.teamId }.toSet(), input.parents).map { (depth, ids) ->
            AccessLevel(
                source = if (depth == 0) DecisionSource.TEAM else DecisionSource.INHERITED_TEAM,
                depth = depth,
                rules = ids.flatMap { id -> input.teamRules[id].orEmpty().mapNotNull(::sourcedRule) },
                grants = emptyList(),
            )
        }
        return listOf(own) + teams
    }

    /**
     * The rules of a token. A node that does not parse makes the list deny everything, so an unreadable token never
     * widens: dropping one of its rules could otherwise give back a right the list had taken away.
     */
    private fun tokenRulesOf(nodes: List<NodeRule>?): List<SourcedRule>? {
        if (nodes == null) return null
        val parsed = nodes.map { sourcedRule(it) }
        return if (parsed.any { it == null }) emptyList() else parsed.filterNotNull()
    }

    private fun sourcedRule(rule: NodeRule): SourcedRule? =
        runCatching { NodePattern.of(rule.node) }.getOrNull()?.let { SourcedRule(subject = "", pattern = it, allowed = rule.allowed) }
}
