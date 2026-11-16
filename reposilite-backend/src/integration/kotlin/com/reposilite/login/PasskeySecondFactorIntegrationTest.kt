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
import com.fasterxml.jackson.databind.ObjectMapper
import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteParameters
import com.reposilite.ReposiliteSpecification
import io.javalin.http.HttpStatus.BAD_REQUEST
import io.javalin.http.HttpStatus.OK
import io.javalin.http.HttpStatus.UNAUTHORIZED
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import kong.unirest.core.HttpResponse
import kong.unirest.core.Unirest
import net.onelitefeather.ingot.identity.login.LoginFacade
import net.onelitefeather.ingot.identity.login.mfa.SoftwarePasskey
import net.onelitefeather.ingot.identity.login.mfa.TotpAlgorithm
import net.onelitefeather.ingot.identity.login.mfa.TotpEnrolmentStart
import net.onelitefeather.ingot.settings.encryption.SettingsKeySources
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * The passkey on the wire. A signed-in user registers a passkey, and a user whose only factor is that passkey is challenged at
 * the password and completes the sign-in with an assertion of the software authenticator. The relying party is the address of
 * the test server, `localhost`, because no public address is configured.
 *
 * Every test creates its own users, so that the tests do not depend on each other.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class PasskeySecondFactorIntegrationTest : ReposiliteSpecification() {

    private val password = "correct horse battery"
    private val key = Base64.getEncoder().encodeToString(ByteArray(32) { (it * 5 + 1).toByte() })
    private val json = ObjectMapper()

    /** A client without cookie management, so that the test decides which cookie a request carries. */
    private val http = Unirest.spawnInstance().also { it.config().enableCookieManagement(false) }

    /** The clock of the server, fixed, so that the challenges of the test are the challenges of the server at any time of day. */
    private val serverClock = Clock.fixed(Instant.parse("2026-11-14T10:00:00Z"), ZoneOffset.UTC)

    override fun overrideParameters(parameters: ReposiliteParameters) {
        parameters.settingsSecretEnvironment = mapOf(SettingsKeySources.KEY to key)
        parameters.clock = serverClock
    }

    private val loginFacade: LoginFacade
        get() = useFacade()

    /** The relying party of the test server: the request host, and the origin of the base address. */
    private val rpId = "localhost"
    private val origin: String
        get() = base

    /** The cookies and the csrf header value of a browser session. */
    private data class Session(val cookies: String, val csrf: String)

    private fun cookieValue(headers: List<String>, name: String): String =
        headers.single { it.startsWith("$name=") }.substringAfter("=").substringBefore(";")

    private fun b64(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun passwordLogin(username: String): HttpResponse<String> =
        http.post("$base/api/auth/login")
            .header("Content-Type", "application/json")
            .body("""{"provider":"local","username":"$username","password":"$password"}""")
            .asString()

    private fun createUser(username: String) {
        checkNotNull(loginFacade.createUser(username, username, password)) { "user $username could not be created" }
    }

    /** Signs in a user without a second factor, and returns the session of the answer. */
    private fun sessionOf(username: String): Session {
        val headers = passwordLogin(username).headers.get("Set-Cookie")
        val cookies = listOf("ingot_session", "ingot_refresh", "ingot_csrf").joinToString("; ") { "$it=${cookieValue(headers, it)}" }
        return Session(cookies, cookieValue(headers, "ingot_csrf"))
    }

    private fun HttpResponse<String>.fieldsOf(): JsonNode = json.readTree(body)

    /** Starts a registration for the session, and completes it with [passkey]. Returns the answer of the completion. */
    private fun register(session: Session, passkey: SoftwarePasskey, attestationOrigin: String = origin): HttpResponse<String> {
        val options = http.post("$base/api/identity/me/factors/passkey/options")
            .header("Cookie", session.cookies)
            .header("X-CSRF-Token", session.csrf)
            .asString()
        assertThat(options.status).`as`("status of the registration options").isEqualTo(OK.code)
        val challenge = options.fieldsOf()
        val attestation = passkey.registration(challenge = challenge.get("challenge").asText(), rpId = rpId, origin = attestationOrigin)
        return http.post("$base/api/identity/me/factors/passkey")
            .header("Content-Type", "application/json")
            .header("Cookie", session.cookies)
            .header("X-CSRF-Token", session.csrf)
            .body(
                """{"challengeId":"${challenge.get("challengeId").asText()}","clientDataJSON":"${b64(attestation.clientDataJson)}","attestationObject":"${b64(attestation.attestationObject)}"}"""
            )
            .asString()
    }

    /** Starts the passkey step of the sign-in that [challengeCookie] names. Returns the options of the answer. */
    private fun passkeyOptions(challengeCookie: String): JsonNode {
        val response = http.post("$base/api/auth/mfa/passkey/options")
            .header("Cookie", "ingot_mfa=$challengeCookie")
            .asString()
        assertThat(response.status).`as`("status of the sign-in passkey options").isEqualTo(OK.code)
        return response.fieldsOf()
    }

    /**
     * Completes the sign-in that [challengeCookie] names with an assertion of [passkey]. [signedChallenge] is the challenge the
     * authenticator signs, which is the one of the options unless a test signs another one.
     */
    private fun verifyPasskey(
        challengeCookie: String,
        options: JsonNode,
        passkey: SoftwarePasskey,
        signedChallenge: String = options.get("challenge").asText(),
        ceremonyId: String = options.get("challengeId").asText(),
    ): HttpResponse<String> {
        val assertion = passkey.assertion(challenge = signedChallenge, rpId = rpId, origin = origin)
        return http.post("$base/api/auth/mfa/verify")
            .header("Content-Type", "application/json")
            .header("Cookie", "ingot_mfa=$challengeCookie")
            .body(
                """{"passkey":{"challengeId":"$ceremonyId","credentialId":"${passkey.credentialIdBase64Url}","clientDataJSON":"${b64(assertion.clientDataJson)}","authenticatorData":"${b64(assertion.authenticatorData)}","signature":"${b64(assertion.signature)}"}}"""
            )
            .asString()
    }

    /** A user with a registered passkey and no other factor. Returns the passkey of the user. */
    private fun userWithPasskey(username: String): SoftwarePasskey {
        createUser(username)
        val passkey = SoftwarePasskey()
        val registered = register(sessionOf(username), passkey)
        assertThat(registered.status).`as`("status of the registration").isEqualTo(OK.code)
        return passkey
    }

    @Test
    fun `the passkey options of a signed-in user name the relying party of the request`() {
        createUser("olaf-pk")
        val session = sessionOf("olaf-pk")

        val response = http.post("$base/api/identity/me/factors/passkey/options")
            .header("Cookie", session.cookies)
            .header("X-CSRF-Token", session.csrf)
            .asString()

        assertThat(response.status).`as`("status of the options").isEqualTo(OK.code)
        assertThat(response.fieldsOf().get("rpId").asText()).`as`("relying party of the request host").isEqualTo(rpId)
    }

    @Test
    fun `a passkey registered by the signed-in user is listed among their factors`() {
        createUser("ruth-pk")
        val session = sessionOf("ruth-pk")

        val registered = register(session, SoftwarePasskey())
        val listed = http.get("$base/api/identity/me/factors").header("Cookie", session.cookies).asString()

        assertThat(registered.status).`as`("status of the registration").isEqualTo(OK.code)
        assertThat(json.readTree(listed.body).map { it.get("kind").asText() }).`as`("kinds of the own factors").contains("PASSKEY")
    }

    @Test
    fun `a passkey registration whose origin is not the one of the server is refused`() {
        createUser("nils-pk")
        val session = sessionOf("nils-pk")

        val registered = register(session, SoftwarePasskey(), attestationOrigin = "https://evil.example.test")

        assertThat(registered.status).`as`("status of the foreign registration").isEqualTo(BAD_REQUEST.code)
    }

    @Test
    fun `a user whose only factor is a passkey is challenged, and the methods name the passkey`() {
        userWithPasskey("lena-pk")

        val response = passwordLogin("lena-pk")

        assertThat(response.status).`as`("status of the challenge answer").isEqualTo(OK.code)
        assertThat(response.fieldsOf().get("mfaRequired").asBoolean()).`as`("mfaRequired").isTrue()
        assertThat(response.fieldsOf().get("methods").map { it.asText() }).`as`("methods").contains("PASSKEY")
    }

    @Test
    fun `a passkey assertion completes the sign-in and starts the session`() {
        // given: a user with a passkey, and a challenge from the password
        val passkey = userWithPasskey("ada-pk")
        val challenge = cookieValue(passwordLogin("ada-pk").headers.get("Set-Cookie"), "ingot_mfa")
        val options = passkeyOptions(challenge)

        // when: the assertion of the passkey is sent with the challenge cookie
        val response = verifyPasskey(challenge, options, passkey)

        // then: the session starts, and the answer names the user
        assertThat(response.status).`as`("status of the verification").isEqualTo(OK.code)
        assertThat(response.fieldsOf().get("principal").get("name").asText()).isEqualTo("ada-pk")
        assertThat(response.headers.get("Set-Cookie").any { it.startsWith("ingot_session=") }).`as`("session cookie").isTrue()
    }

    @Test
    fun `a wrong passkey assertion keeps the sign-in challenge open for a correct one`() {
        // given: a user with a passkey, and a challenge from the password
        val passkey = userWithPasskey("bert-pk")
        val challenge = cookieValue(passwordLogin("bert-pk").headers.get("Set-Cookie"), "ingot_mfa")
        val first = passkeyOptions(challenge)

        // when: an assertion that signs another challenge is sent, and then a correct one with new options
        val wrong = verifyPasskey(challenge, first, passkey, signedChallenge = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
        val correct = verifyPasskey(challenge, passkeyOptions(challenge), passkey)

        // then: the wrong one is refused, and the challenge still completes with the correct one
        assertThat(wrong.status).`as`("status of the wrong assertion").isEqualTo(UNAUTHORIZED.code)
        assertThat(correct.status).`as`("status of the correct assertion after the wrong one").isEqualTo(OK.code)
    }

    @Test
    fun `the passkey options of a sign-in need the sign-in challenge cookie`() {
        val response = http.post("$base/api/auth/mfa/passkey/options").asString()

        // An unknown route answers 401 too, so the message is what shows that the route checked the challenge cookie.
        assertThat(response.status).`as`("status without the challenge cookie").isEqualTo(UNAUTHORIZED.code)
        assertThat(response.body).`as`("answer without the challenge cookie").contains("Sign in with your password first")
    }

    @Test
    fun `a challenge of a passkey ceremony cannot stand in for the sign-in challenge`() {
        // given: a user with a passkey, and the options of an assertion ceremony that the browser received
        val passkey = userWithPasskey("cora-pk")
        val challenge = cookieValue(passwordLogin("cora-pk").headers.get("Set-Cookie"), "ingot_mfa")
        val options = passkeyOptions(challenge)
        val ceremony = options.get("challengeId").asText()

        // when: the ceremony id is sent as the sign-in cookie, with a correct assertion of that ceremony
        val response = verifyPasskey(challengeCookie = ceremony, options = options, passkey = passkey)

        // then: the sign-in is refused, because the cookie names no sign-in challenge
        assertThat(response.status).`as`("status with the ceremony id as cookie").isEqualTo(UNAUTHORIZED.code)
    }

    @Test
    fun `a user with an authenticator is offered the authenticator and the recovery code methods`() {
        // given: a user with a confirmed authenticator, which needs the settings key of the test instance
        val principalId = checkNotNull(loginFacade.createUser("finn-pk", "finn-pk", password)).id
        val seed = (loginFacade.startAuthenticatorEnrolment(principalId, "finn-pk", code = null, ip = "127.0.0.1") as TotpEnrolmentStart.Started).seed
        loginFacade.confirmAuthenticatorEnrolment(
            principalId,
            "finn-pk",
            TotpAlgorithm.code(seed, serverClock.instant().epochSecond / TotpAlgorithm.STEP_SECONDS),
            ip = "127.0.0.1",
        )

        // when: the password is sent
        val response = passwordLogin("finn-pk")

        // then: the methods are the ones of an authenticator user, as before this change
        assertThat(response.fieldsOf().get("methods").map { it.asText() })
            .`as`("methods of an authenticator user")
            .containsExactlyInAnyOrder("AUTHENTICATOR", "RECOVERY_CODE")
    }

}
