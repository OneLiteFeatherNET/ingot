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

import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Supplies the nonce of one seal. The production source is random; tests inject a fixed one.
 */
fun interface NonceSource {
    fun nextNonce(): ByteArray
}

/**
 * The nonce source of the running instance: a fresh random 96 bit nonce for every seal.
 */
class SecureNonceSource : NonceSource {

    private val random = SecureRandom()

    override fun nextNonce(): ByteArray =
        ByteArray(SettingsEnvelopeCodec.NONCE_LENGTH).also { random.nextBytes(it) }

}

/**
 * A failure to open or to read an envelope. The message names the path of the value and never the value, the envelope or a key.
 */
class SettingsEnvelopeException(message: String) : IllegalArgumentException(message)

/**
 * Seals and opens the secret values of shared settings as `enc:v1:<kid>:<base64(nonce || ciphertext || tag)>`.
 *
 * AES-256-GCM binds every value to its domain and property path through the additional authenticated data, so an envelope moved to
 * another field does not open. The key identifier is public: it names the key that sealed the value, and it is not a secret.
 *
 * The codec holds no global state. Its key and its nonce source come in through the constructor.
 */
class SettingsEnvelopeCodec(
    key: ByteArray,
    private val nonces: NonceSource = SecureNonceSource(),
) {

    private val secret: SecretKeySpec

    /** Eight lower case hex characters, the first four bytes of an HMAC over a fixed label. */
    val keyId: String

    /** Base64 of an HMAC over a second fixed label. Stored once per key, so a start can tell two keys apart without a value. */
    val check: String

    init {
        require(key.size == KEY_LENGTH) { "A settings key must be $KEY_LENGTH bytes long, found ${key.size}" }
        secret = SecretKeySpec(key, "AES")
        keyId = hmac(key, KEY_ID_LABEL).copyOf(4).joinToString(separator = "") { "%02x".format(it) }
        check = Base64.getEncoder().encodeToString(hmac(key, CHECK_LABEL))
    }

    fun seal(domain: String, path: String, plain: String): String {
        val nonce = nonces.nextNonce()
        require(nonce.size == NONCE_LENGTH) { "A settings nonce must be $NONCE_LENGTH bytes long" }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secret, GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(additionalData(domain, path))
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))

        val body = Base64.getEncoder().encodeToString(nonce + sealed)
        return "$PREFIX$VERSION:$keyId:$body"
    }

    /**
     * @throws SettingsEnvelopeException when the value is not an envelope of this format, names another key, or was changed or moved
     */
    fun open(domain: String, path: String, envelope: String): String {
        val parts = envelope.split(':', limit = 4)
        if (parts.size != 4 || parts[0] + ":" != PREFIX) {
            throw SettingsEnvelopeException("The value of '$path' is not a settings envelope")
        }
        if (parts[1] != VERSION) {
            throw SettingsEnvelopeException("The value of '$path' uses the unknown envelope version '${parts[1]}', this build reads '$VERSION'")
        }
        if (parts[2] != keyId) {
            throw SettingsEnvelopeException("The value of '$path' was sealed with the settings key id '${parts[2]}', not with the configured key")
        }

        val raw = try {
            Base64.getDecoder().decode(parts[3])
        } catch (_: IllegalArgumentException) {
            throw SettingsEnvelopeException("The value of '$path' is not a valid envelope body")
        }
        if (raw.size <= NONCE_LENGTH + TAG_BITS / 8) {
            throw SettingsEnvelopeException("The value of '$path' is too short to be an envelope")
        }

        val nonce = raw.copyOfRange(0, NONCE_LENGTH)
        val sealed = raw.copyOfRange(NONCE_LENGTH, raw.size)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secret, GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(additionalData(domain, path))
            String(cipher.doFinal(sealed), Charsets.UTF_8)
        } catch (_: GeneralSecurityException) {
            throw SettingsEnvelopeException("The value of '$path' cannot be decrypted: it was changed, moved to another place or sealed with another key")
        }
    }

    private fun additionalData(domain: String, path: String): ByteArray =
        "ingot-settings|$VERSION|$domain|$path".toByteArray(Charsets.UTF_8)

    private fun hmac(key: ByteArray, label: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(label.toByteArray(Charsets.UTF_8))
    }

    companion object {
        const val PREFIX = "enc:"
        const val VERSION = "v1"
        const val KEY_LENGTH = 32
        const val NONCE_LENGTH = 12
        private const val TAG_BITS = 128
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_ID_LABEL = "ingot-settings-kid-v1"
        private const val CHECK_LABEL = "ingot-settings-check-v1"

        /** True for every value that is written in the envelope format, whatever its version. */
        fun isEnvelope(value: String): Boolean =
            value.startsWith(PREFIX)
    }

}
