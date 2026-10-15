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
)

data class PrincipalDetailDto(
    val principal: PrincipalDto,
    val teams: List<String>,
    val instanceRole: Boolean,
    val tokens: List<TokenDto>,
)

data class TokenDto(
    val name: String,
    val description: String,
    val createdAt: LocalDate,
    val expiresAt: Instant?,
    val scopes: List<String>?,
    val lastUsedAt: Instant?,
    val lastUsedIp: String?,
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
)

data class TeamDetailDto(
    val name: String,
    val description: String,
    val externalRef: String?,
    val members: List<MemberDto>,
    val grants: List<GrantDto>,
    val instanceRole: Boolean,
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

data class ReplaceGrantsRequest(
    val grants: List<GrantRequest>?,
)

data class GrantRequest(
    val repository: String? = null,
    val pathPrefix: String? = null,
    val role: String?,
)
