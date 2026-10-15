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

package net.onelitefeather.ingot.identity.session

/**
 * The three cookies of a browser session, as `Set-Cookie` header values. The session token and the refresh token are
 * HttpOnly, so page scripts cannot read them. The csrf token is not HttpOnly: the page echoes it in a header, which is
 * how the double-submit check works. The refresh cookie is sent to the authentication endpoints only.
 */
internal object SessionCookies {

    const val SESSION = "ingot_session"
    const val REFRESH = "ingot_refresh"
    const val CSRF = "ingot_csrf"
    const val CSRF_HEADER = "X-CSRF-Token"

    private const val AUTH_PATH = "api/auth"

    fun setCookies(tokens: SessionTokens, basePath: String, secure: Boolean): List<String> = listOf(
        cookie(SESSION, tokens.sessionJwt, path(basePath), tokens.sessionMaxAgeSeconds, httpOnly = true, secure = secure),
        cookie(REFRESH, tokens.refreshToken, path(basePath, AUTH_PATH), tokens.refreshMaxAgeSeconds, httpOnly = true, secure = secure),
        cookie(CSRF, tokens.csrfToken, path(basePath), tokens.refreshMaxAgeSeconds, httpOnly = false, secure = secure),
    )

    /** Expires all three cookies at once, on logout and after a failed renewal. */
    fun clearCookies(basePath: String, secure: Boolean): List<String> = listOf(
        cookie(SESSION, "", path(basePath), 0, httpOnly = true, secure = secure),
        cookie(REFRESH, "", path(basePath, AUTH_PATH), 0, httpOnly = true, secure = secure),
        cookie(CSRF, "", path(basePath), 0, httpOnly = false, secure = secure),
    )

    /**
     * A request is secure when it arrived over TLS, or when a proxy in front of Ingot terminated TLS and says so in
     * `X-Forwarded-Proto`. The first value counts when the header lists several proxies.
     */
    fun isSecure(scheme: String, forwardedProto: String?): Boolean =
        scheme.equals("https", ignoreCase = true) ||
            forwardedProto?.substringBefore(',')?.trim()?.equals("https", ignoreCase = true) == true

    private fun cookie(name: String, value: String, path: String, maxAgeSeconds: Long, httpOnly: Boolean, secure: Boolean): String {
        val attributes = buildList {
            add("Path=$path")
            add("Max-Age=$maxAgeSeconds")
            if (httpOnly) {
                add("HttpOnly")
            }
            add("SameSite=Lax")
            if (secure) {
                add("Secure")
            }
        }
        return "$name=$value; " + attributes.joinToString(separator = "; ")
    }

    /** The cookie path under the base path of the instance, for example `/reposilite/api/auth`. */
    private fun path(basePath: String, suffix: String = ""): String {
        val base = basePath.trim().trimEnd('/').let { if (it.isEmpty() || it.startsWith('/')) it else "/$it" }
        return when {
            suffix.isEmpty() -> base.ifEmpty { "/" }
            else -> "$base/$suffix"
        }
    }

}
