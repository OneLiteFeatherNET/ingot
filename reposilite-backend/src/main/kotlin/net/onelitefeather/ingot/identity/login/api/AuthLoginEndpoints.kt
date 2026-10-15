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
import io.javalin.http.Context
import io.javalin.http.HttpStatus.CONFLICT
import io.javalin.http.HttpStatus.FORBIDDEN
import io.javalin.http.HttpStatus.SERVICE_UNAVAILABLE
import io.javalin.http.HttpStatus.TOO_MANY_REQUESTS
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiRequestBody
import io.javalin.openapi.OpenApiResponse
import net.onelitefeather.ingot.identity.login.LoginFacade
import net.onelitefeather.ingot.identity.login.LoginOutcome
import net.onelitefeather.ingot.identity.login.SetupOutcome
import net.onelitefeather.ingot.identity.model.PrincipalRecord
import net.onelitefeather.ingot.identity.session.SessionCookies
import net.onelitefeather.ingot.identity.session.SessionTokens
import panda.std.Result
import panda.std.asSuccess
import panda.std.reactive.Reference

/**
 * The login endpoints of a browser: providers, password login, first administrator setup, renewal and logout. Login,
 * setup, renewal and logout set or clear the session cookies, so that the browser never handles a token itself.
 */
internal class AuthLoginEndpoints(
    private val facade: LoginFacade,
    private val basePath: Reference<String>,
) : ReposiliteRoutes() {

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
            providers = facade.providers().map { LoginProviderDto(id = it.id, type = it.type.name, label = it.label, icon = it.icon, loginUrl = it.loginUrl) },
            tokenLogin = true,
            setupRequired = facade.setupRequired(),
        ).asSuccess()
    }

    @OpenApi(
        path = "/api/auth/login",
        methods = [HttpMethod.POST],
        summary = "Log in with a login provider",
        description = "Checks the credentials of a human user and starts a browser session. Wrong credentials and unknown users get the same answer",
        tags = ["Auth"],
        requestBody = OpenApiRequestBody(content = [ OpenApiContent(from = LoginRequest::class) ]),
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

        response = if (provider == null || username == null || password == null) {
            badRequestError("Login requires provider, username and password")
        } else {
            when (val outcome = facade.login(provider, username, password, ctx.ip())) {
                is LoginOutcome.Succeeded -> startLoginSession(ctx, facade, basePath.get(), outcome.principal, outcome.mustChangePassword)
                LoginOutcome.Rejected -> unauthorizedError("Invalid username or password")
                LoginOutcome.Disabled -> errorResponse(FORBIDDEN, "This user is disabled")
                is LoginOutcome.Blocked -> tooManyRequests(ctx, outcome.retryAfterSeconds, "Too many failed login attempts, try again later")
                LoginOutcome.UnknownProvider -> badRequestError("Unknown login provider")
            }
        }
    }

    @OpenApi(
        path = "/api/auth/setup",
        methods = [HttpMethod.GET],
        summary = "Setup status",
        description = "Public. Tells whether the first administrator still has to be created",
        tags = ["Auth"],
        responses = [
            OpenApiResponse(status = "200", content = [ OpenApiContent(from = SetupStatusResponse::class) ]),
        ]
    )
    private val setupStatus = ReposiliteRoute<SetupStatusResponse>("/api/auth/setup", Route.GET) {
        response = SetupStatusResponse(required = facade.setupRequired()).asSuccess()
    }

    @OpenApi(
        path = "/api/auth/setup",
        methods = [HttpMethod.POST],
        summary = "Create the first administrator",
        description = "Creates the first user as an instance administrator, using the one-time setup code from the log, and starts a session. Only while no user exists",
        tags = ["Auth"],
        requestBody = OpenApiRequestBody(content = [ OpenApiContent(from = SetupRequest::class) ]),
        responses = [
            OpenApiResponse(status = "200", content = [ OpenApiContent(from = LoginResponse::class) ]),
            OpenApiResponse(status = "400", content = [ OpenApiContent(from = ErrorResponse::class) ]),
            OpenApiResponse(status = "403", content = [ OpenApiContent(from = ErrorResponse::class) ]),
            OpenApiResponse(status = "409", content = [ OpenApiContent(from = ErrorResponse::class) ]),
            OpenApiResponse(status = "429", content = [ OpenApiContent(from = ErrorResponse::class) ]),
        ]
    )
    private val setup = ReposiliteRoute<LoginResponse>("/api/auth/setup", Route.POST) {
        val request = runCatching { body<SetupRequest>() }.getOrNull()
        val code = request?.setupCode?.takeIf { it.isNotBlank() }
        val username = request?.username?.takeIf { it.isNotBlank() }
        val password = request?.password?.takeIf { it.isNotBlank() }

        response = if (request == null || code == null || username == null || password == null) {
            badRequestError("Setup requires setupCode, username and password")
        } else {
            when (val outcome = facade.completeSetup(code, username, password, request.displayName, ctx.ip())) {
                is SetupOutcome.Created -> startLoginSession(ctx, facade, basePath.get(), outcome.principal, mustChangePassword = false)
                SetupOutcome.NotRequired -> errorResponse(FORBIDDEN, "Setup is not required, a user already exists")
                SetupOutcome.AlreadyCompleted -> errorResponse(CONFLICT, "The first administrator was created by another instance of this database, sign in instead")
                SetupOutcome.WrongCode -> errorResponse(FORBIDDEN, "Invalid setup code")
                is SetupOutcome.Blocked -> tooManyRequests(ctx, outcome.retryAfterSeconds, "Too many failed setup attempts, try again later")
                SetupOutcome.NameTaken -> errorResponse(CONFLICT, "This user name is already taken")
                is SetupOutcome.InvalidInput -> badRequestError(outcome.problem)
            }
        }
    }

    @OpenApi(
        path = "/api/auth/refresh",
        methods = [HttpMethod.POST],
        summary = "Renew the browser session",
        description = "Exchanges the refresh cookie for a new session. The refresh token is single use: presenting a spent one revokes the whole session",
        tags = ["Auth"],
        responses = [
            OpenApiResponse(status = "200", content = [ OpenApiContent(from = SessionResponse::class) ]),
            OpenApiResponse(status = "401", content = [ OpenApiContent(from = ErrorResponse::class) ]),
        ]
    )
    private val refresh = ReposiliteRoute<SessionResponse>("/api/auth/refresh", Route.POST) {
        val session = ctx.cookie(SessionCookies.REFRESH)?.let { facade.refreshSession(it, ctx.ip(), ctx.header("User-Agent")) }

        response = if (session == null) {
            clearSessionCookies(ctx, basePath.get())
            unauthorizedError("Invalid or expired session")
        } else {
            writeSessionCookies(ctx, session, basePath.get())
            SessionResponse(principal = session.principal.toLoginDto(), expiresAt = session.sessionExpiresAt).asSuccess()
        }
    }

    @OpenApi(
        path = "/api/auth/logout",
        methods = [HttpMethod.POST],
        summary = "End the browser session",
        description = "Revokes the refresh token family of the session and clears the session cookies",
        tags = ["Auth"],
        responses = [
            OpenApiResponse(status = "200", description = "Logged out"),
        ]
    )
    private val logout = ReposiliteRoute<Unit>("/api/auth/logout", Route.POST) {
        facade.endSession(ctx.cookie(SessionCookies.REFRESH), ctx.ip())
        clearSessionCookies(ctx, basePath.get())
        response = Unit.asSuccess()
    }

    override val routes = routes(providers, login, setupStatus, setup, refresh, logout)

}

/** A 429 that tells the client when to try again, as `Retry-After` in whole seconds. */
private fun <V> tooManyRequests(ctx: Context, retryAfterSeconds: Long, message: String): Result<V, ErrorResponse> {
    ctx.header("Retry-After", retryAfterSeconds.toString())
    return errorResponse(TOO_MANY_REQUESTS, message)
}

private fun startLoginSession(
    ctx: Context,
    facade: LoginFacade,
    basePath: String,
    principal: PrincipalRecord,
    mustChangePassword: Boolean,
): Result<LoginResponse, ErrorResponse> {
    val session = facade.startSession(principal, ctx.ip(), ctx.header("User-Agent"))
        ?: return errorResponse(SERVICE_UNAVAILABLE, "Browser sessions need the database")
    writeSessionCookies(ctx, session, basePath)
    return LoginResponse(
        principal = principal.toLoginDto(),
        mustChangePassword = mustChangePassword,
        expiresAt = session.sessionExpiresAt,
    ).asSuccess()
}

private fun PrincipalRecord.toLoginDto() = LoginPrincipalDto(
    id = id,
    kind = kind.name,
    name = name,
    displayName = displayName,
)

private fun writeSessionCookies(ctx: Context, session: SessionTokens, basePath: String) {
    SessionCookies.setCookies(session, basePath, secure(ctx)).forEach { ctx.res().addHeader("Set-Cookie", it) }
}

private fun clearSessionCookies(ctx: Context, basePath: String) {
    SessionCookies.clearCookies(basePath, secure(ctx)).forEach { ctx.res().addHeader("Set-Cookie", it) }
}

private fun secure(ctx: Context): Boolean =
    SessionCookies.isSecure(ctx.scheme(), ctx.header("X-Forwarded-Proto"))
