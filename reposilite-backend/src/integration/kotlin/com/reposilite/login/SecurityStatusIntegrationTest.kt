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

import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteParameters
import com.reposilite.ReposiliteSpecification
import com.reposilite.audit.api.AuditActor
import com.reposilite.configuration.shared.SharedConfigurationFacade
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Base64
import kong.unirest.core.Unirest
import net.onelitefeather.ingot.identity.login.LoginFacade
import net.onelitefeather.ingot.identity.login.LoginSettings
import net.onelitefeather.ingot.identity.login.MfaSettings
import net.onelitefeather.ingot.identity.login.OwnTokenOutcome
import net.onelitefeather.ingot.identity.login.api.SecurityStatusDto
import net.onelitefeather.ingot.identity.login.api.SelfTokenRequest
import net.onelitefeather.ingot.identity.login.mfa.EnrolmentConfirmation
import net.onelitefeather.ingot.identity.login.mfa.TotpAlgorithm
import net.onelitefeather.ingot.identity.login.mfa.TotpEnrolmentStart
import net.onelitefeather.ingot.identity.session.SessionCookies
import net.onelitefeather.ingot.settings.encryption.SettingsKeySources
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * The security status on the wire (design D14). It is a read, so it answers with a session or a Basic credential, whatever the
 * step-up state and the scope of the session. The clock of the server moves only when a test moves it.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class SecurityStatusIntegrationTest : ReposiliteSpecification() {

    private val password = "correct horse battery"
    private val key = Base64.getEncoder().encodeToString(ByteArray(32) { (it * 7 + 3).toByte() })
    private val start = Instant.parse("2026-11-14T10:00:00Z")

    /** A client without cookie management, so that the test decides which cookie a request carries. */
    private val http = Unirest.spawnInstance().also { it.config().enableCookieManagement(false) }

    private val clock = ShiftableClock(start)

    override fun overrideParameters(parameters: ReposiliteParameters) {
        parameters.settingsSecretEnvironment = mapOf(SettingsKeySources.KEY to key)
        parameters.clock = clock
    }

    private val loginFacade: LoginFacade
        get() = useFacade()

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

    private fun codeNow(seed: ByteArray): String =
        TotpAlgorithm.code(seed, clock.instant().epochSecond / TotpAlgorithm.STEP_SECONDS)

    private fun passwordLogin(username: String) =
        http.post("$base/api/auth/login")
            .header("Content-Type", "application/json")
            .body("""{"provider":"local","username":"$username","password":"$password"}""")
            .asString()

    /** The session cookie of a sign-in, as the browser sends it, and the session token alone. */
    private fun sessionCookie(headers: List<String>): String =
        "${SessionCookies.SESSION}=${cookieValue(headers, SessionCookies.SESSION)}"

    /** A session of a user without a factor, which starts with the password login. */
    private fun sessionWithoutFactor(username: String): String =
        sessionCookie(passwordLogin(username).headers.get("Set-Cookie"))

    /** A user with a confirmed authenticator and a session completed with its code. Returns the id, the seed and the cookie. */
    private fun userWithAuthenticator(username: String): Triple<Int, ByteArray, String> {
        val principalId = checkNotNull(loginFacade.createUser(username, username, password)) { "user $username" }.id
        val started = loginFacade.startAuthenticatorEnrolment(principalId, username, code = null, ip = "127.0.0.1") as TotpEnrolmentStart.Started
        clock.advance(Duration.ofSeconds(TotpAlgorithm.STEP_SECONDS))
        val confirmed = loginFacade.confirmAuthenticatorEnrolment(principalId, username, codeNow(started.seed), ip = "127.0.0.1")
        assertThat(confirmed).`as`("enrolment with the current code").isInstanceOf(EnrolmentConfirmation.Confirmed::class.java)
        clock.advance(Duration.ofSeconds(TotpAlgorithm.STEP_SECONDS))
        val challenge = cookieValue(passwordLogin(username).headers.get("Set-Cookie"), "ingot_mfa")
        clock.advance(Duration.ofSeconds(TotpAlgorithm.STEP_SECONDS))
        val answer = http.post("$base/api/auth/mfa/verify")
            .header("Content-Type", "application/json")
            .header("Cookie", "ingot_mfa=$challenge")
            .body("""{"code":"${codeNow(started.seed)}"}""")
            .asString()
        assertThat(answer.status).`as`("second factor of the sign-in").isEqualTo(200)
        return Triple(principalId, started.seed, sessionCookie(answer.headers.get("Set-Cookie")))
    }

    /** Sets the policy for administrators, with the given grace, and starts the instance with its first administrator. */
    private fun setupAdministrator(username: String, graceDays: Long): String {
        useFacade<SharedConfigurationFacade>().getDomainSettings<LoginSettings>()
            .update { old -> old.copy(mfa = MfaSettings(requiredForAdmins = true, graceDays = graceDays)) }
        val code = checkNotNull(loginFacade.issueSetupCode()) { "the setup code of a fresh instance" }
        val setup = http.post("$base/api/auth/setup")
            .header("Content-Type", "application/json")
            .body("""{"setupCode":"$code","username":"$username","password":"$password"}""")
            .asString()
        assertThat(setup.status).`as`("setup of the administrator").isEqualTo(200)
        return sessionCookie(setup.headers.get("Set-Cookie"))
    }

    private fun statusWithSession(cookie: String) =
        http.get("$base/api/identity/me/security")
            .header("Cookie", cookie)
            .asString()

    private fun statusWithBasic(name: String, secret: String) =
        http.get("$base/api/identity/me/security")
            .basicAuth(name, secret)
            .asString()

    @Test
    fun `the status of a session with a factor reports the factor counts, the recovery codes and the step-up end`() {
        // given: a session of a user with an authenticator, completed at the current instant
        val (_, _, cookie) = userWithAuthenticator("status-factor")
        val authenticatedAt = loginFacade.verifySession(cookie.substringAfter("=").substringBefore(";"))?.authenticatedAt
        checkNotNull(authenticatedAt) { "the authentication time of the session" }

        // when: the status is read
        val response = statusWithSession(cookie)
        val status = DEFAULT_OBJECT_MAPPER.readValue(response.body, SecurityStatusDto::class.java)

        // then: the factors are counted and the step-up window ends five minutes after the second factor
        assertThat(response.status).`as`("status of a read").isEqualTo(200)
        assertThat(status.factorCounts.authenticator).isEqualTo(1)
        assertThat(status.factorCounts.passkey).isEqualTo(0)
        assertThat(status.recoveryCodesLeft).`as`("recovery codes of the first factor").isEqualTo(10)
        assertThat(status.stepUpUntil).`as`("step-up end of the session").isEqualTo(authenticatedAt.plus(Duration.ofMinutes(5)))
        assertThat(status.scope).isEqualTo("full")
    }

    @Test
    fun `the status of a basic credential has no step-up end and the full scope`() {
        // given: a user without a factor and a Basic token of their own
        val principalId = checkNotNull(loginFacade.createUser("status-basic", "status-basic", password)).id
        val token = (loginFacade.issueOwnToken(principalId, AuditActor.named("status-basic", "127.0.0.1"), SelfTokenRequest(name = "status-ci")) as OwnTokenOutcome.Issued).dto

        // when: the status is read with the Basic credential
        val response = statusWithBasic(token.token.name, token.secret)
        val status = DEFAULT_OBJECT_MAPPER.readValue(response.body, SecurityStatusDto::class.java)

        // then: no step-up window applies to a token
        assertThat(response.status).`as`("status of a Basic read").isEqualTo(200)
        assertThat(status.stepUpUntil).`as`("step-up end of a Basic caller").isNull()
        assertThat(status.scope).isEqualTo("full")
    }

    @Test
    fun `the status of a covered administrator in grace ends the grace fourteen days after the setup`() {
        // given: the policy covers administrators with the default grace, and the setup starts the session
        val cookie = setupAdministrator("status-grace", graceDays = 14)

        // when: the status is read
        val response = statusWithSession(cookie)
        val status = DEFAULT_OBJECT_MAPPER.readValue(response.body, SecurityStatusDto::class.java)

        // then: the administrator is covered and the grace ends at the setup plus the grace days
        assertThat(response.status).`as`("status of a covered administrator").isEqualTo(200)
        assertThat(status.policy.covered).isTrue()
        assertThat(status.policy.requiredForAdmins).isTrue()
        assertThat(status.graceUntil).`as`("grace end").isEqualTo(start.plus(Duration.ofDays(14)))
    }

    @Test
    fun `the status of a covered administrator after the grace is a restricted session that may still read it`() {
        // given: the policy with no grace, so that the session of the setup is restricted
        val cookie = setupAdministrator("status-enrol", graceDays = 0)

        // when: the status is read by the restricted session
        val response = statusWithSession(cookie)
        val status = DEFAULT_OBJECT_MAPPER.readValue(response.body, SecurityStatusDto::class.java)

        // then: the read is answered, and the status names the restricted scope
        assertThat(response.status).`as`("status of a restricted session").isEqualTo(200)
        assertThat(status.scope).isEqualTo("enrol")
        assertThat(status.graceUntil).`as`("grace end of an administrator after the grace").isEqualTo(start)
    }

    @Test
    fun `the status of a user outside the policy is not covered and has no grace end`() {
        // given: a user without a factor, outside any policy
        checkNotNull(loginFacade.createUser("status-plain-user", "status-plain-user", password))
        val cookie = sessionWithoutFactor("status-plain-user")

        // when: the status is read
        val response = statusWithSession(cookie)
        val status = DEFAULT_OBJECT_MAPPER.readValue(response.body, SecurityStatusDto::class.java)

        // then: the user is not covered and no grace applies
        assertThat(response.status).`as`("status of a plain user").isEqualTo(200)
        assertThat(status.policy.covered).isFalse()
        assertThat(status.policy.requiredForAdmins).isFalse()
        assertThat(status.graceUntil).isNull()
    }

}
