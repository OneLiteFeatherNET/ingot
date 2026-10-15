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

import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import net.onelitefeather.ingot.identity.login.LoginSettings
import net.onelitefeather.ingot.identity.login.MutableClock
import net.onelitefeather.ingot.identity.login.external.ExternalAccountService
import net.onelitefeather.ingot.identity.login.external.ExternalIdentityStore
import net.onelitefeather.ingot.identity.login.oidc.OidcBegin.Started
import net.onelitefeather.ingot.identity.login.oidc.OidcFinish.Failed
import net.onelitefeather.ingot.identity.login.oidc.OidcFinish.LoggedIn
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.storage.IdentityStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.reactive.Reference
import java.net.URI
import java.net.URLDecoder
import java.nio.file.Path
import java.time.Instant

/** The GitHub login through [OidcLoginService], with GitHub's endpoints replaced by the in-process stub. */
internal class GitHubFlowTest {

    private val clock = MutableClock(Instant.parse("2026-10-15T10:00:00Z"))

    @TempDir
    lateinit var workingDirectory: Path

    private val redirectUri = "http://localhost:8080/api/auth/oidc/github/callback"

    private fun withGitHub(block: (OidcTestIdentityProvider, OidcLoginService, IdentityStore) -> Unit) {
        val stub = OidcTestIdentityProvider(clock).start()
        try {
            DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
                val identities = IdentityStore(connection.database, clock)
                val settings = LoginSettings(
                    oidcProviders = listOf(
                        OidcProviderSettings(
                            id = "github",
                            enabled = true,
                            label = "GitHub",
                            icon = OidcIcon.GITHUB,
                            clientId = OidcTestIdentityProvider.CLIENT_ID,
                            clientSecret = OidcTestIdentityProvider.CLIENT_SECRET,
                        ),
                    ),
                )
                val service = OidcLoginService(
                    catalog = OidcProviderCatalog(Reference.reference(settings)),
                    metadata = OidcMetadata(JdkHttpFetcher(), clock),
                    http = JdkHttpFetcher(),
                    states = OidcStateCookie(ByteArray(32) { it.toByte() }, clock),
                    validator = IdTokenValidator(clock),
                    accounts = ExternalAccountService(identities, ExternalIdentityStore(connection.database)),
                    clock = clock,
                    github = GitHubEndpoints(
                        authorize = "${stub.issuer}/github/authorize",
                        token = "${stub.issuer}/github/token",
                        api = "${stub.issuer}/github/api",
                    ),
                )
                block(stub, service, identities)
            }
        } finally {
            stub.close()
        }
    }

    private fun begin(service: OidcLoginService): Started =
        service.begin("github", redirect = "/repositories", redirectUri = redirectUri) as? Started
            ?: throw AssertionError("expected a started GitHub login")

    private fun queryOf(location: String): Map<String, String> =
        URI(location).rawQuery.split('&').associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8") }

    @Test
    fun `the github authorization uses github's scopes and no nonce, and never asks for discovery`() {
        withGitHub { stub, service, _ ->
            val started = begin(service)
            val query = queryOf(started.location)

            assertThat(started.location).startsWith("${stub.issuer}/github/authorize?")
            assertThat(query["scope"]).isEqualTo("read:user user:email read:org")
            assertThat(query).doesNotContainKey("nonce")
            assertThat(query["code_challenge_method"]).isEqualTo("S256")
            assertThat(stub.discoveryRequests.get()).describedAs("discovery requests").isZero()
        }
    }

    @Test
    fun `a github login creates the user named after the login and links the numeric id`() {
        withGitHub { stub, service, identities ->
            identities.createPrincipal(PrincipalKind.USER, "admin", "Admin", false, clock.instant())
            val started = begin(service)
            val state = queryOf(started.location)["state"]!!

            val outcome = service.finish(
                providerId = "github",
                state = state,
                code = "code-1",
                error = null,
                cookie = started.stateCookie,
                redirectUri = redirectUri,
                ip = "10.0.0.1",
            )

            assertThat(outcome).isInstanceOf(LoggedIn::class.java)
            assertThat((outcome as LoggedIn).principal.name).isEqualTo("octocat")
            assertThat(stub.githubTokenForms.single()["code_verifier"]).describedAs("pkce verifier sent").isNotBlank()
            assertThat(stub.githubAuthorizations.last()).isEqualTo("Bearer gho_test")
        }
    }

    @Test
    fun `a refused github code fails the login with token_exchange_failed`() {
        withGitHub { stub, service, identities ->
            identities.createPrincipal(PrincipalKind.USER, "admin", "Admin", false, clock.instant())
            stub.githubTokenBody = """{"error":"bad_verification_code"}"""
            val started = begin(service)

            val outcome = service.finish(
                providerId = "github",
                state = queryOf(started.location)["state"],
                code = "stale",
                error = null,
                cookie = started.stateCookie,
                redirectUri = redirectUri,
                ip = null,
            )

            assertThat(outcome).isEqualTo(Failed("token_exchange_failed"))
        }
    }
}
