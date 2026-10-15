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

package net.onelitefeather.ingot.identity.login.api

import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.badRequestError
import com.reposilite.shared.errorResponse
import com.reposilite.shared.unauthorizedError
import com.reposilite.web.api.ReposiliteRoute
import com.reposilite.web.api.ReposiliteRoutes
import io.javalin.community.routing.Route
import io.javalin.http.HttpStatus.FORBIDDEN
import io.javalin.http.HttpStatus.TOO_MANY_REQUESTS
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiResponse
import net.onelitefeather.ingot.identity.login.LoginFacade
import net.onelitefeather.ingot.identity.login.LoginOutcome
import panda.std.asSuccess

internal class AuthLoginEndpoints(private val facade: LoginFacade) : ReposiliteRoutes() {

    @OpenApi(
        path = "/api/auth/providers",
        methods = [HttpMethod.GET],
        summary = "List login providers",
        description = "Public. Lists the configured login providers and whether the first admin still has to be set up",
        tags = ["Auth"],
        responses = [
            OpenApiResponse(status = "200", content = [ OpenApiContent(from = LoginProvidersResponse::class) ]),
        ]
    )
    private val providers = ReposiliteRoute<LoginProvidersResponse>("/api/auth/providers", Route.GET) {
        response = LoginProvidersResponse(
            providers = facade.providers().map { LoginProviderDto(id = it.id, type = it.type.name, label = it.label) },
            tokenLogin = true,
            setupRequired = facade.setupRequired(),
        ).asSuccess()
    }

    @OpenApi(
        path = "/api/auth/login",
        methods = [HttpMethod.POST],
        summary = "Log in with a login provider",
        description = "Checks the credentials of a human user. Wrong credentials and unknown users get the same answer",
        tags = ["Auth"],
        requestBody = io.javalin.openapi.OpenApiRequestBody(content = [ OpenApiContent(from = LoginRequest::class) ]),
        responses = [
            OpenApiResponse(status = "200", content = [ OpenApiContent(from = LoginResponse::class) ]),
            OpenApiResponse(status = "400", content = [ OpenApiContent(from = ErrorResponse::class) ]),
            OpenApiResponse(status = "401", content = [ OpenApiContent(from = ErrorResponse::class) ]),
            OpenApiResponse(status = "403", content = [ OpenApiContent(from = ErrorResponse::class) ]),
            OpenApiResponse(status = "429", content = [ OpenApiContent(from = ErrorResponse::class) ]),
        ]
    )
    private val login = ReposiliteRoute<LoginResponse>("/api/auth/login", Route.POST) {
        val request = runCatching { body<LoginRequest>() }.getOrNull()
        val provider = request?.provider?.takeIf { it.isNotBlank() }
        val username = request?.username?.takeIf { it.isNotBlank() }
        val password = request?.password?.takeIf { it.isNotBlank() }

        response = when {
            provider == null || username == null || password == null ->
                badRequestError("Login requires provider, username and password")
            else -> when (val outcome = facade.login(provider, username, password, ctx.ip())) {
                is LoginOutcome.Succeeded -> LoginResponse(
                    principal = LoginPrincipalDto(
                        id = outcome.principal.id,
                        kind = outcome.principal.kind.name,
                        name = outcome.principal.name,
                        displayName = outcome.principal.displayName,
                    ),
                    mustChangePassword = outcome.mustChangePassword,
                ).asSuccess()
                LoginOutcome.Rejected -> unauthorizedError("Invalid username or password")
                LoginOutcome.Disabled -> errorResponse(FORBIDDEN, "This user is disabled")
                LoginOutcome.Blocked -> errorResponse(TOO_MANY_REQUESTS, "Too many failed login attempts, try again later")
                LoginOutcome.UnknownProvider -> badRequestError("Unknown login provider")
            }
        }
    }

    override val routes = routes(providers, login)

}
