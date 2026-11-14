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

package net.onelitefeather.ingot.plugin.store

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The keys and signatures are GnuPG output made with a faked creation time of 2026-01-01. The test keys are trusted by no
 * production configuration. The clock is fixed, so the expiry of the test key is the same at every run.
 */
internal class PluginSignatureVerifierTest {

    private val now: Clock = Clock.fixed(Instant.parse("2026-11-14T00:00:00Z"), ZoneOffset.UTC)

    private val content: ByteArray = StoreFixtures.resource("sample-plugin.bin")

    @Test
    fun `should verify content signed by a trusted key and report the key fingerprint`() {
        // given: a verifier that trusts the test key
        val verifier = PluginSignatureVerifier(PluginSignatureVerifier.readKeyRings(StoreFixtures.resource("trusted-public.asc")), now)

        // when: the content is checked against its signature
        val outcome = verifier.verify(content, StoreFixtures.resource("sample-plugin.trusted.asc"))

        // then: the content is verified and the fingerprint of the signing key is reported
        assertThat(outcome).isInstanceOf(SignatureOutcome.Verified::class.java)
        assertThat((outcome as SignatureOutcome.Verified).fingerprint)
            .describedAs("the fingerprint must be the one of the trusted key")
            .isEqualTo(StoreFixtures.fingerprint("trusted-public.asc"))
    }

    @Test
    fun `should refuse content that differs from the content that was signed`() {
        // given: a verifier that trusts the test key and content with one byte changed
        val verifier = PluginSignatureVerifier(PluginSignatureVerifier.readKeyRings(StoreFixtures.resource("trusted-public.asc")), now)
        val altered = content.copyOf().also { it[0] = (it[0] + 1).toByte() }

        // when: the altered content is checked against the signature
        val outcome = verifier.verify(altered, StoreFixtures.resource("sample-plugin.trusted.asc"))

        // then: the signature step refuses it
        assertThat(outcome).isInstanceOf(SignatureOutcome.Refused::class.java)
        assertThat((outcome as SignatureOutcome.Refused).reason)
            .describedAs("the reason must say that the signature does not match the content")
            .contains("does not match")
    }

    @Test
    fun `should refuse a signature made by a key that is not trusted`() {
        // given: a verifier that trusts only the test key, and a signature by another key
        val verifier = PluginSignatureVerifier(PluginSignatureVerifier.readKeyRings(StoreFixtures.resource("trusted-public.asc")), now)

        // when: the content is checked against the signature of the untrusted key
        val outcome = verifier.verify(content, StoreFixtures.resource("sample-plugin.untrusted.asc"))

        // then: the signature step refuses it and names the trust problem
        assertThat(outcome).isInstanceOf(SignatureOutcome.Refused::class.java)
        assertThat((outcome as SignatureOutcome.Refused).reason)
            .describedAs("a signature by an untrusted key must be refused as not trusted")
            .contains("not trusted")
    }

    @Test
    fun `should refuse a signature whose key validity has ended`() {
        // given: a verifier that trusts the expired test key, which lived for one day from 2026-01-01
        val verifier = PluginSignatureVerifier(PluginSignatureVerifier.readKeyRings(StoreFixtures.resource("expired-public.asc")), now)

        // when: the content is checked against a signature by that key
        val outcome = verifier.verify(content, StoreFixtures.resource("sample-plugin.expired.asc"))

        // then: the signature is refused and the reason says the key expired
        assertThat(outcome).isInstanceOf(SignatureOutcome.Refused::class.java)
        assertThat((outcome as SignatureOutcome.Refused).reason)
            .describedAs("a key past its validity must be refused as expired")
            .contains("expired")
    }

    @Test
    fun `should refuse a file that holds no OpenPGP signature`() {
        // given: a verifier that trusts the test key
        val verifier = PluginSignatureVerifier(PluginSignatureVerifier.readKeyRings(StoreFixtures.resource("trusted-public.asc")), now)

        // when: an HTML page is checked as if it were a signature
        val outcome = verifier.verify(content, "<html>not a signature</html>".toByteArray())

        // then: the file is refused as not a signature
        assertThat(outcome).isInstanceOf(SignatureOutcome.Refused::class.java)
        assertThat((outcome as SignatureOutcome.Refused).reason)
            .describedAs("the reason must say that the file is not an OpenPGP signature")
            .contains("not an OpenPGP signature")
    }

    @Test
    fun `should match a digest case-insensitively and refuse any other digest`() {
        // given: the SHA-256 of the content in lower case and in upper case
        val digest = PluginDigest.sha256Hex(content)

        // when: the digests are compared
        // then: only the same value matches, in either case, and a changed value does not
        assertThat(PluginDigest.matches(content, digest.uppercase())).describedAs("the case of a digest must not matter").isTrue()
        assertThat(PluginDigest.matches(content, "0".repeat(64))).describedAs("a different digest must be refused").isFalse()
    }

}

/** The fixtures of the store tests, read from the test resources of this package. */
internal object StoreFixtures {

    fun resource(name: String): ByteArray =
        requireNotNull(StoreFixtures::class.java.getResourceAsStream("/net/onelitefeather/ingot/plugin/store/$name")) {
            "Missing test fixture $name"
        }.use { it.readBytes() }

    fun fingerprint(keyFile: String): String =
        PluginSignatureVerifier.readKeyRings(resource(keyFile)).single().publicKey.fingerprint.joinToString("") { "%02X".format(it) }

}
