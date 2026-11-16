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

import com.fasterxml.jackson.databind.ObjectMapper
import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteParameters
import com.reposilite.ReposiliteSpecification
import com.reposilite.configuration.shared.SharedConfigurationFacade
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Base64
import net.onelitefeather.ingot.identity.login.LoginFacade
import net.onelitefeather.ingot.identity.login.LoginSettings
import net.onelitefeather.ingot.identity.login.MfaSettings
import net.onelitefeather.ingot.identity.login.mfa.EnrolmentConfirmation
import net.onelitefeather.ingot.identity.login.mfa.TotpAlgorithm
import net.onelitefeather.ingot.identity.login.mfa.TotpEnrolmentStart
import net.onelitefeather.ingot.identity.login.oidc.OidcProviderSettings
import net.onelitefeather.ingot.identity.login.oidc.OidcTestIdentityProvider
import net.onelitefeather.ingot.settings.encryption.SettingsKeySources
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * The second factor at the OpenID Connect callback, over HTTP (design D12). An uncovered user with an authenticator signs in
 * without a challenge, because the provider authenticated the user. A covered user, an instance administrator through a
 * synchronised group, gets the challenge cookie and no session at the callback, and passes at `/api/auth/mfa/verify`.
 *
 * The server clock and the clock of the provider are one clock that moves only when a test moves it, so the ID tokens and the
 * codes are the same at any time of day, and no test waits.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class ExternalSecondFactorIntegrationTest : ReposiliteSpecification() {

    private val password = "correct horse battery"
    private val key = Base64.getEncoder().encodeToString(ByteArray(32) { (it * 3 + 7).toByte() })

    private val clock = ShiftableClock(Instant.parse("2026-11-14T10:00:00Z"))
    private val provider = OidcTestIdentityProvider(clock)
    private val browser: HttpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()

    override fun overrideParameters(parameters: ReposiliteParameters) {
        parameters.settingsSecretEnvironment = mapOf(SettingsKeySources.KEY to key)
        parameters.clock = clock
    }

    @BeforeEach
    fun startProvider() {
        provider.start()
    }

    @AfterEach
    fun stopProvider() {
        provider.close()
    }

    /** A clock that moves only when a test moves it. */
    private class ShiftableClock(private var now: Instant) : Clock() {

        fun advance(duration: Duration) {
            now = now.plus(duration)
        }

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = now

    }

    private val loginFacade: LoginFacade
        get() = useFacade()

    private fun configureCorporateProvider(adminGroups: List<String>, requiredForAdmins: Boolean) {
        useFacade<SharedConfigurationFacade>()
            .getDomainSettings<LoginSettings>()
            .update { old ->
                old.copy(
                    oidcProviders = listOf(
                        OidcProviderSettings(
                            id = "corp",
                            enabled = true,
                            label = "Corporate SSO",
                            issuerUrl = provider.issuer,
                            clientId = OidcTestIdentityProvider.CLIENT_ID,
                            clientSecret = OidcTestIdentityProvider.CLIENT_SECRET,
                            adminGroups = adminGroups,
                        ),
                    ),
                    mfa = MfaSettings(requiredForAdmins = requiredForAdmins),
                )
            }
    }

    /** The first administrator of the instance, created through the setup, so that it may read the principals. */
    private fun setupAdministrator() {
        val code = checkNotNull(loginFacade.issueSetupCode()) { "the instance does not ask for its setup" }
        loginFacade.completeSetup(code, "admin", password, "Administrator", "127.0.0.1")
    }

    private fun get(path: String, cookie: String? = null): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI("$base$path")).GET()
        cookie?.let { request.header("Cookie", it) }
        return browser.send(request.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun postJson(path: String, body: String, cookie: String? = null): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI("$base$path"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
        cookie?.let { request.header("Cookie", it) }
        return browser.send(request.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun HttpResponse<*>.location(): String =
        headers().firstValue("Location").orElseThrow { AssertionError("no Location header in ${statusCode()}") }

    private fun HttpResponse<*>.cookieValue(name: String): String? =
        headers().allValues("Set-Cookie")
            .firstOrNull { it.startsWith("$name=") }
            ?.substringAfter('=')
            ?.substringBefore(';')

    private fun HttpResponse<*>.query(): Map<String, String> =
        URI(location()).rawQuery.split('&').associate {
            it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8")
        }

    /** One browser sign-in through the provider: the login redirect, the provider's answer, and the callback. */
    private fun signInWithProvider(): HttpResponse<String> {
        val start = get("/api/auth/oidc/corp/login?redirect=${URLEncoder.encode("/repositories", "UTF-8")}")
        provider.nonce = start.query()["nonce"]
        return get(
            "/api/auth/oidc/corp/callback?code=authorization-code&state=${start.query()["state"]}",
            cookie = "ingot_oidc=${start.cookieValue("ingot_oidc")}",
        )
    }

    /** The id of a principal, read by the administrator, who is the only one that may list the principals. */
    private fun principalIdOf(name: String): Int {
        val login = postJson("/api/auth/login", """{"provider":"local","username":"admin","password":"$password"}""")
        val session = checkNotNull(login.cookieValue("ingot_session")) { "no session of the administrator: ${login.statusCode()}" }
        val listing = get("/api/identity/principals", cookie = "ingot_session=$session")
        return ObjectMapper().readTree(listing.body()).single { it.get("name").asText() == name }.get("id").asInt()
    }

    private fun codeNow(seed: ByteArray): String =
        TotpAlgorithm.code(seed, clock.instant().epochSecond / TotpAlgorithm.STEP_SECONDS)

    /** Enrols an authenticator for the user [name], and returns its seed. */
    private fun enrolAuthenticatorOf(name: String): ByteArray {
        val principalId = principalIdOf(name)
        val started = loginFacade.startAuthenticatorEnrolment(principalId, name, code = null, ip = "127.0.0.1") as TotpEnrolmentStart.Started
        val confirmed = loginFacade.confirmAuthenticatorEnrolment(principalId, name, codeNow(started.seed), ip = "127.0.0.1")
        assertThat(confirmed).describedAs("enrolment of $name with the current code").isInstanceOf(EnrolmentConfirmation.Confirmed::class.java)
        return started.seed
    }

    @Test
    fun `an oidc user outside the policy with an authenticator signs in without a challenge`() {
        // given: the first sign-in creates the user, who then enrols an authenticator. Nothing covers the user.
        configureCorporateProvider(adminGroups = emptyList(), requiredForAdmins = false)
        setupAdministrator()
        signInWithProvider()
        enrolAuthenticatorOf("alice")
        clock.advance(Duration.ofSeconds(TotpAlgorithm.STEP_SECONDS))

        // when: the user signs in again through the provider
        val callback = signInWithProvider()

        // then: the browser gets a session and no challenge
        assertThat(callback.location()).describedAs("redirect after the callback").isEqualTo("/repositories")
        assertThat(callback.cookieValue("ingot_session")).describedAs("session cookie").isNotNull()
        assertThat(callback.cookieValue("ingot_mfa")).describedAs("challenge cookie of an uncovered user").isNull()
    }

    @Test
    fun `a covered oidc user with an authenticator is challenged at the callback and passes at verify with the code`() {
        // given: the group of the provider makes the user an instance administrator, which the policy covers. The user
        // enrols an authenticator on the first sign-in.
        configureCorporateProvider(adminGroups = listOf("devs"), requiredForAdmins = true)
        setupAdministrator()
        signInWithProvider()
        val seed = enrolAuthenticatorOf("alice")
        clock.advance(Duration.ofSeconds(TotpAlgorithm.STEP_SECONDS))

        // when: the user signs in through the provider
        val callback = signInWithProvider()

        // then: no session, the challenge cookie is set, and the browser is sent to the login with the error
        assertThat(callback.location()).describedAs("redirect of the challenge").isEqualTo("/?loginError=second_factor_required")
        assertThat(callback.cookieValue("ingot_session")).describedAs("no session before the second factor").isNull()
        val challenge = checkNotNull(callback.cookieValue("ingot_mfa")) { "the challenge cookie of a covered user" }

        // when: the code of the authenticator is sent with the challenge cookie
        clock.advance(Duration.ofSeconds(TotpAlgorithm.STEP_SECONDS))
        val verify = postJson("/api/auth/mfa/verify", """{"code":"${codeNow(seed)}"}""", cookie = "ingot_mfa=$challenge")

        // then: the sign-in completes with a session
        assertThat(verify.statusCode()).describedAs("status of the verification").isEqualTo(200)
        assertThat(verify.cookieValue("ingot_session")).describedAs("session cookie after the second factor").isNotNull()
    }

}
