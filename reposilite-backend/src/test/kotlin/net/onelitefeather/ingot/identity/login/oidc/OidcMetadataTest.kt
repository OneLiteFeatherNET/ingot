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

import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import net.onelitefeather.ingot.identity.login.MutableClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.Instant

internal class OidcMetadataTest {

    private val clock = MutableClock(Instant.parse("2026-10-15T10:00:00Z"))
    private lateinit var provider: OidcTestIdentityProvider
    private lateinit var metadata: OidcMetadata

    @BeforeEach
    fun setUp() {
        provider = OidcTestIdentityProvider(clock).start()
        metadata = OidcMetadata(JdkHttpFetcher(), clock)
    }

    @AfterEach
    fun tearDown() {
        provider.close()
    }

    @Nested
    inner class Discovery {

        @Test
        fun `a second discovery within the hour is served from the cache`() {
            // when: discovery is read twice within the time-to-live
            val first = metadata.discovery(provider.issuer)
            clock.advance(Duration.ofMinutes(59))
            val second = metadata.discovery(provider.issuer)

            // then: one request reached the identity provider and both answers are the same
            assertThat(provider.discoveryRequests.get()).describedAs("discovery requests").isEqualTo(1)
            assertThat(second).isEqualTo(first)
        }

        @Test
        fun `discovery is requested again once the hour is over`() {
            metadata.discovery(provider.issuer)

            clock.advance(Duration.ofHours(1))
            metadata.discovery(provider.issuer)

            assertThat(provider.discoveryRequests.get()).describedAs("discovery requests").isEqualTo(2)
        }

        @Test
        fun `the endpoints of the document are read from it`() {
            val endpoints = metadata.discovery(provider.issuer)

            assertThat(endpoints.issuer).isEqualTo(provider.issuer)
            assertThat(endpoints.token).isEqualTo("${provider.issuer}/token")
            assertThat(endpoints.jwksUri).isEqualTo("${provider.issuer}/jwks")
        }

        @Test
        fun `a document whose issuer differs from the configured one is refused`() {
            provider.discoveryIssuer = "https://someone-else.example.org"

            assertThrows<OidcUnavailableException> { metadata.discovery(provider.issuer) }
        }

        @Test
        fun `a plain http issuer that is not loopback is refused without a request`() {
            assertThrows<OidcUnavailableException> { metadata.discovery("http://idp.example.org") }
            assertThat(provider.discoveryRequests.get()).isZero()
        }
    }

    @Nested
    inner class Jwks {

        @Test
        fun `the jwks is cached for the hour`() {
            val endpoints = metadata.discovery(provider.issuer)

            metadata.jwks(endpoints)
            clock.advance(Duration.ofMinutes(30))
            metadata.jwks(endpoints)

            assertThat(provider.jwksRequests.get()).describedAs("jwks requests").isEqualTo(1)
        }

        @Test
        fun `an unknown key is looked up again at most once a minute`() {
            val endpoints = metadata.discovery(provider.issuer)
            metadata.jwks(endpoints)

            // when: a refetch is asked for within the first minute
            assertThat(metadata.refetchJwks(endpoints)).describedAs("refetch too early").isNull()
            assertThat(provider.jwksRequests.get()).isEqualTo(1)

            // when: the provider rotated its key and a minute has passed
            provider.jwksKeys = listOf(RSAKeyGenerator(2048).keyID("rotated").generate().toPublicJWK())
            clock.advance(Duration.ofMinutes(1))
            val refreshed = metadata.refetchJwks(endpoints)

            // then
            assertThat(refreshed?.getKeyByKeyId("rotated")).describedAs("rotated key").isNotNull
            assertThat(provider.jwksRequests.get()).isEqualTo(2)
        }
    }
}
