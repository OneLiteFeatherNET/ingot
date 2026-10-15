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

package net.onelitefeather.ingot.identity.login.oidc

import net.onelitefeather.ingot.identity.login.MutableClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

/** The GitHub API mapping, against the in-process stub of GitHub's endpoints. */
internal class GitHubLoginTest {

    private val clock = MutableClock(Instant.parse("2026-10-15T10:00:00Z"))
    private lateinit var stub: OidcTestIdentityProvider
    private lateinit var login: GitHubLogin

    private val provider = OidcProviderSettings(
        id = "github",
        enabled = true,
        icon = OidcIcon.GITHUB,
        clientId = OidcTestIdentityProvider.CLIENT_ID,
        clientSecret = OidcTestIdentityProvider.CLIENT_SECRET,
    )

    @BeforeEach
    fun setUp() {
        stub = OidcTestIdentityProvider(clock).start()
        login = GitHubLogin(JdkHttpFetcher(), endpointsOf(stub))
    }

    @AfterEach
    fun tearDown() {
        stub.close()
    }

    @Test
    fun `the subject is the numeric id and the groups are organisations and their teams`() {
        // when
        val profile = login.profile("gho_test")

        // then
        assertThat(profile.subject).isEqualTo("4242")
        assertThat(profile.login).isEqualTo("octocat")
        assertThat(profile.name).isEqualTo("The Octocat")
        assertThat(profile.groups).containsExactly("acme", "acme/platform")
    }

    @Test
    fun `the access token is sent as a bearer credential`() {
        login.profile("gho_test")

        assertThat(stub.githubAuthorizations).isNotEmpty().allMatch { it == "Bearer gho_test" }
    }

    @Test
    fun `without a public email the primary verified one is used`() {
        // given: the user document hides the email
        stub.githubUser = """{"id":4242,"login":"octocat","name":null,"email":null}"""

        // when
        val profile = login.profile("gho_test")

        // then
        assertThat(profile.email).isEqualTo("octo@example.org")
        assertThat(profile.name).describedAs("a missing name stays missing").isNull()
    }

    @Test
    fun `an unverified primary email is not used`() {
        stub.githubUser = """{"id":4242,"login":"octocat","email":null}"""
        stub.githubEmails = """[{"email":"unverified@example.org","primary":true,"verified":false}]"""

        assertThat(login.profile("gho_test").email).isNull()
    }

    @Test
    fun `the code exchange sends the client credentials and the pkce verifier`() {
        val token = login.exchange(provider, code = "code-1", verifier = "verifier-1", redirectUri = "http://localhost/cb")

        assertThat(token).isEqualTo("gho_test")
        val form = stub.githubTokenForms.single()
        assertThat(form["client_secret"]).isEqualTo(OidcTestIdentityProvider.CLIENT_SECRET)
        assertThat(form["client_id"]).isEqualTo(OidcTestIdentityProvider.CLIENT_ID)
        assertThat(form["code_verifier"]).isEqualTo("verifier-1")
    }

    @Test
    fun `a code that github refuses is answered with an error body and fails the exchange`() {
        stub.githubTokenBody = """{"error":"bad_verification_code","error_description":"The code passed is incorrect or expired."}"""

        assertThrows<TokenRefusedException> {
            login.exchange(provider, code = "stale", verifier = "v", redirectUri = "http://localhost/cb")
        }
    }

    private fun endpointsOf(stub: OidcTestIdentityProvider) = GitHubEndpoints(
        authorize = "${stub.issuer}/github/authorize",
        token = "${stub.issuer}/github/token",
        api = "${stub.issuer}/github/api",
    )
}
