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

import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
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
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

internal class OidcLoginServiceTest {

    private val clock = MutableClock(Instant.parse("2026-10-15T10:00:00Z"))

    @TempDir
    lateinit var workingDirectory: Path

    private class Fixture(
        val identities: IdentityStore,
        val provider: OidcTestIdentityProvider,
        val service: OidcLoginService,
        val states: OidcStateCookie,
    )

    private fun withFlow(
        provider: OidcProviderSettings = corp(),
        block: (Fixture, IdentityStore) -> Unit,
    ) {
        val idp = OidcTestIdentityProvider(clock).start()
        try {
            DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
                val identities = IdentityStore(connection.database, clock)
                val links = ExternalIdentityStore(connection.database)
                val settings = Reference.reference(LoginSettings(oidcProviders = listOf(provider.copy(issuerUrl = idp.issuer))))
                val states = OidcStateCookie(ByteArray(32) { it.toByte() }, clock)
                val service = OidcLoginService(
                    catalog = OidcProviderCatalog(settings),
                    metadata = OidcMetadata(JdkHttpFetcher(), clock),
                    http = JdkHttpFetcher(),
                    states = states,
                    validator = IdTokenValidator(clock),
                    accounts = ExternalAccountService(identities, links),
                    clock = clock,
                )
                block(Fixture(identities, idp, service, states), identities)
            }
        } finally {
            idp.close()
        }
    }

    private fun corp(autoCreate: Boolean = true, domains: List<String> = emptyList()) = OidcProviderSettings(
        id = "corp",
        enabled = true,
        label = "Corporate",
        clientId = OidcTestIdentityProvider.CLIENT_ID,
        clientSecret = OidcTestIdentityProvider.CLIENT_SECRET,
        autoCreateUsers = autoCreate,
        allowedEmailDomains = domains,
    )

    private fun Fixture.begin(redirect: String = "/dashboard"): Started {
        val outcome = service.begin("corp", redirect, redirectUri = "http://localhost:8080/api/auth/oidc/corp/callback")
        return outcome as? Started ?: throw AssertionError("expected a started login, got $outcome")
    }

    private fun queryOf(location: String): Map<String, String> =
        URI(location).rawQuery.split('&').associate { it.substringBefore('=') to java.net.URLDecoder.decode(it.substringAfter('='), "UTF-8") }

    /** Starts a login and makes the identity provider echo its nonce, the way a browser round trip does. */
    private fun Fixture.startAndEchoNonce(): Pair<Started, AuthorizationState> {
        val started = begin()
        val state = states.open(started.stateCookie, "corp")!!
        provider.nonce = state.nonce
        return started to state
    }

    private fun Fixture.finish(started: Started, code: String = "code-1", state: String? = null): OidcFinish {
        val expected = states.open(started.stateCookie, "corp")!!
        return service.finish(
            providerId = "corp",
            state = state ?: expected.state,
            code = code,
            error = null,
            cookie = started.stateCookie,
            redirectUri = "http://localhost:8080/api/auth/oidc/corp/callback",
            ip = "10.0.0.1",
        )
    }

    private fun Fixture.localAdmin() {
        identities.createPrincipal(PrincipalKind.USER, "admin", "Admin", false, clock.instant())
    }

    @Test
    fun `the authorization redirect carries pkce, state and nonce`() {
        withFlow { fixture, _ ->
            // when
            val started = fixture.begin(redirect = "/repositories")
            val state = fixture.states.open(started.stateCookie, "corp")!!
            val query = queryOf(started.location)

            // then
            assertThat(started.location).startsWith(fixture.provider.issuer + "/authorize?")
            assertThat(query["response_type"]).isEqualTo("code")
            assertThat(query["client_id"]).isEqualTo(OidcTestIdentityProvider.CLIENT_ID)
            assertThat(query["state"]).isEqualTo(state.state)
            assertThat(query["nonce"]).isEqualTo(state.nonce)
            assertThat(query["code_challenge_method"]).isEqualTo("S256")
            assertThat(query["code_challenge"]).isEqualTo(Pkce.challengeOf(state.verifier))
            assertThat(state.redirect).isEqualTo("/repositories")
        }
    }

    @Test
    fun `an unsafe redirect is replaced by the root when the login starts`() {
        withFlow { fixture, _ ->
            val started = fixture.begin(redirect = "https://evil.example/")

            assertThat(fixture.states.open(started.stateCookie, "corp")!!.redirect).isEqualTo("/")
        }
    }

    @Test
    fun `a valid login creates the user, links the subject and returns the redirect`() {
        withFlow { fixture, identities ->
            fixture.localAdmin()
            val (started, state) = fixture.startAndEchoNonce()

            // when
            val outcome = fixture.finish(started)

            // then
            assertThat(outcome).isInstanceOf(LoggedIn::class.java)
            val loggedIn = outcome as LoggedIn
            assertThat(loggedIn.principal.name).isEqualTo("alice")
            assertThat(loggedIn.principal.kind).isEqualTo(PrincipalKind.USER)
            assertThat(loggedIn.redirect).isEqualTo(state.redirect)
            assertThat(identities.findPrincipal("alice")).describedAs("just-in-time user").isNotNull
        }
    }

    @Test
    fun `the token request carries the code, the pkce verifier and the client credentials`() {
        withFlow { fixture, _ ->
            fixture.localAdmin()
            val (started, state) = fixture.startAndEchoNonce()

            fixture.finish(started, code = "code-42")

            val form = fixture.provider.tokenForms.single()
            assertThat(form["grant_type"]).isEqualTo("authorization_code")
            assertThat(form["code"]).isEqualTo("code-42")
            assertThat(form["code_verifier"]).isEqualTo(state.verifier)
            assertThat(form["redirect_uri"]).isEqualTo("http://localhost:8080/api/auth/oidc/corp/callback")
        }
    }

    @Test
    fun `a state that differs from the cookie is refused before any token request`() {
        withFlow { fixture, _ ->
            fixture.localAdmin()
            val (started, _) = fixture.startAndEchoNonce()

            val outcome = fixture.finish(started, state = "forged-state")

            assertThat(outcome).isEqualTo(Failed("invalid_state"))
            assertThat(fixture.provider.tokenRequests.get()).describedAs("token requests").isZero()
        }
    }

    @Test
    fun `a callback without the state cookie is refused`() {
        withFlow { fixture, _ ->
            val outcome = fixture.service.finish(
                providerId = "corp",
                state = "anything",
                code = "code-1",
                error = null,
                cookie = null,
                redirectUri = "http://localhost:8080/api/auth/oidc/corp/callback",
                ip = "10.0.0.1",
            )

            assertThat(outcome).isEqualTo(Failed("invalid_state"))
        }
    }

    @Test
    fun `a provider that denies the login is reported as access denied`() {
        withFlow { fixture, _ ->
            val started = fixture.begin()
            val state = fixture.states.open(started.stateCookie, "corp")!!

            val outcome = fixture.service.finish(
                providerId = "corp",
                state = state.state,
                code = null,
                error = "access_denied",
                cookie = started.stateCookie,
                redirectUri = "http://localhost:8080/api/auth/oidc/corp/callback",
                ip = "10.0.0.1",
            )

            assertThat(outcome).isEqualTo(Failed("access_denied"))
            assertThat(fixture.provider.tokenRequests.get()).isZero()
        }
    }

    @Test
    fun `a token endpoint that refuses the code fails the login`() {
        withFlow { fixture, _ ->
            fixture.localAdmin()
            val (started, _) = fixture.startAndEchoNonce()
            fixture.provider.tokenStatus = 400

            assertThat(fixture.finish(started)).isEqualTo(Failed("token_exchange_failed"))
        }
    }

    @Test
    fun `an id token signed by a key outside the jwks fails the login`() {
        withFlow { fixture, identities ->
            fixture.localAdmin()
            val (started, _) = fixture.startAndEchoNonce()
            fixture.provider.idTokenKey = RSAKeyGenerator(2048).keyID("test-key").generate()

            assertThat(fixture.finish(started)).isEqualTo(Failed("invalid_id_token"))
            assertThat(identities.findPrincipal("alice")).describedAs("no user is created for a rejected token").isNull()
        }
    }

    @Test
    fun `an id token with the nonce of another login fails the login`() {
        withFlow { fixture, _ ->
            fixture.localAdmin()
            val (started, _) = fixture.startAndEchoNonce()
            fixture.provider.nonce = "another-login"

            assertThat(fixture.finish(started)).isEqualTo(Failed("invalid_id_token"))
        }
    }

    @Test
    fun `a rotated signing key is accepted after the jwks is fetched again`() {
        withFlow { fixture, _ ->
            fixture.localAdmin()
            val (first, _) = fixture.startAndEchoNonce()
            assertThat(fixture.finish(first)).isInstanceOf(LoggedIn::class.java)

            // when: the provider rotates its key and a minute passes
            val rotated = RSAKeyGenerator(2048).keyID("rotated").generate()
            fixture.provider.jwksKeys = listOf(rotated.toPublicJWK())
            fixture.provider.idTokenKey = rotated
            clock.advance(Duration.ofMinutes(1))
            val (second, _) = fixture.startAndEchoNonce()

            // then
            assertThat(fixture.finish(second)).isInstanceOf(LoggedIn::class.java)
        }
    }

    @Test
    fun `an existing local user with the same name is never linked`() {
        withFlow { fixture, identities ->
            identities.createPrincipal(PrincipalKind.USER, "alice", "Local Alice", false, clock.instant())
            val (started, _) = fixture.startAndEchoNonce()

            assertThat(fixture.finish(started)).isEqualTo(Failed("username_taken"))
        }
    }

    @Test
    fun `an empty instance cannot be claimed through a provider`() {
        withFlow { fixture, identities ->
            val (started, _) = fixture.startAndEchoNonce()

            assertThat(fixture.finish(started)).isEqualTo(Failed("setup_required"))
            assertThat(identities.listPrincipals(PrincipalKind.USER)).isEmpty()
        }
    }

    @Test
    fun `a subject without a link is refused when automatic creation is off`() {
        withFlow(provider = corp(autoCreate = false)) { fixture, _ ->
            fixture.localAdmin()
            val (started, _) = fixture.startAndEchoNonce()

            assertThat(fixture.finish(started)).isEqualTo(Failed("user_not_provisioned"))
        }
    }

    @Test
    fun `a linked user who is disabled is refused on the next login`() {
        withFlow { fixture, identities ->
            fixture.localAdmin()
            val (first, _) = fixture.startAndEchoNonce()
            val linked = fixture.finish(first) as LoggedIn
            identities.updatePrincipal(linked.principal.id, displayName = null, disabled = true)

            val (second, _) = fixture.startAndEchoNonce()

            assertThat(fixture.finish(second)).isEqualTo(Failed("user_disabled"))
        }
    }

    @Test
    fun `an email outside the allowed domains is refused`() {
        withFlow(provider = corp(domains = listOf("example.org"))) { fixture, identities ->
            fixture.localAdmin()
            fixture.provider.claimsFor = {
                mapOf("sub" to "subject-2", "preferred_username" to "bob", "email" to "bob@other.example", "nonce" to fixture.provider.nonce)
            }
            val (started, _) = fixture.startAndEchoNonce()

            assertThat(fixture.finish(started)).isEqualTo(Failed("email_domain_not_allowed"))
            assertThat(identities.findPrincipal("bob")).isNull()
        }
    }

    @Test
    fun `an email in an allowed domain passes, regardless of letter case`() {
        withFlow(provider = corp(domains = listOf("example.org"))) { fixture, _ ->
            fixture.localAdmin()
            fixture.provider.claimsFor = {
                mapOf("sub" to "subject-3", "preferred_username" to "carol", "email" to "Carol@EXAMPLE.org", "nonce" to fixture.provider.nonce)
            }
            val (started, _) = fixture.startAndEchoNonce()

            assertThat(fixture.finish(started)).isInstanceOf(LoggedIn::class.java)
        }
    }
}
