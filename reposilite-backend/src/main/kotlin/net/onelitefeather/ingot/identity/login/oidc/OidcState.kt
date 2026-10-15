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
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.MACVerifier
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Date
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Proof Key for Code Exchange, RFC 7636, with the S256 method. */
object Pkce {

    private const val VERIFIER_BYTES = 32

    /** A verifier of 43 URL safe characters, 256 random bits. */
    fun newVerifier(random: SecureRandom = SecureRandom()): String =
        ByteArray(VERIFIER_BYTES).also { random.nextBytes(it) }.let(::base64Url)

    /** `BASE64URL(SHA256(verifier))`, the challenge sent with the authorization request. */
    fun challengeOf(verifier: String): String =
        base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    private fun base64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

}

/**
 * What a login start remembers until the callback: the state and nonce it sent, the PKCE verifier, the provider, and the
 * path to return to. None of it is stored on the server.
 */
data class AuthorizationState(
    val provider: String,
    val state: String,
    val nonce: String,
    val verifier: String,
    val redirect: String,
    val expiresAt: Instant,
)

/**
 * The `ingot_oidc` cookie: an [AuthorizationState] sealed as a JWT signed with HMAC-SHA256. The key is derived from the
 * session signing key, so no second secret has to be stored. A sealed state is accepted for ten minutes and for the provider
 * it was sealed for, and nothing else. Verification uses the injected clock.
 */
class OidcStateCookie internal constructor(sessionKey: ByteArray, private val clock: Clock) {

    private val key: ByteArray = Mac.getInstance("HmacSHA256")
        .apply { init(SecretKeySpec(sessionKey, "HmacSHA256")) }
        .doFinal(LABEL.toByteArray(Charsets.UTF_8))

    fun seal(state: AuthorizationState): String {
        val claims = JWTClaimsSet.Builder()
            .claim(CLAIM_PURPOSE, PURPOSE)
            .claim(CLAIM_PROVIDER, state.provider)
            .claim(CLAIM_STATE, state.state)
            .claim(CLAIM_NONCE, state.nonce)
            .claim(CLAIM_VERIFIER, state.verifier)
            .claim(CLAIM_REDIRECT, state.redirect)
            .expirationTime(Date.from(state.expiresAt))
            .build()
        val jwt = SignedJWT(JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(), claims)
        jwt.sign(MACSigner(key))
        return jwt.serialize()
    }

    /** The state of a valid, unexpired cookie for [provider], or `null` for anything else. Never throws. */
    fun open(sealed: String?, provider: String): AuthorizationState? {
        if (sealed.isNullOrBlank()) return null
        return try {
            val jwt = SignedJWT.parse(sealed)
            if (jwt.header.algorithm != JWSAlgorithm.HS256 || !jwt.verify(MACVerifier(key))) return null
            val claims = jwt.jwtClaimsSet
            val expiresAt = claims.expirationTime?.toInstant() ?: return null
            if (!Instant.now(clock).isBefore(expiresAt)) return null
            if (claims.getStringClaim(CLAIM_PURPOSE) != PURPOSE || claims.getStringClaim(CLAIM_PROVIDER) != provider) return null
            AuthorizationState(
                provider = provider,
                state = claims.getStringClaim(CLAIM_STATE) ?: return null,
                nonce = claims.getStringClaim(CLAIM_NONCE) ?: return null,
                verifier = claims.getStringClaim(CLAIM_VERIFIER) ?: return null,
                redirect = claims.getStringClaim(CLAIM_REDIRECT) ?: return null,
                expiresAt = expiresAt,
            )
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val COOKIE = "ingot_oidc"
        val LIFETIME: Duration = Duration.ofMinutes(10)

        private const val LABEL = "ingot-oidc-state"
        private const val PURPOSE = "oidc-state"
        private const val CLAIM_PURPOSE = "purpose"
        private const val CLAIM_PROVIDER = "prov"
        private const val CLAIM_STATE = "st"
        private const val CLAIM_NONCE = "non"
        private const val CLAIM_VERIFIER = "ver"
        private const val CLAIM_REDIRECT = "red"
    }

}
