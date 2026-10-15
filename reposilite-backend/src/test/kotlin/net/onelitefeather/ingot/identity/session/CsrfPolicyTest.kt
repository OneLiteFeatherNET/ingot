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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class CsrfPolicyTest {

    private fun request(
        method: String = "POST",
        path: String = "/api/identity/me/tokens",
        authorization: Boolean = false,
        session: String? = "session-jwt",
        refresh: String? = null,
        csrfCookie: String? = "csrf-value",
        csrfHeader: String? = "csrf-value",
    ) = CsrfPolicy.Request(
        method = method,
        path = path,
        hasAuthorizationHeader = authorization,
        sessionCookie = session,
        refreshCookie = refresh,
        csrfCookie = csrfCookie,
        csrfHeader = csrfHeader,
    )

    @Test
    fun `lets a safe method through without a csrf header`() {
        assertThat(CsrfPolicy.violation(request(method = "GET", csrfHeader = null)))
            .describedAs("reads cannot change state, so they need no csrf token")
            .isFalse()
    }

    @Test
    fun `accepts a csrf header that equals the csrf cookie`() {
        assertThat(CsrfPolicy.violation(request())).describedAs("matching double-submit values pass").isFalse()
    }

    @Test
    fun `rejects a csrf header that differs from the csrf cookie`() {
        assertThat(CsrfPolicy.violation(request(csrfHeader = "forged")))
            .describedAs("a cross-site request cannot read the cookie, so it cannot send the matching header")
            .isTrue()
    }

    @Test
    fun `rejects a cookie session post without any csrf header`() {
        assertThat(CsrfPolicy.violation(request(csrfHeader = null))).describedAs("a missing header is a mismatch").isTrue()
    }

    @Test
    fun `rejects a cookie session post when the csrf cookie is missing`() {
        assertThat(CsrfPolicy.violation(request(csrfCookie = null, csrfHeader = "anything")))
            .describedAs("without a csrf cookie there is nothing to match")
            .isTrue()
    }

    @Test
    fun `applies to put, patch and delete as well as post`() {
        listOf("PUT", "PATCH", "DELETE").forEach { method ->
            assertThat(CsrfPolicy.violation(request(method = method, csrfHeader = null)))
                .describedAs("$method changes state and needs the csrf token")
                .isTrue()
        }
    }

    @Test
    fun `requires the csrf token for a renewal that carries only the refresh cookie`() {
        assertThat(CsrfPolicy.violation(request(path = "/api/auth/refresh", session = null, refresh = "refresh-value", csrfHeader = null)))
            .describedAs("the refresh cookie is a cookie credential too")
            .isTrue()
    }

    @Test
    fun `does not require the csrf token for basic authentication`() {
        assertThat(CsrfPolicy.violation(request(authorization = true, session = null, csrfCookie = null, csrfHeader = null)))
            .describedAs("basic authentication is not sent by the browser on its own, so it is exempt")
            .isFalse()
    }

    @Test
    fun `does not require the csrf token when a bearer header is present`() {
        assertThat(CsrfPolicy.violation(request(authorization = true, session = "session-jwt", csrfHeader = null)))
            .describedAs("an explicit authorization header decides the request, so the cookies do not")
            .isFalse()
    }

    @Test
    fun `does not require the csrf token for an anonymous request`() {
        assertThat(CsrfPolicy.violation(request(session = null, csrfCookie = null, csrfHeader = null)))
            .describedAs("without any cookie credential there is no ambient authority to protect")
            .isFalse()
    }

    @Test
    fun `does not require the csrf token for login and setup`() {
        assertThat(CsrfPolicy.violation(request(path = "/api/auth/login", csrfCookie = null, csrfHeader = null)))
            .describedAs("login is the request that starts a session, so it cannot carry one yet")
            .isFalse()
        assertThat(CsrfPolicy.violation(request(path = "/api/auth/setup", csrfCookie = null, csrfHeader = null)))
            .describedAs("setup is the request that creates the first session")
            .isFalse()
    }

    @Test
    fun `recognizes the exempt paths below a base path`() {
        assertThat(CsrfPolicy.violation(request(path = "/reposilite/api/auth/login", csrfCookie = null, csrfHeader = null)))
            .describedAs("an instance served under a base path must exempt its login too")
            .isFalse()
    }

}
