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

import java.security.MessageDigest

/**
 * Cross-site request forgery protection for cookie credentials, by the double-submit pattern. A request that is
 * authenticated by a session or refresh cookie, and changes state, must repeat the value of the csrf cookie in the
 * `X-CSRF-Token` header. A cross-site page cannot read that cookie, so it cannot send the header.
 *
 * Requests that carry an Authorization header are not cookie requests and are exempt. So are login and setup, which
 * create the cookies in the first place.
 */
internal object CsrfPolicy {

    private val SAFE_METHODS = setOf("GET", "HEAD", "OPTIONS", "TRACE")
    private val EXEMPT_ROUTES = listOf("/api/auth/login", "/api/auth/setup")

    /**
     * The exempt routes, at the root and below the configured base path. The root is kept because a reverse proxy may
     * strip the base path before the request arrives. The paths are compared exactly: a suffix match would also exempt
     * a repository path such as `/releases/api/auth/login`, which is an ordinary deployment that needs the token.
     */
    private fun exemptPaths(basePath: String): Set<String> {
        val base = basePath.trim().trimEnd('/').let { if (it.isEmpty() || it.startsWith('/')) it else "/$it" }
        return (EXEMPT_ROUTES + EXEMPT_ROUTES.map { base + it }).toSet()
    }

    data class Request(
        val method: String,
        val path: String,
        val hasAuthorizationHeader: Boolean,
        val sessionCookie: String?,
        val refreshCookie: String?,
        val csrfCookie: String?,
        val csrfHeader: String?,
    )

    /** `true` when the request must be refused with 403. */
    fun violation(request: Request, basePath: String = "/"): Boolean {
        if (request.method.uppercase() in SAFE_METHODS) {
            return false
        }
        if (request.hasAuthorizationHeader) {
            return false
        }
        if (request.sessionCookie == null && request.refreshCookie == null) {
            return false
        }
        if (request.path.trimEnd('/') in exemptPaths(basePath)) {
            return false
        }
        val expected = request.csrfCookie
        val presented = request.csrfHeader
        return expected.isNullOrEmpty() || presented.isNullOrEmpty() || !constantTimeEquals(expected, presented)
    }

    private fun constantTimeEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

}
