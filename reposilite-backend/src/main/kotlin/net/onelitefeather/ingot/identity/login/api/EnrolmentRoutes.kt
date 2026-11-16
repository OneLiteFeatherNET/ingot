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

import net.onelitefeather.ingot.identity.session.SessionScope

/** The problem of a restricted session that calls a route outside its set. */
internal const val MFA_ENROLMENT_REQUIRED = "mfa_enrolment_required"

/**
 * The routes a restricted session (scope `enrol`, design D10) may call: the own factor routes, the recovery codes, the renewal that
 * applies the scope, and the logout. Every other route answers 403 with [MFA_ENROLMENT_REQUIRED]. A full session is never refused here.
 *
 * The decision is taken on the request path, not on the endpoint: Javalin runs the handler for wildcard endpoints first, so the endpoint
 * is not the route that answers. The paths are matched with and without the base path, as the CSRF policy does.
 */
internal object EnrolmentRoutes {

    private val allowed: List<Pair<String, Regex>> = listOf(
        "GET" to "/api/identity/me/factors",
        "POST" to "/api/identity/me/factors/totp",
        "POST" to "/api/identity/me/factors/totp/confirm",
        "POST" to "/api/identity/me/factors/passkey/options",
        "POST" to "/api/identity/me/factors/passkey",
        "PATCH" to "/api/identity/me/factors/[^/]+",
        "DELETE" to "/api/identity/me/factors/[^/]+",
        "POST" to "/api/identity/me/recovery-codes",
        "POST" to "/api/auth/refresh",
        "POST" to "/api/auth/logout",
    ).map { (method, template) -> method to Regex(template) }

    /**
     * Whether a request with [method] to [requestPath] is refused for a session of [scope]. [basePath] is the configured base path, which
     * a request may carry in front of the route.
     */
    fun refused(scope: SessionScope, method: String, requestPath: String, basePath: String = "/"): Boolean {
        if (scope != SessionScope.ENROL) {
            return false
        }
        val path = requestPath.trimEnd('/')
        val base = basePath.trim().trimEnd('/').let { if (it.isEmpty() || it.startsWith('/')) it else "/$it" }
        val relative = if (base.isNotEmpty() && path.startsWith("$base/")) path.removePrefix(base) else path
        return allowed.none { (allowedMethod, pattern) ->
            allowedMethod == method && (pattern.matches(path) || pattern.matches(relative))
        }
    }

}
