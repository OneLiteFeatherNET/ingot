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
package net.onelitefeather.ingot.identity.storage

import com.reposilite.token.AccessToken
import com.reposilite.token.AccessTokenIdentifier
import com.reposilite.token.AccessTokenPermission
import com.reposilite.token.AccessTokenPermission.MANAGER
import com.reposilite.token.AccessTokenRepository
import com.reposilite.token.AccessTokenType.PERSISTENT
import com.reposilite.token.Route
import com.reposilite.token.RoutePermission
import com.reposilite.token.UNINITIALIZED_ENTITY_ID
import net.onelitefeather.ingot.identity.evaluation.AccessSubjectSource
import net.onelitefeather.ingot.identity.model.AccessSubject
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.model.PrincipalRecord
import net.onelitefeather.ingot.identity.model.Role
import net.onelitefeather.ingot.identity.model.TeamMemberSource
import net.onelitefeather.ingot.identity.model.TeamRecord
import net.onelitefeather.ingot.identity.model.TokenRecord
import net.onelitefeather.ingot.identity.model.privateTeamName

/**
 * Stores persistent access tokens in the identity tables, behind the interface the access token facade uses. Every
 * token belongs to a machine principal of the same name. Its legacy rights live on the principal's private team: a
 * route is a LEGACY grant there, and MANAGER is the instance role of the principal.
 */
internal class IdentityAccessTokenRepository(
    private val store: IdentityStore,
) : AccessTokenRepository, AccessSubjectSource, TokenUsageSource {

    private val usage = TokenUsageTracker(store)

    override fun recordUsage(tokenId: Int, address: String?) =
        usage.recordUsage(tokenId, address)

    override fun lastUsedOf(tokenId: Int): TokenUsage? =
        usage.lastUsedOf(tokenId)

    override fun subjectOf(identifier: AccessTokenIdentifier): AccessSubject? {
        if (identifier.value == UNINITIALIZED_ENTITY_ID) return null
        val token = store.findTokenById(identifier.value) ?: return null
        val principal = store.findPrincipalById(token.principalId) ?: return null
        return store.loadAccess(principal, token.nodes)
    }

    override fun subjectOfPrincipal(principalId: Int): AccessSubject? {
        val principal = store.findPrincipalById(principalId) ?: return null
        return store.loadAccess(principal, tokenNodes = null)
    }

    override fun principalOf(principalId: Int): PrincipalRecord? =
        store.findPrincipalById(principalId)

    override fun saveAccessToken(accessToken: AccessToken): AccessToken {
        val existing = store.findToken(accessToken.name)
        if (existing != null) {
            store.saveToken(
                existing.copy(
                    secretHash = accessToken.encryptedSecret,
                    expiresAt = accessToken.expiresAt,
                    createdAt = accessToken.createdAt,
                    description = accessToken.description,
                )
            )
            return toAccessToken(store.findTokenById(existing.id)!!)
        }

        val principal = machinePrincipalFor(accessToken.name)
        val created = store.createToken(
            principalId = principal.id,
            name = accessToken.name,
            secretHash = accessToken.encryptedSecret,
            scopes = null,
            expiresAt = accessToken.expiresAt,
            createdAt = accessToken.createdAt,
            description = accessToken.description,
        ) ?: error("Access token '${accessToken.name}' was created concurrently")
        ensurePrivateTeam(principal)
        return toAccessToken(created)
    }

    override fun deleteAccessToken(id: AccessTokenIdentifier) {
        val token = store.findTokenById(id.value) ?: return
        store.deleteToken(token.id)

        // The legacy rights were attached to the token. They end with its last token, as they always did.
        val principal = store.findPrincipalById(token.principalId) ?: return
        if (principal.kind == PrincipalKind.MACHINE && store.tokensOf(principal.id).isEmpty()) {
            findPrivateTeam(principal)?.let { store.deleteLegacyGrants(it.id, pathPrefix = null, role = null) }
            store.revokeInstanceRole(principal.id, teamId = null)
        }
    }

    override fun addPermission(id: AccessTokenIdentifier, permission: AccessTokenPermission): AccessTokenPermission {
        store.grantInstanceRole(principalId = principalOf(id).id, teamId = null)
        return permission
    }

    override fun deletePermission(id: AccessTokenIdentifier, permission: AccessTokenPermission) {
        store.revokeInstanceRole(principalId = principalOf(id).id, teamId = null)
    }

    override fun findAccessTokenPermissionsById(id: AccessTokenIdentifier): Set<AccessTokenPermission> {
        val principal = principalOrNull(id) ?: return emptySet()
        return if (store.hasDirectInstanceRole(principal.id)) setOf(MANAGER) else emptySet()
    }

    override fun addRoute(id: AccessTokenIdentifier, route: Route): Route {
        val principal = principalOf(id)
        val team = ensurePrivateTeam(principal)
        val role = legacyRole(route.permission)
        val exists = store.listGrants(team.id).any { it.pathPrefix == route.path && it.role == role }
        if (!exists) {
            store.addGrant(team.id, repository = null, pathPrefix = route.path, role = role)
        }
        return route
    }

    override fun deleteRoute(id: AccessTokenIdentifier, route: Route) {
        val team = principalOrNull(id)?.let { findPrivateTeam(it) } ?: return
        store.deleteLegacyGrants(team.id, pathPrefix = route.path, role = legacyRole(route.permission))
    }

    override fun deleteRoutesByPath(id: AccessTokenIdentifier, path: String) {
        val team = principalOrNull(id)?.let { findPrivateTeam(it) } ?: return
        store.deleteLegacyGrants(team.id, pathPrefix = path, role = null)
    }

    override fun findAccessTokenRoutesById(id: AccessTokenIdentifier): Set<Route> {
        val team = principalOrNull(id)?.let { findPrivateTeam(it) } ?: return emptySet()
        return store.listGrants(team.id)
            .filter { it.role.legacy && it.pathPrefix != null }
            .map { Route(it.pathPrefix!!, if (it.role == Role.LEGACY_WRITE) RoutePermission.WRITE else RoutePermission.READ) }
            .toSet()
    }

    override fun findAccessTokenById(id: AccessTokenIdentifier): AccessToken? =
        store.findTokenById(id.value)?.let { toAccessToken(it) }

    override fun findAccessTokenByName(name: String): AccessToken? =
        store.findToken(name)?.let { toAccessToken(it) }

    override fun findAll(): Collection<AccessToken> =
        store.listTokens().map { toAccessToken(it) }

    override fun countAccessTokens(): Long =
        store.countTokens()

    private fun toAccessToken(token: TokenRecord): AccessToken =
        AccessToken(
            identifier = AccessTokenIdentifier(PERSISTENT, token.id),
            name = token.name,
            encryptedSecret = token.secretHash,
            createdAt = token.createdAt,
            description = token.description,
            expiresAt = token.expiresAt,
        )

    /** The owner of a token that must exist; writes go through this, so a missing token is an error. */
    private fun principalOf(id: AccessTokenIdentifier): PrincipalRecord =
        principalOrNull(id) ?: error("Access token ${id.value} does not exist")

    /** The owner of a token, or `null` when the token is gone. Reads use this, so a deleted token reads as empty. */
    private fun principalOrNull(id: AccessTokenIdentifier): PrincipalRecord? {
        if (id.value == UNINITIALIZED_ENTITY_ID) return null
        val token = store.findTokenById(id.value) ?: return null
        return store.findPrincipalById(token.principalId)
    }

    /**
     * The machine principal that owns a legacy token of this name. An existing machine principal with exactly the same
     * name is reused, so a token that was revoked and created again keeps its principal. A principal whose name differs
     * only in case, or that is a user, refuses: attaching the token would hand it that principal's rights.
     */
    private fun machinePrincipalFor(name: String): PrincipalRecord {
        val existing = store.findPrincipal(name)
        if (existing == null) {
            return store.createPrincipal(PrincipalKind.MACHINE, name, name, disabled = false)
                ?: error("Principal '$name' was created concurrently")
        }
        check(existing.kind == PrincipalKind.MACHINE && existing.name == name) {
            "Name '$name' is already used by principal '${existing.name}' of kind ${existing.kind}"
        }
        return existing
    }

    private fun findPrivateTeam(principal: PrincipalRecord): TeamRecord? =
        store.findTeam(privateTeamName(principal.name))

    private fun ensurePrivateTeam(principal: PrincipalRecord): TeamRecord {
        val team = findPrivateTeam(principal)
            ?: store.createTeam(privateTeamName(principal.name), "Private team of machine principal ${principal.name}", externalRef = null)
            ?: error("Private team of '${principal.name}' was created concurrently")
        store.addMember(team.id, principal.id, TeamMemberSource.MANUAL)
        return team
    }

    private fun legacyRole(permission: RoutePermission): Role =
        if (permission == RoutePermission.WRITE) Role.LEGACY_WRITE else Role.LEGACY_READ

}
