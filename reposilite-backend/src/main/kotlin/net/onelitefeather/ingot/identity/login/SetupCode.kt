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

package net.onelitefeather.ingot.identity.login

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The one-time code that lets the first administrator be created in the dashboard. It is random, lives in memory of the
 * instance that issued it, and is shown once in the log at start while no user exists. Using it once, or any user
 * appearing, ends it for good.
 */
internal class SetupCode(private val random: SecureRandom = SecureRandom()) {

    private var current: String? = null

    /** The code of this instance. Issuing again returns the same code, so the one in the log stays valid. */
    @Synchronized
    fun issue(): String =
        current ?: generate().also { current = it }

    /** Compares in constant time. Letter case and surrounding spaces do not matter, because the code is copied by hand. */
    @Synchronized
    fun matches(candidate: String): Boolean {
        val expected = current ?: return false
        return MessageDigest.isEqual(
            expected.toByteArray(Charsets.UTF_8),
            candidate.trim().uppercase().toByteArray(Charsets.UTF_8),
        )
    }

    @Synchronized
    fun clear() {
        current = null
    }

    private fun generate(): String =
        buildString {
            repeat(CODE_LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        }

    companion object {

        /** Upper case letters and digits without the characters that are easily confused: 0, O, 1, I and L. */
        const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        const val CODE_LENGTH = 12

    }

}
