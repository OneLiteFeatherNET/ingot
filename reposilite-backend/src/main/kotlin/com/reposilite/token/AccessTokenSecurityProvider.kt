/*
 * Copyright (c) 2023 dzikoysk
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

package com.reposilite.token

import at.favre.lib.crypto.bcrypt.BCrypt
import java.security.SecureRandom
import java.util.Base64

object AccessTokenSecurityProvider {

    /** Work factor used in production. Lowered only by the test harness, see `ReposiliteParameters.tokenHashStrength`. */
    const val DEFAULT_BCRYPT_STRENGTH = 10

    /** The lowest work factor BCrypt accepts. Hashing at [DEFAULT_BCRYPT_STRENGTH] dominates the runtime of token tests. */
    const val MIN_BCRYPT_STRENGTH = 4

    private val bcryptHasher = BCrypt.with(BCrypt.Version.VERSION_2A)
    private val bcryptVerifier = BCrypt.verifyer(BCrypt.Version.VERSION_2A)

    fun generateSecret(): String {
        val secret = ByteArray(48)
        SecureRandom().nextBytes(secret)
        return Base64.getEncoder().encodeToString(secret)
    }

    fun encodeSecret(secret: String, bcryptStrength: Int = DEFAULT_BCRYPT_STRENGTH): String =
        bcryptHasher.hashToString(bcryptStrength, secret.toCharArray())

    fun matches(encryptedSecret: String, rawSecret: String): Boolean =
        try {
            bcryptVerifier.verify(rawSecret.toCharArray(), encryptedSecret.toCharArray()).verified
        } catch (_: IllegalArgumentException) {
            false
        }

}
