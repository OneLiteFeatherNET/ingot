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
package net.onelitefeather.ingot.identity.api

import java.time.Instant
import java.time.LocalDate

// Responses

data class PrincipalDto(
    val id: Int,
    val kind: String,
    val name: String,
    val displayName: String,
    val disabled: Boolean,
    val createdAt: Instant,
    val lastSeenAt: Instant?,
)

/**
 * A principal as a list row shows it: the fields of [PrincipalDto] flat, plus the summaries a row needs so that a client
 * does not read the detail of every principal. [instanceAdmin] is true for the direct role and for the role through a
 * team. [instanceRole] is the direct role only, as in the detail, because
 * the actions on a principal can grant and revoke only that one. [providers] names the login providers the principal can sign in with: `local` for a user with a password, empty
 * for everyone else, machine principals always.
 */
data class PrincipalSummaryDto(
    val id: Int,
    val kind: String,
    val name: String,
    val displayName: String,
    val disabled: Boolean,
    val createdAt: Instant,
    val lastSeenAt: Instant?,
    val instanceAdmin: Boolean,
    val instanceRole: Boolean,
    val tokenCount: Int,
    val providers: List<String>,
    val nodeCount: Int = 0,
    val isAdmin: Boolean = false,
)

data class PrincipalDetailDto(
    val principal: PrincipalDto,
    val teams: List<String>,
    val instanceRole: Boolean,
    val tokens: List<TokenDto>,
    val nodes: List<NodeDto> = emptyList(),
    val nodeCount: Int = 0,
    val isAdmin: Boolean = false,
)

data class TokenDto(
    val name: String,
    val description: String,
    val createdAt: LocalDate,
    val expiresAt: Instant?,
    val scopes: List<String>?,
    val lastUsedAt: Instant?,
    val lastUsedIp: String?,
    val nodes: List<NodeDto>? = null,
)

/** The secret is returned once, on creation. It is never stored in clear and never shown again. */
data class CreatedTokenDto(
    val token: TokenDto,
    val secret: String,
)

data class TeamDto(
    val name: String,
    val description: String,
    val externalRef: String?,
    val memberCount: Int,
    val grantCount: Int,
    val instanceAdmin: Boolean,
    val nodeCount: Int = 0,
    val isAdmin: Boolean = false,
)

data class TeamDetailDto(
    val name: String,
    val description: String,
    val externalRef: String?,
    val members: List<MemberDto>,
    val grants: List<GrantDto>,
    val instanceRole: Boolean,
    val nodes: List<NodeDto> = emptyList(),
    val parents: List<String> = emptyList(),
    val nodeCount: Int = 0,
    val isAdmin: Boolean = false,
)

/**
 * A permission node and whether it allows (true) or denies (false) the subject. The string `-node` is only how a
 * denial is displayed; it is never stored or sent.
 */
data class NodeDto(
    val node: String,
    val allowed: Boolean,
)

/** The node list of a principal or a team: the whole list, replaced as a whole on write. */
data class NodeListDto(
    val nodes: List<NodeDto>,
)

/** The parents of a team, by name. */
data class ParentListDto(
    val parents: List<String>,
)

data class MemberDto(
    val principal: String,
    val kind: String,
    val source: String,
)

data class GrantDto(
    val repository: String?,
    val pathPrefix: String?,
    val role: String,
)

data class RepositoryAccessDto(
    val repository: String,
    val visibility: String,
    val entries: List<AccessEntryDto>,
)

data class AccessEntryDto(
    val principal: String,
    val kind: String,
    val disabled: Boolean,
    val instanceAdmin: Boolean,
    val effective: List<String>,
    val via: List<AccessViaDto>,
)

/** Why a principal has access: a team grant, a legacy grant of its private team, or the instance role. */
data class AccessViaDto(
    val source: String,
    val team: String?,
    val role: String?,
    val pathPrefix: String?,
)

// Requests

data class CreatePrincipalRequest(
    val kind: String?,
    val name: String?,
    val displayName: String? = null,
    val disabled: Boolean? = null,
)

data class UpdatePrincipalRequest(
    val displayName: String? = null,
    val disabled: Boolean? = null,
)

data class CreateTokenRequest(
    val name: String?,
    val description: String? = null,
    val expiresAt: Instant? = null,
    val scopes: List<String>? = null,
    val nodes: List<NodeDto>? = null,
    val secret: String? = null,
)

data class CreateTeamRequest(
    val name: String?,
    val description: String? = null,
    val externalRef: String? = null,
)

data class UpdateTeamRequest(
    val description: String? = null,
    val externalRef: String? = null,
)

data class ReplaceMembersRequest(
    val members: List<String>?,
)

data class ReplaceNodesRequest(
    val nodes: List<NodeDto>?,
)

data class ReplaceParentsRequest(
    val parents: List<String>?,
)

data class ReplaceGrantsRequest(
    val grants: List<GrantRequest>?,
)

data class GrantRequest(
    val repository: String? = null,
    val pathPrefix: String? = null,
    val role: String?,
)

/** One entry of the node catalog. [kind] is STATIC, REPOSITORY or WILDCARD. */
data class CatalogEntryDto(
    val node: String,
    val description: String,
    val kind: String,
)

data class CatalogDto(
    val nodes: List<CatalogEntryDto>,
)

/**
 * A predefined node bundle for the user interface. `{repository}` stands for the repository the bundle is applied to,
 * `{groupId}` and `{artifactId}` for the coordinates of the artifact (the artifactId part is optional).
 */
data class TemplateDto(
    val id: String,
    val label: String,
    val nodes: List<String>,
)

data class TemplatesDto(
    val templates: List<TemplateDto>,
)

/**
 * The decision of a check. [decidingNode] is the text of the node that decided and [decidingKind] its kind (`ARTIFACT`,
 * `REPOSITORY`, `ADMIN`, `EVERYTHING`, or `GRANT` for a role grant, which has no node); both are null when no node
 * matched and the default denial applied. [explanation] is the decision in one sentence.
 */
data class CheckDecisionDto(
    val source: String,
    val subject: String?,
    val node: String,
    val allowed: Boolean,
    val depth: Int,
    val decidingNode: String? = null,
    val decidingKind: String? = null,
    val explanation: String? = null,
)

/** One candidate the decision looked at: whether it matched the requested node, and why it did or did not decide. */
data class CheckTraceDto(
    val source: String,
    val subject: String,
    val node: String,
    val allowed: Boolean,
    val matched: Boolean,
    val reason: String,
    val depth: Int,
    val kind: String? = null,
)

/**
 * The answer of a check. A node check has [node]; a path check has null there and names its [repository], [path] and
 * [action] instead, as the question was asked.
 */
data class PermissionCheckDto(
    val principal: String,
    val node: String?,
    val allowed: Boolean,
    val decision: CheckDecisionDto,
    val trace: List<CheckTraceDto>,
    val repository: String? = null,
    val path: String? = null,
    val action: String? = null,
)
