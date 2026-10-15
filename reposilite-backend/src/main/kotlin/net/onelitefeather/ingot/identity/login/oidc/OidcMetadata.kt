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

import com.nimbusds.jose.jwk.JWKSet
import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import java.io.IOException
import java.net.URI
import java.text.ParseException
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** The endpoints an OpenID Connect provider publishes in its discovery document. */
data class OidcEndpoints(
    val issuer: String,
    val authorization: String,
    val token: String,
    val jwksUri: String,
    val userinfo: String?,
)

/** The provider cannot be used right now: it is unreachable, or it answers with something that is not its discovery document. */
class OidcUnavailableException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Discovery documents and key sets of the identity providers, cached per provider. Entries live for [ttl] on the injected
 * clock. A key id that the cached key set does not hold triggers a refetch, but at most one per [refetchInterval], so a
 * flood of unknown key ids cannot turn into a flood of requests to the provider.
 */
internal class OidcMetadata(
    private val http: HttpFetcher,
    private val clock: Clock,
    private val ttl: Duration = Duration.ofHours(1),
    private val refetchInterval: Duration = Duration.ofMinutes(1),
) {

    private class Cached<T>(val value: T, val fetchedAt: Instant)

    private val discoveryCache = HashMap<String, Cached<OidcEndpoints>>()
    private val jwksCache = HashMap<String, Cached<JWKSet>>()

    /** The endpoints of [issuer]. Throws [OidcUnavailableException] when they cannot be read. */
    @Synchronized
    fun discovery(issuer: String): OidcEndpoints {
        val key = normalize(issuer)
        val now = clock.instant()
        discoveryCache[key]?.takeIf { now.isBefore(it.fetchedAt.plus(ttl)) }?.let { return it.value }
        val endpoints = fetchDiscovery(key)
        discoveryCache[key] = Cached(endpoints, now)
        return endpoints
    }

    /** Drops the cached discovery document of [issuer] and its key set, so the next read goes to the provider. Used by the connection test. */
    @Synchronized
    fun forget(issuer: String) {
        val removed = discoveryCache.remove(normalize(issuer))
        removed?.value?.jwksUri?.let { jwksCache.remove(it) }
    }

    /** The key set published at the jwks uri of [endpoints], from the cache while it is fresh. */
    @Synchronized
    fun jwks(endpoints: OidcEndpoints): JWKSet {
        val now = clock.instant()
        jwksCache[endpoints.jwksUri]?.takeIf { now.isBefore(it.fetchedAt.plus(ttl)) }?.let { return it.value }
        return fetchJwks(endpoints.jwksUri, now)
    }

    /**
     * Fetches the key set again, for a key id that the cached set does not hold. Returns `null` without a request when the
     * last fetch is less than [refetchInterval] old.
     */
    @Synchronized
    fun refetchJwks(endpoints: OidcEndpoints): JWKSet? {
        val now = clock.instant()
        val cached = jwksCache[endpoints.jwksUri]
        if (cached != null && now.isBefore(cached.fetchedAt.plus(refetchInterval))) {
            return null
        }
        return fetchJwks(endpoints.jwksUri, now)
    }

    private fun fetchDiscovery(issuer: String): OidcEndpoints {
        requireSafeIssuer(issuer)
        val reply = get("$issuer/.well-known/openid-configuration", "discovery document")
        if (reply.status != 200) {
            throw OidcUnavailableException("discovery answered ${reply.status}")
        }
        val document = try {
            DEFAULT_OBJECT_MAPPER.readTree(reply.body)
        } catch (exception: Exception) {
            throw OidcUnavailableException("discovery document is not JSON", exception)
        }
        val documentIssuer = document.get("issuer")?.asText()
        if (documentIssuer == null || normalize(documentIssuer) != normalize(issuer)) {
            throw OidcUnavailableException("discovery issuer does not match the configured issuer")
        }
        val authorization = document.get("authorization_endpoint")?.asText()
        val token = document.get("token_endpoint")?.asText()
        val jwks = document.get("jwks_uri")?.asText()
        if (authorization.isNullOrBlank() || token.isNullOrBlank() || jwks.isNullOrBlank()) {
            throw OidcUnavailableException("discovery document lacks an endpoint")
        }
        return OidcEndpoints(
            issuer = issuer,
            authorization = authorization,
            token = token,
            jwksUri = jwks,
            userinfo = document.get("userinfo_endpoint")?.asText()?.takeIf { it.isNotBlank() },
        )
    }

    private fun fetchJwks(uri: String, now: Instant): JWKSet {
        val reply = get(uri, "key set")
        if (reply.status != 200) {
            throw OidcUnavailableException("key set answered ${reply.status}")
        }
        val keys = try {
            JWKSet.parse(reply.body)
        } catch (exception: ParseException) {
            throw OidcUnavailableException("key set is not a JWK set", exception)
        }
        jwksCache[uri] = Cached(keys, now)
        return keys
    }

    private fun get(url: String, what: String) =
        try {
            http.get(URI.create(url))
        } catch (exception: IOException) {
            throw OidcUnavailableException("$what not reachable", exception)
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw OidcUnavailableException("$what not reachable", exception)
        } catch (exception: IllegalArgumentException) {
            throw OidcUnavailableException("$what has an invalid address", exception)
        }

    companion object {

        private val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1", "[::1]")

        /** Issuers are https, except on loopback, where the test providers and local development run without TLS. */
        fun requireSafeIssuer(issuer: String) {
            val uri = try {
                URI.create(issuer)
            } catch (exception: IllegalArgumentException) {
                throw OidcUnavailableException("issuer is not a URL", exception)
            }
            val safe = when (uri.scheme) {
                "https" -> true
                "http" -> uri.host in LOOPBACK_HOSTS
                else -> false
            }
            if (!safe) {
                throw OidcUnavailableException("issuer must use https")
            }
        }

        /** Issuers compare without a trailing slash. */
        fun normalize(issuer: String): String =
            issuer.trim().trimEnd('/')
    }

}
