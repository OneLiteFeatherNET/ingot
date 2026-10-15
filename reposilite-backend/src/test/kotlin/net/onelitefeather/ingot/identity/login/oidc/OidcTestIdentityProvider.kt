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

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * An OpenID Connect identity provider that runs in this process on a loopback port. It serves discovery, the JWKS and the
 * token endpoint, so the login flow runs over real HTTP without any external network. The keys are generated for every
 * instance. The ID token is built from [claimsFor] and the [nonce] a test read from the authorize redirect.
 */
internal class OidcTestIdentityProvider(private val clock: Clock) : AutoCloseable {

    val signingKey: RSAKey = RSAKeyGenerator(2048).keyID("test-key").generate()

    /** The key that signs ID tokens. A test replaces it to produce a token that does not match the JWKS. */
    var idTokenKey: RSAKey = signingKey

    /** The keys the JWKS serves. A test can drop or add keys to model a rotation. */
    var jwksKeys: List<JWK> = listOf(signingKey.toPublicJWK())

    /** The `alg` of the ID token header. Used to check that other algorithms are refused. */
    var idTokenAlgorithm: JWSAlgorithm = JWSAlgorithm.RS256

    /** The HTTP status of the token endpoint. Anything but 200 refuses the code. */
    var tokenStatus: Int = 200

    /** When set, the token endpoint returns this ID token instead of a built one. */
    var fixedIdToken: String? = null

    /** The nonce of the authorize redirect, which the ID token echoes. */
    var nonce: String? = null

    /** The issuer in the discovery document. Defaults to the address of this server. */
    var discoveryIssuer: String? = null

    /** Builds the claims of the ID token. The default is an active user with one group. */
    var claimsFor: () -> Map<String, Any?> = {
        mapOf(
            "sub" to "subject-1",
            "preferred_username" to "alice",
            "name" to "Alice Example",
            "email" to "alice@example.org",
            "groups" to listOf("devs"),
            "nonce" to nonce,
        )
    }

    val discoveryRequests = AtomicInteger()
    val jwksRequests = AtomicInteger()
    val tokenRequests = AtomicInteger()

    /** The form of every token request, in arrival order. */
    val tokenForms: MutableList<Map<String, String>> = CopyOnWriteArrayList()

    private val server: HttpServer = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)

    val issuer: String
        get() = "http://127.0.0.1:${server.address.port}"

    init {
        server.createContext("/.well-known/openid-configuration") { exchange ->
            discoveryRequests.incrementAndGet()
            val document = """{"issuer":"${discoveryIssuer ?: issuer}","authorization_endpoint":"$issuer/authorize",""" +
                """"token_endpoint":"$issuer/token","jwks_uri":"$issuer/jwks"}"""
            respond(exchange, 200, document)
        }
        server.createContext("/jwks") { exchange ->
            jwksRequests.incrementAndGet()
            respond(exchange, 200, JWKSet(jwksKeys).toString(false))
        }
        server.createContext("/token") { exchange ->
            tokenRequests.incrementAndGet()
            val form = readForm(exchange)
            tokenForms.add(form)
            if (tokenStatus != 200) {
                respond(exchange, tokenStatus, """{"error":"invalid_grant"}""")
            } else {
                val idToken = fixedIdToken ?: buildIdToken()
                respond(exchange, 200, """{"token_type":"Bearer","access_token":"access-1","id_token":"$idToken"}""")
            }
        }
    }

    fun start(): OidcTestIdentityProvider {
        server.start()
        return this
    }

    /** A signed ID token built from [claimsFor], with the standard claims of a fresh token. */
    fun buildIdToken(overrides: Map<String, Any?> = emptyMap()): String {
        val now = clock.instant()
        val standard = mapOf<String, Any?>(
            "iss" to issuer,
            "aud" to CLIENT_ID,
            "iat" to now.epochSecond,
            "exp" to now.plus(Duration.ofMinutes(5)).epochSecond,
        )
        val merged = LinkedHashMap<String, Any?>(standard)
        merged.putAll(claimsFor())
        merged.putAll(overrides)
        val payload = JWTClaimsSet.parse(merged.filterValues { it != null }.mapValues { it.value!! })
        val header = JWSHeader.Builder(idTokenAlgorithm).keyID(idTokenKey.keyID).type(JOSEObjectType.JWT).build()
        return SignedJWT(header, payload).also { it.sign(RSASSASigner(idTokenKey)) }.serialize()
    }

    private fun respond(exchange: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun readForm(exchange: HttpExchange): Map<String, String> {
        val body = exchange.requestBody.readAllBytes().toString(StandardCharsets.UTF_8)
        return body.split('&')
            .filter { it.isNotEmpty() }
            .associate { part ->
                URLDecoder.decode(part.substringBefore('='), StandardCharsets.UTF_8) to
                    URLDecoder.decode(part.substringAfter('=', ""), StandardCharsets.UTF_8)
            }
    }

    override fun close() {
        server.stop(0)
    }

    companion object {
        const val CLIENT_ID = "ingot-client"
        const val CLIENT_SECRET = "client-secret-value"
    }

}
