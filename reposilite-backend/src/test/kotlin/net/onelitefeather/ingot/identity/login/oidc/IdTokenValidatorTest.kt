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
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import net.onelitefeather.ingot.identity.login.MutableClock
import net.onelitefeather.ingot.identity.login.oidc.IdTokenCheck.Invalid
import net.onelitefeather.ingot.identity.login.oidc.IdTokenCheck.UnknownKey
import net.onelitefeather.ingot.identity.login.oidc.IdTokenCheck.Valid
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.Date

internal class IdTokenValidatorTest {

    private val now = Instant.parse("2026-10-15T10:00:00Z")
    private val clock = MutableClock(now)
    private val validator = IdTokenValidator(clock)
    private val issuer = "https://idp.example.org"
    private val nonce = "expected-nonce"

    private lateinit var keys: OidcTestKeys

    @BeforeEach
    fun setUp() {
        keys = OidcTestKeys(RSAKeyGenerator(2048).keyID("active").generate())
    }

    private fun claims(
        subject: String? = "subject-1",
        audience: List<String> = listOf(CLIENT_ID),
        expiresAt: Instant = now.plus(Duration.ofMinutes(5)),
        issuedAt: Instant = now,
        notBefore: Instant? = null,
        nonce: String? = this.nonce,
        azp: String? = null,
        issuer: String = this.issuer,
    ): JWTClaimsSet {
        val builder = JWTClaimsSet.Builder()
            .issuer(issuer)
            .audience(audience)
            .subject(subject)
            .expirationTime(Date.from(expiresAt))
            .issueTime(Date.from(issuedAt))
            .claim("nonce", nonce)
        notBefore?.let { builder.notBeforeTime(Date.from(it)) }
        azp?.let { builder.claim("azp", it) }
        return builder.build()
    }

    private fun validate(token: String, keySet: JWKSet = keys.publicSet): IdTokenCheck =
        validator.validate(token, keySet, issuer = issuer, clientId = CLIENT_ID, nonce = nonce)

    @Test
    fun `a token signed by a key of the jwks with matching claims is valid`() {
        val check = validate(keys.sign(claims()))

        assertThat(check).isInstanceOf(Valid::class.java)
        assertThat((check as Valid).claims.subject).isEqualTo("subject-1")
    }

    @Test
    fun `a token signed by a key that is not in the jwks is refused`() {
        val stranger = RSAKeyGenerator(2048).keyID("active").generate()

        val check = validate(keys.signWith(stranger, claims()))

        assertThat(check).describedAs("same kid, other key").isInstanceOf(Invalid::class.java)
    }

    @Test
    fun `an unknown key id asks for a refetch of the jwks`() {
        val rotated = RSAKeyGenerator(2048).keyID("rotated").generate()

        val check = validate(OidcTestKeys(rotated).sign(claims()))

        assertThat(check).isEqualTo(UnknownKey)
    }

    @Test
    fun `an hmac token is refused even when it is signed with a shared secret`() {
        val header = com.nimbusds.jose.JWSHeader(JWSAlgorithm.HS256)
        val token = com.nimbusds.jwt.SignedJWT(header, claims())
            .also { it.sign(com.nimbusds.jose.crypto.MACSigner(ByteArray(32) { 1 })) }
            .serialize()

        assertThat(validate(token)).describedAs("HS256").isInstanceOf(Invalid::class.java)
    }

    @Test
    fun `an unsecured token is refused`() {
        val unsigned = com.nimbusds.jwt.PlainJWT(claims()).serialize()

        assertThat(validate(unsigned)).describedAs("alg none").isInstanceOf(Invalid::class.java)
    }

    @Test
    fun `a token from another issuer is refused`() {
        val check = validate(keys.sign(claims(issuer = "https://evil.example.org")))

        assertThat(check).isInstanceOf(Invalid::class.java)
        assertThat((check as Invalid).reason).contains("issuer")
    }

    @Test
    fun `a token for another audience is refused`() {
        val check = validate(keys.sign(claims(audience = listOf("someone-else"))))

        assertThat(check).isInstanceOf(Invalid::class.java)
        assertThat((check as Invalid).reason).contains("audience")
    }

    @Test
    fun `several audiences need the authorized party to be this client`() {
        val withoutAzp = validate(keys.sign(claims(audience = listOf(CLIENT_ID, "other"))))
        val withAzp = validate(keys.sign(claims(audience = listOf(CLIENT_ID, "other"), azp = CLIENT_ID)))

        assertThat(withoutAzp).describedAs("no azp").isInstanceOf(Invalid::class.java)
        assertThat(withAzp).describedAs("azp is this client").isInstanceOf(Valid::class.java)
    }

    @Test
    fun `a token that expired 59 seconds ago is still accepted within the clock skew`() {
        val token = keys.sign(claims(expiresAt = now.minus(Duration.ofSeconds(59)), issuedAt = now.minus(Duration.ofMinutes(10))))

        assertThat(validate(token)).isInstanceOf(Valid::class.java)
    }

    @Test
    fun `a token that expired 61 seconds ago is refused`() {
        val token = keys.sign(claims(expiresAt = now.minus(Duration.ofSeconds(61)), issuedAt = now.minus(Duration.ofMinutes(10))))

        val check = validate(token)

        assertThat(check).isInstanceOf(Invalid::class.java)
        assertThat((check as Invalid).reason).contains("expired")
    }

    @Test
    fun `the expiry is judged on the injected clock, not on the system clock`() {
        val token = keys.sign(claims(expiresAt = now.plus(Duration.ofMinutes(5))))

        clock.advance(Duration.ofMinutes(6))

        assertThat(validate(token)).isInstanceOf(Invalid::class.java)
    }

    @Test
    fun `a token issued more than the skew in the future is refused`() {
        val check = validate(keys.sign(claims(issuedAt = now.plus(Duration.ofMinutes(2)))))

        assertThat(check).isInstanceOf(Invalid::class.java)
    }

    @Test
    fun `a token that is not yet valid is refused`() {
        val check = validate(keys.sign(claims(notBefore = now.plus(Duration.ofMinutes(5)))))

        assertThat(check).isInstanceOf(Invalid::class.java)
    }

    @Test
    fun `a token without the nonce of the login is refused`() {
        val check = validate(keys.sign(claims(nonce = "replayed-nonce")))

        assertThat(check).isInstanceOf(Invalid::class.java)
        assertThat((check as Invalid).reason).contains("nonce")
    }

    @Test
    fun `a token without a subject is refused`() {
        assertThat(validate(keys.sign(claims(subject = null)))).isInstanceOf(Invalid::class.java)
    }

    @Test
    fun `a token without a key id is accepted only when the jwks holds exactly one key`() {
        val unlabelled = com.nimbusds.jose.JWSHeader.Builder(JWSAlgorithm.RS256).build()
        val token = com.nimbusds.jwt.SignedJWT(unlabelled, claims())
            .also { it.sign(com.nimbusds.jose.crypto.RSASSASigner(keys.key)) }
            .serialize()
        val twoKeys = JWKSet(listOf(keys.key.toPublicJWK(), RSAKeyGenerator(2048).keyID("other").generate().toPublicJWK()))

        assertThat(validate(token, keys.publicSet)).describedAs("one key").isInstanceOf(Valid::class.java)
        assertThat(validate(token, twoKeys)).describedAs("two keys, none named").isInstanceOf(Invalid::class.java)
    }

    private companion object {
        const val CLIENT_ID = "ingot-client"
    }
}
