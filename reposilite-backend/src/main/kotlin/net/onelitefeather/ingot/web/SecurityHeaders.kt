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

package net.onelitefeather.ingot.web

import io.javalin.http.Context
import net.onelitefeather.ingot.identity.session.SessionCookies

/**
 * What an operator can change about the security headers. Both switches are read once at start, from the
 * environment variable or, when that is unset, from the system property next to it.
 *
 * @property repositorySandbox `INGOT_REPOSITORY_SANDBOX` / `ingot.repository.sandbox`. On by default: repository files
 * carry `Content-Security-Policy: sandbox`, as they always did. `false` leaves them without it.
 * @property hstsMaxAgeSeconds `INGOT_HSTS_MAX_AGE` / `ingot.hsts.maxAge`. Off by default. A positive number of seconds
 * turns on `Strict-Transport-Security`, which is only sent on HTTPS requests.
 */
data class SecurityHeadersSettings(
    val repositorySandbox: Boolean = true,
    val hstsMaxAgeSeconds: Long? = null,
) {

    companion object {

        fun resolve(
            environment: (String) -> String? = System::getenv,
            property: (String) -> String? = System::getProperty,
        ): SecurityHeadersSettings {
            fun lookup(variable: String, key: String): String? =
                (environment(variable) ?: property(key))?.trim()?.takeIf { it.isNotEmpty() }

            return SecurityHeadersSettings(
                repositorySandbox = !"false".equals(lookup("INGOT_REPOSITORY_SANDBOX", "ingot.repository.sandbox"), ignoreCase = true),
                hstsMaxAgeSeconds = lookup("INGOT_HSTS_MAX_AGE", "ingot.hsts.maxAge")?.toLongOrNull()?.takeIf { it > 0 },
            )
        }

    }

}

/**
 * Adds the security headers to every response. Endpoints that serve a document a browser renders tag the request
 * with [markDashboard] or [markRepositoryContent], and this class turns the tag into the matching headers.
 */
class SecurityHeaders(private val settings: SecurityHeadersSettings) {

    companion object {
        private const val DASHBOARD = "ingot.security.dashboard"
        private const val REPOSITORY_CONTENT = "ingot.security.repository-content"

        private const val FRAME_ANCESTORS = "frame-ancestors 'none'"

        /**
         * The dashboard is a Vue single page application: its build has no inline script or style, but the settings
         * forms compile JSON schemas with Ajv, which uses `Function`, hence `'unsafe-eval'`. Logos configured by the
         * operator can sit on any host, hence the open `img-src`.
         */
        internal const val DASHBOARD_POLICY =
            "default-src 'self'; script-src 'self' 'unsafe-eval'; style-src 'self' 'unsafe-inline'; " +
                "img-src 'self' data: https: http:; font-src 'self' data:; connect-src 'self'; " +
                "object-src 'none'; base-uri 'self'; $FRAME_ANCESTORS"

        internal const val PERMISSIONS_POLICY =
            "accelerometer=(), camera=(), geolocation=(), gyroscope=(), microphone=(), payment=(), usb=()"

        internal const val REPOSITORY_SANDBOX = "sandbox"

        /** The response is the dashboard document. */
        fun markDashboard(ctx: Context) {
            ctx.attribute(DASHBOARD, true)
        }

        /** The response is repository content that a browser may render: a file or the generated directory index. */
        fun markRepositoryContent(ctx: Context) {
            ctx.attribute(REPOSITORY_CONTENT, true)
        }
    }

    fun apply(ctx: Context) {
        ctx.header("X-Content-Type-Options", "nosniff")
        ctx.header("Referrer-Policy", "strict-origin-when-cross-origin")

        val path = ctx.path()
        when {
            ctx.attribute<Boolean>(DASHBOARD) == true -> {
                ctx.header("X-Frame-Options", "DENY")
                ctx.header("Content-Security-Policy", DASHBOARD_POLICY)
                ctx.header("Permissions-Policy", PERMISSIONS_POLICY)
            }
            path == "/api" || path.startsWith("/api/") -> {
                ctx.header("X-Frame-Options", "DENY")
                ctx.header("Content-Security-Policy", FRAME_ANCESTORS)
            }
            ctx.attribute<Boolean>(REPOSITORY_CONTENT) == true && settings.repositorySandbox ->
                ctx.header("Content-Security-Policy", REPOSITORY_SANDBOX)
        }

        val maxAge = settings.hstsMaxAgeSeconds
        if (maxAge != null && SessionCookies.isSecure(ctx.scheme(), ctx.header("X-Forwarded-Proto"))) {
            ctx.header("Strict-Transport-Security", "max-age=$maxAge")
        }
    }

}
