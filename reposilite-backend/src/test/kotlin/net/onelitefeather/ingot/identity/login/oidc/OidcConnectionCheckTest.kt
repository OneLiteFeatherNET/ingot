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

import net.onelitefeather.ingot.identity.login.LoginSettings
import net.onelitefeather.ingot.identity.login.MutableClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import panda.std.reactive.Reference
import java.time.Instant

internal class OidcConnectionCheckTest {

    private val clock = MutableClock(Instant.parse("2026-10-15T10:00:00Z"))
    private lateinit var stub: OidcTestIdentityProvider

    @BeforeEach
    fun start() {
        stub = OidcTestIdentityProvider(clock).start()
    }

    @AfterEach
    fun stop() {
        stub.close()
    }

    private fun checkFor(provider: OidcProviderSettings): OidcConnectionCheck {
        val catalog = OidcProviderCatalog(Reference.reference(LoginSettings(oidcProviders = listOf(provider))))
        return OidcConnectionCheck(catalog, OidcMetadata(JdkHttpFetcher(), clock))
    }

    private fun corp(issuer: String = stub.issuer) = OidcProviderSettings(
        id = "corp",
        enabled = true,
        issuerUrl = issuer,
        clientId = OidcTestIdentityProvider.CLIENT_ID,
        clientSecret = OidcTestIdentityProvider.CLIENT_SECRET,
    )

    @Test
    fun `a reachable provider reports its endpoints`() {
        val report = checkFor(corp()).check("corp")!!

        assertThat(report.ok).isTrue
        assertThat(report.issuer).isEqualTo(stub.issuer)
        assertThat(report.endpoints?.token).isEqualTo("${stub.issuer}/token")
        assertThat(report.endpoints?.jwks).isEqualTo("${stub.issuer}/jwks")
    }

    @Test
    fun `each test reads the provider again instead of a cached answer`() {
        val check = checkFor(corp())

        check.check("corp")
        check.check("corp")

        assertThat(stub.discoveryRequests.get()).describedAs("discovery requests").isEqualTo(2)
    }

    @Test
    fun `an issuer that does not match its document is reported without a secret`() {
        stub.discoveryIssuer = "https://someone-else.example.org"

        val report = checkFor(corp()).check("corp")!!

        assertThat(report.ok).isFalse
        assertThat(report.error).contains("issuer")
        assertThat(report.toString()).doesNotContain(OidcTestIdentityProvider.CLIENT_SECRET)
    }

    @Test
    fun `a provider without a client id is reported as missing that setting`() {
        val report = checkFor(corp().copy(clientId = "")).check("corp")!!

        assertThat(report.ok).isFalse
        assertThat(report.error).contains("clientId")
    }

    @Test
    fun `an unknown provider has no report`() {
        assertThat(checkFor(corp()).check("other")).isNull()
    }

    @Test
    fun `github is reported with its own endpoints without a request`() {
        val github = OidcProviderSettings(id = "github", enabled = true, icon = OidcIcon.GITHUB, clientId = "id", clientSecret = "secret")

        val report = checkFor(github).check("github")!!

        assertThat(report.ok).isTrue
        assertThat(report.endpoints?.authorization).isEqualTo(GitHubEndpoints.PUBLIC.authorize)
        assertThat(stub.discoveryRequests.get()).isZero()
    }
}
