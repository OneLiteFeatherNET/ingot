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
import io.javalin.http.HttpStatus.TOO_MANY_REQUESTS
import io.javalin.http.HttpStatus.UNAUTHORIZED
import kong.unirest.core.HttpRequestWithBody
import kong.unirest.core.Unirest
import net.onelitefeather.ingot.identity.api.CreatedTokenDto
import net.onelitefeather.ingot.identity.login.LoginFacade
import net.onelitefeather.ingot.identity.login.api.LoginProvidersResponse
import net.onelitefeather.ingot.identity.login.api.LoginResponse
import net.onelitefeather.ingot.identity.login.api.SessionResponse
import net.onelitefeather.ingot.identity.login.api.SetupStatusResponse
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

    /** The cookie values of one browser session, as the login response sets them. */
    private data class Session(val session: String, val refresh: String, val csrf: String)

    private fun login(body: String) =
        http.post("$base/api/auth/login")
            .header("Content-Type", "application/json")
            .body(body)

    private fun cookieValue(headers: List<String>, name: String): String =
        headers.single { it.startsWith("$name=") }.substringAfter("=").substringBefore(";")

    private fun loginSession(username: String): Session {
        loginFacade.createUser(username, username, password)
        val headers = login("""{"provider":"local","username":"$username","password":"$password"}""")
            .asJacksonObject(LoginResponse::class)
            .headers
            .get("Set-Cookie")
        return Session(
            session = cookieValue(headers, "ingot_session"),
            refresh = cookieValue(headers, "ingot_refresh"),
            csrf = cookieValue(headers, "ingot_csrf"),
        )
    }

    /** A renewal as the browser sends it: the refresh cookie, the csrf cookie and the header that echoes it. */
    private fun renew(refresh: String, csrf: String) =
        http.post("$base/api/auth/refresh")
            .header("Cookie", "ingot_refresh=$refresh; ingot_csrf=$csrf")
            .header("X-CSRF-Token", csrf)

    @Test
    fun `announces setup instead of the local provider on an empty instance`() {
        // when: the login providers are requested on an instance without any user
        val response = http.get("$base/api/auth/providers").asJacksonObject(LoginProvidersResponse::class)

        // then: no password provider is listed, because there is no account to log in with yet, and setup is required
        assertSuccessResponse(OK, response) { providers ->
            assertThat(providers.providers).isEmpty()
            assertThat(providers.setupRequired).isTrue()
            assertThat(providers.tokenLogin).isTrue()
        }
    }

    @Test
    fun `publishes the local password provider once a user exists`() {
        // given: a user exists, so the local provider has an account to check
        loginFacade.createUser("dave-login", "Dave", password)

        // when: the login providers are requested without any credentials
        val response = http.get("$base/api/auth/providers").asJacksonObject(LoginProvidersResponse::class)

        // then: the local provider is announced with its label, setup is no longer required, and token login stays available
        assertSuccessResponse(OK, response) { providers ->
            assertThat(providers.providers.map { it.id to it.type }).containsExactly("local" to "LOCAL")
            assertThat(providers.providers.single().label).isEqualTo("Username and password")
            assertThat(providers.setupRequired).isFalse()
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

    @Test
    fun `answers a locked login with 429 and tells the client when to retry`() {
        // given: a user with a password, and five wrong passwords for that user name from this address
        loginFacade.createUser("dave-login", "Dave", password)
        repeat(5) { login("""{"provider":"local","username":"dave-login","password":"wrong password entirely"}""").asString() }

        // when: the right password is sent while the user name is locked
        val response = login("""{"provider":"local","username":"dave-login","password":"$password"}""")
            .asJacksonObject(ErrorResponse::class)

        // then: the login is refused with 429 and the wait is given in whole seconds, at most the default lockout
        assertErrorResponse(TOO_MANY_REQUESTS, response)
        assertThat(response.headers.getFirst("Retry-After").toLong()).isBetween(1L, 15 * 60L)
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
        val session = loginSession("erin-session")

        // when: the current user is requested with the session cookie only
        val response = http.get("$base/api/auth/me")
            .header("Cookie", "ingot_session=${session.session}")
            .asString()

        // then: the request is served as that user
        assertThat(response.status).isEqualTo(OK.code)
        assertThat(response.body).contains("erin-session")
    }

    @Test
    fun `renews the session and rotates the refresh token`() {
        // given: a logged in user
        val session = loginSession("frank-session")

        // when: the session is renewed with the refresh cookie
        val response = renew(session.refresh, session.csrf).asJacksonObject(SessionResponse::class)

        // then: the principal is reported, and a new refresh token replaces the old one
        assertSuccessResponse(OK, response) { renewed ->
            assertThat(renewed.principal.name).isEqualTo("frank-session")
        }
        assertThat(cookieValue(response.headers.get("Set-Cookie"), "ingot_refresh")).isNotEqualTo(session.refresh)
    }

    @Test
    fun `revokes the session when a spent refresh token is replayed`() {
        // given: a session that has been renewed once, so its first refresh token is spent
        val first = loginSession("gina-session")
        val renewed = renew(first.refresh, first.csrf).asJacksonObject(SessionResponse::class)
        val second = cookieValue(renewed.headers.get("Set-Cookie"), "ingot_refresh")
        val secondCsrf = cookieValue(renewed.headers.get("Set-Cookie"), "ingot_csrf")

        // when: the spent token is replayed
        val replay = renew(first.refresh, first.csrf).asString()

        // then: the replay is refused, and the token the legitimate client holds is revoked as well
        assertThat(replay.status).describedAs("replay body: ${replay.body}").isEqualTo(UNAUTHORIZED.code)
        val current = renew(second, secondCsrf).asString()
        assertThat(current.status).describedAs("current body: ${current.body}").isEqualTo(UNAUTHORIZED.code)
    }

    @Test
    fun `logout revokes the refresh token`() {
        // given: a logged in user
        val session = loginSession("hugo-session")

        // when: the user logs out and the old refresh token is presented afterwards
        val logout = http.post("$base/api/auth/logout")
            .header("Cookie", "ingot_refresh=${session.refresh}; ingot_csrf=${session.csrf}")
            .header("X-CSRF-Token", session.csrf)
            .asString()
        val afterLogout = renew(session.refresh, session.csrf).asJacksonObject(ErrorResponse::class)

        // then: the logout succeeds and the token no longer renews anything
        assertThat(logout.status).isEqualTo(OK.code)
        assertErrorResponse(UNAUTHORIZED, afterLogout)
    }

    @Test
    fun `refuses a cookie logout without the csrf header and keeps the session`() {
        // given: a logged in user
        val session = loginSession("ivan-csrf")

        // when: the logout carries the cookies but not the header that echoes the csrf cookie
        val logout = http.post("$base/api/auth/logout")
            .header("Cookie", "ingot_refresh=${session.refresh}; ingot_csrf=${session.csrf}")
            .asJacksonObject(ErrorResponse::class)

        // then: the request is refused, and the refresh token still renews the session
        assertErrorResponse(FORBIDDEN, logout)
        val renewed = renew(session.refresh, session.csrf).asJacksonObject(SessionResponse::class)
        assertSuccessResponse(OK, renewed) { renewedSession -> assertThat(renewedSession.principal.name).isEqualTo("ivan-csrf") }
    }

    @Test
    fun `refuses a cookie logout with a forged csrf header`() {
        // given: a logged in user
        val session = loginSession("judy-csrf")

        // when: the header carries a value other than the csrf cookie
        val logout = http.post("$base/api/auth/logout")
            .header("Cookie", "ingot_refresh=${session.refresh}; ingot_csrf=${session.csrf}")
            .header("X-CSRF-Token", "forged-value")
            .asJacksonObject(ErrorResponse::class)

        // then: the request is refused
        assertErrorResponse(FORBIDDEN, logout)
    }

    @Test
    fun `accepts a cookie logout that echoes the csrf cookie in the header`() {
        // given: a logged in user
        val session = loginSession("kate-csrf")

        // when: the logout echoes the csrf cookie
        val logout = http.post("$base/api/auth/logout")
            .header("Cookie", "ingot_refresh=${session.refresh}; ingot_csrf=${session.csrf}")
            .header("X-CSRF-Token", session.csrf)
            .asString()

        // then: the logout succeeds
        assertThat(logout.status).isEqualTo(OK.code)
    }

    @Test
    fun `does not ask for the csrf header of a basic authenticated request`() {
        // when: a request authenticated by Basic authentication, with no cookies, changes state
        val response = http.post("$base/api/auth/logout")
            .basicAuth("nobody", "no-secret")
            .asString()

        // then: the csrf check does not apply, so the logout is served
        assertThat(response.status).isEqualTo(OK.code)
    }

    private fun withSession(request: HttpRequestWithBody, session: Session): HttpRequestWithBody =
        request
            .header("Cookie", "ingot_session=${session.session}; ingot_csrf=${session.csrf}")
            .header("X-CSRF-Token", session.csrf)
            .header("Content-Type", "application/json")

    private fun administratorSession(): Session {
        val code = loginFacade.issueSetupCode()!!
        val headers = setup("""{"setupCode":"$code","username":"root-admin","password":"$password"}""")
            .asJacksonObject(LoginResponse::class)
            .headers
            .get("Set-Cookie")
        return Session(
            session = cookieValue(headers, "ingot_session"),
            refresh = cookieValue(headers, "ingot_refresh"),
            csrf = cookieValue(headers, "ingot_csrf"),
        )
    }

    private fun createOwnToken(session: Session, name: String): String =
        withSession(http.post("$base/api/identity/me/tokens"), session)
            .body("""{"name":"$name"}""")
            .asJacksonObject(CreatedTokenDto::class)
            .body
            .secret

    private fun bearerStatus(secret: String): Int =
        http.get("$base/api/auth/me").header("Authorization", "Bearer $secret").asString().status

    @Test
    fun `creates an own token whose secret authenticates as a bearer and never shows again`() {
        // given: a signed-in user
        val session = loginSession("liam-token")

        // when: a token is created, and the user uses its secret as a bearer
        val created = withSession(http.post("$base/api/identity/me/tokens"), session)
            .body("""{"name":"ci"}""")
            .asJacksonObject(CreatedTokenDto::class)
        assertSuccessResponse(OK, created) { dto -> assertThat(dto.token.name).isEqualTo("ci") }
        val secret = created.body.secret
        assertThat(secret).startsWith("ingot_")

        // then: the secret authenticates as the user, and the token list does not show it
        val me = http.get("$base/api/auth/me").header("Authorization", "Bearer $secret").asString()
        assertThat(me.status).describedAs("bearer body: ${me.body}").isEqualTo(OK.code)
        assertThat(me.body).contains("liam-token")
        val listed = http.get("$base/api/identity/me/tokens").header("Cookie", "ingot_session=${session.session}").asString()
        assertThat(listed.body).contains("\"ci\"").doesNotContain(secret)
    }

    @Test
    fun `refuses a bearer secret that is changed by one character`() {
        // given: a token and its secret
        val secret = createOwnToken(loginSession("mona-token"), "ci")

        // when: the secret is presented with its last character changed
        val forged = secret.dropLast(1) + (if (secret.last() == 'A') 'B' else 'A')

        // then: the request is unauthorized
        assertThat(bearerStatus(forged)).isEqualTo(UNAUTHORIZED.code)
    }

    @Test
    fun `rotates an own token and the old bearer secret stops working`() {
        // given: a token whose secret is in use
        val session = loginSession("nico-token")
        val old = createOwnToken(session, "ci")

        // when: the token is rotated
        val rotated = withSession(http.post("$base/api/identity/me/tokens/ci/rotate"), session).asJacksonObject(CreatedTokenDto::class)
        assertSuccessResponse(OK, rotated) { dto -> assertThat(dto.secret).isNotEqualTo(old) }

        // then: the old secret is refused and the new one works
        assertThat(bearerStatus(old)).describedAs("the old secret must stop at once").isEqualTo(UNAUTHORIZED.code)
        assertThat(bearerStatus(rotated.body.secret)).isEqualTo(OK.code)
    }

    @Test
    fun `deletes an own token and its bearer secret stops working`() {
        // given: a token whose secret is in use
        val session = loginSession("olga-token")
        val secret = createOwnToken(session, "ci")

        // when: the token is deleted
        val deleted = withSession(http.delete("$base/api/identity/me/tokens/ci"), session).asString()

        // then: the deletion succeeds and the secret is refused
        assertThat(deleted.status).isEqualTo(OK.code)
        assertThat(bearerStatus(secret)).isEqualTo(UNAUTHORIZED.code)
    }

    @Test
    fun `changes the own password, ends the sessions and accepts only the new password`() {
        // given: a signed-in user
        val session = loginSession("nina-pw")
        val replacement = "a brand new passphrase"

        // when: the password is changed with the current one
        val changed = withSession(http.post("$base/api/identity/me/password"), session)
            .body("""{"currentPassword":"$password","newPassword":"$replacement"}""")
            .asString()

        // then: the old password is refused, the new one logs in, and the old session cannot be renewed
        assertThat(changed.status).isEqualTo(OK.code)
        assertThat(login("""{"provider":"local","username":"nina-pw","password":"$password"}""").asString().status).isEqualTo(UNAUTHORIZED.code)
        assertThat(login("""{"provider":"local","username":"nina-pw","password":"$replacement"}""").asString().status).isEqualTo(OK.code)
        assertThat(renew(session.refresh, session.csrf).asString().status).describedAs("sessions must end with the password").isEqualTo(UNAUTHORIZED.code)
    }

    @Test
    fun `refuses a password change with the wrong current password`() {
        // given: a signed-in user
        val session = loginSession("otto-pw")

        // when: the current password is wrong
        val response = withSession(http.post("$base/api/identity/me/password"), session)
            .body("""{"currentPassword":"not the current one","newPassword":"a brand new passphrase"}""")
            .asJacksonObject(ErrorResponse::class)

        // then: the change is refused
        assertErrorResponse(FORBIDDEN, response)
    }

    @Test
    fun `resets a user's password as administrator and forces a change at the next login`() {
        // given: an administrator and a user
        val admin = administratorSession()
        loginFacade.createUser("oscar-reset", "Oscar", password)

        // when: the administrator resets the password
        val reset = withSession(http.post("$base/api/identity/principals/oscar-reset/password"), admin)
            .body("""{"newPassword":"reset passphrase here"}""")
            .asString()

        // then: the user logs in with the new password and must change it
        assertThat(reset.status).isEqualTo(OK.code)
        val login = login("""{"provider":"local","username":"oscar-reset","password":"reset passphrase here"}""")
            .asJacksonObject(LoginResponse::class)
        assertSuccessResponse(OK, login) { response -> assertThat(response.mustChangePassword).isTrue() }
    }

    @Test
    fun `refuses a password reset from a user who is no administrator`() {
        // given: a user who is not an administrator, and another user
        val session = loginSession("paula-reset")
        loginFacade.createUser("quinn-reset", "Quinn", password)

        // when: the first user tries to reset the second password
        val response = withSession(http.post("$base/api/identity/principals/quinn-reset/password"), session)
            .body("""{"newPassword":"reset passphrase here"}""")
            .asJacksonObject(ErrorResponse::class)

        // then: the reset is forbidden
        assertErrorResponse(FORBIDDEN, response)
    }

    @Test
    fun `rejects a request without any credentials`() {
        // when: a protected endpoint is requested without a header or a cookie
        val response = http.get("$base/api/auth/me").asJacksonObject(ErrorResponse::class)

        // then: the request is unauthorized
        assertErrorResponse(UNAUTHORIZED, response)
    }

    private fun setup(body: String) =
        http.post("$base/api/auth/setup")
            .header("Content-Type", "application/json")
            .body(body)

    @Test
    fun `reports the setup as required on an empty instance`() {
        // when: the setup status is requested on an instance without users
        val response = http.get("$base/api/auth/setup").asJacksonObject(SetupStatusResponse::class)

        // then: the first administrator must still be created
        assertSuccessResponse(OK, response) { status -> assertThat(status.required).isTrue() }
    }

    @Test
    fun `creates the first administrator with the setup code and starts an administrator session`() {
        // given: the setup code as it is shown in the log
        val code = loginFacade.issueSetupCode()!!

        // when: the first administrator is created with it
        val response = setup("""{"setupCode":"$code","username":"root-setup","password":"$password"}""")
            .asJacksonObject(LoginResponse::class)

        // then: the user is reported, and the session cookie carries the instance administrator permission
        assertSuccessResponse(OK, response) { login -> assertThat(login.principal.name).isEqualTo("root-setup") }
        val session = cookieValue(response.headers.get("Set-Cookie"), "ingot_session")
        val me = http.get("$base/api/auth/me").header("Cookie", "ingot_session=$session").asString()
        assertThat(me.body).describedAs("session body: ${me.body}").contains("access-token:manager")
        val status = http.get("$base/api/auth/setup").asJacksonObject(SetupStatusResponse::class)
        assertSuccessResponse(OK, status) { setupStatus -> assertThat(setupStatus.required).isFalse() }
    }

    @Test
    fun `refuses a wrong setup code with 403 and keeps the setup open`() {
        // given: an instance where the setup code has been issued
        loginFacade.issueSetupCode()

        // when: a wrong code is used
        val response = setup("""{"setupCode":"WRONGCODE234","username":"root-wrong","password":"$password"}""")
            .asJacksonObject(ErrorResponse::class)

        // then: the request is forbidden, and the setup is still open
        assertErrorResponse(FORBIDDEN, response)
        val status = http.get("$base/api/auth/setup").asJacksonObject(SetupStatusResponse::class)
        assertSuccessResponse(OK, status) { setupStatus -> assertThat(setupStatus.required).isTrue() }
    }

    @Test
    fun `refuses setup once an administrator exists`() {
        // given: an existing user
        loginFacade.createUser("existing-admin", "Existing", password)

        // when: someone tries to create another first administrator
        val response = setup("""{"setupCode":"ANYCODE12345","username":"intruder","password":"$password"}""")
            .asJacksonObject(ErrorResponse::class)

        // then: the setup is refused
        assertErrorResponse(FORBIDDEN, response)
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
