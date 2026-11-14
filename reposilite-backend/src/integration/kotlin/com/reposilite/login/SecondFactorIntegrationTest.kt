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
import com.reposilite.ReposiliteParameters
import com.reposilite.ReposiliteSpecification
import com.reposilite.audit.api.AuditActor
import com.reposilite.shared.ErrorResponse
import io.javalin.http.HttpStatus.FORBIDDEN
import io.javalin.http.HttpStatus.OK
import io.javalin.http.HttpStatus.UNAUTHORIZED
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import kong.unirest.core.Unirest
import net.onelitefeather.ingot.identity.api.CreatedTokenDto
import net.onelitefeather.ingot.identity.login.LoginFacade
import net.onelitefeather.ingot.identity.login.OwnTokenOutcome
import net.onelitefeather.ingot.identity.login.api.LoginResponse
import net.onelitefeather.ingot.identity.login.api.SecondFactorRequiredResponse
import net.onelitefeather.ingot.identity.login.api.SelfTokenRequest
import net.onelitefeather.ingot.identity.login.mfa.EnrolmentConfirmation
import net.onelitefeather.ingot.identity.login.mfa.TotpAlgorithm
import net.onelitefeather.ingot.identity.login.mfa.TotpEnrolmentStart
import net.onelitefeather.ingot.settings.encryption.SettingsKeySources
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * The second factor on the wire. A user with an authenticator gets a challenge instead of a session, the code completes the
 * sign-in, and a Basic access token of the same user keeps working without any prompt. The instance has a settings key, so
 * an authenticator can be enrolled at all.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class SecondFactorIntegrationTest : ReposiliteSpecification() {

    private val password = "correct horse battery"
    private val key = Base64.getEncoder().encodeToString(ByteArray(32) { (it * 5 + 1).toByte() })

    /** A client without cookie management, so that the test decides which cookie a request carries. */
    private val http = Unirest.spawnInstance().also { it.config().enableCookieManagement(false) }

    /** The clock of the server, fixed, so that the codes of the test are the codes of the server at any time of day. */
    private val serverClock = Clock.fixed(Instant.parse("2026-11-14T10:00:00Z"), ZoneOffset.UTC)

    override fun overrideParameters(parameters: ReposiliteParameters) {
        parameters.settingsSecretEnvironment = mapOf(SettingsKeySources.KEY to key)
        parameters.clock = serverClock
    }

    private val loginFacade: LoginFacade
        get() = useFacade()

    private fun cookieValue(headers: List<String>, name: String): String =
        headers.single { it.startsWith("$name=") }.substringAfter("=").substringBefore(";")

    private fun passwordLogin(username: String) =
        http.post("$base/api/auth/login")
            .header("Content-Type", "application/json")
            .body("""{"provider":"local","username":"$username","password":"$password"}""")

    /** A user with a confirmed authenticator. Returns the id and the seed of the authenticator. */
    private fun userWithAuthenticator(username: String): Pair<Int, ByteArray> {
        val principalId = checkNotNull(loginFacade.createUser(username, username, password)) { "user $username" }.id
        val started = loginFacade.startAuthenticatorEnrolment(principalId, username, code = null, ip = "127.0.0.1") as TotpEnrolmentStart.Started
        val now = TotpAlgorithm.code(started.seed, currentStep())
        val confirmed = loginFacade.confirmAuthenticatorEnrolment(principalId, username, now, ip = "127.0.0.1")
        assertThat(confirmed).`as`("enrolment with the current code").isInstanceOf(EnrolmentConfirmation.Confirmed::class.java)
        return principalId to started.seed
    }

    private fun issueToken(principalId: Int, name: String): CreatedTokenDto {
        val issued = loginFacade.issueOwnToken(principalId, AuditActor.named(name, "127.0.0.1"), SelfTokenRequest(name = name))
        return (issued as OwnTokenOutcome.Issued).dto
    }

    private fun currentStep() = serverClock.instant().epochSecond / TotpAlgorithm.STEP_SECONDS

    @Test
    fun `a password login of a user with an authenticator answers with a challenge and sets no session cookie`() {
        // given: a user whose authenticator is confirmed
        userWithAuthenticator("mia-mfa")

        // when: the password is sent
        val response = passwordLogin("mia-mfa").asJacksonObject(SecondFactorRequiredResponse::class)

        // then: the answer asks for the second factor, and only the challenge cookie is set
        assertThat(response.status).`as`("status of the challenge answer").isEqualTo(OK.code)
        assertThat(response.body.mfaRequired).isTrue()
        val cookies = response.headers.get("Set-Cookie")
        assertThat(cookies.any { it.startsWith("ingot_mfa=") }).`as`("challenge cookie").isTrue()
        assertThat(cookies.any { it.startsWith("ingot_session=") }).`as`("no session cookie before the second factor").isFalse()
    }

    @Test
    fun `the current authenticator code completes the sign-in and starts the session`() {
        // given: a user with an authenticator and a challenge from the password
        val (_, seed) = userWithAuthenticator("nina-mfa")
        val challenge = cookieValue(passwordLogin("nina-mfa").asString().headers.get("Set-Cookie"), "ingot_mfa")

        // when: the code of the next step is sent with the challenge cookie. The enrolment used the current step, so the
        // next one is the first step that may still be accepted.
        val response = http.post("$base/api/auth/mfa/verify")
            .header("Content-Type", "application/json")
            .header("Cookie", "ingot_mfa=$challenge")
            .body("""{"code":"${TotpAlgorithm.code(seed, currentStep() + 1)}"}""")
            .asJacksonObject(LoginResponse::class)

        // then: the session starts, and the challenge cookie is cleared
        assertSuccessResponse(OK, response) { login -> assertThat(login.principal.name).isEqualTo("nina-mfa") }
        val cookies = response.headers.get("Set-Cookie")
        assertThat(cookies.any { it.startsWith("ingot_session=") }).`as`("session cookie after the second factor").isTrue()
        assertThat(cookies.single { it.startsWith("ingot_mfa=") }).`as`("cleared challenge cookie").contains("Max-Age=0")
    }

    @Test
    fun `a challenge cannot complete a second sign-in once it was spent`() {
        // given: a user with an authenticator and a challenge
        val (_, seed) = userWithAuthenticator("olga-mfa")
        val challenge = cookieValue(passwordLogin("olga-mfa").asString().headers.get("Set-Cookie"), "ingot_mfa")
        val verify = { code: String ->
            http.post("$base/api/auth/mfa/verify")
                .header("Content-Type", "application/json")
                .header("Cookie", "ingot_mfa=$challenge")
                .body("""{"code":"$code"}""")
        }
        val right = TotpAlgorithm.code(seed, currentStep() + 1)
        verify(right).asString()

        // when: the same challenge is completed a second time
        val again = verify(right).asJacksonObject(ErrorResponse::class)

        // then: it is refused, because the challenge was spent by the first completion
        assertThat(again.status).`as`("status of a spent challenge").isEqualTo(UNAUTHORIZED.code)
    }

    @Test
    fun `a basic access token of a user with an authenticator needs no second factor`() {
        // given: a user with an authenticator and an access token of their own
        val (principalId, _) = userWithAuthenticator("pia-mfa")
        val issued = loginFacade.issueOwnToken(principalId, AuditActor.named("pia-mfa", "127.0.0.1"), SelfTokenRequest(name = "ci-pia"))
        val token = (issued as OwnTokenOutcome.Issued).dto

        // when: the token is used for Basic authentication
        val response = http.get("$base/api/identity/me/tokens")
            .basicAuth(token.token.name, token.secret)
            .asString()

        // then: it is answered without any challenge
        assertThat(response.status).`as`("status with the token").isEqualTo(OK.code)
    }

    @Test
    fun `a basic token cannot list the second factors of its user`() {
        val (principalId, _) = userWithAuthenticator("sara-mfa")
        val token = issueToken(principalId, "sara-ci")

        val response = http.get("$base/api/identity/me/factors").basicAuth(token.token.name, token.secret).asString()

        assertThat(response.status).`as`("status of a token request").isEqualTo(FORBIDDEN.code)
    }

    @Test
    fun `a basic token cannot start an authenticator enrolment`() {
        val (principalId, _) = userWithAuthenticator("tara-mfa")
        val token = issueToken(principalId, "tara-ci")

        val response = http.post("$base/api/identity/me/factors/totp").basicAuth(token.token.name, token.secret).asString()

        assertThat(response.status).`as`("status of a token enrolment").isEqualTo(FORBIDDEN.code)
    }

    @Test
    fun `a basic token cannot remove a factor even with a code`() {
        val (principalId, seed) = userWithAuthenticator("ursula-mfa")
        val factorId = loginFacade.secondFactorsOf(principalId).single().id
        val token = issueToken(principalId, "ursula-ci")

        val response = http.delete("$base/api/identity/me/factors/$factorId")
            .basicAuth(token.token.name, token.secret)
            .header("Content-Type", "application/json")
            .body("""{"code":"${TotpAlgorithm.code(seed, currentStep() + 1)}"}""")
            .asString()

        assertThat(response.status).`as`("status of a token removal").isEqualTo(FORBIDDEN.code)
    }

    @Test
    fun `a basic token cannot regenerate recovery codes`() {
        val (principalId, seed) = userWithAuthenticator("vera-mfa")
        val token = issueToken(principalId, "vera-ci")

        val response = http.post("$base/api/identity/me/recovery-codes")
            .basicAuth(token.token.name, token.secret)
            .header("Content-Type", "application/json")
            .body("""{"code":"${TotpAlgorithm.code(seed, currentStep() + 1)}"}""")
            .asString()

        assertThat(response.status).`as`("status of a token regeneration").isEqualTo(FORBIDDEN.code)
    }

    @Test
    fun `a user without an authenticator logs in exactly as before`() {
        // given: a user without any second factor
        loginFacade.createUser("quinn-mfa", "Quinn", password)

        // when: the password is sent
        val response = passwordLogin("quinn-mfa").asJacksonObject(LoginResponse::class)

        // then: the session starts, with no challenge
        assertSuccessResponse(OK, response) { login -> assertThat(login.principal.name).isEqualTo("quinn-mfa") }
        assertThat(response.headers.get("Set-Cookie").any { it.startsWith("ingot_mfa=") }).isFalse()
    }

    @Test
    fun `a wrong password of a user with an authenticator is refused like any wrong password`() {
        // given: a user with an authenticator
        userWithAuthenticator("rita-mfa")

        // when: a wrong password is sent
        val response = http.post("$base/api/auth/login")
            .header("Content-Type", "application/json")
            .body("""{"provider":"local","username":"rita-mfa","password":"wrong password entirely"}""")
            .asJacksonObject(ErrorResponse::class)

        // then: the answer is the plain refusal, which does not reveal the second factor
        assertErrorResponse(UNAUTHORIZED, response)
    }

}
