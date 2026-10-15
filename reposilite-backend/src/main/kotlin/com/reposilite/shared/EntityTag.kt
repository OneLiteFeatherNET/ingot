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

package com.reposilite.shared

import com.reposilite.configuration.ConfigurationFacade
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Strong entity tags for optimistic concurrency. A client that reads a resource keeps its tag and sends it back in
 * `If-Match`; the write is applied only when the tag still describes the stored state.
 */
object EntityTag {

    /**
     * Evaluates an `If-Match` header against the current entity tag of a resource, `null` when the resource does not exist.
     * An absent or blank header imposes no precondition, which keeps clients that never send it working as before.
     */
    fun matches(ifMatch: String?, current: String?): Boolean {
        if (ifMatch.isNullOrBlank()) {
            return true
        }

        return ifMatch.split(',')
            .map { it.trim() }
            .any { candidate ->
                when {
                    candidate == "*" -> current != null
                    current == null -> false
                    else -> candidate == current
                }
            }
    }

}

/**
 * Derives entity tags as HMAC-SHA256 over the canonical representation of a resource, keyed with a server-side secret.
 *
 * The content of a tag can contain secrets, and a manager may be able to read tags but not the secrets. An unkeyed hash
 * would let such a manager test guesses for a weak secret offline, while a keyed tag reveals nothing without the key.
 *
 * Instances that share a configuration or database must derive identical tags, so the key is stored there, see [stored].
 */
class EntityTagSigner private constructor(private val key: ByteArray) {

    /**
     * Derives a strong, quoted entity tag from the canonical representation of a resource.
     */
    fun of(content: ByteArray): String {
        val mac = Mac.getInstance(MAC_ALGORITHM)
        mac.init(SecretKeySpec(key, MAC_ALGORITHM))
        return "\"" + mac.doFinal(content).joinToString(separator = "") { "%02x".format(it) } + "\""
    }

    companion object {

        /**
         * Name of the configuration entry that holds the signing key. It is shared by every instance that uses the same
         * configuration store, and it is never part of the settings that a manager can read.
         */
        internal const val KEY_CONFIGURATION_NAME = "entity-tag-key"

        private const val MAC_ALGORITHM = "HmacSHA256"
        private const val KEY_LENGTH_BYTES = 32
        private val random = SecureRandom()

        /**
         * A signer with a random key that lives only as long as this object, for a single instance without shared storage.
         */
        fun ephemeral(): EntityTagSigner =
            EntityTagSigner(randomKey())

        /**
         * A signer with the key from the configuration store. The first instance that starts generates a random 256-bit key
         * and stores it; every later instance, and every instance that starts concurrently, reads back the same key.
         */
        fun stored(configurationFacade: ConfigurationFacade): EntityTagSigner {
            val encodedKey = configurationFacade.findOrCreateConfiguration(KEY_CONFIGURATION_NAME) {
                Base64.getEncoder().encodeToString(randomKey())
            }
            return EntityTagSigner(Base64.getDecoder().decode(encodedKey))
        }

        private fun randomKey(): ByteArray =
            ByteArray(KEY_LENGTH_BYTES).also { random.nextBytes(it) }

    }

}
