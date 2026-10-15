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

package com.reposilite.login

import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteSpecification
import com.reposilite.shared.ErrorResponse
import io.javalin.http.HttpStatus.BAD_REQUEST
import io.javalin.http.HttpStatus.FORBIDDEN
import io.javalin.http.HttpStatus.OK
import io.javalin.http.HttpStatus.UNAUTHORIZED
import kong.unirest.core.Unirest
import net.onelitefeather.ingot.identity.login.LoginFacade
import net.onelitefeather.ingot.identity.login.api.LoginProvidersResponse
import net.onelitefeather.ingot.identity.login.api.LoginResponse
import net.onelitefeather.ingot.identity.login.api.SessionResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class LocalLoginIntegrationTest : ReposiliteSpecification() {

    private val password = "correct horse battery"

    /**
     * A client of its own without cookie management. The default client stores the cookies of every response and sends
     * them back on later requests, which would replace the cookie a test sends on purpose.
     */
    private val http = Unirest.spawnInstance().also { it.config().enableCookieManagement(false) }

    private val loginFacade: LoginFacade
        get() = useFacade()

    private fun login(body: String) =
        http.post("$base/api/auth/login")
            .header("Content-Type", "application/json")
            .body(body)

    @Test
    fun `publishes the local password provider`() {
        // when: the login providers are requested without any credentials
        val response = http.get("$base/api/auth/providers").asJacksonObject(LoginProvidersResponse::class)

        // then: the local provider is announced with its label, and token login stays available
        assertSuccessResponse(OK, response) { providers ->
            assertThat(providers.providers.map { it.id to it.type }).containsExactly("local" to "LOCAL")
            assertThat(providers.providers.single().label).isEqualTo("Username and password")
            assertThat(providers.tokenLogin).isTrue()
        }
    }

    @Test
    fun `logs a user in with the right password`() {
        // given: a user with a password
        loginFacade.createUser("alice-login", "Alice", password)

        // when: the user logs in
        val response = login("""{"provider":"local","username":"alice-login","password":"$password"}""")
            .asJacksonObject(LoginResponse::class)

        // then: the principal is reported
        assertSuccessResponse(OK, response) { login ->
            assertThat(login.principal.name).isEqualTo("alice-login")
            assertThat(login.principal.kind).isEqualTo("USER")
            assertThat(login.principal.displayName).isEqualTo("Alice")
            assertThat(login.mustChangePassword).isFalse()
        }
    }

    @Test
    fun `answers a wrong password with 401`() {
        // given: a user with a password
        loginFacade.createUser("bob-login", "Bob", password)

        // when: the user logs in with a wrong password
        val response = login("""{"provider":"local","username":"bob-login","password":"wrong password entirely"}""")
            .asJacksonObject(ErrorResponse::class)

        // then: the request is rejected without saying which part was wrong
        assertErrorResponse(UNAUTHORIZED, response)
        assertThat(response.body.message).doesNotContain("bob-login")
    }

    @Test
    fun `refuses a disabled user with 403`() {
        // given: a disabled user with the right password
        loginFacade.createUser("carol-login", "Carol", password, disabled = true)

        // when: the user logs in
        val response = login("""{"provider":"local","username":"carol-login","password":"$password"}""")
            .asJacksonObject(ErrorResponse::class)

        // then: the login is refused
        assertErrorResponse(FORBIDDEN, response)
    }

    private fun cookieValue(headers: List<String>, name: String): String =
        headers.single { it.startsWith("$name=") }.substringAfter("=").substringBefore(";")

    private fun loginSession(username: String): Triple<String, String, String> {
        loginFacade.createUser(username, username, password)
        val response = login("""{"provider":"local","username":"$username","password":"$password"}""")
            .asJacksonObject(LoginResponse::class)
        val headers = response.headers.get("Set-Cookie")
        return Triple(
            cookieValue(headers, "ingot_session"),
            cookieValue(headers, "ingot_refresh"),
            cookieValue(headers, "ingot_csrf"),
        )
    }

    @Test
    fun `sets the session cookies HttpOnly where they must be and readable where they must be`() {
        // given: a user with a password
        loginFacade.createUser("dave-session", "Dave", password)

        // when: the user logs in
        val response = login("""{"provider":"local","username":"dave-session","password":"$password"}""")
            .asJacksonObject(LoginResponse::class)

        // then: the session and refresh cookies are HttpOnly, and the csrf cookie is not
        val headers = response.headers.get("Set-Cookie")
        assertThat(headers.single { it.startsWith("ingot_session=") }).contains("HttpOnly", "SameSite=Lax")
        assertThat(headers.single { it.startsWith("ingot_refresh=") }).contains("HttpOnly", "Path=/api/auth;")
        assertThat(headers.single { it.startsWith("ingot_csrf=") }).doesNotContain("HttpOnly")
    }

    @Test
    fun `authenticates a request with the session cookie`() {
        // given: a logged in user
        val (session) = loginSession("erin-session")

        // when: the current user is requested with the session cookie only
        val response = http.get("$base/api/auth/me")
            .header("Cookie", "ingot_session=$session")
            .asString()

        // then: the request is served as that user
        assertThat(response.status).isEqualTo(OK.code)
        assertThat(response.body).contains("erin-session")
    }

    @Test
    fun `renews the session and rotates the refresh token`() {
        // given: a logged in user
        val (_, refresh) = loginSession("frank-session")

        // when: the session is renewed with the refresh cookie
        val response = http.post("$base/api/auth/refresh")
            .header("Cookie", "ingot_refresh=$refresh")
            .asJacksonObject(SessionResponse::class)

        // then: a new refresh token is issued in place of the old one
        assertSuccessResponse(OK, response) { session ->
            assertThat(session.principal.name).isEqualTo("frank-session")
        }
        assertThat(cookieValue(response.headers.get("Set-Cookie"), "ingot_refresh")).isNotEqualTo(refresh)
    }

    @Test
    fun `revokes the session when a spent refresh token is replayed`() {
        // given: a session that has been renewed once, so its first refresh token is spent
        val (_, first) = loginSession("gina-session")
        val renewed = http.post("$base/api/auth/refresh")
            .header("Cookie", "ingot_refresh=$first")
            .asJacksonObject(SessionResponse::class)
        val second = cookieValue(renewed.headers.get("Set-Cookie"), "ingot_refresh")

        // when: the spent token is replayed
        val replay = http.post("$base/api/auth/refresh")
            .header("Cookie", "ingot_refresh=$first")
            .asString()

        // then: the replay is refused, and the token the legitimate client holds is revoked as well
        assertThat(replay.status).describedAs("replay body: ${replay.body}").isEqualTo(UNAUTHORIZED.code)
        val current = http.post("$base/api/auth/refresh")
            .header("Cookie", "ingot_refresh=$second")
            .asJacksonObject(ErrorResponse::class)
        assertErrorResponse(UNAUTHORIZED, current)
    }

    @Test
    fun `logout revokes the refresh token`() {
        // given: a logged in user
        val (_, refresh) = loginSession("hugo-session")

        // when: the user logs out and the old refresh token is presented afterwards
        val logout = http.post("$base/api/auth/logout")
            .header("Cookie", "ingot_refresh=$refresh")
            .asString()
        val afterLogout = http.post("$base/api/auth/refresh")
            .header("Cookie", "ingot_refresh=$refresh")
            .asJacksonObject(ErrorResponse::class)

        // then: the logout succeeds and the token no longer renews anything
        assertThat(logout.status).isEqualTo(OK.code)
        assertErrorResponse(UNAUTHORIZED, afterLogout)
    }

    @Test
    fun `rejects a request without any credentials`() {
        // when: a protected endpoint is requested without a header or a cookie
        val response = http.get("$base/api/auth/me").asJacksonObject(ErrorResponse::class)

        // then: the request is unauthorized
        assertErrorResponse(UNAUTHORIZED, response)
    }

    @Test
    fun `rejects an unknown provider with 400`() {
        // when: a login is sent to a provider that is not configured
        val response = login("""{"provider":"oidc","username":"alice","password":"$password"}""")
            .asJacksonObject(ErrorResponse::class)

        // then: the request is a bad request
        assertErrorResponse(BAD_REQUEST, response)
    }

    @Test
    fun `rejects a login without a password with 400`() {
        // when: a login arrives without a password
        val response = login("""{"provider":"local","username":"alice"}""")
            .asJacksonObject(ErrorResponse::class)

        // then: the request is a bad request
        assertErrorResponse(BAD_REQUEST, response)
    }

}
