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

import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.model.PrincipalRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

internal class SessionCookiesTest {

    private val principal = PrincipalRecord(
        id = 1,
        kind = PrincipalKind.USER,
        name = "alice",
        displayName = "alice",
        disabled = false,
        createdAt = Instant.parse("2026-10-14T08:00:00Z"),
        lastSeenAt = null,
    )

    private fun tokens() = SessionTokens(
        principal = principal,
        sessionJwt = "header.payload.signature",
        sessionExpiresAt = Instant.parse("2026-10-15T10:15:00Z"),
        sessionMaxAgeSeconds = 900,
        refreshToken = "refresh-value",
        refreshExpiresAt = Instant.parse("2026-10-22T10:00:00Z"),
        refreshMaxAgeSeconds = 604_800,
        csrfToken = "csrf-value",
    )

    private fun cookieNamed(headers: List<String>, name: String): String =
        headers.single { it.startsWith("$name=") }

    @Test
    fun `issues the session cookie HttpOnly, SameSite Lax and limited to its lifetime`() {
        val session = cookieNamed(SessionCookies.setCookies(tokens(), basePath = "/", secure = false), "ingot_session")

        assertThat(session).describedAs("the session token must not be readable by page scripts").contains("HttpOnly")
        assertThat(session).contains("SameSite=Lax")
        assertThat(session).describedAs("the browser must drop the cookie with the token").contains("Max-Age=900")
        assertThat(session).contains("Path=/;")
    }

    @Test
    fun `scopes the refresh cookie to the authentication endpoints`() {
        val refresh = cookieNamed(SessionCookies.setCookies(tokens(), basePath = "/", secure = false), "ingot_refresh")

        assertThat(refresh).describedAs("the refresh token is sent only to the auth paths").contains("Path=/api/auth;")
        assertThat(refresh).contains("HttpOnly")
    }

    @Test
    fun `issues the csrf cookie readable by the page`() {
        val csrf = cookieNamed(SessionCookies.setCookies(tokens(), basePath = "/", secure = false), "ingot_csrf")

        assertThat(csrf).describedAs("the page must read the csrf cookie to echo it in a header").doesNotContain("HttpOnly")
        assertThat(csrf).startsWith("ingot_csrf=csrf-value;")
    }

    @Test
    fun `marks no cookie Secure over plain HTTP`() {
        val headers = SessionCookies.setCookies(tokens(), basePath = "/", secure = false)

        assertThat(headers.none { it.contains("Secure") }).describedAs("plain HTTP must still receive the cookies").isTrue()
    }

    @Test
    fun `marks every cookie Secure when the request is secure`() {
        val headers = SessionCookies.setCookies(tokens(), basePath = "/", secure = true)

        assertThat(headers.all { it.contains("; Secure") }).describedAs("every cookie must be Secure over TLS").isTrue()
    }

    @Test
    fun `follows the base path of the instance`() {
        val headers = SessionCookies.setCookies(tokens(), basePath = "/reposilite/", secure = false)

        assertThat(cookieNamed(headers, "ingot_session")).contains("Path=/reposilite;")
        assertThat(cookieNamed(headers, "ingot_refresh")).contains("Path=/reposilite/api/auth;")
    }

    @Test
    fun `clearing the cookies expires them at once`() {
        val headers = SessionCookies.clearCookies(basePath = "/", secure = false)

        assertThat(headers).hasSize(3)
        assertThat(headers.all { it.contains("Max-Age=0") }).describedAs("cleared cookies must expire immediately").isTrue()
    }

    @Test
    fun `treats a request as secure behind a proxy that terminates TLS`() {
        assertThat(SessionCookies.isSecure(scheme = "http", forwardedProto = "https")).isTrue()
        assertThat(SessionCookies.isSecure(scheme = "https", forwardedProto = null)).isTrue()
        assertThat(SessionCookies.isSecure(scheme = "http", forwardedProto = null)).isFalse()
        assertThat(SessionCookies.isSecure(scheme = "http", forwardedProto = "https, http")).isTrue()
    }

}
