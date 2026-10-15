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

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Argon2id cost: memory in KiB, number of passes, number of lanes. */
data class Argon2Cost(val memoryKib: Int, val iterations: Int, val parallelism: Int) {

    companion object {

        /** The OWASP password storage minimum for argon2id: 19 MiB, two passes, one lane. */
        val OWASP = Argon2Cost(memoryKib = 19_456, iterations = 2, parallelism = 1)

    }

}

/**
 * Hashes local passwords with argon2id and verifies them in constant time. Hashes are stored in the PHC string format
 * (`$argon2id$v=19$m=...,t=...,p=...$salt$hash`), so every hash carries the cost it was made with and keeps verifying
 * after the configured cost changes.
 */
class PasswordHasher(private val cost: Argon2Cost = Argon2Cost.OWASP) {

    private val random = SecureRandom()

    fun hash(password: String): String {
        val salt = ByteArray(SALT_LENGTH).also { random.nextBytes(it) }
        val hash = derive(password, salt, cost, HASH_LENGTH)
        val encoder = Base64.getEncoder().withoutPadding()
        return "\$$ALGORITHM\$v=$VERSION\$m=${cost.memoryKib},t=${cost.iterations},p=${cost.parallelism}" +
            "\$${encoder.encodeToString(salt)}\$${encoder.encodeToString(hash)}"
    }

    /** `false` for a wrong password and for any stored value that is not a well-formed argon2id hash. */
    fun matches(password: String, encoded: String): Boolean {
        val stored = parse(encoded) ?: return false
        val candidate = derive(password, stored.salt, stored.cost, stored.hash.size)
        return MessageDigest.isEqual(candidate, stored.hash)
    }

    private fun derive(password: String, salt: ByteArray, cost: Argon2Cost, length: Int): ByteArray {
        val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(cost.iterations)
            .withMemoryAsKB(cost.memoryKib)
            .withParallelism(cost.parallelism)
            .withSalt(salt)
            .build()
        val generator = Argon2BytesGenerator()
        generator.init(parameters)
        return ByteArray(length).also { generator.generateBytes(password.toByteArray(Charsets.UTF_8), it) }
    }

    private fun parse(encoded: String): StoredHash? {
        val parts = encoded.split('$')
        if (parts.size != 6 || parts[0].isNotEmpty() || parts[1] != ALGORITHM || parts[2] != "v=$VERSION") {
            return null
        }
        return try {
            val values = parts[3].split(',').associate { it.substringBefore('=') to it.substringAfter('=').toInt() }
            val storedCost = Argon2Cost(
                memoryKib = values.getValue("m"),
                iterations = values.getValue("t"),
                parallelism = values.getValue("p"),
            )
            val decoder = Base64.getDecoder()
            val salt = decoder.decode(parts[4])
            val hash = decoder.decode(parts[5])
            if (hash.isEmpty() || salt.isEmpty() || storedCost.iterations < 1 || storedCost.parallelism < 1) {
                null
            } else {
                StoredHash(storedCost, salt, hash)
            }
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: NoSuchElementException) {
            null
        }
    }

    private class StoredHash(val cost: Argon2Cost, val salt: ByteArray, val hash: ByteArray)

    private companion object {
        const val ALGORITHM = "argon2id"
        const val VERSION = 19
        const val SALT_LENGTH = 16
        const val HASH_LENGTH = 32
    }

}
