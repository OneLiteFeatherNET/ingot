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

import com.fasterxml.jackson.databind.JsonNode
import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteParameters
import com.reposilite.ReposiliteSpecification
import com.reposilite.audit.api.AuditActor
import com.reposilite.audit.api.AuditEventDto
import com.reposilite.audit.api.AuditPageDto
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
import net.onelitefeather.ingot.identity.login.api.SelfTokenRequest
import net.onelitefeather.ingot.identity.login.mfa.EnrolmentConfirmation
import net.onelitefeather.ingot.identity.login.mfa.TotpAlgorithm
import net.onelitefeather.ingot.identity.login.mfa.TotpEnrolmentStart
import net.onelitefeather.ingot.settings.encryption.SettingsKeySources
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * The administrator reset of the second factors of a user, on the wire. An instance administrator lists the factors of a
 * user and removes one or all of them. A reset ends the sessions of the user, the next sign-in needs the factors that are
 * left, and the reset is audited. Users without the instance administration get 403, also on their own factors.
 *
 * Every test builds its own users, so that the tests do not depend on each other.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class SecondFactorAdminResetIntegrationTest : ReposiliteSpecification() {

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

    private fun currentStep() = serverClock.instant().epochSecond / TotpAlgorithm.STEP_SECONDS

    private fun cookieValue(headers: List<String>, name: String): String =
        headers.single { it.startsWith("$name=") }.substringAfter("=").substringBefore(";")

    private fun passwordLogin(username: String) =
        http.post("$base/api/auth/login")
            .header("Content-Type", "application/json")
            .body("""{"provider":"local","username":"$username","password":"$password"}""")

    private fun userWithPassword(username: String): Int =
        checkNotNull(loginFacade.createUser(username, username, password)) { "user $username" }.id

    /**
     * Confirms an authenticator for the user. [existing] is the seed of a factor that must approve the new one, if any. A
     * factor claims the step of each code it accepts, so the approving code is taken from the next step.
     */
    private fun enrol(principalId: Int, account: String, existing: ByteArray?): ByteArray {
        val proof = existing?.let { TotpAlgorithm.code(it, currentStep() + 1) }
        val started = loginFacade.startAuthenticatorEnrolment(principalId, account, code = proof, ip = "127.0.0.1") as TotpEnrolmentStart.Started
        val confirmed = loginFacade.confirmAuthenticatorEnrolment(principalId, account, TotpAlgorithm.code(started.seed, currentStep()), ip = "127.0.0.1")
        assertThat(confirmed).`as`("enrolment with the current code").isInstanceOf(EnrolmentConfirmation.Confirmed::class.java)
        return started.seed
    }

    /** A user with two authenticators. Returns the seeds in the order of enrolment. */
    private fun userWithTwoFactors(username: String): Pair<ByteArray, ByteArray> {
        val principalId = userWithPassword(username)
        val first = enrol(principalId, username, existing = null)
        val second = enrol(principalId, username, existing = first)
        return first to second
    }

    private fun issueToken(principalId: Int, name: String): CreatedTokenDto {
        val issued = loginFacade.issueOwnToken(principalId, AuditActor.named(name, "127.0.0.1"), SelfTokenRequest(name = "$name-ci"))
        return (issued as OwnTokenOutcome.Issued).dto
    }

    /** A user with an instance administration, and an access token of their own. */
    private fun administrator(username: String): CreatedTokenDto {
        val principalId = userWithPassword(username)
        val (manager, secret) = useDefaultManagementToken()
        http.put("$base/api/identity/principals/$username/instance-admin").basicAuth(manager, secret).asString()
        return issueToken(principalId, username)
    }

    private fun factorsPath(username: String) = "$base/api/identity/principals/$username/factors"

    private fun listFactors(caller: CreatedTokenDto, username: String) =
        http.get(factorsPath(username)).basicAuth(caller.token.name, caller.secret).asJacksonObject(JsonNode::class)

    /** The factors as the default manager sees them, for checks that must not depend on the reset under test. */
    private fun factorCountAsManager(username: String): Int {
        val (manager, secret) = useDefaultManagementToken()
        return http.get(factorsPath(username)).basicAuth(manager, secret).asJacksonObject(JsonNode::class).body.size()
    }

    /** Signs in with the password and completes the challenge with the code of [seed] at [step]. Returns the session value. */
    private fun signIn(username: String, seed: ByteArray, step: Long): String {
        val challenge = cookieValue(passwordLogin(username).asString().headers.get("Set-Cookie"), "ingot_mfa")
        val response = http.post("$base/api/auth/mfa/verify")
            .header("Content-Type", "application/json")
            .header("Cookie", "ingot_mfa=$challenge")
            .body("""{"code":"${TotpAlgorithm.code(seed, step)}"}""")
            .asJacksonObject(LoginResponse::class)
        return cookieValue(response.headers.get("Set-Cookie"), "ingot_session")
    }

    private fun auditEvents(action: String): List<AuditEventDto> {
        val (manager, secret) = useDefaultManagementToken()
        return http.get("$base/api/audit?action=$action")
            .basicAuth(manager, secret)
            .asJacksonObject(AuditPageDto::class)
            .body
            .events
    }

    @Test
    fun `an administrator lists the factors of a user with names, kinds and times and no secrets`() {
        // given: a user with two authenticators and an administrator
        val administrator = administrator("root-list")
        val (first, _) = userWithTwoFactors("lena-list")

        // when: the factors are listed by the administrator
        val response = listFactors(administrator, "lena-list")

        // then: each factor has the public fields, and no seed or otpauth URI appears in the answer
        assertThat(response.status).`as`("status of the listing").isEqualTo(OK.code)
        assertThat(response.body.size()).`as`("number of listed factors").isEqualTo(2)
        response.body.forEach { factor ->
            val fields = factor.fieldNames().asSequence().toSet()
            assertThat(fields).`as`("fields of a listed factor").contains("id", "kind", "name", "createdAt")
            assertThat(fields).`as`("fields of a listed factor").isSubsetOf("id", "kind", "name", "createdAt", "lastUsedAt")
            assertThat(factor.get("createdAt").asText()).`as`("creation time of a factor").isNotBlank()
        }
        assertThat(response.body.toString()).`as`("the answer carries no seed").doesNotContain(TotpAlgorithm.base32(first))
        assertThat(response.body.toString()).`as`("the answer carries no otpauth URI").doesNotContain("otpauth")
    }

    @Test
    fun `an administrator removes one factor and the other factor stays`() {
        // given: a user with two authenticators and an administrator
        val administrator = administrator("root-one")
        userWithTwoFactors("lena-one")
        val removedId = listFactors(administrator, "lena-one").body.first().get("id").asInt()

        // when: one factor is removed
        val response = http.delete("${factorsPath("lena-one")}/$removedId")
            .basicAuth(administrator.token.name, administrator.secret)
            .asJacksonObject(JsonNode::class)

        // then: one factor was removed, and the other one is still listed
        assertThat(response.status).`as`("status of the removal").isEqualTo(OK.code)
        assertThat(response.body.get("removed").asInt()).`as`("factors removed").isEqualTo(1)
        val remaining = listFactors(administrator, "lena-one").body
        assertThat(remaining.size()).`as`("factors left").isEqualTo(1)
        assertThat(remaining.first().get("id").asInt()).`as`("id of the factor left").isNotEqualTo(removedId)
    }

    @Test
    fun `an administrator removes all factors of a user at once`() {
        // given: a user with two authenticators and an administrator
        val administrator = administrator("root-all")
        userWithTwoFactors("lena-all")

        // when: every factor of the user is removed
        val response = http.delete(factorsPath("lena-all"))
            .basicAuth(administrator.token.name, administrator.secret)
            .asJacksonObject(JsonNode::class)

        // then: both factors were removed and none is listed
        assertThat(response.status).`as`("status of the reset").isEqualTo(OK.code)
        assertThat(response.body.get("removed").asInt()).`as`("factors removed").isEqualTo(2)
        assertThat(listFactors(administrator, "lena-all").body.size()).`as`("factors left").isEqualTo(0)
    }

    @Test
    fun `a reset ends the open sessions of the user`() {
        // given: a user with one authenticator who has signed in, and an administrator
        val administrator = administrator("root-session")
        val principalId = userWithPassword("lena-session")
        val seed = enrol(principalId, "lena-session", existing = null)
        val session = signIn("lena-session", seed, currentStep() + 1)
        val sessionRequest = { http.get("$base/api/identity/me/factors").header("Cookie", "ingot_session=$session").asString() }
        assertThat(sessionRequest().status).`as`("the session works before the reset").isEqualTo(OK.code)

        // when: the administrator resets the factors of the user
        http.delete(factorsPath("lena-session")).basicAuth(administrator.token.name, administrator.secret).asString()

        // then: the session that was open before the reset is refused
        assertThat(sessionRequest().status).`as`("the session after the reset").isEqualTo(UNAUTHORIZED.code)
    }

    @Test
    fun `the sign-in after a reset of one factor needs the factor that is left`() {
        // given: a user with two authenticators, and an administrator who removes the first one
        val administrator = administrator("root-cache")
        val (removedSeed, remainingSeed) = userWithTwoFactors("lena-cache")
        val removedId = listFactors(administrator, "lena-cache").body.first().get("id").asInt()
        http.delete("${factorsPath("lena-cache")}/$removedId").basicAuth(administrator.token.name, administrator.secret).asString()

        // when: the user signs in with the password and the code of the removed factor
        val challengeWithRemoved = cookieValue(passwordLogin("lena-cache").asString().headers.get("Set-Cookie"), "ingot_mfa")
        val refused = http.post("$base/api/auth/mfa/verify")
            .header("Content-Type", "application/json")
            .header("Cookie", "ingot_mfa=$challengeWithRemoved")
            .body("""{"code":"${TotpAlgorithm.code(removedSeed, currentStep() + 1)}"}""")
            .asString()

        // then: the removed factor does not complete the sign-in, and no session starts
        assertThat(refused.status).`as`("status with the removed factor").isNotEqualTo(OK.code)
        assertThat(refused.headers.get("Set-Cookie").any { it.startsWith("ingot_session=") }).`as`("no session with the removed factor").isFalse()

        // when: the sign-in is repeated with the factor that is left
        val session = signIn("lena-cache", remainingSeed, currentStep() + 1)

        // then: the remaining factor starts the session
        assertThat(session).`as`("session with the remaining factor").isNotBlank
    }

    @Test
    fun `a reset of the factors is written to the audit trail with the user and the count`() {
        // given: a user with two authenticators and an administrator
        val administrator = administrator("root-audit")
        userWithTwoFactors("lena-audit")

        // when: the administrator resets the factors of the user
        http.delete(factorsPath("lena-audit")).basicAuth(administrator.token.name, administrator.secret).asString()

        // then: the audit trail names the user and the number of removed factors
        val event = auditEvents("MFA_FACTORS_RESET").single { it.details?.get("principal")?.asText() == "lena-audit" }
        assertThat(event.details?.get("removed")?.asInt()).`as`("removed factors in the audit event").isEqualTo(2)
        assertThat(event.actor).`as`("actor of the audit event").isNotBlank
    }

    @Test
    fun `a user without the instance administration gets 403 on the factors of another user`() {
        // given: a user without administration, a user with two authenticators
        val plain = issueToken(userWithPassword("plain-other"), "plain-other")
        userWithTwoFactors("lena-other")

        // when: the plain user lists and resets the factors of the other user
        val list = http.get(factorsPath("lena-other")).basicAuth(plain.token.name, plain.secret).asString()
        val reset = http.delete(factorsPath("lena-other")).basicAuth(plain.token.name, plain.secret).asString()

        // then: both are refused, and the factors are still there
        assertThat(list.status).`as`("status of a listing by a plain user").isEqualTo(FORBIDDEN.code)
        assertThat(reset.status).`as`("status of a reset by a plain user").isEqualTo(FORBIDDEN.code)
        assertThat(factorCountAsManager("lena-other")).`as`("factors left after the refused reset").isEqualTo(2)
    }

    @Test
    fun `a user cannot reset their own factors through the administrator routes`() {
        // given: a user without administration who has two authenticators, and a token of their own
        val principalId = userWithPassword("lena-self")
        enrol(principalId, "lena-self", existing = null).also { first ->
            enrol(principalId, "lena-self", existing = first)
        }
        val self = issueToken(principalId, "lena-self")

        // when: the user resets their own factors through the administrator route
        val reset = http.delete(factorsPath("lena-self")).basicAuth(self.token.name, self.secret).asString()

        // then: the reset is refused, and the factors are still there
        assertThat(reset.status).`as`("status of a self reset through the admin route").isEqualTo(FORBIDDEN.code)
        assertThat(factorCountAsManager("lena-self")).`as`("factors left after the refused reset").isEqualTo(2)
    }

    @Test
    fun `a bearer access token of an administrator lists the factors like a basic token does`() {
        // given: an administrator with an access token, and a user with two authenticators
        val administrator = administrator("root-token")
        userWithTwoFactors("lena-token")

        // when: the factors are listed with the token as a bearer value
        val response = http.get(factorsPath("lena-token"))
            .header("Authorization", "Bearer ${administrator.secret}")
            .asJacksonObject(JsonNode::class)

        // then: the bearer value is accepted, as the routes declare
        assertThat(response.status).`as`("status with the bearer value").isEqualTo(OK.code)
        assertThat(response.body.size()).`as`("factors listed with the bearer value").isEqualTo(2)
    }

}
