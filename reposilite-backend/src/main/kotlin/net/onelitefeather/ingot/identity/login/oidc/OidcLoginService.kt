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

import com.nimbusds.jwt.JWTClaimsSet
import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.audit.api.AuditAction
import com.reposilite.audit.api.AuditActor
import com.reposilite.audit.api.AuditEvent
import net.onelitefeather.ingot.identity.login.LoginRedirect
import net.onelitefeather.ingot.identity.login.external.ExternalAccountService
import net.onelitefeather.ingot.identity.login.external.ExternalIdentity
import net.onelitefeather.ingot.identity.login.external.ExternalOutcome
import net.onelitefeather.ingot.identity.model.PrincipalRecord
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.util.Base64

/** The result of starting a login. */
sealed interface OidcBegin {

    /** Redirect the browser to [location] and set [stateCookie] as `ingot_oidc`. */
    data class Started(val location: String, val stateCookie: String) : OidcBegin

    data object UnknownProvider : OidcBegin

    /** The provider is enabled but lacks a setting. [field] names it; no secret is ever named with its value. */
    data class Unusable(val field: String) : OidcBegin

    /** The discovery document cannot be read. */
    data object Unavailable : OidcBegin

}

/** The result of the callback. */
sealed interface OidcFinish {

    /** The subject is known or was just created. The caller starts the browser session and redirects to [redirect]. */
    data class LoggedIn(val principal: PrincipalRecord, val redirect: String) : OidcFinish

    /** The login failed. [code] is one of the login codes of the identity specification, section 14.4. */
    data class Failed(val code: String) : OidcFinish

}

/** Thrown inside the callback when the login must fail with [code]. */
internal class LoginFailure(val code: String) : RuntimeException(code)

/**
 * The redirect login flow of the OpenID Connect and GitHub providers: the authorization redirect with PKCE, state and nonce,
 * and the callback that exchanges the code and maps the answer to a subject for [ExternalAccountService]. Nothing is kept
 * between the two requests except the sealed `ingot_oidc` cookie. A GitHub provider skips discovery and the ID token, see
 * [GitHubLogin].
 */
internal class OidcLoginService(
    private val catalog: OidcProviderCatalog,
    private val metadata: OidcMetadata,
    private val http: HttpFetcher,
    private val states: OidcStateCookie,
    private val validator: IdTokenValidator,
    private val accounts: ExternalAccountService,
    private val clock: Clock,
    private val audit: (AuditEvent) -> Unit = {},
    private val github: GitHubEndpoints = GitHubEndpoints.PUBLIC,
    private val random: SecureRandom = SecureRandom(),
) {

    private val githubLogin = GitHubLogin(http, github)

    fun begin(providerId: String, redirect: String?, redirectUri: String): OidcBegin {
        val provider = catalog.find(providerId) ?: return OidcBegin.UnknownProvider
        provider.missingField()?.let { return OidcBegin.Unusable(it) }
        val authorization = if (provider.usesGitHubEndpoints()) {
            github.authorize
        } else {
            try {
                metadata.discovery(provider.issuerUrl).authorization
            } catch (_: OidcUnavailableException) {
                return OidcBegin.Unavailable
            }
        }
        val state = AuthorizationState(
            provider = providerId,
            state = randomToken(),
            nonce = randomToken(),
            verifier = Pkce.newVerifier(random),
            redirect = LoginRedirect.sanitize(redirect),
            expiresAt = Instant.now(clock).plus(OidcStateCookie.LIFETIME),
        )
        val location = authorizeUrl(authorization, provider, state, redirectUri)
        return OidcBegin.Started(location = location, stateCookie = states.seal(state))
    }

    fun finish(
        providerId: String,
        state: String?,
        code: String?,
        error: String?,
        cookie: String?,
        redirectUri: String,
        ip: String?,
    ): OidcFinish {
        val expected = states.open(cookie, providerId) ?: return protocolFailure(providerId, ip, "invalid_state")
        if (state == null || state != expected.state) {
            return protocolFailure(providerId, ip, "invalid_state")
        }
        if (error != null) {
            return protocolFailure(providerId, ip, if (error == "access_denied") "access_denied" else "provider_error")
        }
        val provider = catalog.find(providerId) ?: return protocolFailure(providerId, ip, "provider_unavailable")
        if (code.isNullOrBlank()) {
            return protocolFailure(providerId, ip, "provider_error")
        }

        return try {
            val identity = if (provider.usesGitHubEndpoints()) {
                githubIdentity(provider, expected, code, redirectUri)
            } else {
                oidcIdentity(provider, expected, code, redirectUri)
            }
            when (val outcome = accounts.resolve(identity)) {
                is ExternalOutcome.Succeeded -> {
                    audit(AuditEvent(action = AuditAction.LOGIN_SUCCEEDED, actor = AuditActor.named(outcome.principal.name, ip)))
                    OidcFinish.LoggedIn(outcome.principal, expected.redirect)
                }
                is ExternalOutcome.Refused -> protocolFailure(providerId, ip, outcome.reason.code)
            }
        } catch (failure: LoginFailure) {
            protocolFailure(providerId, ip, failure.code)
        } catch (_: OidcUnavailableException) {
            protocolFailure(providerId, ip, "provider_unavailable")
        } catch (_: TokenRefusedException) {
            protocolFailure(providerId, ip, "token_exchange_failed")
        }
    }

    /** The identity of an OpenID Connect login: the ID token, validated, and the claims the provider configured. */
    private fun oidcIdentity(provider: OidcProviderSettings, expected: AuthorizationState, code: String, redirectUri: String): ExternalIdentity {
        val endpoints = metadata.discovery(provider.issuerUrl)
        val idToken = exchangeCode(provider, endpoints.token, code, expected.verifier, redirectUri)
        val claims = validateIdToken(idToken, endpoints, provider.clientId, expected.nonce)
            ?: throw LoginFailure("invalid_id_token")
        val subject = claims.subject?.takeIf { it.isNotBlank() } ?: throw LoginFailure("invalid_id_token")
        val username = claims.getClaim(provider.usernameClaim) as? String
        if (username.isNullOrBlank() && provider.autoCreateUsers) {
            throw LoginFailure("invalid_id_token")
        }
        return ExternalIdentity(
            provider = "oidc:${provider.id}",
            subject = subject,
            username = username.orEmpty(),
            displayName = claims.getClaim(provider.displayNameClaim) as? String,
            email = claims.getClaim(provider.emailClaim) as? String,
            autoCreateUsers = provider.autoCreateUsers,
            allowedEmailDomains = provider.allowedEmailDomains,
        )
    }

    /** The identity of a GitHub login: the access token, then the user and the verified primary email from the API. */
    private fun githubIdentity(provider: OidcProviderSettings, expected: AuthorizationState, code: String, redirectUri: String): ExternalIdentity {
        val accessToken = githubLogin.exchange(provider, code, expected.verifier, redirectUri)
        val profile = githubLogin.profile(accessToken)
        return ExternalIdentity(
            provider = "oidc:${provider.id}",
            subject = profile.subject,
            username = profile.login,
            displayName = profile.name,
            email = profile.email,
            autoCreateUsers = provider.autoCreateUsers,
            allowedEmailDomains = provider.allowedEmailDomains,
        )
    }

    private fun authorizeUrl(endpoint: String, provider: OidcProviderSettings, state: AuthorizationState, redirectUri: String): String {
        val parameters = linkedMapOf(
            "response_type" to "code",
            "client_id" to provider.clientId,
            "redirect_uri" to redirectUri,
            "scope" to effectiveScopes(provider),
            "state" to state.state,
            "code_challenge" to Pkce.challengeOf(state.verifier),
            "code_challenge_method" to "S256",
        )
        if (!provider.usesGitHubEndpoints()) {
            parameters["nonce"] = state.nonce
        }
        val query = parameters.entries.joinToString("&") { (name, value) -> "${encode(name)}=${encode(value)}" }
        return "$endpoint?$query"
    }

    /** GitHub has no `openid` scope. The default of the OpenID Connect scopes is replaced by GitHub's own scopes. */
    private fun effectiveScopes(provider: OidcProviderSettings): String =
        if (provider.usesGitHubEndpoints() && (provider.scopes.isBlank() || provider.scopes == OidcProviderSettings.OIDC_DEFAULT_SCOPES)) {
            OidcProviderSettings.GITHUB_DEFAULT_SCOPES
        } else {
            provider.scopes
        }

    /** The ID token of the code exchange. Throws [TokenRefusedException] for a refused code or a reply without a token. */
    private fun exchangeCode(provider: OidcProviderSettings, tokenEndpoint: String, code: String, verifier: String, redirectUri: String): String {
        val form = linkedMapOf(
            "grant_type" to "authorization_code",
            "code" to code,
            "redirect_uri" to redirectUri,
            "code_verifier" to verifier,
        )
        val headers = if (provider.clientSecret.isBlank()) {
            form["client_id"] = provider.clientId
            emptyMap()
        } else {
            mapOf("Authorization" to basicAuthorization(provider.clientId, provider.clientSecret))
        }
        val reply = try {
            http.postForm(URI.create(tokenEndpoint), form, headers)
        } catch (exception: IOException) {
            throw OidcUnavailableException("token endpoint not reachable", exception)
        }
        if (reply.status != 200) {
            throw TokenRefusedException()
        }
        val idToken = try {
            DEFAULT_OBJECT_MAPPER.readTree(reply.body).get("id_token")?.asText()
        } catch (_: Exception) {
            null
        }
        return idToken?.takeIf { it.isNotBlank() } ?: throw TokenRefusedException()
    }

    /**
     * The verified claims of [idToken], or `null` when the token is refused. An unknown key id gets one fetch of the key set,
     * when the cached set is old enough; otherwise the token is refused.
     */
    private fun validateIdToken(idToken: String, endpoints: OidcEndpoints, clientId: String, nonce: String): JWTClaimsSet? {
        val first = validator.validate(idToken, metadata.jwks(endpoints), endpoints.issuer, clientId, nonce)
        val check = if (first is IdTokenCheck.UnknownKey) {
            val refreshed = metadata.refetchJwks(endpoints) ?: return null
            validator.validate(idToken, refreshed, endpoints.issuer, clientId, nonce)
        } else {
            first
        }
        return (check as? IdTokenCheck.Valid)?.claims
    }

    private fun protocolFailure(providerId: String, ip: String?, code: String): OidcFinish {
        audit(AuditEvent(action = AuditAction.LOGIN_FAILED, actor = AuditActor.named("oidc:$providerId", ip), success = false))
        return OidcFinish.Failed(code)
    }

    private fun randomToken(): String =
        ByteArray(TOKEN_BYTES).also { random.nextBytes(it) }.let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

    private fun basicAuthorization(clientId: String, secret: String): String {
        val credentials = "${encode(clientId)}:${encode(secret)}"
        return "Basic " + Base64.getEncoder().encodeToString(credentials.toByteArray(StandardCharsets.UTF_8))
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)

    private companion object {
        const val TOKEN_BYTES = 32
    }

}
