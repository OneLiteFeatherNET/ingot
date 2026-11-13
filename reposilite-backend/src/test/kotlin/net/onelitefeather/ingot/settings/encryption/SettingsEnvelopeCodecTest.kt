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

package net.onelitefeather.ingot.settings.encryption

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Base64

/**
 * The envelope codec is a pure function of its key and its nonce source, so every test builds its own codec with a fixed key and a
 * fixed nonce. No test reads the clock, the environment or a random source.
 */
internal class SettingsEnvelopeCodecTest {

    private val key = ByteArray(32) { (it * 7 + 1).toByte() }
    private val otherKey = ByteArray(32) { (it * 13 + 5).toByte() }
    private val nonce = ByteArray(12) { 0x2a }

    private fun codec(key: ByteArray = this.key, nonce: ByteArray = this.nonce) =
        SettingsEnvelopeCodec(key = key, nonces = FixedNonceSource(nonce))

    private val path = "s3.secretKey"

    @Test
    fun `a sealed value opens to the value it was sealed from`() {
        // given: a codec with a fixed key
        val codec = codec()

        // when: a value is sealed and opened under the same domain and path
        val envelope = codec.seal(domain = "maven", path = path, plain = "s3cret")

        // then: the original value comes back
        assertThat(codec.open(domain = "maven", path = path, envelope = envelope))
            .describedAs("opened value")
            .isEqualTo("s3cret")
    }

    @Test
    fun `a sealed value starts with the version prefix and the eight character key identifier`() {
        // given: a codec with a fixed key
        val codec = codec()

        // when: a value is sealed
        val envelope = codec.seal(domain = "maven", path = path, plain = "s3cret")

        // then: the envelope names the format, the key identifier and holds a base64 body
        val parts = envelope.split(":")
        assertThat(parts).describedAs("envelope parts").hasSize(4)
        assertThat(parts[0] + ":" + parts[1]).describedAs("format and version").isEqualTo("enc:v1")
        assertThat(parts[2]).describedAs("key identifier").isEqualTo(codec.keyId).hasSize(8).matches("[0-9a-f]{8}")
        assertThat(Base64.getDecoder().decode(parts[3]).size)
            .describedAs("body holds nonce, ciphertext and tag")
            .isEqualTo(12 + "s3cret".length + 16)
    }

    @Test
    fun `the same value with the same nonce seals to the same envelope`() {
        // given: two codecs with the same key and the same fixed nonce
        val first = codec().seal(domain = "maven", path = path, plain = "s3cret")
        val second = codec().seal(domain = "maven", path = path, plain = "s3cret")

        // then: the envelopes are identical, so the nonce is the only source of variation
        assertThat(second).describedAs("second envelope").isEqualTo(first)
    }

    @Test
    fun `a different key does not open the envelope and the failure holds no key bytes`() {
        // given: a value sealed with one key
        val envelope = codec(key = key).seal(domain = "maven", path = path, plain = "s3cret")
        val foreign = codec(key = otherKey)

        // when: the envelope is opened with another key
        val failure = assertThrows<SettingsEnvelopeException> {
            foreign.open(domain = "maven", path = path, envelope = envelope)
        }

        // then: the failure names the key identifier and carries no part of either key
        assertThat(failure.message).describedAs("failure message").contains(codec(key = key).keyId)
        assertThat(failure.message).describedAs("failure message").doesNotContain(Base64.getEncoder().encodeToString(key))
        assertThat(failure.message).describedAs("failure message").doesNotContain(Base64.getEncoder().encodeToString(otherKey))
        assertThat(failure.message).describedAs("failure message").doesNotContain(envelope)
    }

    @Test
    fun `one changed byte of the body fails to open`() {
        // given: an envelope whose body has one character replaced
        val codec = codec()
        val envelope = codec.seal(domain = "maven", path = path, plain = "s3cret")
        val prefix = envelope.substringBeforeLast(':') + ":"
        val body = Base64.getDecoder().decode(envelope.substringAfterLast(':'))
        body[body.size / 2] = (body[body.size / 2].toInt() xor 0x01).toByte()
        val tampered = prefix + Base64.getEncoder().encodeToString(body)

        // when / then: opening fails
        assertThrows<SettingsEnvelopeException> {
            codec.open(domain = "maven", path = path, envelope = tampered)
        }
    }

    @Test
    fun `an envelope opened under another domain fails`() {
        // given: an envelope sealed for the maven domain
        val codec = codec()
        val envelope = codec.seal(domain = "maven", path = path, plain = "s3cret")

        // when / then: the same envelope under the login domain fails
        assertThrows<SettingsEnvelopeException> {
            codec.open(domain = "login", path = path, envelope = envelope)
        }
    }

    @Test
    fun `an envelope opened under another property path fails`() {
        // given: an envelope sealed for the s3 secret key
        val codec = codec()
        val envelope = codec.seal(domain = "maven", path = path, plain = "s3cret")

        // when / then: the same envelope under the access key property fails
        assertThrows<SettingsEnvelopeException> {
            codec.open(domain = "maven", path = "s3.accessKey", envelope = envelope)
        }
    }

    @Test
    fun `an envelope of a list entry opens under the path of its entry key and not under another entry`() {
        // given: an envelope sealed for the password of the entry named alpha
        val codec = codec()
        val envelope = codec.seal(domain = "maven", path = "mirrors[alpha].password", plain = "mirror-pass")

        // when / then: the entry named alpha opens it, the entry named beta does not
        assertThat(codec.open(domain = "maven", path = "mirrors[alpha].password", envelope = envelope))
            .describedAs("alpha entry")
            .isEqualTo("mirror-pass")
        assertThrows<SettingsEnvelopeException> {
            codec.open(domain = "maven", path = "mirrors[beta].password", envelope = envelope)
        }
    }

    @Test
    fun `an envelope with an unknown version is refused and the failure names the path`() {
        // given: a value that claims a version this build does not know
        val codec = codec()
        val future = "enc:v2:${codec.keyId}:" + Base64.getEncoder().encodeToString(ByteArray(40))

        // when / then: the codec refuses it and names the path, never treating the text as a plain value
        val failure = assertThrows<SettingsEnvelopeException> {
            codec.open(domain = "maven", path = path, envelope = future)
        }
        assertThat(failure.message).describedAs("failure message").contains(path).contains("v2")
    }

    @Test
    fun `the key identifier is the same for the same key and differs for another key`() {
        assertThat(codec(key = key).keyId).describedAs("same key").isEqualTo(codec(key = key).keyId)
        assertThat(codec(key = otherKey).keyId).describedAs("other key").isNotEqualTo(codec(key = key).keyId)
    }

    @Test
    fun `a key that is not 32 bytes is refused`() {
        assertThrows<IllegalArgumentException> {
            codec(key = ByteArray(16))
        }
    }

    private class FixedNonceSource(private val nonce: ByteArray) : NonceSource {
        override fun nextNonce(): ByteArray = nonce.copyOf()
    }

}
