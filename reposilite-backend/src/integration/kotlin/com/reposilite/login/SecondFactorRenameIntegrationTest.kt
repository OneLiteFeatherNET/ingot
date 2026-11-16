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
import com.reposilite.audit.api.AuditEventDto
import com.reposilite.audit.api.AuditPageDto
import io.javalin.http.HttpStatus.BAD_REQUEST
import io.javalin.http.HttpStatus.FORBIDDEN
import io.javalin.http.HttpStatus.NOT_FOUND
import io.javalin.http.HttpStatus.OK
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Base64
import kong.unirest.core.Unirest
import net.onelitefeather.ingot.identity.login.LoginFacade
import net.onelitefeather.ingot.identity.login.OwnTokenOutcome
import net.onelitefeather.ingot.identity.login.api.SelfTokenRequest
import net.onelitefeather.ingot.identity.login.mfa.EnrolmentConfirmation
import net.onelitefeather.ingot.identity.login.mfa.TotpAlgorithm
import net.onelitefeather.ingot.identity.login.mfa.TotpEnrolmentStart
import net.onelitefeather.ingot.settings.encryption.SettingsKeySources
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Renaming an own second factor on the wire, `PATCH /api/identity/me/factors/{id}`. The rename is a session action under the
 * step-up window of five minutes, and a factor of another user looks exactly like an unknown id.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class SecondFactorRenameIntegrationTest : ReposiliteSpecification() {

    private val password = "correct horse battery"
    private val key = Base64.getEncoder().encodeToString(ByteArray(32) { (it * 11 + 5).toByte() })

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

    private fun sessionOf(headers: List<String>): Session {
        val cookies = listOf("ingot_session", "ingot_refresh", "ingot_csrf").joinToString("; ") { "$it=${cookieValue(headers, it)}" }
        return Session(cookies, cookieValue(headers, "ingot_csrf"))
    }

    private fun sessionPatch(session: Session, path: String, body: String) =
        http.patch("$base$path")
            .header("Content-Type", "application/json")
            .header("Cookie", session.cookies)
            .header("X-CSRF-Token", session.csrf)
            .body(body)
            .asString()

    private fun listedFactorNames(session: Session): List<String> =
        http.get("$base/api/identity/me/factors")
            .header("Cookie", session.cookies)
            .asString()
            .body
            .let { body -> Regex("\"name\":\"([^\"]*)\"").findAll(body).map { it.groupValues[1] }.toList() }

    private fun auditEvents(action: String): List<AuditEventDto> {
        val (manager, secret) = useDefaultManagementToken()
        return http.get("$base/api/audit?action=$action")
            .basicAuth(manager, secret)
            .asJacksonObject(AuditPageDto::class)
            .body
            .events
    }

    @Test
    fun `renaming an own factor with a fresh second factor is accepted and the new name is listed`() {
        // given: a session whose second factor was completed a moment ago
        val (principalId, seed) = userWithAuthenticator("rename-fresh")
        val session = sessionWithFactor("rename-fresh", seed)
        val factorId = loginFacade.secondFactorsOf(principalId).single().id

        // when: the factor is renamed
        val response = sessionPatch(session, "/api/identity/me/factors/$factorId", """{"name":"Work phone"}""")

        // then: the rename is answered with the factor, and the list shows the new name
        assertThat(response.status).`as`("status of the rename").isEqualTo(OK.code)
        assertThat(response.body).`as`("body of the rename").contains("\"name\":\"Work phone\"")
        assertThat(listedFactorNames(session)).`as`("names after the rename").contains("Work phone")
    }

    @Test
    fun `renaming a factor with a session older than five minutes is refused with step_up_required`() {
        // given: a session whose second factor was completed six minutes ago
        val (principalId, seed) = userWithAuthenticator("rename-old")
        val session = sessionWithFactor("rename-old", seed)
        val factorId = loginFacade.secondFactorsOf(principalId).single().id
        clock.advance(Duration.ofSeconds(301))

        // when: the factor is renamed
        val response = sessionPatch(session, "/api/identity/me/factors/$factorId", """{"name":"Work phone"}""")

        // then: the rename is refused, and the name is unchanged
        assertThat(response.status).`as`("status outside the window").isEqualTo(FORBIDDEN.code)
        assertThat(response.body).`as`("problem of the refusal").contains("step_up_required")
        assertThat(loginFacade.secondFactorsOf(principalId).single().name).`as`("name after the refusal").isEqualTo("Authenticator app")
    }

    @Test
    fun `renaming a factor of another user answers 404 as for an unknown id, and keeps its name`() {
        // given: two users with an authenticator, and a fresh session of the second one
        val (ownerId, _) = userWithAuthenticator("rename-owner")
        val (_, seed) = userWithAuthenticator("rename-other")
        val session = sessionWithFactor("rename-other", seed)
        val foreignFactorId = loginFacade.secondFactorsOf(ownerId).single().id

        // when: the factor of the first user is renamed with the session of the second
        val response = sessionPatch(session, "/api/identity/me/factors/$foreignFactorId", """{"name":"Hijacked"}""")

        // then: the answer is the same 404 that an unknown id gets, and the owner keeps the name
        assertThat(response.status).`as`("status for a foreign factor").isEqualTo(NOT_FOUND.code)
        assertThat(response.body).`as`("body for a foreign factor").doesNotContain("Hijacked")
        assertThat(loginFacade.secondFactorsOf(ownerId).single().name).`as`("name of the owner").isEqualTo("Authenticator app")
    }

    @Test
    fun `renaming an id that names no factor answers 404`() {
        // given: a session of a user with a factor
        val (_, seed) = userWithAuthenticator("rename-unknown")
        val session = sessionWithFactor("rename-unknown", seed)

        // when: a factor id that does not exist is renamed
        val response = sessionPatch(session, "/api/identity/me/factors/999999", """{"name":"Work phone"}""")

        // then: the answer is 404
        assertThat(response.status).`as`("status for an unknown factor").isEqualTo(NOT_FOUND.code)
    }

    @Test
    fun `a blank name is refused with 400 and the name is unchanged`() {
        // given: a session of a user with a factor, inside the window
        val (principalId, seed) = userWithAuthenticator("rename-blank")
        val session = sessionWithFactor("rename-blank", seed)
        val factorId = loginFacade.secondFactorsOf(principalId).single().id

        // when: the factor is renamed to a blank name
        val response = sessionPatch(session, "/api/identity/me/factors/$factorId", """{"name":"   "}""")

        // then: the rename is refused
        assertThat(response.status).`as`("status of a blank name").isEqualTo(BAD_REQUEST.code)
        assertThat(loginFacade.secondFactorsOf(principalId).single().name).`as`("name after the refusal").isEqualTo("Authenticator app")
    }

    @Test
    fun `a rename with a Basic token of the user is refused with 403`() {
        // given: a user with a factor and a Basic token of their own
        val (principalId, _) = userWithAuthenticator("rename-token")
        val token = (loginFacade.issueOwnToken(principalId, AuditActor.named("rename-token", "127.0.0.1"), SelfTokenRequest(name = "rename-ci")) as OwnTokenOutcome.Issued).dto
        val factorId = loginFacade.secondFactorsOf(principalId).single().id

        // when: the factor is renamed with the Basic credential
        val response = http.patch("$base/api/identity/me/factors/$factorId")
            .header("Content-Type", "application/json")
            .basicAuth(token.token.name, token.secret)
            .body("""{"name":"Work phone"}""")
            .asString()

        // then: a second factor is managed from a browser session only
        assertThat(response.status).`as`("status of a token rename").isEqualTo(FORBIDDEN.code)
        assertThat(loginFacade.secondFactorsOf(principalId).single().name).`as`("name after the refusal").isEqualTo("Authenticator app")
    }

    @Test
    fun `a rename is written to the audit trail once, with the factor id and without the name`() {
        // given: a user with a factor and a fresh session
        val (principalId, seed) = userWithAuthenticator("rename-audit")
        val session = sessionWithFactor("rename-audit", seed)
        val factorId = loginFacade.secondFactorsOf(principalId).single().id

        // when: the factor is renamed
        sessionPatch(session, "/api/identity/me/factors/$factorId", """{"name":"Work phone"}""")

        // then: one audit event names the user and the factor, and not the chosen name
        val events = auditEvents("MFA_FACTOR_RENAMED").filter { it.details?.get("principal")?.asText() == "rename-audit" }
        assertThat(events).`as`("rename audit events of the user").hasSize(1)
        assertThat(events.single().details?.get("factor")?.asInt()).`as`("factor in the audit event").isEqualTo(factorId)
        assertThat(events.single().details.toString()).`as`("details of the audit event").doesNotContain("Work phone")
    }

}
