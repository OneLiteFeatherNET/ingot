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

package net.onelitefeather.ingot.identity.login.oidc

import com.reposilite.shared.errorResponse
import com.reposilite.web.api.ReposiliteRoute
import com.reposilite.web.api.ReposiliteRoutes
import io.javalin.community.routing.Route
import io.javalin.http.Context
import io.javalin.http.HttpStatus.NOT_FOUND
import io.javalin.http.HttpStatus.SERVICE_UNAVAILABLE
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiResponse
import net.onelitefeather.ingot.identity.login.LoginFacade
import net.onelitefeather.ingot.identity.session.SessionCookies
import net.onelitefeather.ingot.identity.session.SessionTokens
import panda.std.reactive.Reference

/**
 * The redirect flow of the OpenID Connect and GitHub providers. The start route sends the browser to the provider; the
 * callback validates the answer, starts the browser session and redirects back to the UI. A failed callback redirects to
 * `/?loginError=<code>` instead of answering with an error body, because the browser is the one reading the answer.
 */
internal class AuthOidcEndpoints(
    private val facade: LoginFacade,
    private val oidc: OidcLoginService,
    private val basePath: Reference<String>,
    /** The configured public URL, read on every request; blank when the redirect uri is built from the request. */
    private val publicUrl: () -> String,
) : ReposiliteRoutes() {

    @OpenApi(
        path = "/api/auth/oidc/{id}/login",
        methods = [HttpMethod.GET],
        summary = "Start a login with an OpenID Connect provider",
        pathParams = [OpenApiParam(name = "id", description = "Id of the provider", required = true)],
        description = "Redirects to the provider with PKCE, state and nonce. The state travels in the ingot_oidc cookie for ten minutes",
        tags = ["Auth"],
        responses = [
            OpenApiResponse(status = "302", description = "Redirect to the authorization endpoint of the provider"),
            OpenApiResponse(status = "404", description = "No enabled provider has this id"),
            OpenApiResponse(status = "503", description = "The provider lacks a required setting, named in the message"),
        ]
    )
    private val login = ReposiliteRoute<Unit>("/api/auth/oidc/{id}/login", Route.GET) {
        val id = requireParameter("id")
        val redirectUri = redirectUri(ctx, id)
        when (val outcome = oidc.begin(id, queryParameter("redirect"), redirectUri)) {
            is OidcBegin.Started -> {
                setCookie(ctx, OidcStateCookie.COOKIE, outcome.stateCookie, OidcStateCookie.LIFETIME.seconds)
                ctx.redirect(outcome.location)
            }
            OidcBegin.UnknownProvider -> response = errorResponse(NOT_FOUND, "Unknown login provider")
            is OidcBegin.Unusable -> response = errorResponse(SERVICE_UNAVAILABLE, "The login provider is not configured: ${outcome.field} is missing")
            OidcBegin.Unavailable -> ctx.redirect(loginErrorLocation(basePath.get(), "provider_unavailable"))
        }
    }

    @OpenApi(
        path = "/api/auth/oidc/{id}/callback",
        methods = [HttpMethod.GET],
        summary = "Finish a login with an OpenID Connect provider",
        pathParams = [OpenApiParam(name = "id", description = "Id of the provider", required = true)],
        description = "Exchanges the code, validates the ID token, starts the browser session and redirects to the UI, or to /?loginError=<code>",
        tags = ["Auth"],
        responses = [
            OpenApiResponse(status = "302", description = "Redirect back to the UI, with the session cookies or with a login error"),
        ]
    )
    private val callback = ReposiliteRoute<Unit>("/api/auth/oidc/{id}/callback", Route.GET) {
        val id = requireParameter("id")
        val base = basePath.get()
        val outcome = oidc.finish(
            providerId = id,
            state = queryParameter("state"),
            code = queryParameter("code"),
            error = queryParameter("error"),
            cookie = ctx.cookie(OidcStateCookie.COOKIE),
            redirectUri = redirectUri(ctx, id),
            ip = ctx.ip(),
        )
        clearCookie(ctx, OidcStateCookie.COOKIE)

        when (outcome) {
            is OidcFinish.LoggedIn -> {
                val session = facade.startSession(outcome.principal, ctx.ip(), ctx.header("User-Agent"))
                if (session == null) {
                    ctx.redirect(loginErrorLocation(base, "no_database"))
                } else {
                    writeSessionCookies(ctx, session, base)
                    ctx.redirect(trimmedBase(base) + outcome.redirect)
                }
            }
            is OidcFinish.Failed -> ctx.redirect(loginErrorLocation(base, outcome.code))
        }
    }

    override val routes = routes(login, callback)

    private fun redirectUri(ctx: Context, id: String): String {
        val scheme = if (SessionCookies.isSecure(ctx.scheme(), ctx.header("X-Forwarded-Proto"))) "https" else "http"
        return OidcRedirectUri.of(publicUrl(), scheme, ctx.host().orEmpty(), basePath.get(), id)
    }

    private fun writeSessionCookies(ctx: Context, session: SessionTokens, base: String) {
        SessionCookies.setCookies(session, base, SessionCookies.isSecure(ctx.scheme(), ctx.header("X-Forwarded-Proto")))
            .forEach { ctx.res().addHeader("Set-Cookie", it) }
    }

    private fun setCookie(ctx: Context, name: String, value: String, maxAgeSeconds: Long) {
        ctx.res().addHeader("Set-Cookie", cookie(name, value, maxAgeSeconds, ctx))
    }

    private fun clearCookie(ctx: Context, name: String) {
        ctx.res().addHeader("Set-Cookie", cookie(name, "", 0, ctx))
    }

    private fun cookie(name: String, value: String, maxAgeSeconds: Long, ctx: Context): String {
        val secure = if (SessionCookies.isSecure(ctx.scheme(), ctx.header("X-Forwarded-Proto"))) "; Secure" else ""
        return "$name=$value; Path=${trimmedBase(basePath.get())}/api/auth/oidc; Max-Age=$maxAgeSeconds; HttpOnly; SameSite=Lax$secure"
    }

}

/** The base path without its trailing slash, `""` for the root. */
private fun trimmedBase(basePath: String): String =
    basePath.trim().trimEnd('/').let { if (it.isEmpty() || it.startsWith('/')) it else "/$it" }

private fun loginErrorLocation(basePath: String, code: String): String =
    "${trimmedBase(basePath)}/?loginError=$code"
