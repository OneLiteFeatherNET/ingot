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
package net.onelitefeather.ingot.identity.service

import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.audit.api.AuditAction
import com.reposilite.audit.api.AuditAction.INSTANCE_ADMIN_GRANTED
import com.reposilite.audit.api.AuditAction.INSTANCE_ADMIN_REVOKED
import com.reposilite.audit.api.AuditAction.PRINCIPAL_CREATED
import com.reposilite.audit.api.AuditAction.PRINCIPAL_DELETED
import com.reposilite.audit.api.AuditAction.PRINCIPAL_DISABLED
import com.reposilite.audit.api.AuditAction.PRINCIPAL_UPDATED
import com.reposilite.audit.api.AuditAction.TEAM_CREATED
import com.reposilite.audit.api.AuditAction.TEAM_DELETED
import com.reposilite.audit.api.AuditAction.TEAM_GRANTS_CHANGED
import com.reposilite.audit.api.AuditAction.TEAM_MEMBERSHIP_CHANGED
import com.reposilite.audit.api.AuditAction.NODES_CHANGED
import com.reposilite.audit.api.AuditAction.TEAM_UPDATED
import com.reposilite.audit.api.AuditAction.TOKEN_CREATED
import com.reposilite.audit.api.AuditAction.TOKEN_DELETED
import com.reposilite.audit.api.AuditActor
import com.reposilite.audit.api.AuditEvent
import com.reposilite.plugin.Extensions
import com.reposilite.shared.EntityTag
import com.reposilite.shared.EntityTagSigner
import com.reposilite.shared.ErrorProblem
import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.errorResponse
import com.reposilite.shared.notFoundError
import com.reposilite.shared.badRequestError
import com.reposilite.shared.badRequestProblems
import com.reposilite.token.AccessTokenFacade
import com.reposilite.token.AccessTokenIdentifier
import com.reposilite.token.AccessTokenSecurityProvider
import io.javalin.http.HttpStatus.CONFLICT
import io.javalin.http.HttpStatus.FORBIDDEN
import io.javalin.http.HttpStatus.PRECONDITION_FAILED
import net.onelitefeather.ingot.identity.api.AccessEntryDto
import net.onelitefeather.ingot.identity.api.AccessViaDto
import net.onelitefeather.ingot.identity.api.CreateTeamRequest
import net.onelitefeather.ingot.identity.api.CreatePrincipalRequest
import net.onelitefeather.ingot.identity.api.CreateTokenRequest
import net.onelitefeather.ingot.identity.api.CreatedTokenDto
import net.onelitefeather.ingot.identity.api.GrantDto
import net.onelitefeather.ingot.identity.api.GrantRequest
import net.onelitefeather.ingot.identity.api.MemberDto
import net.onelitefeather.ingot.identity.api.NodeDto
import net.onelitefeather.ingot.identity.api.PrincipalDetailDto
import net.onelitefeather.ingot.identity.api.PrincipalDto
import net.onelitefeather.ingot.identity.api.PrincipalSummaryDto
import net.onelitefeather.ingot.identity.api.RepositoryAccessDto
import net.onelitefeather.ingot.identity.api.TeamDetailDto
import net.onelitefeather.ingot.identity.api.TeamDto
import net.onelitefeather.ingot.identity.api.TokenDto
import net.onelitefeather.ingot.identity.api.UpdatePrincipalRequest
import net.onelitefeather.ingot.identity.api.UpdateTeamRequest
import net.onelitefeather.ingot.identity.api.CatalogDto
import net.onelitefeather.ingot.identity.api.CatalogEntryDto
import net.onelitefeather.ingot.identity.api.CheckDecisionDto
import net.onelitefeather.ingot.identity.api.CheckTraceDto
import net.onelitefeather.ingot.identity.api.PermissionCheckDto
import net.onelitefeather.ingot.identity.api.TemplateDto
import net.onelitefeather.ingot.identity.api.TemplatesDto
import net.onelitefeather.ingot.identity.evaluation.AdminReachability
import net.onelitefeather.ingot.identity.evaluation.Decision
import net.onelitefeather.ingot.identity.evaluation.GRANT_KIND
import net.onelitefeather.ingot.identity.evaluation.INSTANCE_ADMIN_KIND
import net.onelitefeather.ingot.identity.evaluation.ScopeRules
import net.onelitefeather.ingot.identity.evaluation.HierarchyProblem
import net.onelitefeather.ingot.identity.evaluation.PermissionEvaluator
import net.onelitefeather.ingot.identity.evaluation.TeamHierarchy
import net.onelitefeather.ingot.identity.login.BearerSecret
import net.onelitefeather.ingot.identity.login.LocalLoginProvider
import net.onelitefeather.ingot.identity.model.Action
import net.onelitefeather.ingot.identity.model.Grant
import net.onelitefeather.ingot.identity.model.NODE_SUBJECT_TEAM
import net.onelitefeather.ingot.identity.model.AccessSubject
import net.onelitefeather.ingot.identity.model.NodePattern
import net.onelitefeather.ingot.identity.model.NodeRule
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.model.PrincipalRecord
import net.onelitefeather.ingot.identity.model.privateTeamName
import net.onelitefeather.ingot.identity.model.Role
import net.onelitefeather.ingot.identity.model.TeamMemberSource
import net.onelitefeather.ingot.identity.model.TeamRecord
import net.onelitefeather.ingot.identity.model.TokenRecord
import net.onelitefeather.ingot.identity.storage.IdentityStore
import panda.std.Result
import panda.std.asSuccess
import java.time.Clock
import java.time.LocalDate

/** A value together with the entity tag of exactly the state it was read from. */
data class IdentitySnapshot<T>(
    val value: T,
    val etag: String,
)

typealias IdentityResult<T> = Result<T, ErrorResponse>

/**
 * A change that may take an instance role away from the principals that hold it: the role of a principal or a team,
 * a principal that stops being usable, a token that goes, or team memberships that end. Used to refuse the removal of
 * the last usable instance admin.
 */
private data class AdminChange(
    val principalRoleLost: Set<Int> = emptySet(),
    val teamRoleLost: Set<Int> = emptySet(),
    val principalsUnusable: Set<Int> = emptySet(),
    val tokenRemoved: Int? = null,
    val membershipsLost: Set<Pair<Int, Int>> = emptySet(),
)

/**
 * The identity administration: principals, their tokens, teams with their members and grants, and the explanation of
 * who may do what on a repository. Every mutation checks the entity tag when one is given, and rejects names that the
 * model reserves. Private teams (named `machine:<name>`) are not visible here: they belong to their machine principal.
 */
class IdentityFacade(
    private val store: IdentityStore,
    private val accessTokens: AccessTokenFacade,
    private val entityTags: EntityTagSigner,
    private val extensions: Extensions,
    private val visibilityOf: (String) -> String?,
    private val repositoryNames: () -> Collection<String> = { emptyList() },
    private val clock: Clock = Clock.systemUTC(),
) {

    private companion object {
        /** The repository actions a path check decides, the ones a request for a path makes. */
        private val PATH_CHECK_ACTIONS = setOf("read", "deploy", "delete")

        const val MAX_NAME_LENGTH = 255
        const val MAX_TEAM_NAME_LENGTH = 320
    }

    // principals

    fun listPrincipals(kind: String?): IdentityResult<List<PrincipalSummaryDto>> {
        val filter = if (kind == null) null else parseKind(kind) ?: return badRequestError("Unknown principal kind '$kind', supported: USER, MACHINE")
        return summarise(store.listPrincipals(filter)).asSuccess()
    }

    fun createPrincipal(request: CreatePrincipalRequest, actor: AuditActor = AuditActor.SYSTEM_ACTOR): IdentityResult<IdentitySnapshot<PrincipalDetailDto>> {
        val kind = request.kind?.let { parseKind(it) } ?: return badRequestError("Principal kind must be USER or MACHINE")
        val name = validateName(request.name, MAX_NAME_LENGTH) ?: return badRequestError(nameProblem(request.name, MAX_NAME_LENGTH, "Principal name"))
        if (store.findPrincipal(name) != null) return errorResponse(CONFLICT, "Principal '$name' already exists")

        val principal = store.createPrincipal(kind, name, request.displayName?.takeIf { it.isNotBlank() } ?: name, request.disabled ?: false, store.now())
            ?: return errorResponse(CONFLICT, "Principal '$name' already exists")
        if (principal.kind == PrincipalKind.MACHINE) {
            // A machine principal owns its private team from the start, as one created by a legacy token does.
            val team = store.createTeam(privateTeamName(principal.name), "Private team of machine principal ${principal.name}", externalRef = null)
            team?.let { store.addMember(it.id, principal.id, TeamMemberSource.MANUAL) }
        }
        audit(PRINCIPAL_CREATED, actor, mapOf("principal" to principal.name, "kind" to principal.kind.name))
        return snapshotOf(principal).asSuccess()
    }

    fun getPrincipal(name: String): IdentityResult<IdentitySnapshot<PrincipalDetailDto>> =
        principalNamed(name)?.let { snapshotOf(it).asSuccess() } ?: notFoundError("Principal not found")

    fun updatePrincipal(
        name: String,
        request: UpdatePrincipalRequest,
        ifMatch: String?,
        actor: AuditActor = AuditActor.SYSTEM_ACTOR,
    ): IdentityResult<IdentitySnapshot<PrincipalDetailDto>> {
        val principal = principalNamed(name) ?: return notFoundError("Principal not found")
        if (!EntityTag.matches(ifMatch, snapshotOf(principal).etag)) return preconditionFailed("Principal '$name'")

        val disabledChanged = request.disabled != null && request.disabled != principal.disabled
        request.displayName?.let { if (it.isBlank()) return badRequestError("Display name cannot be blank") }
        if (request.disabled == true) adminGuard<IdentitySnapshot<PrincipalDetailDto>>(AdminChange(principalsUnusable = setOf(principal.id)))?.let { return it }
        store.updatePrincipal(principal.id, request.displayName, request.disabled)
        if (disabledChanged) invalidateTokensOf(principal)

        // Disabling has its own event. Everything else that changed is listed by field name.
        val disabledNow = disabledChanged && request.disabled == true
        if (disabledNow) audit(PRINCIPAL_DISABLED, actor, mapOf("principal" to principal.name))
        val changed = listOfNotNull(
            "displayName".takeIf { request.displayName != null && request.displayName != principal.displayName },
            "disabled".takeIf { disabledChanged && !disabledNow },
        )
        if (changed.isNotEmpty()) audit(PRINCIPAL_UPDATED, actor, mapOf("principal" to principal.name, "changed" to changed))
        return snapshotOf(principalNamed(name)!!).asSuccess()
    }

    fun deletePrincipal(name: String, ifMatch: String?, actor: AuditActor = AuditActor.SYSTEM_ACTOR): IdentityResult<Unit> {
        val principal = principalNamed(name) ?: return notFoundError("Principal not found")
        if (!EntityTag.matches(ifMatch, snapshotOf(principal).etag)) return preconditionFailed("Principal '$name'")
        adminGuard<Unit>(AdminChange(principalsUnusable = setOf(principal.id)))?.let { return it }

        val tokenNames = store.tokensOf(principal.id).map { it.name }
        invalidateTokensOf(principal)
        store.deletePrincipal(principal)
        audit(PRINCIPAL_DELETED, actor, mapOf("principal" to principal.name, "kind" to principal.kind.name, "tokens" to tokenNames))
        return Unit.asSuccess()
    }

    // tokens

    fun createToken(
        principalName: String,
        request: CreateTokenRequest,
        actor: AuditActor = AuditActor.SYSTEM_ACTOR,
        caller: AccessTokenIdentifier? = null,
    ): IdentityResult<CreatedTokenDto> {
        val principal = principalNamed(principalName) ?: return notFoundError("Principal not found")
        if (caller != null && accessTokens.subjectOf(caller).principalName != principal.name) {
            notHeld<CreatedTokenDto>(caller, rightsOfPrincipal(principal), "the rights of principal '${principal.name}'")?.let { return it }
        }
        val name = validateName(request.name, MAX_NAME_LENGTH)
            ?: return badRequestError(nameProblem(request.name, MAX_NAME_LENGTH, "Token name"))
        if (store.findToken(name) != null) return errorResponse(CONFLICT, "Token '$name' already exists")

        val scopes = request.scopes?.let { names ->
            if (names.isEmpty()) return badRequestError("Scopes cannot be empty, omit them for an unrestricted token")
            names.map { parseAction(it) ?: return badRequestError("Unknown scope '$it', supported: ${Action.entries.joinToString { action -> action.name }}") }.toSet()
        }
        val parsedNodes = request.nodes?.let { raw ->
            if (raw.isEmpty()) return badRequestError("Nodes cannot be empty, omit them for an unrestricted token")
            parseNodes(raw)
        }
        if (parsedNodes != null && parsedNodes.isErr) return badRequestProblems(parsedNodes.error.message, parsedNodes.error.problems.orEmpty())
        val tokenNodes = parsedNodes?.orNull() ?: scopes?.let { ScopeRules.nodesOf(it) }
        val givenSecret = request.secret
        if (givenSecret != null && givenSecret.isBlank()) return badRequestError("Secret cannot be blank")

        // A generated secret carries the id of its row, so the row is written first and the hash after it. Until then the
        // row verifies nothing, which is the safe state to be in if the request is interrupted. A secret the caller gives
        // is stored as it comes, in one write.
        val created = store.createToken(
            principalId = principal.id,
            name = name,
            secretHash = givenSecret?.let { AccessTokenSecurityProvider.encodeSecret(it) }.orEmpty(),
            scopes = scopes,
            expiresAt = request.expiresAt,
            createdAt = LocalDate.now(clock),
            description = request.description.orEmpty(),
            nodes = tokenNodes,
        ) ?: return errorResponse(CONFLICT, "Token '$name' already exists")

        val secret = givenSecret ?: BearerSecret.create(created.id)
        if (givenSecret == null) {
            store.saveToken(created.copy(secretHash = AccessTokenSecurityProvider.encodeSecret(secret)))
        }

        extensions.emitEvent(AuditEvent(action = TOKEN_CREATED, actor = actor, details = mapOf("token" to name, "principal" to principal.name)))
        // The secret goes out in this response only. The audit record names the token and never its secret.
        return CreatedTokenDto(token = created.toDto(), secret = secret).asSuccess()
    }

    fun listTokens(principalName: String): IdentityResult<List<TokenDto>> {
        val principal = principalNamed(principalName) ?: return notFoundError("Principal not found")
        return store.tokensOf(principal.id).map { it.toDto() }.asSuccess()
    }

    fun deleteToken(principalName: String, tokenName: String, actor: AuditActor = AuditActor.SYSTEM_ACTOR): IdentityResult<Unit> {
        val principal = principalNamed(principalName) ?: return notFoundError("Principal not found")
        val token = store.tokensOf(principal.id).firstOrNull { it.name == tokenName } ?: return notFoundError("Token not found")
        adminGuard<Unit>(AdminChange(tokenRemoved = token.id))?.let { return it }

        store.deleteToken(token.id)
        accessTokens.tokenStateChanged(token.name)
        extensions.emitEvent(AuditEvent(action = TOKEN_DELETED, actor = actor, details = mapOf("token" to token.name, "principal" to principal.name)))
        return Unit.asSuccess()
    }

    // teams

    /** The teams with their counts, read in a fixed number of queries however many teams there are. */
    fun listTeams(): IdentityResult<List<TeamDto>> {
        val teams = store.listTeams().filterNot { it.isPrivate }
        val members = store.listAllMemberships().groupingBy { it.teamId }.eachCount()
        val grants = store.countGrantsByTeam()
        val roleTeams = store.listInstanceRoleTeamIds()
        val input = store.adminReachabilityInput()
        return teams.map { team ->
            TeamDto(
                name = team.name,
                description = team.description,
                externalRef = team.externalRef,
                memberCount = members[team.id] ?: 0,
                grantCount = grants[team.id] ?: 0,
                instanceAdmin = team.id in roleTeams,
                nodeCount = input.teamRules[team.id].orEmpty().size,
                isAdmin = AdminReachability.teamIsAdmin(team.id, input),
            )
        }.asSuccess()
    }

    fun createTeam(request: CreateTeamRequest, actor: AuditActor = AuditActor.SYSTEM_ACTOR): IdentityResult<IdentitySnapshot<TeamDetailDto>> {
        // The reserved prefix is checked first: it is a conflict with the private teams, not a malformed name.
        if (request.name?.trim()?.startsWith("machine:", ignoreCase = true) == true) {
            return errorResponse(CONFLICT, "Team names starting with 'machine:' are reserved")
        }
        val name = validateName(request.name, MAX_TEAM_NAME_LENGTH)
            ?: return badRequestError(nameProblem(request.name, MAX_TEAM_NAME_LENGTH, "Team name"))
        if (store.findTeam(name) != null) return errorResponse(CONFLICT, "Team '$name' already exists")

        val team = store.createTeam(name, request.description.orEmpty(), request.externalRef?.takeIf { it.isNotBlank() })
            ?: return errorResponse(CONFLICT, "Team '$name' already exists")
        audit(TEAM_CREATED, actor, mapOf("team" to team.name))
        return snapshotOf(team).asSuccess()
    }

    fun getTeam(name: String): IdentityResult<IdentitySnapshot<TeamDetailDto>> =
        publicTeam(name)?.let { snapshotOf(it).asSuccess() } ?: notFoundError("Team not found")

    fun updateTeam(
        name: String,
        request: UpdateTeamRequest,
        ifMatch: String?,
        actor: AuditActor = AuditActor.SYSTEM_ACTOR,
    ): IdentityResult<IdentitySnapshot<TeamDetailDto>> {
        val team = publicTeam(name) ?: return notFoundError("Team not found")
        if (!EntityTag.matches(ifMatch, snapshotOf(team).etag)) return preconditionFailed("Team '$name'")

        store.updateTeam(team.id, request.description, request.externalRef, updateExternalRef = request.externalRef != null)
        val changed = listOfNotNull(
            "description".takeIf { request.description != null && request.description != team.description },
            "externalRef".takeIf { request.externalRef != null && request.externalRef != team.externalRef },
        )
        if (changed.isNotEmpty()) audit(TEAM_UPDATED, actor, mapOf("team" to team.name, "changed" to changed))
        return snapshotOf(publicTeam(name)!!).asSuccess()
    }

    fun deleteTeam(name: String, ifMatch: String?, actor: AuditActor = AuditActor.SYSTEM_ACTOR): IdentityResult<Unit> {
        val team = publicTeam(name) ?: return notFoundError("Team not found")
        if (!EntityTag.matches(ifMatch, snapshotOf(team).etag)) return preconditionFailed("Team '$name'")
        adminGuard<Unit>(AdminChange(teamRoleLost = setOf(team.id)))?.let { return it }

        invalidateTeamMembers(team)
        store.deleteTeam(team.id)
        audit(TEAM_DELETED, actor, mapOf("team" to team.name))
        return Unit.asSuccess()
    }

    /** Replaces the MANUAL members. SYNC members are not touched, the synchronisation owns them. */
    fun replaceMembers(
        name: String,
        members: List<String>?,
        ifMatch: String?,
        actor: AuditActor = AuditActor.SYSTEM_ACTOR,
        caller: AccessTokenIdentifier? = null,
    ): IdentityResult<IdentitySnapshot<TeamDetailDto>> {
        val team = publicTeam(name) ?: return notFoundError("Team not found")
        if (!EntityTag.matches(ifMatch, snapshotOf(team).etag)) return preconditionFailed("Team '$name'")
        if (members == null) return badRequestError("Members are required, send an empty list to remove all manual members")

        val principals = members.map { memberName ->
            principalNamed(memberName) ?: return badRequestError("Unknown principal '$memberName'")
        }
        val principalIds = principals.map { it.id }.toSet()
        val dropped = store.listMemberships(team.id)
            .filter { it.source == TeamMemberSource.MANUAL && it.principalId !in principalIds }
            .map { team.id to it.principalId }
            .toSet()
        if (manualMemberNames(team) != principals.map { it.name }.toSet()) {
            notHeld<IdentitySnapshot<TeamDetailDto>>(caller, rightsOfTeam(team), "the rights of team '$name'")?.let { return it }
        }
        adminGuard<IdentitySnapshot<TeamDetailDto>>(AdminChange(membershipsLost = dropped))?.let { return it }

        val manualBefore = manualMemberNames(team)
        invalidateTeamMembers(team)
        store.replaceManualMembers(team.id, principalIds)
        invalidateTeamMembers(team)

        val manualAfter = principals.map { it.name }.toSet()
        val added = (manualAfter - manualBefore).sorted()
        val removed = (manualBefore - manualAfter).sorted()
        if (added.isNotEmpty() || removed.isNotEmpty()) {
            audit(TEAM_MEMBERSHIP_CHANGED, actor, mapOf("team" to team.name, "added" to added, "removed" to removed))
        }
        return snapshotOf(team).asSuccess()
    }

    // instance role

    /** Grants the instance role to a principal. Granting it again changes nothing and keeps the entity tag. */
    fun grantPrincipalInstanceAdmin(
        name: String,
        ifMatch: String?,
        actor: AuditActor = AuditActor.SYSTEM_ACTOR,
    ): IdentityResult<IdentitySnapshot<PrincipalDetailDto>> {
        val principal = principalNamed(name) ?: return notFoundError("Principal not found")
        if (!EntityTag.matches(ifMatch, snapshotOf(principal).etag)) return preconditionFailed("Principal '$name'")

        if (!store.hasDirectInstanceRole(principal.id)) {
            store.grantInstanceRole(principalId = principal.id, teamId = null)
            invalidateTokensOf(principal)
            audit(INSTANCE_ADMIN_GRANTED, actor, mapOf("principal" to principal.name))
        }
        return snapshotOf(principalNamed(name)!!).asSuccess()
    }

    /** Removes the instance role of a principal, unless that leaves no usable instance admin (see [adminGuard]). */
    fun revokePrincipalInstanceAdmin(
        name: String,
        ifMatch: String?,
        actor: AuditActor = AuditActor.SYSTEM_ACTOR,
    ): IdentityResult<IdentitySnapshot<PrincipalDetailDto>> {
        val principal = principalNamed(name) ?: return notFoundError("Principal not found")
        if (!EntityTag.matches(ifMatch, snapshotOf(principal).etag)) return preconditionFailed("Principal '$name'")

        if (store.hasDirectInstanceRole(principal.id)) {
            adminGuard<IdentitySnapshot<PrincipalDetailDto>>(AdminChange(principalRoleLost = setOf(principal.id)))?.let { return it }
            store.revokeInstanceRole(principalId = principal.id, teamId = null)
            invalidateTokensOf(principal)
            audit(INSTANCE_ADMIN_REVOKED, actor, mapOf("principal" to principal.name))
        }
        return snapshotOf(principalNamed(name)!!).asSuccess()
    }

    /** Grants the instance role to a team: every member with a usable token becomes an instance admin. */
    fun grantTeamInstanceAdmin(
        name: String,
        ifMatch: String?,
        actor: AuditActor = AuditActor.SYSTEM_ACTOR,
    ): IdentityResult<IdentitySnapshot<TeamDetailDto>> {
        val team = publicTeam(name) ?: return notFoundError("Team not found")
        if (!EntityTag.matches(ifMatch, snapshotOf(team).etag)) return preconditionFailed("Team '$name'")

        if (!store.listInstanceRoleTeamIds().contains(team.id)) {
            store.grantInstanceRole(principalId = null, teamId = team.id)
            invalidateTeamMembers(team)
            audit(INSTANCE_ADMIN_GRANTED, actor, mapOf("team" to team.name))
        }
        return snapshotOf(publicTeam(name)!!).asSuccess()
    }

    /** Removes the instance role of a team, unless that leaves no usable instance admin (see [adminGuard]). */
    fun revokeTeamInstanceAdmin(
        name: String,
        ifMatch: String?,
        actor: AuditActor = AuditActor.SYSTEM_ACTOR,
    ): IdentityResult<IdentitySnapshot<TeamDetailDto>> {
        val team = publicTeam(name) ?: return notFoundError("Team not found")
        if (!EntityTag.matches(ifMatch, snapshotOf(team).etag)) return preconditionFailed("Team '$name'")

        if (store.listInstanceRoleTeamIds().contains(team.id)) {
            adminGuard<IdentitySnapshot<TeamDetailDto>>(AdminChange(teamRoleLost = setOf(team.id)))?.let { return it }
            store.revokeInstanceRole(principalId = null, teamId = team.id)
            invalidateTeamMembers(team)
            audit(INSTANCE_ADMIN_REVOKED, actor, mapOf("team" to team.name))
        }
        return snapshotOf(publicTeam(name)!!).asSuccess()
    }

    /** Replaces every grant of the team. Legacy roles are not assignable: only private teams carry them. */
    fun replaceGrants(
        name: String,
        grants: List<GrantRequest>?,
        ifMatch: String?,
        actor: AuditActor = AuditActor.SYSTEM_ACTOR,
        caller: AccessTokenIdentifier? = null,
    ): IdentityResult<IdentitySnapshot<TeamDetailDto>> {
        val team = publicTeam(name) ?: return notFoundError("Team not found")
        if (!EntityTag.matches(ifMatch, snapshotOf(team).etag)) return preconditionFailed("Team '$name'")
        if (grants == null) return badRequestError("Grants are required, send an empty list to remove all grants")

        val parsed = grants.map { request ->
            val role = request.role?.uppercase()?.let { parseRole(it) } ?: return badRequestError("Unknown role '${request.role}', supported: READ, DEPLOY, MAINTAIN, ADMIN")
            if (role !in Role.ASSIGNABLE) return badRequestError("Role $role cannot be assigned, supported: READ, DEPLOY, MAINTAIN, ADMIN")
            val repository = request.repository?.trim()?.takeIf { it.isNotEmpty() }
            if (repository != null && repository.contains('/')) return badRequestError("Repository '$repository' must not contain '/'")
            val pathPrefix = normalizePrefix(request.pathPrefix)
                ?: return badRequestError("A grant names a path. Repository-wide access is granted with permission nodes, see /api/identity/teams/{name}/nodes")
            if (role == Role.ADMIN) return badRequestError("ADMIN applies to a whole repository, grant it with permission nodes")
            Triple(repository, pathPrefix, role)
        }
        val oldGrants = store.listGrants(team.id).map { Triple(it.repository, it.pathPrefix, it.role) }
        val changedGrants = (oldGrants.toSet() - parsed.toSet()) + (parsed.toSet() - oldGrants.toSet())
        if (changedGrants.isNotEmpty()) {
            notHeld<IdentitySnapshot<TeamDetailDto>>(caller, Rights(emptyList(), changedGrants.toList()), "the grants of team '$name'")?.let { return it }
        }
        val before = store.listGrants(team.id).map { describeGrant(it.repository, it.pathPrefix, it.role.name) }.toSet()
        val after = parsed.map { (repository, pathPrefix, role) -> describeGrant(repository, pathPrefix, role.name) }.toSet()

        store.replaceGrants(team.id, parsed)
        invalidateTeamMembers(team)

        val added = (after - before).sorted()
        val removed = (before - after).sorted()
        if (added.isNotEmpty() || removed.isNotEmpty()) {
            audit(TEAM_GRANTS_CHANGED, actor, mapOf("team" to team.name, "added" to added, "removed" to removed))
        }
        return snapshotOf(team).asSuccess()
    }

    // permission nodes

    /** The node list of a principal, with the entity tag of the principal it was read from. */
    fun principalNodes(name: String): IdentityResult<IdentitySnapshot<List<NodeDto>>> {
        val principal = principalNamed(name) ?: return notFoundError("Principal not found")
        return snapshotOf(principal).let { IdentitySnapshot(it.value.nodes, it.etag) }.asSuccess()
    }

    /** Replaces the whole node list of a principal. A list that changes nothing writes nothing and records no event. */
    fun replacePrincipalNodes(
        name: String,
        nodes: List<NodeDto>?,
        ifMatch: String?,
        actor: AuditActor = AuditActor.SYSTEM_ACTOR,
        caller: AccessTokenIdentifier? = null,
    ): IdentityResult<IdentitySnapshot<List<NodeDto>>> {
        val principal = principalNamed(name) ?: return notFoundError("Principal not found")
        if (!EntityTag.matches(ifMatch, snapshotOf(principal).etag)) return preconditionFailed("Principal '$name'")
        return parseNodes(nodes).flatMap { rules ->
            notHeld<IdentitySnapshot<List<NodeDto>>>(
                caller,
                Rights(changedNodes(store.listNodes(principal.kind.name, principal.id), rules), emptyList()),
                "the nodes of principal '$name'",
            ) ?: run {
                replaceNodeRules(principal.kind.name, principal.id, rules, principal.name, actor, subjectKey = "principal", kind = principal.kind.name)
                principalNodes(name)
            }
        }
    }

    fun teamNodes(name: String): IdentityResult<IdentitySnapshot<List<NodeDto>>> {
        val team = publicTeam(name) ?: return notFoundError("Team not found")
        return snapshotOf(team).let { IdentitySnapshot(it.value.nodes, it.etag) }.asSuccess()
    }

    /** Replaces the whole node list of a team. Every principal below the team loses its cached credentials. */
    fun replaceTeamNodes(
        name: String,
        nodes: List<NodeDto>?,
        ifMatch: String?,
        actor: AuditActor = AuditActor.SYSTEM_ACTOR,
        caller: AccessTokenIdentifier? = null,
    ): IdentityResult<IdentitySnapshot<List<NodeDto>>> {
        val team = publicTeam(name) ?: return notFoundError("Team not found")
        if (!EntityTag.matches(ifMatch, snapshotOf(team).etag)) return preconditionFailed("Team '$name'")
        return parseNodes(nodes).flatMap { rules ->
            notHeld<IdentitySnapshot<List<NodeDto>>>(
                caller,
                Rights(changedNodes(store.listNodes(NODE_SUBJECT_TEAM, team.id), rules), emptyList()),
                "the nodes of team '$name'",
            ) ?: run {
                replaceNodeRules(NODE_SUBJECT_TEAM, team.id, rules, team.name, actor, subjectKey = "team", kind = null)
                teamNodes(name)
            }
        }
    }

    fun teamParents(name: String): IdentityResult<IdentitySnapshot<List<String>>> {
        val team = publicTeam(name) ?: return notFoundError("Team not found")
        return snapshotOf(team).let { IdentitySnapshot(it.value.parents, it.etag) }.asSuccess()
    }

    /**
     * Replaces the parents of a team. Parents must be existing, non-private teams. A change that makes a cycle, or a chain
     * of inheritance deeper than [TeamHierarchy.MAX_DEPTH], is refused with 409 and stores nothing.
     */
    fun replaceTeamParents(
        name: String,
        parents: List<String>?,
        ifMatch: String?,
        actor: AuditActor = AuditActor.SYSTEM_ACTOR,
        caller: AccessTokenIdentifier? = null,
    ): IdentityResult<IdentitySnapshot<List<String>>> {
        val team = publicTeam(name) ?: return notFoundError("Team not found")
        if (!EntityTag.matches(ifMatch, snapshotOf(team).etag)) return preconditionFailed("Team '$name'")
        if (parents == null) return badRequestError("Parents are required, send an empty list to remove all parents")
        val duplicates = parents.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.sorted()
        if (duplicates.isNotEmpty()) {
            return badRequestProblems(
                "Parents listed twice: ${duplicates.joinToString()}",
                duplicates.map { ErrorProblem(it, "Parent '$it' is listed twice") },
            )
        }
        val found = parents.associateWith { publicTeam(it) }
        val unknown = found.filterValues { it == null }.keys.map { ErrorProblem(it, "Unknown team '$it' as parent, parents must be existing teams") }
        if (unknown.isNotEmpty()) {
            return badRequestProblems(unknown.joinToString("; ") { it.message }, unknown)
        }
        val parentTeams = found.values.filterNotNull()
        val graph = store.listAllParents().toMutableMap()
        graph[team.id] = parentTeams.map { it.id }.toSet()
        when (val problem = TeamHierarchy.problemOf(graph)) {
            null -> Unit
            is HierarchyProblem.Cycle -> return errorResponse(
                CONFLICT,
                "Inheritance of '${teamNameOf(problem.teamId)}' would form a cycle: a team cannot inherit from itself, directly or through its parents",
            )
            is HierarchyProblem.TooDeep -> return errorResponse(
                CONFLICT,
                "Inheritance of '${teamNameOf(problem.teamId)}' would be ${problem.depth} levels deep, the limit is ${TeamHierarchy.MAX_DEPTH} levels",
            )
        }

        val currentParents = store.listParents(team.id)
        val newParentIds = parentTeams.map { it.id }.toSet()
        val changedParents = (currentParents - newParentIds) + (newParentIds - currentParents)
        if (changedParents.isNotEmpty()) {
            val parentRights = changedParents.mapNotNull { store.findTeamById(it) }.map { rightsOfTeam(it) }
            notHeld<IdentitySnapshot<List<String>>>(
                caller,
                Rights(parentRights.flatMap { it.nodes }, parentRights.flatMap { it.grants }),
                "the rights of the parents of team '$name'",
            )?.let { return it }
        }
        val before = parentNamesOf(team)
        store.replaceParents(team.id, parentTeams.map { it.id }.toSet())
        invalidateAllTokens()
        val after = parentTeams.map { it.name }.toSet()
        val added = (after - before).sorted()
        val removed = (before - after).sorted()
        if (added.isNotEmpty() || removed.isNotEmpty()) {
            audit(TEAM_UPDATED, actor, mapOf("team" to team.name, "changed" to listOf("parents"), "added" to added, "removed" to removed))
        }
        return teamParents(name)
    }

    // rights: a caller may hand on only what it holds itself

    /** The rights of a subject as the evaluator reads them: its allowed nodes and the grants of its roles. */
    private data class Rights(val nodes: List<String>, val grants: List<Triple<String?, String?, Role>>)

    /** The rights of a team: its allowed nodes and grants, with those of every parent it inherits from. */
    private fun rightsOfTeam(team: TeamRecord): Rights {
        val chain = teamChain(team.id)
        return Rights(
            nodes = chain.flatMap { store.listNodes(NODE_SUBJECT_TEAM, it) }.filter { it.allowed }.map { it.node },
            grants = chain.flatMap { store.listGrants(it) }.map { Triple(it.repository, it.pathPrefix, it.role) },
        )
    }

    /** The rights of a principal: its allowed nodes, and the nodes and grants that its teams give it. */
    private fun rightsOfPrincipal(principal: PrincipalRecord): Rights {
        val subject = store.loadAccess(principal, tokenNodes = null)
        return Rights(
            nodes = subject.levels.flatMap { level -> level.rules.filter { it.allowed }.map { it.pattern.text } },
            grants = subject.levels.flatMap { level -> level.grants.map { Triple(it.repository, it.pathPrefix, it.role) } },
        )
    }

    /** The team and the parents it reaches, up to the depth limit. */
    private fun teamChain(teamId: Int): Set<Int> {
        val reached = linkedSetOf(teamId)
        var frontier = listOf(teamId)
        repeat(TeamHierarchy.MAX_DEPTH) {
            frontier = frontier.flatMap { store.listParents(it) }.filter { it !in reached }
            reached += frontier
        }
        return reached
    }

    /**
     * Whether the caller holds every right of [rights]. An instance admin holds all of them, after token narrowing. Any other
     * caller holds a node the evaluator allows it, and a grant when it may do the grant's actions on the repository or path.
     * Wildcards are held by an instance admin only.
     */
    private fun callerHolds(caller: AccessTokenIdentifier, rights: Rights): Boolean {
        val subject = accessTokens.subjectOf(caller)
        if (PermissionEvaluator.isInstanceAdmin(subject)) return true
        return rights.nodes.all { holdsNode(subject, it) } &&
            rights.grants.all { (repository, pathPrefix, role) -> holdsGrant(subject, repository, pathPrefix, role) }
    }

    private fun holdsNode(subject: AccessSubject, node: String): Boolean {
        if (NodePattern.problemOf(node) != null) return false
        val parsed = NodePattern.of(node)
        if (parsed.kindName == "EVERYTHING" || parsed.repository == NodePattern.WILDCARD || parsed.action == NodePattern.WILDCARD) return false
        return PermissionEvaluator.decide(subject, parsed).allowed
    }

    private fun holdsGrant(subject: AccessSubject, repository: String?, pathPrefix: String?, role: Role): Boolean {
        if (repository == null) return false
        val prefix = pathPrefix?.trim('/').orEmpty()
        return role.actions.all { action ->
            val nodeAction = when (action) {
                Action.READ -> "read"
                Action.WRITE -> "deploy"
                Action.DELETE -> "delete"
                Action.MANAGE_REPOSITORY -> "manage"
                Action.INSTANCE_ADMIN -> return false
            }
            when {
                prefix.isEmpty() -> PermissionEvaluator.decide(subject, NodePattern.repository(repository, nodeAction)).allowed
                nodeAction == "manage" -> false
                else -> PermissionEvaluator.decidePath(subject, repository, prefix, nodeAction).allowed
            }
        }
    }

    /** The node texts whose rule differs between two lists, by text and allowance. */
    private fun changedNodes(before: List<NodeRule>, after: List<NodeRule>): List<String> {
        val old = before.map { it.node to it.allowed }.toSet()
        val new = after.map { it.node to it.allowed }.toSet()
        return ((old - new) + (new - old)).map { it.first }.distinct()
    }

    /** The refusal of a change of rights the caller does not hold, null when the caller holds them all or no caller acts. */
    private fun <T> notHeld(caller: AccessTokenIdentifier?, rights: Rights, what: String): IdentityResult<T>? =
        if (caller == null || callerHolds(caller, rights)) {
            null
        } else {
            errorResponse(FORBIDDEN, "The caller does not hold $what, so it cannot change them. A principal may only give rights it holds itself.")
        }

    // catalog, templates and the permission check

    /**
     * The catalogue text of a repository action. `manage` is reserved: the node can be stored and listed, but no request
     * checks it yet, so granting it changes no access. Repository settings are one shared document, so there is no
     * per-repository settings edit to gate with it.
     */
    private fun repositoryActionText(action: String, scope: String): String =
        if (action == "manage") "Reserved: manage on $scope, no request checks it yet" else "$action on $scope"

    /** The nodes an operator can pick, for autocomplete. Repository nodes are listed for the repositories that exist now. */
    fun nodeCatalog(prefix: String?): IdentityResult<CatalogDto> {
        val entries = mutableListOf<CatalogEntryDto>()
        NodePattern.ADMIN_AREAS.forEach { area -> entries += CatalogEntryDto("ingot.admin.$area", "Administer $area", "STATIC") }
        entries += CatalogEntryDto("ingot.admin.*", "Every administrative area, as the instance administrator", "WILDCARD")
        entries += CatalogEntryDto("*", "Everything: every repository action and every administrative area", "WILDCARD")
        repositoryNames().sorted().forEach { repository ->
            NodePattern.REPOSITORY_ACTIONS.forEach { action ->
                entries += CatalogEntryDto("ingot.repository.$repository.$action", repositoryActionText(action, "repository $repository"), "REPOSITORY")
            }
            entries += CatalogEntryDto("ingot.repository.$repository.*", "Every action on repository $repository", "REPOSITORY")
        }
        NodePattern.REPOSITORY_ACTIONS.forEach { action ->
            entries += CatalogEntryDto("ingot.repository.*.$action", repositoryActionText(action, "every repository"), "WILDCARD")
        }
        NodePattern.ARTIFACT_ACTIONS.forEach { action ->
            entries += CatalogEntryDto("ingot.artifact.*.$action", "$action on every artifact in every repository", "ARTIFACT")
        }
        entries += CatalogEntryDto("ingot.artifact.*.*", "Every artifact action on every artifact in every repository", "ARTIFACT")
        val filtered = entries.filter { prefix.isNullOrEmpty() || it.node.startsWith(prefix) }
        return CatalogDto(filtered).asSuccess()
    }

    /** The predefined node bundles of the user interface. They are data: reading them changes no rights. */
    fun nodeTemplates(): IdentityResult<TemplatesDto> {
        val read = "ingot.repository.{repository}.read"
        val deploy = "ingot.repository.{repository}.deploy"
        val delete = "ingot.repository.{repository}.delete"
        val manage = "ingot.repository.{repository}.manage"
        val artifactRead = "ingot.artifact.{groupId}:{artifactId}.read"
        val artifactDeploy = "ingot.artifact.{groupId}:{artifactId}.deploy"
        val repositoryArtifactRead = "ingot.repository.{repository}.artifact.{groupId}:{artifactId}.read"
        val repositoryArtifactDeploy = "ingot.repository.{repository}.artifact.{groupId}:{artifactId}.deploy"
        return TemplatesDto(
            listOf(
                TemplateDto("read", "Read", listOf(read)),
                TemplateDto("deploy", "Deploy", listOf(read, deploy)),
                TemplateDto("maintain", "Maintain", listOf(read, deploy, delete)),
                TemplateDto("admin", "Admin", listOf(read, deploy, delete, manage)),
                TemplateDto("instance-admin", "Instance admin", listOf("ingot.admin.*")),
                TemplateDto("artifact-read", "Read an artifact", listOf(artifactRead)),
                TemplateDto("artifact-deploy", "Deploy an artifact", listOf(artifactRead, artifactDeploy)),
                TemplateDto("repository-artifact-deploy", "Deploy an artifact in one repository", listOf(repositoryArtifactRead, repositoryArtifactDeploy)),
            ),
        ).asSuccess()
    }

    /**
     * Decides one concrete node for one principal, optionally narrowed by one of its tokens, and explains the decision with
     * every candidate it considered. An instance admin may check any principal; any other caller may check only its own.
     */
    fun permissionCheck(
        caller: AccessTokenIdentifier,
        principal: String?,
        node: String?,
        token: String?,
        repository: String? = null,
        path: String? = null,
        action: String? = null,
    ): IdentityResult<PermissionCheckDto> {
        if (principal.isNullOrBlank()) return badRequestError("The query parameter 'principal' is required")
        if (repository != null || path != null) {
            return pathPermissionCheck(caller, principal, node, token, repository, path, action)
        }
        if (node.isNullOrBlank()) return badRequestError("The query parameter 'node' is required")
        if (node.contains(NodePattern.WILDCARD)) return badRequestError("The node to check must be concrete, without wildcards: '$node'")
        NodePattern.problemOf(node)?.let { return badRequestError("Invalid permission node: $it") }

        val parsed = NodePattern.of(node)
        return answerPermissionCheck(caller, principal, token, node = node, repository = null, path = null, action = null) { access ->
            PermissionEvaluator.decide(access, parsed)
        }
    }

    /**
     * Decides a repository path for one principal. The path is the path inside the repository, as a request names it, and
     * the action is read, deploy or delete: it is given by [action] or, when that is absent, taken from [node]. A node given
     * with the path must be a concrete node with the same action; its other parts are not used.
     */
    private fun pathPermissionCheck(
        caller: AccessTokenIdentifier,
        principal: String,
        node: String?,
        token: String?,
        repository: String?,
        path: String?,
        action: String?,
    ): IdentityResult<PermissionCheckDto> {
        if (repository.isNullOrBlank()) return badRequestError("The query parameter 'repository' is required for a path check")
        if (repository.contains(NodePattern.WILDCARD)) {
            return badRequestError("The repository of a path check must be a name, without wildcards: '$repository'")
        }
        NodePattern.problemOf("ingot.repository.$repository.read")?.let { return badRequestError("Invalid repository: $it") }
        if (path.isNullOrBlank()) return badRequestError("The query parameter 'path' is required for a path check, naming a path inside the repository")
        if (node != null) {
            if (node.contains(NodePattern.WILDCARD)) return badRequestError("The node to check must be concrete, without wildcards: '$node'")
            NodePattern.problemOf(node)?.let { return badRequestError("Invalid permission node: $it") }
        }
        val nodeAction = node?.let { NodePattern.of(it).action }
        if (action != null && nodeAction != null && action != nodeAction) {
            return badRequestError("The action of the node '$node' is $nodeAction, which differs from the action '$action'")
        }
        val chosen = action ?: nodeAction
            ?: return badRequestError("The query parameter 'action' is required for a path check, or give a node whose action it takes")
        if (chosen !in PATH_CHECK_ACTIONS) {
            return badRequestError("The action '$chosen' of a path check is read, deploy or delete")
        }
        return answerPermissionCheck(caller, principal, token, node = null, repository = repository, path = path, action = chosen) { access ->
            PermissionEvaluator.decidePath(access, repository, path, chosen)
        }
    }

    /** Answers a check once its question is valid: the caller, the principal and the token are looked up, then [decide] decides. */
    private fun answerPermissionCheck(
        caller: AccessTokenIdentifier,
        principal: String,
        token: String?,
        node: String?,
        repository: String?,
        path: String?,
        action: String?,
        decide: (AccessSubject) -> Decision,
    ): IdentityResult<PermissionCheckDto> {
        val callerSubject = accessTokens.subjectOf(caller)
        if (!PermissionEvaluator.isInstanceAdmin(callerSubject) && callerSubject.principalName != principal) {
            return errorResponse(FORBIDDEN, "Only instance admins can check the nodes of other principals")
        }
        val subject = principalNamed(principal) ?: return notFoundError("Principal not found")
        val tokenRecord = token?.let { name ->
            store.tokensOf(subject.id).firstOrNull { it.name == name } ?: return notFoundError("Token not found")
        }

        val access = store.loadAccess(subject, tokenNodes = tokenRecord?.nodes)
        val decision = decide(access)
        return PermissionCheckDto(
            principal = subject.name,
            node = node,
            allowed = decision.allowed,
            decision = CheckDecisionDto(
                source = decision.source.name,
                subject = decision.subject,
                node = decision.node,
                allowed = decision.allowed,
                depth = decision.depth,
                decidingNode = decision.decidingNode,
                decidingKind = decision.decidingKind,
                explanation = explain(decision),
            ),
            trace = decision.trace.map { candidate ->
                CheckTraceDto(
                    source = candidate.source.name,
                    subject = candidate.subject,
                    node = candidate.node,
                    allowed = candidate.allowed,
                    matched = candidate.matched,
                    reason = candidate.reason,
                    depth = candidate.depth,
                    kind = candidate.kind,
                )
            },
            repository = repository,
            path = path,
            action = action,
        ).asSuccess()
    }

    /** The decision of a check in one sentence, naming the node or grant that decided. */
    private fun explain(decision: Decision): String {
        val verdict = if (decision.allowed) "Allowed" else "Denied"
        val by = when {
            decision.decidingKind == GRANT_KIND -> "by a role grant of ${decision.subject}"
            decision.decidingKind == INSTANCE_ADMIN_KIND -> "by the instance administrator '${decision.decidingNode}' of ${decision.subject} (${decision.source.name})"
            decision.decidingNode != null -> "by the ${decision.decidingKind?.lowercase()} node '${decision.decidingNode}' of ${decision.subject} (${decision.source.name})"
            else -> "because no node or grant matches"
        }
        return "$verdict $by"
    }

    // access explanation

    /**
     * Who has what on a repository, and why. The visibility comes from the repository itself. Entries are the principals
     * that have an applying grant or the instance role; the effective actions are the union of the applying grants.
     */
    fun repositoryAccess(repository: String): IdentityResult<RepositoryAccessDto> {
        val visibility = visibilityOf(repository) ?: return notFoundError("Repository not found")

        val entries = store.listPrincipals().mapNotNull { principal ->
            val subject = store.loadAccess(principal, tokenNodes = null)
            val admin = PermissionEvaluator.isInstanceAdmin(subject)
            val applying = subject.levels.flatMap { it.grants }.filter { PermissionEvaluator.grantReaches(it, "/$repository") }
            if (!admin && applying.isEmpty()) return@mapNotNull null

            val effective = if (admin) {
                Action.entries.map { it.name }
            } else {
                applying.flatMap { grant -> grant.role.actions.map { it.name } }.distinct()
            }.sorted()
            val via = applying.map { grant ->
                AccessViaDto(
                    source = if (grant.role.legacy) "LEGACY" else "TEAM",
                    team = grant.team,
                    role = grant.role.name,
                    pathPrefix = grant.pathPrefix,
                )
            } + if (admin) listOf(AccessViaDto(source = "INSTANCE_ADMIN", team = null, role = null, pathPrefix = null)) else emptyList()

            AccessEntryDto(
                principal = principal.name,
                kind = principal.kind.name,
                disabled = principal.disabled,
                instanceAdmin = admin,
                effective = effective,
                via = via,
            )
        }
        return RepositoryAccessDto(repository = repository, visibility = visibility, entries = entries).asSuccess()
    }

    // internals

    private fun principalNamed(name: String): PrincipalRecord? =
        store.findPrincipal(name)

    private fun publicTeam(name: String): TeamRecord? =
        store.findTeam(name)?.takeUnless { it.isPrivate }

    /**
     * Validates a node list. Every problem is listed, so the operator fixes them in one round: the message joins them, and
     * the answer carries one [ErrorProblem] per invalid node, so that the editor can show each one next to its row.
     */
    private fun parseNodes(nodes: List<NodeDto>?): IdentityResult<List<NodeRule>> {
        if (nodes == null) return badRequestError("Nodes are required, send an empty list to remove all nodes")
        val problems = nodes.mapNotNull { node -> NodePattern.problemOf(node.node)?.let { ErrorProblem(node.node, it) } }
        if (problems.isNotEmpty()) {
            return badRequestProblems("Invalid permission nodes: ${problems.joinToString("; ") { it.message }}", problems)
        }
        val duplicates = nodes.groupingBy { it.node }.eachCount().filterValues { it > 1 }.keys.sorted()
        if (duplicates.isNotEmpty()) {
            return badRequestProblems(
                "Nodes listed twice: ${duplicates.joinToString()}",
                duplicates.map { ErrorProblem(it, "Node '$it' is listed twice") },
            )
        }
        return nodes.map { NodeRule(it.node, it.allowed) }.asSuccess()
    }

    private fun replaceNodeRules(
        subjectKind: String,
        subjectId: Int,
        rules: List<NodeRule>,
        subjectName: String,
        actor: AuditActor,
        subjectKey: String,
        kind: String?,
    ) {
        val before = store.listNodes(subjectKind, subjectId).map(::describeRule).toSet()
        val after = rules.map(::describeRule).toSet()
        if (before == after) return
        store.replaceNodes(subjectKind, subjectId, rules)
        invalidateAllTokens()
        val details = mutableMapOf<String, Any?>(
            subjectKey to subjectName,
            "added" to (after - before).sorted(),
            "removed" to (before - after).sorted(),
        )
        kind?.let { details["kind"] = it }
        audit(NODES_CHANGED, actor, details)
    }

    private fun describeRule(rule: NodeRule): String =
        "${rule.node}=${if (rule.allowed) "allowed" else "denied"}"

    private fun parentNamesOf(team: TeamRecord): Set<String> =
        store.listParents(team.id).mapNotNull { store.findTeamById(it)?.name }.toSet()

    private fun teamNameOf(teamId: Int): String =
        store.findTeamById(teamId)?.name ?: "team #$teamId"

    private fun invalidateAllTokens() {
        store.listPrincipals().forEach { invalidateTokensOf(it) }
    }

    private fun invalidateTokensOf(principal: PrincipalRecord) {
        store.tokensOf(principal.id).forEach { accessTokens.tokenStateChanged(it.name) }
    }

    private fun invalidateTeamMembers(team: TeamRecord) {
        store.listMemberships(team.id).forEach { membership ->
            store.findPrincipalById(membership.principalId)?.let { invalidateTokensOf(it) }
        }
    }

    /** Records an administrative change. The details name what changed; they never carry a secret. */
    private fun audit(action: AuditAction, actor: AuditActor, details: Map<String, Any?>) {
        extensions.emitEvent(AuditEvent(action = action, actor = actor, details = details))
    }

    private fun manualMemberNames(team: TeamRecord): Set<String> =
        store.listMemberships(team.id)
            .filter { it.source == TeamMemberSource.MANUAL }
            .mapNotNull { store.findPrincipalById(it.principalId)?.name }
            .toSet()

    private fun describeGrant(repository: String?, pathPrefix: String?, role: String): String =
        "repository=${repository ?: "*"} path=${pathPrefix ?: "*"} role=$role"

    /**
     * Refuses a change that would take the instance role away from the last usable instance admin. A usable admin is an
     * enabled principal with an unexpired persistent token that holds the role, directly or through a team it belongs to.
     * Temporary tokens from the command line are not persistent and never count. A change is only refused when at least
     * one usable admin exists before it, so a system without any admin is not locked further.
     */
    private fun <T> adminGuard(change: AdminChange): IdentityResult<T>? {
        val before = usableInstanceAdmins(AdminChange())
        val after = usableInstanceAdmins(change)
        return if (before > 0 && after == 0) {
            errorResponse(CONFLICT, "Cannot remove the last instance admin: keep at least one enabled principal with a valid token that holds the instance role. Temporary tokens from --token do not count.")
        } else {
            null
        }
    }

    /**
     * Counts the usable instance admins as they would be once [change] is applied. The rule itself is pure (see
     * [AdminReachability]); the change only removes what it names from the rows that were read.
     */
    private fun usableInstanceAdmins(change: AdminChange): Int {
        val input = store.adminReachabilityInput()
        val changed = AdminReachability.Input(
            principals = input.principals.map { if (it.id in change.principalsUnusable) it.copy(disabled = true) else it },
            principalRules = input.principalRules.mapValues { (id, rules) -> if (id in change.principalRoleLost) withoutAdminNodes(rules) else rules },
            teamRules = input.teamRules.mapValues { (id, rules) -> if (id in change.teamRoleLost) withoutAdminNodes(rules) else rules },
            parents = input.parents,
            memberships = input.memberships.filterNot { (it.teamId to it.principalId) in change.membershipsLost },
            tokens = input.tokens.filterNot { it.id == change.tokenRemoved },
        )
        return AdminReachability.usableAdmins(changed, clock.instant()).size
    }

    /** The rules that confer the instance admin: the admin node itself and the star node. */
    private fun withoutAdminNodes(rules: List<NodeRule>): List<NodeRule> =
        rules.filterNot { it.allowed && (it.node == NodePattern.INSTANCE_ADMIN.text || it.node == NodePattern.WILDCARD) }

    private fun <T> preconditionFailed(subject: String): IdentityResult<T> =
        errorResponse(PRECONDITION_FAILED, "$subject has been changed since it was read, reload it and try again")

    private fun parseKind(value: String): PrincipalKind? =
        PrincipalKind.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }

    private fun parseAction(value: String): Action? =
        Action.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }

    private fun parseRole(value: String): Role? =
        Role.entries.firstOrNull { it.name == value }

    private fun validateName(value: String?, maxLength: Int): String? {
        val trimmed = value?.trim() ?: return null
        if (trimmed.isEmpty() || trimmed.length > maxLength || trimmed.contains(':')) return null
        return trimmed
    }

    private fun nameProblem(value: String?, maxLength: Int, label: String): String = when {
        value.isNullOrBlank() -> "$label is required"
        value.contains(':') -> "$label must not contain ':'"
        value.length > maxLength -> "$label must be at most $maxLength characters"
        else -> "$label is invalid"
    }

    private fun normalizePrefix(prefix: String?): String? =
        prefix?.trim('/')?.takeIf { it.isNotEmpty() }

    private fun PrincipalRecord.toDto() = PrincipalDto(
        id = id,
        kind = kind.name,
        name = name,
        displayName = displayName,
        disabled = disabled,
        createdAt = createdAt,
        lastSeenAt = lastSeenAt,
    )

    /**
     * The list rows of the given principals. The tokens, the memberships, the instance roles and the credentials are read
     * once for all of them, so the number of queries does not grow with the number of principals. An instance admin is a
     * principal with the direct role or a member of a team that holds it, the rule the access evaluation uses.
     */
    private fun summarise(principals: List<PrincipalRecord>): List<PrincipalSummaryDto> {
        val tokens = store.countTokensByPrincipal()
        val directRoles = store.listInstanceRolePrincipalIds()
        val roleTeams = store.listInstanceRoleTeamIds()
        val throughTeam = store.listAllMemberships().filter { it.teamId in roleTeams }.map { it.principalId }.toSet()
        val withPassword = store.listPrincipalIdsWithLocalCredential()
        val input = store.adminReachabilityInput()
        val effectiveAdmins = AdminReachability.effectiveAdmins(input)
        return principals.map { principal ->
            PrincipalSummaryDto(
                id = principal.id,
                kind = principal.kind.name,
                name = principal.name,
                displayName = principal.displayName,
                disabled = principal.disabled,
                createdAt = principal.createdAt,
                lastSeenAt = principal.lastSeenAt,
                instanceAdmin = principal.id in directRoles || principal.id in throughTeam,
                instanceRole = principal.id in directRoles,
                tokenCount = tokens[principal.id] ?: 0,
                providers = if (principal.kind == PrincipalKind.USER && principal.id in withPassword) listOf(LocalLoginProvider.ID) else emptyList(),
                nodeCount = input.principalRules[principal.id].orEmpty().size,
                isAdmin = principal.id in effectiveAdmins,
            )
        }
    }

    private fun TokenRecord.toDto() = TokenDto(
        name = name,
        description = description,
        createdAt = createdAt,
        expiresAt = expiresAt,
        scopes = scopes?.map { it.name }?.sorted(),
        nodes = nodes?.map { NodeDto(it.node, it.allowed) },
        lastUsedAt = lastUsedAt,
        lastUsedIp = lastUsedIp,
    )

    private fun principalDetail(principal: PrincipalRecord): PrincipalDetailDto {
        val teams = store.membershipsOf(principal.id).mapNotNull { store.findTeamById(it.teamId)?.name }.sorted()
        val tokens = store.tokensOf(principal.id).map { it.toDto() }
        val nodes = store.listNodes(principal.kind.name, principal.id).map { NodeDto(it.node, it.allowed) }
        val isAdmin = PermissionEvaluator.isInstanceAdmin(store.loadAccess(principal, tokenNodes = null))
        return PrincipalDetailDto(principal.toDto(), teams, store.hasDirectInstanceRole(principal.id), tokens, nodes, nodeCount = nodes.size, isAdmin = isAdmin)
    }

    /**
     * The fingerprint covers what a client can change through this API. Last-used times are left out, so that a read
     * does not change the tag.
     */
    private fun snapshotOf(principal: PrincipalRecord): IdentitySnapshot<PrincipalDetailDto> {
        val detail = principalDetail(principal)
        val fingerprint = mapOf(
            "name" to detail.principal.name,
            "displayName" to detail.principal.displayName,
            "disabled" to detail.principal.disabled,
            "teams" to detail.teams,
            "instanceRole" to detail.instanceRole,
            "tokens" to detail.tokens.map { listOf(it.name, it.description, it.createdAt.toString(), it.expiresAt?.toString(), it.scopes) },
            "nodes" to detail.nodes.map { listOf(it.node, it.allowed) },
        )
        return IdentitySnapshot(detail, entityTags.of(DEFAULT_OBJECT_MAPPER.writeValueAsBytes(fingerprint)))
    }

    private fun snapshotOf(team: TeamRecord): IdentitySnapshot<TeamDetailDto> {
        val members = store.listMemberships(team.id).mapNotNull { membership ->
            store.findPrincipalById(membership.principalId)?.let { principal ->
                MemberDto(principal = principal.name, kind = principal.kind.name, source = membership.source.name)
            }
        }.sortedBy { it.principal }
        val grants = store.listGrants(team.id).map { GrantDto(it.repository, it.pathPrefix, it.role.name) }
            .sortedWith(compareBy({ it.repository }, { it.pathPrefix }, { it.role }))
        val detail = TeamDetailDto(
            name = team.name,
            description = team.description,
            externalRef = team.externalRef,
            members = members,
            grants = grants,
            instanceRole = store.listInstanceRoleTeamIds().contains(team.id),
            nodes = store.listNodes(NODE_SUBJECT_TEAM, team.id).map { NodeDto(it.node, it.allowed) },
            parents = parentNamesOf(team).sorted(),
            nodeCount = store.listNodes(NODE_SUBJECT_TEAM, team.id).size,
            isAdmin = AdminReachability.teamIsAdmin(team.id, store.adminReachabilityInput()),
        )
        return IdentitySnapshot(detail, entityTags.of(DEFAULT_OBJECT_MAPPER.writeValueAsBytes(detail)))
    }

}
