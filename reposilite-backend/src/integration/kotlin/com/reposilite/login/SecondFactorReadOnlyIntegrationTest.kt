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
import io.javalin.http.HttpStatus.OK
import io.javalin.http.HttpStatus.SERVICE_UNAVAILABLE
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Base64
import kong.unirest.core.HttpRequestWithBody
import kong.unirest.core.HttpResponse
import kong.unirest.core.Unirest
import net.onelitefeather.ingot.identity.login.LoginFacade
import net.onelitefeather.ingot.identity.login.mfa.EnrolmentConfirmation
import net.onelitefeather.ingot.identity.login.mfa.TotpAlgorithm
import net.onelitefeather.ingot.identity.login.mfa.TotpEnrolmentStart
import net.onelitefeather.ingot.readonly.ReadOnlySetting
import net.onelitefeather.ingot.settings.encryption.SettingsKeySources
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * The writes of the own second factors on an instance that the environment forces read-only: rename, remove, regenerate the
 * recovery codes and confirm a new authenticator. Each write is refused with 503 and Retry-After before the factor is touched.
 * Every test prepares a state in which the write would succeed on a writable instance, so that the refusal is the only reason
 * for the unchanged state.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class SecondFactorReadOnlyIntegrationTest : ReposiliteSpecification() {

    override fun readOnlySetting(): ReadOnlySetting =
        ReadOnlySetting(forced = true)

    private val password = "correct horse battery"
    private val key = Base64.getEncoder().encodeToString(ByteArray(32) { (it * 11 + 5).toByte() })

    /** A client without cookie management, so that the test decides which cookie a request carries. */
    private val http = Unirest.spawnInstance().also { it.config().enableCookieManagement(false) }

    /** The clock of the server. It moves only through [nextStep], so that every TOTP code is fresh and deterministic. */
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

    /** A user with a confirmed authenticator, so that the user holds ten recovery codes. Returns the id and the seed. */
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
        assertThat(answer.status).`as`("status of the second factor, which a read-only instance must still accept").isEqualTo(OK.code)
        return sessionOf(answer.headers.get("Set-Cookie"))
    }

    private fun sessionOf(headers: List<String>): Session {
        val cookies = listOf("ingot_session", "ingot_refresh", "ingot_csrf").joinToString("; ") { "$it=${cookieValue(headers, it)}" }
        return Session(cookies, cookieValue(headers, "ingot_csrf"))
    }

    private fun HttpRequestWithBody.asSession(session: Session, body: String): HttpResponse<String> =
        header("Content-Type", "application/json")
            .header("Cookie", session.cookies)
            .header("X-CSRF-Token", session.csrf)
            .body(body)
            .asString()

    private fun assertRefusedWhileReadOnly(response: HttpResponse<String>, write: String) {
        assertThat(response.status).`as`("status of the $write on a read-only instance").isEqualTo(SERVICE_UNAVAILABLE.code)
        assertThat(response.headers.getFirst("Retry-After")).`as`("Retry-After header of the $write").isNotBlank()
        assertThat(response.body).`as`("body of the $write names the read-only state").contains("read-only")
    }

    @Test
    fun `renaming an own factor on a read-only instance is refused with 503 and the name is kept`() {
        // given: a user with an authenticator and a session whose second factor is fresh
        val (principalId, seed) = userWithAuthenticator("ro-rename")
        val session = sessionWithFactor("ro-rename", seed)
        val before = loginFacade.secondFactorsOf(principalId).single()

        // when: the factor is renamed
        val response = http.patch("$base/api/identity/me/factors/${before.id}").asSession(session, """{"name":"Work phone"}""")

        // then: the rename is refused, and the name is unchanged
        assertRefusedWhileReadOnly(response, "rename")
        assertThat(loginFacade.secondFactorsOf(principalId).single().name).`as`("name after the refused rename").isEqualTo(before.name)
    }

    @Test
    fun `removing an own factor on a read-only instance is refused with 503 and the factor stays`() {
        // given: a user with an authenticator and a session whose second factor is fresh
        val (principalId, seed) = userWithAuthenticator("ro-remove")
        val session = sessionWithFactor("ro-remove", seed)
        val before = loginFacade.secondFactorsOf(principalId).single()
        nextStep()

        // when: the factor is removed with a current code of the user
        val response = http.delete("$base/api/identity/me/factors/${before.id}").asSession(session, """{"code":"${codeNow(seed)}"}""")

        // then: the removal is refused, and the factor is still there
        assertRefusedWhileReadOnly(response, "removal")
        assertThat(loginFacade.secondFactorsOf(principalId).map { it.id }).`as`("factor ids after the refused removal").containsExactly(before.id)
    }

    @Test
    fun `regenerating the recovery codes on a read-only instance is refused with 503 and the codes stay`() {
        // given: a user with an authenticator, who holds recovery codes, and a session whose second factor is fresh
        val (principalId, seed) = userWithAuthenticator("ro-recovery")
        val session = sessionWithFactor("ro-recovery", seed)
        val codesBefore = loginFacade.securityStatusOf(principalId, null).recoveryCodesLeft
        assertThat(codesBefore).`as`("recovery codes before the refused regeneration").isGreaterThan(0)
        nextStep()

        // when: the recovery codes are regenerated with a current code of the user
        val response = http.post("$base/api/identity/me/recovery-codes").asSession(session, """{"code":"${codeNow(seed)}"}""")

        // then: the regeneration is refused, and the number of codes is unchanged
        assertRefusedWhileReadOnly(response, "regeneration")
        assertThat(loginFacade.securityStatusOf(principalId, null).recoveryCodesLeft).`as`("recovery codes after the refused regeneration").isEqualTo(codesBefore)
    }

    @Test
    fun `confirming a new authenticator on a read-only instance is refused with 503 and no factor is added`() {
        // given: a user without a factor, a pending authenticator enrolment and a password session
        val principalId = checkNotNull(loginFacade.createUser("ro-confirm", "ro-confirm", password)) { "user ro-confirm" }.id
        val started = loginFacade.startAuthenticatorEnrolment(principalId, "ro-confirm", code = null, ip = "127.0.0.1") as TotpEnrolmentStart.Started
        val session = sessionOf(passwordLogin("ro-confirm").headers.get("Set-Cookie"))

        // when: the pending enrolment is confirmed with its current code
        val response = http.post("$base/api/identity/me/factors/totp/confirm").asSession(session, """{"code":"${codeNow(started.seed)}"}""")

        // then: the confirmation is refused, and no factor exists
        assertRefusedWhileReadOnly(response, "confirmation")
        assertThat(loginFacade.secondFactorsOf(principalId)).`as`("factors after the refused confirmation").isEmpty()
    }

}
