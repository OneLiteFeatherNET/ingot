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

package com.reposilite.token.infrastructure

import com.fasterxml.jackson.databind.JsonNode
import com.reposilite.audit.api.auditActor
import com.reposilite.shared.badRequest
import com.reposilite.shared.notFoundError
import com.reposilite.shared.toErrorResult
import com.reposilite.token.AccessTokenFacade
import com.reposilite.token.AccessTokenPermission
import com.reposilite.token.RoutePermission
import com.reposilite.token.api.AccessTokenDetailsDto
import com.reposilite.token.api.CreateAccessTokenRequest
import com.reposilite.token.api.CreateAccessTokenResponse
import com.reposilite.token.api.CreateAccessTokenWithNoNameRequest
import com.reposilite.token.api.UpdateAccessTokenRequest
import com.reposilite.token.api.UpdateAccessTokenWithNoNameRequest
import com.reposilite.web.api.ReposiliteRoute
import com.reposilite.web.api.ReposiliteRoutes
import io.javalin.community.routing.Route.DELETE
import io.javalin.community.routing.Route.GET
import io.javalin.community.routing.Route.PATCH
import io.javalin.community.routing.Route.POST
import io.javalin.community.routing.Route.PUT
import io.javalin.http.Header
import io.javalin.http.HttpStatus.FORBIDDEN
import io.javalin.http.bodyAsClass
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiRequestBody
import panda.std.Result.ok
import panda.std.Result.supplyThrowing
import panda.std.asSuccess

internal class AccessTokenApiEndpoints(private val accessTokenFacade: AccessTokenFacade) : ReposiliteRoutes() {

    @OpenApi(
        path = "/api/tokens",
        tags = ["Tokens"],
        summary = "Returns all existing tokens and data such as their permissions. Note: Requires the tokens area (ingot.admin.tokens)",
        methods = [HttpMethod.GET]
    )
    val findAllTokens = ReposiliteRoute<Collection<AccessTokenDetailsDto>>("/api/tokens", GET) {
        managerOnly("tokens") {
            response = ok(accessTokenFacade.getAccessTokensDetails())
        }
    }

    @OpenApi(
        path = "/api/tokens/{name}",
        tags = ["Tokens"],
        summary = "Returns data about the token given via it's name. Note: Requires manager or you must be the token owner",
        pathParams = [OpenApiParam(name = "name", description = "Name of the token to be deleted", required = true)],
        methods = [HttpMethod.GET]
    )
    val findToken = ReposiliteRoute<AccessTokenDetailsDto>("/api/tokens/{name}", GET) {
        authenticated {
            response = accessTokenFacade.getAccessTokenSnapshot(requireParameter("name"))
                ?.takeIf { requireArea("tokens").isOk || name == it.value.name }
                ?.let { snapshot ->
                    ctx.header(Header.ETAG, snapshot.etag)
                    snapshot.value
                }
                ?.asSuccess()
                ?: FORBIDDEN.toErrorResult("You must be the token owner or a manager to access this.")
        }
    }

    @OpenApi(
        path = "/api/tokens/{name}",
        tags = ["Tokens"],
        summary = "Creates / Updates a token via the specified body. Note: Requires manager permission.",
        requestBody = OpenApiRequestBody(
            content = [OpenApiContent(CreateAccessTokenWithNoNameRequest::class)],
            required = true,
            description = "Data about the account including the secret and it's permissions"
        ),
        pathParams = [OpenApiParam(name = "name", description = "Name of the token to be created or updated", required = true)],
        methods = [HttpMethod.PUT]
    )
    val upsertToken = ReposiliteRoute<CreateAccessTokenResponse>("/api/tokens/{name}", PUT) {
        managerOnly("tokens") {
            response = supplyThrowing { ctx.bodyAsClass<CreateAccessTokenWithNoNameRequest>() }
                .mapErr { badRequest("Failed to read body") }
                .filter({ request -> request.permissions.all { AccessTokenPermission.findByAny(it) != null } }) { unknownPermissionError() }
                .filter({ request -> request.routes.all { route -> route.permissions.all { RoutePermission.findRoutePermissionByShortcut(it).isOk }}}) {
                    unknownRouteError()
                }
                .flatMap { request ->
                    accessTokenFacade.upsertAccessToken(
                        CreateAccessTokenRequest(
                            type = request.type,
                            secret = request.secret,
                            secretType = request.secretType,
                            name = requireParameter("name"),
                            permissions = toPermissions(request.permissions),
                            routes = toRequestRoutes(request.routes),
                            expiresAt = request.expiresAt,
                            description = request.description,
                        ),
                        ifMatch = ctx.header(Header.IF_MATCH),
                        actor = auditActor(),
                    )
                }
                .map { snapshot ->
                    ctx.header(Header.ETAG, snapshot.etag)
                    snapshot.value
                }
        }
    }

    @OpenApi(
        path = "/api/tokens/{name}",
        tags = ["Tokens"],
        summary = "Updates token metadata (description, permissions, routes, expiry) without regenerating the secret. Note: Requires the tokens area (ingot.admin.tokens)",
        requestBody = OpenApiRequestBody(
            content = [OpenApiContent(UpdateAccessTokenWithNoNameRequest::class)],
            required = true,
            description = "Token metadata to update. Any field that is not provided is left unchanged"
        ),
        pathParams = [OpenApiParam(name = "name", description = "Name of the token to be updated", required = true)],
        methods = [HttpMethod.PATCH]
    )
    val patchToken = ReposiliteRoute<AccessTokenDetailsDto>("/api/tokens/{name}", PATCH) {
        managerOnly("tokens") {
            response = supplyThrowing { ctx.bodyAsClass<UpdateAccessTokenWithNoNameRequest>() to ctx.bodyAsClass<JsonNode>().has("expiresAt") }
                .mapErr { badRequest("Failed to read body") }
                .filter({ (request, _) -> request.permissions.orEmpty().all { AccessTokenPermission.findByAny(it) != null } }) {
                    unknownPermissionError()
                }
                .filter({ (request, _) -> request.routes.orEmpty().all { route -> route.permissions.all { RoutePermission.findRoutePermissionByShortcut(it).isOk }}}) {
                    unknownRouteError()
                }
                .flatMap { (request, expiresAtProvided) ->
                    accessTokenFacade.updateAccessToken(
                        requireParameter("name"),
                        UpdateAccessTokenRequest(
                            description = request.description,
                            permissions = request.permissions?.let { toPermissions(it) },
                            routes = request.routes?.let { toRequestRoutes(it) },
                            expiresAt = request.expiresAt,
                            updateExpiresAt = expiresAtProvided,
                        ),
                        ifMatch = ctx.header(Header.IF_MATCH),
                        actor = auditActor(),
                    )
                }
                .map { snapshot ->
                    ctx.header(Header.ETAG, snapshot.etag)
                    snapshot.value
                }
        }
    }

    @OpenApi(
        path = "/api/tokens/{name}/secret",
        tags = ["Tokens"],
        summary = "Regenerates and returns a new secret for the token, invalidating the previous one. Note: Requires the tokens area (ingot.admin.tokens)",
        pathParams = [OpenApiParam(name = "name", description = "Name of the token whose secret is regenerated", required = true)],
        methods = [HttpMethod.POST]
    )
    val regenerateSecret = ReposiliteRoute<String>("/api/tokens/{name}/secret", POST) {
        managerOnly("tokens") {
            response = accessTokenFacade.getAccessToken(requireParameter("name"))
                ?.let { accessTokenFacade.regenerateAccessToken(it, secret = null, actor = auditActor()) }
                ?: notFoundError("Token not found")
        }
    }

    @OpenApi(
        path = "/api/tokens/{name}",
        tags = ["Tokens"],
        summary = "Deletes the token specified via it's name. Note: Requires the tokens area (ingot.admin.tokens)",
        pathParams = [OpenApiParam(name = "name", description = "Name of the token to be deleted", required = true)],
        methods = [HttpMethod.DELETE]
    )
    val deleteToken = ReposiliteRoute<Unit>("/api/tokens/{name}", DELETE) {
        managerOnly("tokens") {
            response = accessTokenFacade.getAccessToken(requireParameter("name"))
                ?.let { accessTokenFacade.deleteToken(it.identifier, actor = auditActor()) }
                ?: notFoundError("Token not found")
        }
    }

    private fun unknownPermissionError() =
        badRequest("Unknown access token permission, supported: ${AccessTokenPermission.entries.joinToString { it.identifier }}")

    private fun unknownRouteError() =
        badRequest("Unknown route permission, supported: ${RoutePermission.entries.joinToString { it.shortcut }}")

    private fun toPermissions(permissions: Set<String>) =
        permissions.mapNotNullTo(HashSet()) { AccessTokenPermission.findByAny(it) }

    private fun toRequestRoutes(routes: Set<CreateAccessTokenWithNoNameRequest.Route>) =
        routes.mapTo(HashSet()) { route ->
            CreateAccessTokenRequest.Route(
                path = route.path,
                permissions = route.permissions.mapNotNullTo(HashSet()) { RoutePermission.findRoutePermissionByShortcut(it).orNull() },
            )
        }

    override val routes = routes(findAllTokens, findToken, upsertToken, patchToken, regenerateSecret, deleteToken)

}
