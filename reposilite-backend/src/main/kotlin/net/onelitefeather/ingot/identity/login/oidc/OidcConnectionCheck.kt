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

/** The endpoints a connection test found. [jwks] and [userinfo] are absent for GitHub, which publishes no key set. */
data class OidcEndpointsReport(
    val authorization: String,
    val token: String,
    val jwks: String?,
    val userinfo: String?,
)

/** The answer of the OpenID Connect connection test. [error] is set when [ok] is false and never holds a secret. */
data class OidcConnectionReport(
    val ok: Boolean,
    val issuer: String?,
    val endpoints: OidcEndpointsReport?,
    val error: String?,
)

/**
 * The connection test of an OpenID Connect provider for administrators. The discovery document and the key set are fetched
 * again, not read from the cache, so that a broken provider is reported at once. GitHub has no discovery document, so its
 * endpoints are reported as they are configured.
 */
internal class OidcConnectionCheck(
    private val catalog: OidcProviderCatalog,
    private val metadata: OidcMetadata,
) {

    /** `null` when no enabled provider has [id]. */
    fun check(id: String): OidcConnectionReport? {
        val provider = catalog.find(id) ?: return null
        provider.missingField()?.let { missing ->
            return OidcConnectionReport(ok = false, issuer = null, endpoints = null, error = "The setting $missing is missing")
        }
        if (provider.usesGitHubEndpoints()) {
            val github = GitHubEndpoints.PUBLIC
            return OidcConnectionReport(
                ok = true,
                issuer = null,
                endpoints = OidcEndpointsReport(authorization = github.authorize, token = github.token, jwks = null, userinfo = "${github.api}/user"),
                error = null,
            )
        }
        return try {
            metadata.forget(provider.issuerUrl)
            val endpoints = metadata.discovery(provider.issuerUrl)
            metadata.jwks(endpoints)
            OidcConnectionReport(
                ok = true,
                issuer = endpoints.issuer,
                endpoints = OidcEndpointsReport(endpoints.authorization, endpoints.token, endpoints.jwksUri, endpoints.userinfo),
                error = null,
            )
        } catch (failure: OidcUnavailableException) {
            OidcConnectionReport(ok = false, issuer = provider.issuerUrl, endpoints = null, error = failure.message ?: "the provider cannot be reached")
        }
    }

}
