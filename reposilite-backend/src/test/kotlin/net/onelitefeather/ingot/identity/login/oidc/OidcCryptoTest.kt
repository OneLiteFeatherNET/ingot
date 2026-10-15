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

import net.onelitefeather.ingot.identity.login.MutableClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant

internal class OidcCryptoTest {

    private val clock = MutableClock(Instant.parse("2026-10-15T10:00:00Z"))
    private val sessionKey = ByteArray(32) { it.toByte() }

    private val state = AuthorizationState(
        provider = "corp",
        state = "state-value",
        nonce = "nonce-value",
        verifier = "verifier-value",
        redirect = "/dashboard",
        expiresAt = clock.instant().plus(Duration.ofMinutes(10)),
    )

    @Test
    fun `the pkce challenge of the rfc 7636 example is its S256 hash`() {
        // when: the challenge is derived from the verifier of appendix B
        val challenge = Pkce.challengeOf("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")

        // then
        assertThat(challenge).isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
    }

    @Test
    fun `a generated verifier has 43 url safe characters and differs every time`() {
        // when
        val random = SecureRandom()
        val first = Pkce.newVerifier(random)
        val second = Pkce.newVerifier(random)

        // then
        assertThat(first).hasSize(43).matches("[A-Za-z0-9_-]+")
        assertThat(second).isNotEqualTo(first)
    }

    @Test
    fun `a sealed state opens for its provider within ten minutes`() {
        // given
        val cookie = OidcStateCookie(sessionKey, clock)

        // when
        val opened = cookie.open(cookie.seal(state), provider = "corp")

        // then
        assertThat(opened).isEqualTo(state)
    }

    @Test
    fun `a sealed state is refused for another provider`() {
        val cookie = OidcStateCookie(sessionKey, clock)

        assertThat(cookie.open(cookie.seal(state), provider = "other")).describedAs("another provider").isNull()
    }

    @Test
    fun `a state sealed with another key is refused`() {
        val sealed = OidcStateCookie(sessionKey, clock).seal(state)
        val otherKey = ByteArray(32) { 7 }

        assertThat(OidcStateCookie(otherKey, clock).open(sealed, provider = "corp")).describedAs("another key").isNull()
    }

    @Test
    fun `a tampered state is refused`() {
        val cookie = OidcStateCookie(sessionKey, clock)
        val parts = cookie.seal(state).split('.')
        val forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"prov":"corp","st":"x","non":"y","ver":"z","red":"/","exp":4102444800}""".toByteArray())
        val tampered = listOf(parts[0], forgedPayload, parts[2]).joinToString(".")

        assertThat(cookie.open(tampered, provider = "corp")).describedAs("a payload that was changed").isNull()
    }

    @Test
    fun `an unsigned token is refused`() {
        val unsigned = "eyJhbGciOiJub25lIn0.eyJwcm92IjoiY29ycCJ9."

        assertThat(OidcStateCookie(sessionKey, clock).open(unsigned, provider = "corp")).describedAs("unsigned").isNull()
    }

    @Test
    fun `a state is refused once its ten minutes are over`() {
        val cookie = OidcStateCookie(sessionKey, clock)
        val sealed = cookie.seal(state)

        clock.advance(Duration.ofMinutes(10))

        assertThat(cookie.open(sealed, provider = "corp")).describedAs("expired at the instant of expiry").isNull()
    }

    @Test
    fun `a missing state cookie is refused`() {
        assertThat(OidcStateCookie(sessionKey, clock).open(null, provider = "corp")).isNull()
    }
}
