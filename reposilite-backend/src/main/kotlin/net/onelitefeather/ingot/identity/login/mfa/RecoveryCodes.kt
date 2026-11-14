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

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Single-use codes that sign a user in when the second factor is lost. A set holds [SET_SIZE] codes of 80 random bits, shown
 * once in groups of four characters. Only a SHA-256 hash of the normalised code is stored. The input has 80 bits of entropy,
 * so a fast hash is enough, and a code can be checked without a slow hash on every failed attempt.
 */
internal object RecoveryCodes {

    const val SET_SIZE = 10
    private const val CODE_BYTES = 10
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    /** A new set of codes, in display form. */
    fun generate(random: SecureRandom): List<String> =
        List(SET_SIZE) { display(random) }

    /** The form that is hashed: upper case, without dashes or spaces, so that the way a code is typed does not matter. */
    fun normalise(code: String): String =
        code.filterNot { it == '-' || it.isWhitespace() }.uppercase()

    fun hash(code: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(normalise(code).toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { "%02x".format(it) }

    private fun display(random: SecureRandom): String {
        val bytes = ByteArray(CODE_BYTES).also { random.nextBytes(it) }
        val chars = CharArray(CODE_BYTES * 8 / 5) { index ->
            val bit = index * 5
            var value = 0
            for (offset in 0 until 5) {
                val position = bit + offset
                val byte = bytes[position / 8].toInt()
                value = (value shl 1) or ((byte shr (7 - position % 8)) and 1)
            }
            ALPHABET[value]
        }
        return chars.concatToString().chunked(4).joinToString("-")
    }

}
