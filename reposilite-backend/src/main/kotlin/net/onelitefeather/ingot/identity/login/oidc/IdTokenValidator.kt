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

import com.nimbusds.jose.JOSEException
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.factories.DefaultJWSVerifierFactory
import com.nimbusds.jose.jwk.AsymmetricJWK
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.security.Key
import java.text.ParseException
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** The outcome of checking an ID token. */
sealed interface IdTokenCheck {

    data class Valid(val claims: JWTClaimsSet) : IdTokenCheck

    /** The token is refused. [reason] names the failed check and never contains the token. */
    data class Invalid(val reason: String) : IdTokenCheck

    /**
     * The token names a key id that the key set does not hold. The caller may fetch the key set again and check once more.
     */
    data object UnknownKey : IdTokenCheck

}

/**
 * Checks the ID token of an OpenID Connect login (OIDC Core 3.1.3.7). The signature must verify with a public key of the
 * provider. HMAC and unsecured tokens are refused, because a token signed with the client secret must never be accepted as
 * if the provider had signed it. Expiry and not-before are judged on the injected clock with a skew of [skew].
 */
internal class IdTokenValidator(
    private val clock: Clock,
    private val skew: Duration = Duration.ofSeconds(60),
) {

    fun validate(token: String, keys: JWKSet, issuer: String, clientId: String, nonce: String): IdTokenCheck {
        val jwt = try {
            SignedJWT.parse(token)
        } catch (_: ParseException) {
            return IdTokenCheck.Invalid("the id token is not a signed JWT")
        }
        val algorithm = jwt.header.algorithm
        if (algorithm == null || algorithm !in ASYMMETRIC_ALGORITHMS) {
            return IdTokenCheck.Invalid("the id token algorithm $algorithm is not accepted")
        }
        val key = when (val lookup = keyFor(jwt, keys)) {
            is KeyLookup.Found -> lookup.key
            KeyLookup.Unknown -> return IdTokenCheck.UnknownKey
            KeyLookup.Ambiguous -> return IdTokenCheck.Invalid("the id token names no key and the key set holds several")
        }
        if (!signatureVerifies(jwt, key)) {
            return IdTokenCheck.Invalid("the id token signature does not verify")
        }
        return checkClaims(jwt.jwtClaimsSet, issuer, clientId, nonce)
    }

    private sealed interface KeyLookup {
        data class Found(val key: Key) : KeyLookup
        data object Unknown : KeyLookup
        data object Ambiguous : KeyLookup
    }

    private fun keyFor(jwt: SignedJWT, keys: JWKSet): KeyLookup {
        val kid = jwt.header.keyID
        val candidates = if (kid != null) {
            listOfNotNull(keys.getKeyByKeyId(kid))
        } else {
            keys.keys.toList()
        }
        if (kid != null && candidates.isEmpty()) return KeyLookup.Unknown
        if (kid == null && candidates.size != 1) return KeyLookup.Ambiguous
        val jwk = candidates.first()
        if (jwk !is AsymmetricJWK) return KeyLookup.Unknown
        return try {
            KeyLookup.Found(jwk.toPublicKey())
        } catch (_: JOSEException) {
            KeyLookup.Unknown
        }
    }

    private fun signatureVerifies(jwt: SignedJWT, key: Key): Boolean =
        try {
            jwt.verify(DefaultJWSVerifierFactory().createJWSVerifier(jwt.header, key))
        } catch (_: JOSEException) {
            false
        }

    private fun checkClaims(claims: JWTClaimsSet, issuer: String, clientId: String, nonce: String): IdTokenCheck {
        val now = Instant.now(clock)
        if (claims.issuer != issuer) {
            return IdTokenCheck.Invalid("the issuer of the id token is not the configured issuer")
        }
        val audiences = claims.audience.orEmpty()
        if (clientId !in audiences) {
            return IdTokenCheck.Invalid("the audience of the id token does not include this client")
        }
        if (audiences.size > 1 && claims.getStringClaim("azp") != clientId) {
            return IdTokenCheck.Invalid("the authorized party is not this client")
        }
        val expiresAt = claims.expirationTime?.toInstant() ?: return IdTokenCheck.Invalid("the id token has no expiry")
        if (!now.isBefore(expiresAt.plus(skew))) {
            return IdTokenCheck.Invalid("the id token is expired")
        }
        claims.issueTime?.toInstant()?.let { issued ->
            if (issued.isAfter(now.plus(skew))) return IdTokenCheck.Invalid("the id token was issued in the future")
        }
        claims.notBeforeTime?.toInstant()?.let { notBefore ->
            if (now.plus(skew).isBefore(notBefore)) return IdTokenCheck.Invalid("the id token is not valid yet")
        }
        if (claims.subject.isNullOrBlank()) {
            return IdTokenCheck.Invalid("the id token has no subject")
        }
        if (claims.getStringClaim("nonce") != nonce) {
            return IdTokenCheck.Invalid("the nonce of the id token is not the nonce of this login")
        }
        return IdTokenCheck.Valid(claims)
    }

    companion object {
        /** The asymmetric algorithms an identity provider may sign ID tokens with. */
        val ASYMMETRIC_ALGORITHMS: Set<JWSAlgorithm> = setOf(
            JWSAlgorithm.RS256, JWSAlgorithm.RS384, JWSAlgorithm.RS512,
            JWSAlgorithm.PS256, JWSAlgorithm.PS384, JWSAlgorithm.PS512,
            JWSAlgorithm.ES256, JWSAlgorithm.ES384, JWSAlgorithm.ES512,
        )
    }

}
