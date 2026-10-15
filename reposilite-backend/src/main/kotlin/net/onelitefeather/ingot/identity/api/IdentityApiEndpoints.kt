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

import com.reposilite.audit.api.auditActor
import com.reposilite.shared.ContextDsl
import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.badRequest
import com.reposilite.web.api.ReposiliteRoute
import com.reposilite.web.api.ReposiliteRoutes
import io.javalin.community.routing.Route.DELETE
import io.javalin.community.routing.Route.GET
import io.javalin.community.routing.Route.PATCH
import io.javalin.community.routing.Route.POST
import io.javalin.community.routing.Route.PUT
import io.javalin.http.Header
import io.javalin.http.bodyAsClass
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiRequestBody
import net.onelitefeather.ingot.identity.service.IdentityFacade
import panda.std.Result
import panda.std.Result.supplyThrowing

/**
 * The identity administration API. Every route requires an instance admin. Mutations accept If-Match, and reads
 * return the entity tag of the state they read.
 */
internal class IdentityApiEndpoints(private val identity: IdentityFacade) : ReposiliteRoutes() {

    @OpenApi(
        path = "/api/identity/principals",
        tags = ["Identity"],
        summary = "Lists the principals (users and machine accounts). Note: Requires Manager",
        methods = [HttpMethod.GET],
    )
    val listPrincipals = ReposiliteRoute<Collection<PrincipalSummaryDto>>("/api/identity/principals", GET) {
        managerOnly {
            response = identity.listPrincipals(queryParameter("kind"))
        }
    }

    @OpenApi(
        path = "/api/identity/principals",
        tags = ["Identity"],
        summary = "Creates a principal. Note: Requires Manager",
        requestBody = OpenApiRequestBody(content = [OpenApiContent(CreatePrincipalRequest::class)], required = true),
        methods = [HttpMethod.POST],
    )
    val createPrincipal = ReposiliteRoute<PrincipalDetailDto>("/api/identity/principals", POST) {
        managerOnly {
            response = readBody<CreatePrincipalRequest>()
                .flatMap { request -> identity.createPrincipal(request, actor = auditActor()) }
                .map { snapshot ->
                    ctx.header(Header.ETAG, snapshot.etag)
                    snapshot.value
                }
        }
    }

    @OpenApi(
        path = "/api/identity/principals/{name}",
        tags = ["Identity"],
        summary = "Returns a principal with its teams and tokens (without secrets). Note: Requires Manager",
        pathParams = [OpenApiParam(name = "name", description = "Name of the principal", required = true)],
        methods = [HttpMethod.GET],
    )
    val getPrincipal = ReposiliteRoute<PrincipalDetailDto>("/api/identity/principals/{name}", GET) {
        managerOnly {
            response = identity.getPrincipal(requireParameter("name"))
                .map { snapshot ->
                    ctx.header(Header.ETAG, snapshot.etag)
                    snapshot.value
                }
        }
    }

    @OpenApi(
        path = "/api/identity/principals/{name}",
        tags = ["Identity"],
        summary = "Updates the display name or the disabled flag of a principal. Note: Requires Manager",
        pathParams = [OpenApiParam(name = "name", description = "Name of the principal", required = true)],
        requestBody = OpenApiRequestBody(content = [OpenApiContent(UpdatePrincipalRequest::class)], required = true),
        methods = [HttpMethod.PATCH],
    )
    val updatePrincipal = ReposiliteRoute<PrincipalDetailDto>("/api/identity/principals/{name}", PATCH) {
        managerOnly {
            response = readBody<UpdatePrincipalRequest>()
                .flatMap { request -> identity.updatePrincipal(requireParameter("name"), request, ctx.header(Header.IF_MATCH), actor = auditActor()) }
                .map { snapshot ->
                    ctx.header(Header.ETAG, snapshot.etag)
                    snapshot.value
                }
        }
    }

    @OpenApi(
        path = "/api/identity/principals/{name}",
        tags = ["Identity"],
        summary = "Deletes a principal with its tokens, memberships and instance role. Note: Requires Manager",
        pathParams = [OpenApiParam(name = "name", description = "Name of the principal", required = true)],
        methods = [HttpMethod.DELETE],
    )
    val deletePrincipal = ReposiliteRoute<Unit>("/api/identity/principals/{name}", DELETE) {
        managerOnly {
            response = identity.deletePrincipal(requireParameter("name"), ctx.header(Header.IF_MATCH), actor = auditActor())
        }
    }

    @OpenApi(
        path = "/api/identity/principals/{name}/tokens",
        tags = ["Identity"],
        summary = "Lists the tokens of a principal. Note: Requires Manager",
        pathParams = [OpenApiParam(name = "name", description = "Name of the principal", required = true)],
        methods = [HttpMethod.GET],
    )
    val listTokens = ReposiliteRoute<Collection<TokenDto>>("/api/identity/principals/{name}/tokens", GET) {
        managerOnly {
            response = identity.listTokens(requireParameter("name"))
        }
    }

    @OpenApi(
        path = "/api/identity/principals/{name}/tokens",
        tags = ["Identity"],
        summary = "Creates a token for a principal. The secret is returned once. Note: Requires Manager",
        pathParams = [OpenApiParam(name = "name", description = "Name of the principal", required = true)],
        requestBody = OpenApiRequestBody(content = [OpenApiContent(CreateTokenRequest::class)], required = true),
        methods = [HttpMethod.POST],
    )
    val createToken = ReposiliteRoute<CreatedTokenDto>("/api/identity/principals/{name}/tokens", POST) {
        managerOnly {
            response = readBody<CreateTokenRequest>()
                .flatMap { request -> identity.createToken(requireParameter("name"), request, actor = auditActor()) }
        }
    }

    @OpenApi(
        path = "/api/identity/principals/{name}/tokens/{tokenName}",
        tags = ["Identity"],
        summary = "Deletes a token of a principal. Note: Requires Manager",
        pathParams = [
            OpenApiParam(name = "name", description = "Name of the principal", required = true),
            OpenApiParam(name = "tokenName", description = "Name of the token", required = true),
        ],
        methods = [HttpMethod.DELETE],
    )
    val deleteToken = ReposiliteRoute<Unit>("/api/identity/principals/{name}/tokens/{tokenName}", DELETE) {
        managerOnly {
            response = identity.deleteToken(requireParameter("name"), requireParameter("tokenName"), actor = auditActor())
        }
    }

    @OpenApi(
        path = "/api/identity/teams",
        tags = ["Identity"],
        summary = "Lists the teams with their member and grant counts. Note: Requires Manager",
        methods = [HttpMethod.GET],
    )
    val listTeams = ReposiliteRoute<Collection<TeamDto>>("/api/identity/teams", GET) {
        managerOnly {
            response = identity.listTeams()
        }
    }

    @OpenApi(
        path = "/api/identity/teams",
        tags = ["Identity"],
        summary = "Creates a team. Note: Requires Manager",
        requestBody = OpenApiRequestBody(content = [OpenApiContent(CreateTeamRequest::class)], required = true),
        methods = [HttpMethod.POST],
    )
    val createTeam = ReposiliteRoute<TeamDetailDto>("/api/identity/teams", POST) {
        managerOnly {
            response = readBody<CreateTeamRequest>()
                .flatMap { request -> identity.createTeam(request, actor = auditActor()) }
                .map { snapshot ->
                    ctx.header(Header.ETAG, snapshot.etag)
                    snapshot.value
                }
        }
    }

    @OpenApi(
        path = "/api/identity/teams/{name}",
        tags = ["Identity"],
        summary = "Returns a team with its members and grants. Note: Requires Manager",
        pathParams = [OpenApiParam(name = "name", description = "Name of the team", required = true)],
        methods = [HttpMethod.GET],
    )
    val getTeam = ReposiliteRoute<TeamDetailDto>("/api/identity/teams/{name}", GET) {
        managerOnly {
            response = identity.getTeam(requireParameter("name"))
                .map { snapshot ->
                    ctx.header(Header.ETAG, snapshot.etag)
                    snapshot.value
                }
        }
    }

    @OpenApi(
        path = "/api/identity/teams/{name}",
        tags = ["Identity"],
        summary = "Updates the description or the external reference of a team. Note: Requires Manager",
        pathParams = [OpenApiParam(name = "name", description = "Name of the team", required = true)],
        requestBody = OpenApiRequestBody(content = [OpenApiContent(UpdateTeamRequest::class)], required = true),
        methods = [HttpMethod.PATCH],
    )
    val updateTeam = ReposiliteRoute<TeamDetailDto>("/api/identity/teams/{name}", PATCH) {
        managerOnly {
            response = readBody<UpdateTeamRequest>()
                .flatMap { request -> identity.updateTeam(requireParameter("name"), request, ctx.header(Header.IF_MATCH), actor = auditActor()) }
                .map { snapshot ->
                    ctx.header(Header.ETAG, snapshot.etag)
                    snapshot.value
                }
        }
    }

    @OpenApi(
        path = "/api/identity/teams/{name}",
        tags = ["Identity"],
        summary = "Deletes a team with its memberships and grants. Note: Requires Manager",
        pathParams = [OpenApiParam(name = "name", description = "Name of the team", required = true)],
        methods = [HttpMethod.DELETE],
    )
    val deleteTeam = ReposiliteRoute<Unit>("/api/identity/teams/{name}", DELETE) {
        managerOnly {
            response = identity.deleteTeam(requireParameter("name"), ctx.header(Header.IF_MATCH), actor = auditActor())
        }
    }

    @OpenApi(
        path = "/api/identity/teams/{name}/members",
        tags = ["Identity"],
        summary = "Replaces the manual members of a team. Synchronised members are kept. Note: Requires Manager",
        pathParams = [OpenApiParam(name = "name", description = "Name of the team", required = true)],
        requestBody = OpenApiRequestBody(content = [OpenApiContent(ReplaceMembersRequest::class)], required = true),
        methods = [HttpMethod.PUT],
    )
    val replaceMembers = ReposiliteRoute<TeamDetailDto>("/api/identity/teams/{name}/members", PUT) {
        managerOnly {
            response = readBody<ReplaceMembersRequest>()
                .flatMap { request -> identity.replaceMembers(requireParameter("name"), request.members, ctx.header(Header.IF_MATCH), actor = auditActor()) }
                .map { snapshot ->
                    ctx.header(Header.ETAG, snapshot.etag)
                    snapshot.value
                }
        }
    }

    @OpenApi(
        path = "/api/identity/teams/{name}/grants",
        tags = ["Identity"],
        summary = "Replaces all grants of a team. Roles: READ, DEPLOY, MAINTAIN, ADMIN. Note: Requires Manager",
        pathParams = [OpenApiParam(name = "name", description = "Name of the team", required = true)],
        requestBody = OpenApiRequestBody(content = [OpenApiContent(ReplaceGrantsRequest::class)], required = true),
        methods = [HttpMethod.PUT],
    )
    val replaceGrants = ReposiliteRoute<TeamDetailDto>("/api/identity/teams/{name}/grants", PUT) {
        managerOnly {
            response = readBody<ReplaceGrantsRequest>()
                .flatMap { request -> identity.replaceGrants(requireParameter("name"), request.grants, ctx.header(Header.IF_MATCH), actor = auditActor()) }
                .map { snapshot ->
                    ctx.header(Header.ETAG, snapshot.etag)
                    snapshot.value
                }
        }
    }

    @OpenApi(
        path = "/api/identity/principals/{name}/instance-admin",
        tags = ["Identity"],
        summary = "Grants the instance role to a principal. Granting it again changes nothing. Note: Requires Manager",
        pathParams = [OpenApiParam(name = "name", description = "Name of the principal", required = true)],
        methods = [HttpMethod.PUT],
    )
    val grantPrincipalInstanceAdmin = ReposiliteRoute<PrincipalDetailDto>("/api/identity/principals/{name}/instance-admin", PUT) {
        managerOnly {
            response = identity.grantPrincipalInstanceAdmin(requireParameter("name"), ctx.header(Header.IF_MATCH), actor = auditActor())
                .map { snapshot ->
                    ctx.header(Header.ETAG, snapshot.etag)
                    snapshot.value
                }
        }
    }

    @OpenApi(
        path = "/api/identity/principals/{name}/instance-admin",
        tags = ["Identity"],
        summary = "Removes the instance role of a principal. The last usable instance admin cannot be removed (409). Note: Requires Manager",
        pathParams = [OpenApiParam(name = "name", description = "Name of the principal", required = true)],
        methods = [HttpMethod.DELETE],
    )
    val revokePrincipalInstanceAdmin = ReposiliteRoute<PrincipalDetailDto>("/api/identity/principals/{name}/instance-admin", DELETE) {
        managerOnly {
            response = identity.revokePrincipalInstanceAdmin(requireParameter("name"), ctx.header(Header.IF_MATCH), actor = auditActor())
                .map { snapshot ->
                    ctx.header(Header.ETAG, snapshot.etag)
                    snapshot.value
                }
        }
    }

    @OpenApi(
        path = "/api/identity/teams/{name}/instance-admin",
        tags = ["Identity"],
        summary = "Grants the instance role to a team: its members with a valid token become instance admins. Note: Requires Manager",
        pathParams = [OpenApiParam(name = "name", description = "Name of the team", required = true)],
        methods = [HttpMethod.PUT],
    )
    val grantTeamInstanceAdmin = ReposiliteRoute<TeamDetailDto>("/api/identity/teams/{name}/instance-admin", PUT) {
        managerOnly {
            response = identity.grantTeamInstanceAdmin(requireParameter("name"), ctx.header(Header.IF_MATCH), actor = auditActor())
                .map { snapshot ->
                    ctx.header(Header.ETAG, snapshot.etag)
                    snapshot.value
                }
        }
    }

    @OpenApi(
        path = "/api/identity/teams/{name}/instance-admin",
        tags = ["Identity"],
        summary = "Removes the instance role of a team. The last usable instance admin cannot be removed (409). Note: Requires Manager",
        pathParams = [OpenApiParam(name = "name", description = "Name of the team", required = true)],
        methods = [HttpMethod.DELETE],
    )
    val revokeTeamInstanceAdmin = ReposiliteRoute<TeamDetailDto>("/api/identity/teams/{name}/instance-admin", DELETE) {
        managerOnly {
            response = identity.revokeTeamInstanceAdmin(requireParameter("name"), ctx.header(Header.IF_MATCH), actor = auditActor())
                .map { snapshot ->
                    ctx.header(Header.ETAG, snapshot.etag)
                    snapshot.value
                }
        }
    }

    @OpenApi(
        path = "/api/identity/repositories/{repository}/access",
        tags = ["Identity"],
        summary = "Explains who has which access on a repository, and why. Note: Requires Manager",
        pathParams = [OpenApiParam(name = "repository", description = "Name of the repository", required = true)],
        methods = [HttpMethod.GET],
    )
    val repositoryAccess = ReposiliteRoute<RepositoryAccessDto>("/api/identity/repositories/{repository}/access", GET) {
        managerOnly {
            response = identity.repositoryAccess(requireParameter("repository"))
        }
    }

    /** Reads the request body, answering 400 when it cannot be read. */
    private inline fun <reified B : Any> ContextDsl<*>.readBody(): Result<B, ErrorResponse> =
        supplyThrowing { ctx.bodyAsClass<B>() }.mapErr { badRequest("Failed to read body") }

    override val routes = routes(
        listPrincipals, createPrincipal, getPrincipal, updatePrincipal, deletePrincipal,
        listTokens, createToken, deleteToken,
        listTeams, createTeam, getTeam, updateTeam, deleteTeam, replaceMembers, replaceGrants,
        grantPrincipalInstanceAdmin, revokePrincipalInstanceAdmin, grantTeamInstanceAdmin, revokeTeamInstanceAdmin,
        repositoryAccess,
    )

}
