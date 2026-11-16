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
import com.reposilite.configuration.shared.SharedConfigurationFacade
import kong.unirest.core.Unirest
import net.onelitefeather.ingot.identity.login.LoginFacade
import net.onelitefeather.ingot.identity.login.LoginSettings
import net.onelitefeather.ingot.identity.login.MfaSettings
import net.onelitefeather.ingot.identity.session.SessionCookies
import net.onelitefeather.ingot.identity.session.SessionScope
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * The restricted session on the wire. With the policy on for administrators and no grace, an administrator without a factor
 * signs in with a session that may only manage its factors and log out. Every other route answers 403 mfa_enrolment_required.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class SecondFactorPolicyIntegrationTest : ReposiliteSpecification() {

    private val password = "correct horse battery"

    /** A client without cookie management, so that the test decides which cookie a request carries. */
    private val http = Unirest.spawnInstance().also { it.config().enableCookieManagement(false) }

    private fun cookieValue(headers: List<String>, name: String): String =
        headers.single { it.startsWith("$name=") }.substringAfter("=").substringBefore(";")

    /**
     * The first administrator, created by the setup route, which makes it an instance administrator. The policy is set before, with no
     * grace, so the session of the setup is already restricted. Returns the session, the refresh and the csrf cookie.
     */
    private fun restrictedLogin(username: String): Triple<String, String, String> {
        val settings = useFacade<SharedConfigurationFacade>().getDomainSettings<LoginSettings>()
        settings.update { old -> old.copy(mfa = MfaSettings(requiredForAdmins = true, graceDays = 0)) }
        assertThat(settings.get().mfa.requiredForAdmins).`as`("policy stored before the setup").isTrue()
        val code = checkNotNull(useFacade<LoginFacade>().issueSetupCode()) { "the setup code of a fresh instance" }

        val setup = http.post("$base/api/auth/setup")
            .header("Content-Type", "application/json")
            .body("""{"setupCode":"$code","username":"$username","password":"$password"}""")
            .asString()
        assertThat(setup.status).`as`("setup of the administrator").isEqualTo(200)
        val setCookies = setup.headers.get("Set-Cookie")
        val session = cookieValue(setCookies, SessionCookies.SESSION)
        assertThat(useFacade<LoginFacade>().verifySession(session)?.scope)
            .`as`("scope of the session of an administrator without a factor after the grace")
            .isEqualTo(SessionScope.ENROL)
        return Triple(
            session,
            cookieValue(setCookies, SessionCookies.REFRESH),
            cookieValue(setCookies, SessionCookies.CSRF),
        )
    }

    @Test
    fun `a restricted session gets 403 mfa_enrolment_required on a route outside its set`() {
        // given: an administrator without a factor after the grace, which is none here
        val (session, _, _) = restrictedLogin("rita-admin")

        // when: the tokens of the user are listed
        val response = http.get("$base/api/identity/me/tokens")
            .header("Cookie", "${SessionCookies.SESSION}=$session")
            .asString()

        // then: the request is refused with the problem of the enrolment
        assertThat(response.status).`as`("status of a route outside the restricted set").isEqualTo(403)
        assertThat(response.body).`as`("problem of the refusal").contains("mfa_enrolment_required")
    }

    @Test
    fun `a restricted session lists its own factors`() {
        // given: the restricted session of an administrator without a factor
        val (session, _, _) = restrictedLogin("rita-factors")

        // when: its factors are listed
        val response = http.get("$base/api/identity/me/factors")
            .header("Cookie", "${SessionCookies.SESSION}=$session")
            .asString()

        // then: the factor route answers as usual
        assertThat(response.status).`as`("status of the factor route: ${response.body}").isEqualTo(200)
    }

    @Test
    fun `a restricted session may log out`() {
        // given: the restricted session with its refresh and csrf cookies
        val (session, refresh, csrf) = restrictedLogin("rita-logout")

        // when: it logs out with the csrf header that the browser sends
        val response = http.post("$base/api/auth/logout")
            .header("Cookie", "${SessionCookies.SESSION}=$session; ${SessionCookies.REFRESH}=$refresh; ${SessionCookies.CSRF}=$csrf")
            .header(SessionCookies.CSRF_HEADER, csrf)
            .asString()

        // then: the logout is answered as before
        assertThat(response.status).`as`("status of the logout: ${response.body}").isEqualTo(200)
    }
}
