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

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT

/** An RSA key generated for one test, with the JWKS that publishes its public half. */
internal class OidcTestKeys(val key: RSAKey) {

    val publicSet: JWKSet
        get() = JWKSet(listOf(key.toPublicJWK()))

    /** An RS256 token with the key id of [key], signed by [key]. */
    fun sign(claims: JWTClaimsSet): String =
        signWith(key, claims)

    /** An RS256 token that names the key id of [key] but is signed by [signer]. */
    fun signWith(signer: RSAKey, claims: JWTClaimsSet): String {
        val header = JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.keyID).build()
        return SignedJWT(header, claims).also { it.sign(RSASSASigner(signer)) }.serialize()
    }

}
