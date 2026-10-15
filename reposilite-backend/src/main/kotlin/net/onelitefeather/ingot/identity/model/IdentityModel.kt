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
package net.onelitefeather.ingot.identity.model

import java.time.Instant
import java.time.LocalDate

/** Who an identity is. USER is a human, MACHINE a CI job or bot. */
enum class PrincipalKind { USER, MACHINE }

/** How a team membership came to be. SYNC rows are written by external synchronisation only. */
enum class TeamMemberSource { MANUAL, SYNC }

/** What a request asks for. Scopes of a token are sets of these. */
enum class Action { READ, WRITE, DELETE, MANAGE_REPOSITORY, INSTANCE_ADMIN }

/**
 * A role a team holds. The new roles are cumulative; the legacy roles carry the old route semantics and are only
 * created on private machine teams. LEGACY_WRITE includes DELETE because the old code deleted under WRITE.
 */
enum class Role(val actions: Set<Action>, val legacy: Boolean) {
    READ(setOf(Action.READ), legacy = false),
    DEPLOY(setOf(Action.READ, Action.WRITE), legacy = false),
    MAINTAIN(setOf(Action.READ, Action.WRITE, Action.DELETE), legacy = false),
    ADMIN(setOf(Action.READ, Action.WRITE, Action.DELETE, Action.MANAGE_REPOSITORY), legacy = false),
    LEGACY_READ(setOf(Action.READ), legacy = true),
    LEGACY_WRITE(setOf(Action.WRITE, Action.DELETE), legacy = true);

    companion object {
        /** Roles an operator can assign through the identity API. */
        val ASSIGNABLE: Set<Role> = setOf(READ, DEPLOY, MAINTAIN, ADMIN)
    }
}

/** Name of the only instance-wide role. It replaces the legacy MANAGER permission. */
const val INSTANCE_ADMIN_ROLE: String = "INSTANCE_ADMIN"

/** Prefix of the private team that belongs to exactly one machine principal. */
const val PRIVATE_TEAM_PREFIX: String = "machine:"

fun privateTeamName(principalName: String): String =
    "$PRIVATE_TEAM_PREFIX$principalName"

data class PrincipalRecord(
    val id: Int,
    val kind: PrincipalKind,
    val name: String,
    val displayName: String,
    val disabled: Boolean,
    val createdAt: Instant,
    val lastSeenAt: Instant?,
)

data class TeamRecord(
    val id: Int,
    val name: String,
    val description: String,
    val externalRef: String?,
) {
    val isPrivate: Boolean
        get() = name.startsWith(PRIVATE_TEAM_PREFIX, ignoreCase = true)
}

data class MembershipRecord(
    val teamId: Int,
    val principalId: Int,
    val source: TeamMemberSource,
)

data class GrantRecord(
    val id: Int,
    val teamId: Int,
    val repository: String?,
    val pathPrefix: String?,
    val role: Role,
)

/** A stored permission node: the node as written and whether it allows (true) or denies (false). */
data class NodeRule(
    val node: String,
    val allowed: Boolean,
)

/** Subject kinds of permission nodes. Principals use their [PrincipalKind] name, teams use [NODE_SUBJECT_TEAM]. */
const val NODE_SUBJECT_TEAM: String = "TEAM"

data class TokenRecord(
    val id: Int,
    val principalId: Int,
    val name: String,
    val secretHash: String,
    val scopes: Set<Action>?,
    val expiresAt: Instant?,
    val lastUsedAt: Instant?,
    val lastUsedIp: String?,
    val createdAt: LocalDate,
    val description: String,
)

/** A grant as the evaluator sees it: the team it belongs to is kept for explanations. */
data class Grant(
    val team: String,
    val role: Role,
    val repository: String?,
    val pathPrefix: String?,
)

/** Where a decision came from. [TOKEN] is the node list of the token; [DEFAULT] means no rule matched at all. */
enum class DecisionSource { USER, TEAM, INHERITED_TEAM, LEGACY, TOKEN, DEFAULT }

/** A node rule with the subject that holds it and the parsed pattern. */
data class SourcedRule(
    val subject: String,
    val pattern: NodePattern,
    val allowed: Boolean,
)

/**
 * One precedence level. The rules and grants of all teams at the same distance are pooled, so the most specific rule
 * decides across them and a denial wins a tie. [depth] is 0 for the principal's own rules and its own teams, and the
 * distance of an inherited team otherwise.
 */
data class AccessLevel(
    val source: DecisionSource,
    val depth: Int,
    val rules: List<SourcedRule>,
    val grants: List<Grant>,
)

/**
 * Everything the evaluator needs about the owner of a token: the precedence levels and the node list of the token when
 * it narrows its owner. Built from the store, or from the in-memory legacy view for temporary tokens. Holds no reference
 * to storage, so the evaluator stays pure.
 */
data class AccessSubject(
    val principalId: Int?,
    val principalName: String?,
    val kind: PrincipalKind,
    val disabled: Boolean,
    val levels: List<AccessLevel>,
    /** The node list of the token; null when the token does not narrow its owner. */
    val tokenRules: List<SourcedRule>?,
    val teams: List<String> = emptyList(),
)
