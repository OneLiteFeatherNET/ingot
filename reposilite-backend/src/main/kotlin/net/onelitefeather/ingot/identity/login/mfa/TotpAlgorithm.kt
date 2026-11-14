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

package net.onelitefeather.ingot.identity.login.mfa

import java.net.URLEncoder
import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Time-based one-time passwords as RFC 6238 defines them with HMAC-SHA1, six digits and a thirty second step. A code is
 * accepted for the current step and for one step either side, which absorbs a device that is a little off the clock.
 *
 * The functions take the time as a number of seconds, so the caller decides the clock. Nothing here reads the system time.
 */
object TotpAlgorithm {

    const val STEP_SECONDS = 30L
    const val DIGITS = 6
    const val SEED_BYTES = 20

    /** How many steps either side of the current one a code may belong to. */
    private const val WINDOW = 1L

    private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    /** The six digit code of [step], zero padded. */
    fun code(seed: ByteArray, step: Long): String {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(seed, "HmacSHA1"))
        val hash = mac.doFinal(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(step).array())
        val offset = hash.last().toInt() and 0x0f
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)
        return (binary % POWERS_OF_TEN[DIGITS]).toString().padStart(DIGITS, '0')
    }

    /**
     * The step of the code that [candidate] matches, looking at the steps within [WINDOW] of [nowSeconds]. `null` when none
     * matches or [candidate] is not six digits. Every candidate is compared in constant time, so the answer does not reveal
     * which step came closest.
     */
    fun matchingStep(seed: ByteArray, candidate: String, nowSeconds: Long): Long? {
        if (candidate.length != DIGITS || !candidate.all { it in '0'..'9' }) {
            return null
        }
        val current = nowSeconds / STEP_SECONDS
        var matched: Long? = null
        for (step in current - WINDOW..current + WINDOW) {
            val equal = MessageDigest.isEqual(code(seed, step).toByteArray(Charsets.US_ASCII), candidate.toByteArray(Charsets.US_ASCII))
            if (equal && matched == null) {
                matched = step
            }
        }
        return matched
    }

    /** The seed as base32 without padding, the form authenticator apps read. */
    fun base32(bytes: ByteArray): String {
        val out = StringBuilder()
        var buffer = 0
        var bits = 0
        for (byte in bytes) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xff)
            bits += 8
            while (bits >= 5) {
                out.append(BASE32_ALPHABET[(buffer shr (bits - 5)) and 0x1f])
                bits -= 5
            }
        }
        if (bits > 0) {
            out.append(BASE32_ALPHABET[(buffer shl (5 - bits)) and 0x1f])
        }
        return out.toString()
    }

    /** The `otpauth` URI that authenticator apps take in as a QR code or a typed secret. */
    fun otpauthUri(issuer: String, account: String, seed: ByteArray): String {
        val label = "${enc(issuer)}:${enc(account)}"
        return "otpauth://totp/$label?secret=${base32(seed)}&issuer=${enc(issuer)}&algorithm=SHA1&digits=$DIGITS&period=$STEP_SECONDS"
    }

    private fun enc(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")

    private val POWERS_OF_TEN = longArrayOf(1L, 10L, 100L, 1_000L, 10_000L, 100_000L, 1_000_000L)

}
