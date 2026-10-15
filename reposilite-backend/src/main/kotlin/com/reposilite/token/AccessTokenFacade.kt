/*
 * Copyright (c) 2023 dzikoysk
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
package com.reposilite.token

import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.audit.api.AuditAction.TOKEN_CREATED
import com.reposilite.audit.api.AuditAction.TOKEN_DELETED
import com.reposilite.audit.api.AuditAction.TOKEN_ROUTES_CHANGED
import com.reposilite.audit.api.AuditAction.TOKEN_SECRET_REGENERATED
import com.reposilite.audit.api.AuditActor
import com.reposilite.audit.api.AuditEvent
import com.reposilite.journalist.Journalist
import com.reposilite.journalist.Logger
import com.reposilite.plugin.Extensions
import com.reposilite.plugin.api.Facade
import com.reposilite.shared.EntityTag
import com.reposilite.shared.EntityTagSigner
import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.errorResponse
import com.reposilite.shared.notFoundError
import com.reposilite.token.AccessTokenPermission.MANAGER
import net.onelitefeather.ingot.identity.evaluation.AccessRequest
import net.onelitefeather.ingot.identity.evaluation.AccessSubjectSource
import net.onelitefeather.ingot.identity.evaluation.PermissionEvaluator
import net.onelitefeather.ingot.identity.model.AccessSubject
import net.onelitefeather.ingot.identity.model.Action
import net.onelitefeather.ingot.identity.model.Grant
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.model.Role
import net.onelitefeather.ingot.identity.model.privateTeamName
import com.reposilite.token.AccessTokenSecurityProvider.generateSecret
import com.reposilite.token.AccessTokenType.PERSISTENT
import com.reposilite.token.api.AccessTokenDetails
import com.reposilite.token.api.AccessTokenDetailsDto
import com.reposilite.token.api.AccessTokenDto
import com.reposilite.token.api.CreateAccessTokenRequest
import com.reposilite.token.api.CreateAccessTokenResponse
import com.reposilite.token.api.SecretType
import com.reposilite.token.api.SecretType.ENCRYPTED
import com.reposilite.token.api.SecretType.RAW
import com.reposilite.token.api.UpdateAccessTokenRequest
import io.javalin.http.HttpStatus.PRECONDITION_FAILED
import panda.std.Result
import panda.std.asSuccess
import panda.std.letIf
import java.nio.file.Path
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Stored state of a token together with the entity tag that identifies exactly that state.
 */
data class AccessTokenSnapshot<T>(
    val value: T,
    val etag: String,
)

/**
 * Access tokens and their rights. Every change that an operator can make emits an [AuditEvent];
 * the methods that take an [actor] record who asked for it, and the rest record the system.
 * Secrets are never part of an event. Every change also notifies the listeners registered with
 * [onTokenChanged], so that state derived from a token can be dropped at once.
 */
class AccessTokenFacade internal constructor(
    private val journalist: Journalist,
    private val temporaryRepository: AccessTokenRepository,
    private val persistentRepository: AccessTokenRepository,
    private val exportService: ExportService,
    private val extensions: Extensions = Extensions(journalist),
    private val entityTags: EntityTagSigner = EntityTagSigner.ephemeral(),
    private val subjectSource: AccessSubjectSource? = persistentRepository as? AccessSubjectSource,
) : Facade, Journalist {

    /**
     * Serializes the conditional writes of this instance, so a precondition is checked against the state it writes.
     */
    private val mutationLock = Any()

    private val changeListeners = CopyOnWriteArrayList<(String) -> Unit>()

    /**
     * Registers a callback that receives the name of every token whose stored state changed on this instance, and the old
     * name of a renamed token. The callback runs after the change has been written. Other instances of a cluster are not
     * notified.
     */
    fun onTokenChanged(listener: (tokenName: String) -> Unit) {
        changeListeners.add(listener)
    }

    private fun tokenChanged(vararg names: String) {
        names.forEach { name -> changeListeners.forEach { it(name) } }
    }

    /**
     * Creates the token with the given name. [audited] is `false` only for the startup bootstrap, which re-applies the
     * `--token` configuration on every start: those creations are not part of what an operator did, so they are not recorded.
     */
    fun createAccessToken(
        request: CreateAccessTokenRequest,
        actor: AuditActor = AuditActor.SYSTEM_ACTOR,
        audited: Boolean = true,
    ): CreateAccessTokenResponse =
        storeAccessToken(request, actor, audited).response

    private fun storeAccessToken(request: CreateAccessTokenRequest, actor: AuditActor, audited: Boolean): StoredAccessToken {
        val existingToken = request.type.getRepository().findAccessTokenByName(request.name)
        val oppositeToken = request.type.getOppositeRepository().findAccessTokenByName(request.name)
        val before = snapshotAccess(existingToken?.identifier)

        val secret = request.secret ?: generateSecret()
        val encodedSecret =
            when (request.secretType) {
                RAW -> AccessTokenSecurityProvider.encodeSecret(secret)
                ENCRYPTED -> secret
            }

        val savedToken =
            request.type.getRepository()
                .saveAccessToken(
                    AccessToken(
                        identifier = existingToken?.identifier ?: AccessTokenIdentifier(type = request.type),
                        name = request.name,
                        createdAt = LocalDate.now(),
                        encryptedSecret = encodedSecret,
                        description = request.description,
                        expiresAt = request.expiresAt,
                    )
                )
        oppositeToken?.let { request.type.getOppositeRepository().deleteAccessToken(it.identifier) }

        val routes = syncPermissionsAndRoutes(
            identifier = savedToken.identifier,
            permissions = request.permissions,
            routeRequests = request.routes
        )
        tokenChanged(request.name)

        if (audited) {
            extensions.emitEvent(
                AuditEvent(
                    action = TOKEN_CREATED,
                    actor = actor,
                    details = mapOf("token" to request.name, "type" to request.type.name, "replaced" to (existingToken != null)),
                )
            )
            recordAccessChanges(actor, request.name, before, snapshotAccess(savedToken.identifier))
        }

        return StoredAccessToken(
            token = savedToken,
            response = CreateAccessTokenResponse(
                accessToken = savedToken.toDto(),
                permissions = request.permissions,
                secret = secret,
                routes = routes
            )
        )
    }

    private data class StoredAccessToken(
        val token: AccessToken,
        val response: CreateAccessTokenResponse,
    )

    /**
     * Creates or replaces the token with the given name. When [ifMatch] is present the write is applied only while the entity tag
     * still describes the stored token, a missing token never matches.
     */
    fun upsertAccessToken(
        request: CreateAccessTokenRequest,
        ifMatch: String? = null,
        actor: AuditActor = AuditActor.SYSTEM_ACTOR,
    ): Result<AccessTokenSnapshot<CreateAccessTokenResponse>, ErrorResponse> {
        synchronized(mutationLock) {
            val existing = findRawAccessTokenByName(request.name)
            if (!EntityTag.matches(ifMatch, existing?.let { snapshotOf(it).etag })) {
                return preconditionFailed(request.name)
            }

            val stored = storeAccessToken(request, actor, audited = true)
            return AccessTokenSnapshot(stored.response, snapshotOf(stored.token).etag).asSuccess()
        }
    }

    /**
     * Updates the metadata of the token with the given name. When [ifMatch] is present the update is applied only while the entity
     * tag still describes the stored token.
     */
    fun updateAccessToken(
        name: String,
        request: UpdateAccessTokenRequest,
        ifMatch: String? = null,
        actor: AuditActor = AuditActor.SYSTEM_ACTOR,
    ): Result<AccessTokenSnapshot<AccessTokenDetailsDto>, ErrorResponse> {
        synchronized(mutationLock) {
            val existing = findRawAccessTokenByName(name) ?: return notFoundError("Token not found")
            if (!EntityTag.matches(ifMatch, snapshotOf(existing).etag)) {
                return preconditionFailed(name)
            }
            val before = snapshotAccess(existing.identifier)

            val updated = existing.identifier.type.getRepository()
                .saveAccessToken(existing.copy(
                    description = request.description ?: existing.description,
                    expiresAt = if (request.updateExpiresAt) request.expiresAt else existing.expiresAt,
                ))

            syncPermissionsAndRoutes(updated.identifier, request.permissions, request.routes)
            tokenChanged(updated.name)
            recordAccessChanges(actor, updated.name, before, snapshotAccess(updated.identifier))

            return snapshotOf(updated).asSuccess()
        }
    }

    /**
     * Details of the token with the given name together with the entity tag of its stored state.
     */
    fun getAccessTokenSnapshot(name: String): AccessTokenSnapshot<AccessTokenDetailsDto>? {
        synchronized(mutationLock) {
            return findRawAccessTokenByName(name)?.let { snapshotOf(it) }
        }
    }

    private fun findRawAccessTokenByName(name: String): AccessToken? =
        temporaryRepository.findAccessTokenByName(name) ?: persistentRepository.findAccessTokenByName(name)

    private fun <V> preconditionFailed(name: String): Result<V, ErrorResponse> =
        errorResponse(PRECONDITION_FAILED, "Token '$name' has been changed since it was read, reload it and try again")

    /**
     * Built from the stored token itself rather than looked up again, since a lookup evicts expired tokens and a token
     * updated to a past expiry must still yield its details. The entity tag covers everything a client can change through
     * the API, the stored secret hash included, so regenerating a secret also invalidates earlier tags. The raw secret is
     * never part of it.
     */
    private fun snapshotOf(raw: AccessToken): AccessTokenSnapshot<AccessTokenDetailsDto> {
        val permissions = getPermissions(raw.identifier)
        val routes = getRoutes(raw.identifier)
        val fingerprint = mapOf(
            "identifier" to raw.identifier,
            "name" to raw.name,
            "createdAt" to raw.createdAt,
            "description" to raw.description,
            "expiresAt" to raw.expiresAt,
            "encryptedSecret" to raw.encryptedSecret,
            "permissions" to permissions.map { it.identifier }.sorted(),
            "routes" to routes.map { "${it.permission.identifier}:${it.path}" }.sorted(),
        )
        return AccessTokenSnapshot(
            value = AccessTokenDetailsDto.of(raw.toDto(), permissions, routes),
            etag = entityTags.of(DEFAULT_OBJECT_MAPPER.writeValueAsBytes(fingerprint)),
        )
    }

    /**
     * Applies the requested permissions and routes through the repositories directly, so the caller
     * records one event for the whole change instead of one per row.
     */
    private fun syncPermissionsAndRoutes(
        identifier: AccessTokenIdentifier,
        permissions: Set<AccessTokenPermission>?,
        routeRequests: Set<CreateAccessTokenRequest.Route>?,
    ): Set<Route> {
        val repository = identifier.type.getRepository()

        permissions?.let {
            val currentPermissions = getPermissions(identifier)
            (currentPermissions - it).forEach { permission -> repository.deletePermission(identifier, permission) }
            (it - currentPermissions).forEach { permission -> repository.addPermission(identifier, permission) }
        }

        if (routeRequests == null) return getRoutes(identifier)

        val routes = routeRequests.flatMapTo(HashSet()) { route -> route.permissions.map { Route(route.path, it) } }
        val currentRoutes = getRoutes(identifier)
        (currentRoutes - routes).forEach { repository.deleteRoute(identifier, it) }
        (routes - currentRoutes).forEach { repository.addRoute(identifier, it) }
        return routes
    }

    private data class AccessSnapshot(
        val permissions: Set<AccessTokenPermission>,
        val routes: Set<Route>,
    )

    private fun snapshotAccess(identifier: AccessTokenIdentifier?): AccessSnapshot =
        if (identifier == null) {
            AccessSnapshot(permissions = emptySet(), routes = emptySet())
        } else {
            AccessSnapshot(permissions = getPermissions(identifier), routes = getRoutes(identifier))
        }

    private fun recordAccessChanges(actor: AuditActor, token: String, before: AccessSnapshot, after: AccessSnapshot) {
        emitRightsChanged(
            actor = actor,
            token = token,
            permissionsAdded = after.permissions - before.permissions,
            permissionsRemoved = before.permissions - after.permissions,
            routesAdded = after.routes - before.routes,
            routesRemoved = before.routes - after.routes,
        )
    }

    /**
     * Records a change of what a token may do. Nothing is emitted for an empty change.
     */
    private fun emitRightsChanged(
        actor: AuditActor,
        token: String,
        permissionsAdded: Collection<AccessTokenPermission> = emptySet(),
        permissionsRemoved: Collection<AccessTokenPermission> = emptySet(),
        routesAdded: Collection<Route> = emptySet(),
        routesRemoved: Collection<Route> = emptySet(),
    ) {
        if (permissionsAdded.isEmpty() && permissionsRemoved.isEmpty() && routesAdded.isEmpty() && routesRemoved.isEmpty()) {
            return
        }

        extensions.emitEvent(
            AuditEvent(
                action = TOKEN_ROUTES_CHANGED,
                actor = actor,
                details = mapOf(
                    "token" to token,
                    "permissionsAdded" to permissionsAdded.map { it.identifier },
                    "permissionsRemoved" to permissionsRemoved.map { it.identifier },
                    "routesAdded" to routesAdded.map { it.describe() },
                    "routesRemoved" to routesRemoved.map { it.describe() },
                ),
            )
        )
    }

    private fun Route.describe(): String =
        "$path (${permission.identifier})"

    private fun nameOf(identifier: AccessTokenIdentifier): String =
        getRawAccessTokenById(identifier)?.name ?: "unknown"

    fun addAccessToken(accessTokenDetails: AccessTokenDetails): AccessTokenDto =
        with (accessTokenDetails) {
            val (accessTokenDto) = createAccessToken(
                CreateAccessTokenRequest(
                    type = accessToken.identifier.type,
                    name = accessToken.name,
                    secretType = ENCRYPTED,
                    secret = accessToken.encryptedSecret,
                    description = accessToken.description
                )
            )

            permissions.forEach {
                addPermission(accessTokenDto.identifier, it)
            }

            routes.forEach {
                addRoute(accessTokenDto.identifier, Route(it.path, it.permission))
            }

            accessTokenDto
        }

    fun exportToFile(toFile: Path): Path =
        getAccessTokens()
            .map { getAccessTokenDetailsById(it.identifier)!! }
            .let { exportService.exportToFile(it, toFile) }

    fun importFromFile(fromFile: Path): Result<Collection<AccessTokenDetails>, Exception> =
        exportService.importFromFile(fromFile)

    fun secretMatches(id: AccessTokenIdentifier, secret: String): Boolean =
        getRawAccessTokenById(id)
            ?.let { AccessTokenSecurityProvider.matches(it.encryptedSecret, secret) }
            ?: false

    /**
     * Grants the permission. [audited] is `false` only for the startup bootstrap, see [createAccessToken].
     */
    fun addPermission(
        identifier: AccessTokenIdentifier,
        permission: AccessTokenPermission,
        actor: AuditActor = AuditActor.SYSTEM_ACTOR,
        audited: Boolean = true,
    ): AccessTokenPermission {
        val granted = identifier.type.getRepository().addPermission(identifier, permission)
        tokenChanged(nameOf(identifier))
        if (audited) {
            emitRightsChanged(actor, nameOf(identifier), permissionsAdded = listOf(permission))
        }
        return granted
    }

    /**
     * The rights of the owner of a token. Persistent tokens are read from the identity tables. Temporary tokens, and
     * persistent tokens that the identity tables do not know, are read from their legacy rights.
     */
    fun subjectOf(identifier: AccessTokenIdentifier): AccessSubject {
        if (identifier.type == PERSISTENT) {
            subjectSource?.subjectOf(identifier)?.let { return it }
        }
        return legacySubjectOf(identifier)
    }

    private fun legacySubjectOf(identifier: AccessTokenIdentifier): AccessSubject {
        val repository = identifier.type.getRepository()
        val permissions = repository.findAccessTokenPermissionsById(identifier)
        val routes = repository.findAccessTokenRoutesById(identifier)
        val name = nameOf(identifier)
        return AccessSubject(
            principalId = null,
            principalName = name,
            kind = PrincipalKind.MACHINE,
            disabled = false,
            instanceAdmin = MANAGER in permissions,
            grants = routes.map { route ->
                Grant(
                    team = privateTeamName(name),
                    role = if (route.permission == RoutePermission.WRITE) Role.LEGACY_WRITE else Role.LEGACY_READ,
                    repository = null,
                    pathPrefix = route.path,
                )
            },
            scopes = null,
            teams = listOf(privateTeamName(name)),
        )
    }

    /** Drops the state a client may have cached for the token. Called after any change that affects authentication. */
    fun tokenStateChanged(tokenName: String) {
        tokenChanged(tokenName)
    }

    fun hasPermission(identifier: AccessTokenIdentifier, permission: AccessTokenPermission): Boolean =
        when (permission) {
            MANAGER -> PermissionEvaluator.isInstanceAdmin(subjectOf(identifier))
        }

    fun hasPermissionTo(identifier: AccessTokenIdentifier, toPath: String, requiredPermission: RoutePermission): Boolean =
        hasAccessTo(identifier, toPath, if (requiredPermission == RoutePermission.WRITE) Action.WRITE else Action.READ)

    /** Whether the owner of the token may perform [action] on [toPath], a full path such as `/repository/gav`. */
    fun hasAccessTo(identifier: AccessTokenIdentifier, toPath: String, action: Action): Boolean =
        PermissionEvaluator.allows(subjectOf(identifier), AccessRequest(toPath, action))

    fun canSee(identifier: AccessTokenIdentifier, pathFragment: String): Boolean =
        PermissionEvaluator.canSee(subjectOf(identifier), pathFragment)

    /** Whether a HIDDEN repository is listed for the token. See [PermissionEvaluator.canListHidden]. */
    fun canListHidden(identifier: AccessTokenIdentifier, repository: String): Boolean =
        PermissionEvaluator.canListHidden(subjectOf(identifier), repository)

    /** A disabled principal authenticates to nothing and is granted nothing. */
    fun isDisabled(identifier: AccessTokenIdentifier): Boolean =
        subjectOf(identifier).disabled

    fun deletePermission(identifier: AccessTokenIdentifier, permission: AccessTokenPermission, actor: AuditActor = AuditActor.SYSTEM_ACTOR) {
        identifier.type.getRepository().deletePermission(identifier, permission)
        tokenChanged(nameOf(identifier))
        emitRightsChanged(actor, nameOf(identifier), permissionsRemoved = listOf(permission))
    }

    fun getPermissions(identifier: AccessTokenIdentifier): Set<AccessTokenPermission> =
        identifier.type.getRepository().findAccessTokenPermissionsById(identifier)

    fun addRoute(identifier: AccessTokenIdentifier, route: Route, actor: AuditActor = AuditActor.SYSTEM_ACTOR): Route {
        val added = identifier.type.getRepository().addRoute(identifier, route)
        tokenChanged(nameOf(identifier))
        emitRightsChanged(actor, nameOf(identifier), routesAdded = listOf(route))
        return added
    }

    fun deleteRoute(identifier: AccessTokenIdentifier, route: Route, actor: AuditActor = AuditActor.SYSTEM_ACTOR) {
        identifier.type.getRepository().deleteRoute(identifier, route)
        tokenChanged(nameOf(identifier))
        emitRightsChanged(actor, nameOf(identifier), routesRemoved = listOf(route))
    }

    fun deleteRoutesByPath(identifier: AccessTokenIdentifier, path: String, actor: AuditActor = AuditActor.SYSTEM_ACTOR) {
        val removed = getRoutes(identifier).filter { it.path == path }
        identifier.type.getRepository().deleteRoutesByPath(identifier, path)
        tokenChanged(nameOf(identifier))
        emitRightsChanged(actor, nameOf(identifier), routesRemoved = removed)
    }

    fun getRoutes(id: AccessTokenIdentifier): Set<Route> =
        id.type.getRepository().findAccessTokenRoutesById(id)

    fun updateToken(updatedToken: AccessTokenDto): AccessTokenDto? {
        val existing = getRawAccessTokenById(updatedToken.identifier) ?: return null
        val saved = existing.identifier.type.getRepository().saveAccessToken(
            existing.copy(
                name = updatedToken.name,
                createdAt = updatedToken.createdAt,
                description = updatedToken.description
            )
        )
        tokenChanged(existing.name, saved.name)
        return saved.toDto()
    }

    fun deleteToken(id: AccessTokenIdentifier, actor: AuditActor = AuditActor.SYSTEM_ACTOR): Result<Unit, ErrorResponse> {
        val token = getRawAccessTokenById(id) ?: return notFoundError("Token not found")
        token.identifier.type.getRepository().deleteAccessToken(token.identifier)
        tokenChanged(token.name)
        extensions.emitEvent(AuditEvent(action = TOKEN_DELETED, actor = actor, details = mapOf("token" to token.name)))
        return Unit.asSuccess()
    }

    fun regenerateAccessToken(
        accessTokenDto: AccessTokenDto,
        secret: String?,
        secretType: SecretType = RAW,
        actor: AuditActor = AuditActor.SYSTEM_ACTOR,
    ): Result<String, ErrorResponse> {
        val rawSecret = secret ?: generateSecret()
        val encodedSecret = rawSecret.letIf(secretType == RAW) { AccessTokenSecurityProvider.encodeSecret(it) } // encode if not already encoded

        val token = getRawAccessTokenById(accessTokenDto.identifier) ?: return notFoundError("Token not found")
        token.copy(encryptedSecret = encodedSecret).let { it.identifier.type.getRepository().saveAccessToken(it) }
        tokenChanged(token.name)

        // The event names the token only. The secret itself is never part of an audit record.
        extensions.emitEvent(AuditEvent(action = TOKEN_SECRET_REGENERATED, actor = actor, details = mapOf("token" to token.name)))
        return rawSecret.asSuccess()
    }

    private fun getRawAccessTokenById(id: AccessTokenIdentifier): AccessToken? =
        id.type.getRepository().findAccessTokenById(id)

    fun getAccessTokenById(id: AccessTokenIdentifier): AccessTokenDto? =
        getRawAccessTokenById(id)?.toDto()

    internal fun getAccessTokenDetailsById(id: AccessTokenIdentifier): AccessTokenDetails? =
        getRawAccessTokenById(id)?.let { AccessTokenDetails(it, getPermissions(id), getRoutes(id)) }

    fun getAccessToken(name: String): AccessTokenDto? =
        (temporaryRepository.findAccessTokenByName(name) ?: persistentRepository.findAccessTokenByName(name))?.toDto()

    fun getAccessTokens(): Collection<AccessTokenDto> =
        (temporaryRepository.findAll() + persistentRepository.findAll()).map { it.toDto() }

    fun getAccessTokenDetails(token: AccessTokenDto): AccessTokenDetailsDto =
        AccessTokenDetailsDto.of(token, getPermissions(token.identifier), getRoutes(token.identifier))

    fun getAccessTokensDetails(): Collection<AccessTokenDetailsDto> =
        getAccessTokens().map { getAccessTokenDetails(it) }

    fun count(): Long =
        temporaryRepository.countAccessTokens() + persistentRepository.countAccessTokens()

    private fun AccessTokenType.getRepository(): AccessTokenRepository =
        if (this == PERSISTENT) persistentRepository else temporaryRepository

    private fun AccessTokenType.getOppositeRepository(): AccessTokenRepository =
        if (this == PERSISTENT) temporaryRepository else persistentRepository

    override fun getLogger(): Logger =
        journalist.logger

}
