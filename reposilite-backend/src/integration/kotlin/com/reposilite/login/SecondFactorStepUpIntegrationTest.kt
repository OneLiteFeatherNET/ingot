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
import io.javalin.http.HttpStatus.CONFLICT
import io.javalin.http.HttpStatus.FORBIDDEN
import io.javalin.http.HttpStatus.OK
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Base64
import kong.unirest.core.Unirest
import net.onelitefeather.ingot.identity.login.LoginFacade
import net.onelitefeather.ingot.identity.login.api.SelfTokenRequest
import net.onelitefeather.ingot.identity.login.mfa.EnrolmentConfirmation
import net.onelitefeather.ingot.identity.login.mfa.TotpAlgorithm
import net.onelitefeather.ingot.identity.login.mfa.TotpEnrolmentStart
import net.onelitefeather.ingot.identity.login.OwnTokenOutcome
import net.onelitefeather.ingot.settings.encryption.SettingsKeySources
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * The step-up window on the wire. A browser session of a user with an authenticator may change its account only while the
 * second factor is recent, five minutes, and a session of a user without a factor needs no step-up except for the first
 * factor. Basic tokens are not prompted at all.
 *
 * The server clock moves only when a test moves it, so the five-minute boundary is exact and no test waits.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class SecondFactorStepUpIntegrationTest : ReposiliteSpecification() {

    private val password = "correct horse battery"
    private val newPassword = "a new correct horse battery"
    private val key = Base64.getEncoder().encodeToString(ByteArray(32) { (it * 7 + 3).toByte() })

    /** A client without cookie management, so that the test decides which cookie a request carries. */
    private val http = Unirest.spawnInstance().also { it.config().enableCookieManagement(false) }

    /** The clock of the server. It moves only through [ShiftableClock.advance]. */
    private val clock = ShiftableClock(Instant.parse("2026-11-14T10:00:00Z"))

    override fun overrideParameters(parameters: ReposiliteParameters) {
        parameters.settingsSecretEnvironment = mapOf(SettingsKeySources.KEY to key)
        parameters.clock = clock
    }

    private val loginFacade: LoginFacade
        get() = useFacade()

    /** The cookies and the csrf header value of a browser session. */
    private data class Session(val cookies: String, val csrf: String)

    /** A clock that moves only when a test moves it. */
    private class ShiftableClock(private var now: Instant) : Clock() {

        fun advance(duration: Duration) {
            now = now.plus(duration)
        }

        override fun getZone(): ZoneId = ZoneId.of("UTC")

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = now

    }

    private fun cookieValue(headers: List<String>, name: String): String =
        headers.single { it.startsWith("$name=") }.substringAfter("=").substringBefore(";")

    /** Moves the clock to the next authenticator step, so that a code of the previous step is never reused. */
    private fun nextStep() {
        clock.advance(Duration.ofSeconds(TotpAlgorithm.STEP_SECONDS))
    }

    private fun codeNow(seed: ByteArray): String =
        TotpAlgorithm.code(seed, clock.instant().epochSecond / TotpAlgorithm.STEP_SECONDS)

    private fun passwordLogin(username: String) =
        http.post("$base/api/auth/login")
            .header("Content-Type", "application/json")
            .body("""{"provider":"local","username":"$username","password":"$password"}""")
            .asString()

    /** A user with a confirmed authenticator. Returns the id and the seed. */
    private fun userWithAuthenticator(username: String): Pair<Int, ByteArray> {
        val principalId = checkNotNull(loginFacade.createUser(username, username, password)) { "user $username" }.id
        val started = loginFacade.startAuthenticatorEnrolment(principalId, username, code = null, ip = "127.0.0.1") as TotpEnrolmentStart.Started
        val confirmed = loginFacade.confirmAuthenticatorEnrolment(principalId, username, codeNow(started.seed), ip = "127.0.0.1")
        assertThat(confirmed).`as`("enrolment with the current code").isInstanceOf(EnrolmentConfirmation.Confirmed::class.java)
        return principalId to started.seed
    }

    /** A browser session of a user with an authenticator. The second factor is completed at the moment of the answer. */
    private fun sessionWithFactor(username: String, seed: ByteArray): Session {
        nextStep()
        val challenge = cookieValue(passwordLogin(username).headers.get("Set-Cookie"), "ingot_mfa")
        nextStep()
        val answer = http.post("$base/api/auth/mfa/verify")
            .header("Content-Type", "application/json")
            .header("Cookie", "ingot_mfa=$challenge")
            .body("""{"code":"${codeNow(seed)}"}""")
            .asString()
        assertThat(answer.status).`as`("status of the second factor").isEqualTo(OK.code)
        return sessionOf(answer.headers.get("Set-Cookie"))
    }

    /** A browser session of a user without a factor. */
    private fun sessionWithoutFactor(username: String): Session =
        sessionOf(passwordLogin(username).headers.get("Set-Cookie"))

    private fun sessionOf(headers: List<String>): Session {
        val cookies = listOf("ingot_session", "ingot_refresh", "ingot_csrf").joinToString("; ") { "$it=${cookieValue(headers, it)}" }
        return Session(cookies, cookieValue(headers, "ingot_csrf"))
    }

    private fun sessionDelete(session: Session, path: String, body: String) =
        http.delete("$base$path")
            .header("Content-Type", "application/json")
            .header("Cookie", session.cookies)
            .header("X-CSRF-Token", session.csrf)
            .body(body)
            .asString()

    private fun sessionPost(session: Session, path: String, body: String) =
        http.post("$base$path")
            .header("Content-Type", "application/json")
            .header("Cookie", session.cookies)
            .header("X-CSRF-Token", session.csrf)
            .body(body)
            .asString()

    @Test
    fun `removing a factor with a session older than five minutes is refused with step_up_required`() {
        // given: a session whose second factor was completed, and six minutes later
        val (principalId, seed) = userWithAuthenticator("step-remove-old")
        val session = sessionWithFactor("step-remove-old", seed)
        val factorId = loginFacade.secondFactorsOf(principalId).single().id
        clock.advance(Duration.ofSeconds(301))
        nextStep()

        // when: the factor is removed with a current code
        val response = sessionDelete(session, "/api/identity/me/factors/$factorId", """{"code":"${codeNow(seed)}"}""")

        // then: the request is refused, and names the step-up
        assertThat(response.status).`as`("status outside the window").isEqualTo(FORBIDDEN.code)
        assertThat(response.body).`as`("problem of the refusal").contains("step_up_required")
    }

    @Test
    fun `removing a factor with a session inside the five minute window is accepted`() {
        // given: a session whose second factor was completed, and 299 seconds later
        val (principalId, seed) = userWithAuthenticator("step-remove-fresh")
        val session = sessionWithFactor("step-remove-fresh", seed)
        val factorId = loginFacade.secondFactorsOf(principalId).single().id
        clock.advance(Duration.ofSeconds(299))

        // when: the factor is removed with a current code
        val response = sessionDelete(session, "/api/identity/me/factors/$factorId", """{"code":"${codeNow(seed)}"}""")

        // then: the removal is accepted
        assertThat(response.status).`as`("status inside the window").isEqualTo(OK.code)
    }

    @Test
    fun `changing the password with a session older than five minutes is refused when the user has a factor`() {
        // given: a session of a user with a factor, six minutes after its second factor
        val (_, seed) = userWithAuthenticator("step-password-old")
        val session = sessionWithFactor("step-password-old", seed)
        clock.advance(Duration.ofSeconds(301))

        // when: the password is changed
        val response = sessionPost(session, "/api/identity/me/password", """{"currentPassword":"$password","newPassword":"$newPassword"}""")

        // then: the change is refused with step_up_required
        assertThat(response.status).`as`("status outside the window").isEqualTo(FORBIDDEN.code)
        assertThat(response.body).contains("step_up_required")
    }

    @Test
    fun `creating an own token with a session older than five minutes is refused when the user has a factor`() {
        // given: a session of a user with a factor, six minutes after its second factor
        val (_, seed) = userWithAuthenticator("step-token-old")
        val session = sessionWithFactor("step-token-old", seed)
        clock.advance(Duration.ofSeconds(301))

        // when: a token is created from the session
        val response = sessionPost(session, "/api/identity/me/tokens", """{"name":"step-token-ci"}""")

        // then: the creation is refused with step_up_required
        assertThat(response.status).`as`("status outside the window").isEqualTo(FORBIDDEN.code)
        assertThat(response.body).contains("step_up_required")
    }

    @Test
    fun `creating an own token with a basic credential is not prompted, even outside the window`() {
        // given: a user with a factor and a Basic token of their own, long after any second factor
        val (principalId, _) = userWithAuthenticator("step-token-basic")
        val token = (loginFacade.issueOwnToken(principalId, AuditActor.named("step-token-basic", "127.0.0.1"), SelfTokenRequest(name = "step-basic-ci")) as OwnTokenOutcome.Issued).dto
        clock.advance(Duration.ofHours(1))

        // when: a token is created with the Basic credential
        val response = http.post("$base/api/identity/me/tokens")
            .header("Content-Type", "application/json")
            .basicAuth(token.token.name, token.secret)
            .body("""{"name":"step-basic-second"}""")
            .asString()

        // then: no step-up is asked of a Basic caller
        assertThat(response.body).`as`("body of a Basic request").doesNotContain("step_up_required")
        assertThat(response.status).`as`("status of a Basic request").isEqualTo(OK.code)
    }

    @Test
    fun `starting the first factor with a session older than five minutes is refused with step_up_required`() {
        // given: a user without a factor and a session older than the window
        checkNotNull(loginFacade.createUser("step-first-old", "step-first-old", password))
        val session = sessionWithoutFactor("step-first-old")
        clock.advance(Duration.ofSeconds(301))

        // when: an authenticator enrolment is started
        val response = sessionPost(session, "/api/identity/me/factors/totp", "")

        // then: the enrolment is refused, because the password was typed too long ago
        assertThat(response.status).`as`("status of a stale first enrolment").isEqualTo(FORBIDDEN.code)
        assertThat(response.body).contains("step_up_required")
    }

    @Test
    fun `changing the password with a session of a user without a factor is not gated by its age`() {
        // given: a user without a factor and a session an hour old
        checkNotNull(loginFacade.createUser("step-plain-old", "step-plain-old", password))
        val session = sessionWithoutFactor("step-plain-old")
        clock.advance(Duration.ofHours(1))

        // when: the password is changed
        val response = sessionPost(session, "/api/identity/me/password", """{"currentPassword":"$password","newPassword":"$newPassword"}""")

        // then: no step-up is asked
        assertThat(response.status).`as`("status without a factor").isEqualTo(OK.code)
    }

    @Test
    fun `listing the own factors is never gated by the age of the session`() {
        // given: a session of a user with a factor, an hour after its second factor
        val (_, seed) = userWithAuthenticator("step-read-old")
        val session = sessionWithFactor("step-read-old", seed)
        clock.advance(Duration.ofHours(1))

        // when: the factors are listed
        val response = http.get("$base/api/identity/me/factors")
            .header("Cookie", session.cookies)
            .asString()

        // then: a read is answered
        assertThat(response.status).`as`("status of a read").isEqualTo(OK.code)
    }

    @Test
    fun `the step-up route answers 409 for a user without any factor`() {
        // given: a session of a user without a factor
        checkNotNull(loginFacade.createUser("step-none-route", "step-none-route", password))
        val session = sessionWithoutFactor("step-none-route")

        // when: a step-up is asked
        val response = sessionPost(session, "/api/identity/me/step-up", """{"code":"123456"}""")

        // then: there is nothing to step up
        assertThat(response.status).`as`("status without a factor").isEqualTo(CONFLICT.code)
    }

    @Test
    fun `a step-up with the current code renews the window, so that the removal is accepted`() {
        // given: a session six minutes after its second factor, so that a removal is refused
        val (principalId, seed) = userWithAuthenticator("step-up-renew")
        val session = sessionWithFactor("step-up-renew", seed)
        val factorId = loginFacade.secondFactorsOf(principalId).single().id
        clock.advance(Duration.ofSeconds(301))
        nextStep()

        // when: the second factor is completed again through the step-up route, and the factor is removed with the new session
        val stepUp = sessionPost(session, "/api/identity/me/step-up", """{"code":"${codeNow(seed)}"}""")
        assertThat(stepUp.status).`as`("status of the step-up").isEqualTo(OK.code)
        val renewed = sessionOf(stepUp.headers.get("Set-Cookie"))
        nextStep()
        val removal = sessionDelete(renewed, "/api/identity/me/factors/$factorId", """{"code":"${codeNow(seed)}"}""")

        // then: the removal is accepted in the same session family
        assertThat(removal.status).`as`("status of the removal after the step-up").isEqualTo(OK.code)
    }

}
