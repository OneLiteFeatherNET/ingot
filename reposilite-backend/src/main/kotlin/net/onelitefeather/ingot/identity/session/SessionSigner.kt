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

package net.onelitefeather.ingot.identity.session

import com.nimbusds.jose.JOSEException
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.MACVerifier
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.text.ParseException
import java.time.Clock
import java.time.Instant
import java.util.Date

/**
 * The claims of a browser session that passed verification. [authenticatedAt] is the last authentication of the family (`ath`),
 * `null` for a session issued before that claim existed, which is never fresh. [mfa] is true once a second factor completed the
 * family.
 */
data class VerifiedSession(
    val principalId: Int,
    val name: String,
    val kind: String,
    val familyId: String,
    val expiresAt: Instant,
    val authenticatedAt: Instant? = null,
    val mfa: Boolean = false,
)

/**
 * Signs and verifies the short-lived browser session tokens (JWT, HS256). The key is shared by every instance of a
 * database, so a session issued by one instance is accepted by all of them. Verification accepts HS256 only, and checks
 * the expiry against the injected clock: a token is over at the instant of its expiry, not one second later.
 */
class SessionSigner internal constructor(
    private val key: ByteArray,
    private val clock: Clock,
) {

    fun issue(
        principalId: Int,
        name: String,
        kind: String,
        familyId: String,
        expiresAt: Instant,
        authenticatedAt: Instant? = null,
        mfa: Boolean = false,
    ): String {
        val claims = JWTClaimsSet.Builder()
            .subject(principalId.toString())
            .claim(CLAIM_NAME, name)
            .claim(CLAIM_KIND, kind)
            .claim(CLAIM_FAMILY, familyId)
            .claim(CLAIM_SCOPE, SCOPE_FULL)
            .claim(CLAIM_MFA, mfa)
            .apply { authenticatedAt?.let { claim(CLAIM_AUTH_TIME, it.epochSecond) } }
            .issueTime(Date.from(Instant.now(clock)))
            .expirationTime(Date.from(expiresAt))
            .build()
        val jwt = SignedJWT(JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(), claims)
        jwt.sign(MACSigner(key))
        return jwt.serialize()
    }

    /** `null` for anything that is not a valid, unexpired session signed with this key. Never throws. */
    fun verify(token: String): VerifiedSession? =
        try {
            verifyOrThrow(token)
        } catch (_: ParseException) {
            null
        } catch (_: JOSEException) {
            null
        }

    private fun verifyOrThrow(token: String): VerifiedSession? {
        val jwt = SignedJWT.parse(token)
        if (jwt.header.algorithm != JWSAlgorithm.HS256 || !jwt.verify(MACVerifier(key))) {
            return null
        }
        val claims = jwt.jwtClaimsSet
        val expiresAt = claims.expirationTime?.toInstant() ?: return null
        if (!Instant.now(clock).isBefore(expiresAt)) {
            return null
        }
        return VerifiedSession(
            principalId = claims.subject?.toIntOrNull() ?: return null,
            name = claims.getStringClaim(CLAIM_NAME) ?: return null,
            kind = claims.getStringClaim(CLAIM_KIND) ?: return null,
            familyId = claims.getStringClaim(CLAIM_FAMILY) ?: return null,
            expiresAt = expiresAt,
            authenticatedAt = claims.getLongClaim(CLAIM_AUTH_TIME)?.let { Instant.ofEpochSecond(it) },
            mfa = claims.getBooleanClaim(CLAIM_MFA) ?: false,
        )
    }

    companion object {

        /** Name of the configuration entry that holds the session signing key, shared like the ETag key. */
        const val KEY_CONFIGURATION_NAME = "jwt-signing-key"

        private const val CLAIM_NAME = "name"
        private const val CLAIM_KIND = "kind"
        private const val CLAIM_FAMILY = "sid"
        private const val CLAIM_AUTH_TIME = "ath"
        private const val CLAIM_MFA = "mfa"
        private const val CLAIM_SCOPE = "scope"

        /** The scope of a session that may use the whole API. The restricted scope comes with the enrolment policy. */
        private const val SCOPE_FULL = "full"

    }

}
