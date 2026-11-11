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

package net.onelitefeather.ingot.settings.export

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** The Argon2id cost of an export: memory in KiB, passes and lanes. */
data class ExportCost(val memoryKib: Int, val iterations: Int, val parallelism: Int) {

    companion object {
        /** 64 MiB, three passes, one lane. */
        val STANDARD = ExportCost(memoryKib = 65_536, iterations = 3, parallelism = 1)
    }

}

/**
 * A failure of an export or of an import. The message of an import failure is always [SettingsExportCodec.REFUSED_MESSAGE], so the
 * answer does not say which check failed.
 */
class SettingsExportException(message: String, val reason: String = SettingsExportCodec.REASON_FILE) : IllegalArgumentException(message)

/**
 * The password protected settings file. The plain text is the canonical JSON of the saved settings. The key is Argon2id of the password
 * and a random salt, and AES-256-GCM seals the plain text with the header as additional data, so a changed header field fails the check.
 *
 * The random source and the cost are injected, so tests run with a fixed salt, a fixed nonce and a small cost.
 */
class SettingsExportCodec(
    private val random: (Int) -> ByteArray = { size -> ByteArray(size).also { secureRandom.nextBytes(it) } },
    private val cost: ExportCost = ExportCost.STANDARD,
) {

    /**
     * @throws SettingsExportException when the password is shorter than [MIN_PASSWORD_LENGTH] characters
     */
    fun seal(plainDocument: String, password: String, createdAt: String): ObjectNode {
        if (password.length < MIN_PASSWORD_LENGTH) {
            throw SettingsExportException("The export password must be at least $MIN_PASSWORD_LENGTH characters long")
        }

        val salt = random(SALT_LENGTH)
        val nonce = random(NONCE_LENGTH)
        val header = header(createdAt, cost, salt, nonce)
        val key = deriveKey(password, salt, cost)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(additionalData(header))
        val ciphertext = cipher.doFinal(plainDocument.toByteArray(Charsets.UTF_8))

        return header.deepCopy().apply {
            put("ciphertext", Base64.getEncoder().encodeToString(ciphertext))
        }
    }

    /**
     * @return the plain JSON document of the export
     * @throws SettingsExportException with [REFUSED_MESSAGE] for every failure of the file or of the password
     */
    fun open(file: JsonNode, password: String): String {
        val refused = { SettingsExportException(REFUSED_MESSAGE) }
        if (file.path("format").asText() != FORMAT || file.path("version").asInt(-1) != VERSION) {
            throw refused()
        }

        val kdf = file.path("kdf")
        val cipherNode = file.path("cipher")
        val memoryKib = kdf.path("memoryKiB").asInt(-1)
        val iterations = kdf.path("iterations").asInt(-1)
        val parallelism = kdf.path("parallelism").asInt(-1)
        if (kdf.path("algorithm").asText() != "argon2id" ||
            memoryKib !in MIN_MEMORY_KIB..MAX_MEMORY_KIB || iterations !in 1..MAX_ITERATIONS || parallelism !in 1..MAX_PARALLELISM
        ) {
            throw refused()
        }

        val salt = decode(kdf.path("salt").asText()) ?: throw refused()
        val nonce = decode(cipherNode.path("nonce").asText()) ?: throw refused()
        val ciphertext = decode(file.path("ciphertext").asText()) ?: throw refused()
        if (cipherNode.path("algorithm").asText() != "AES-256-GCM" || nonce.size != NONCE_LENGTH) {
            throw refused()
        }

        val header = JsonNodeFactory.instance.objectNode().apply {
            put("format", FORMAT)
            put("version", VERSION)
            put("createdAt", file.path("createdAt").asText())
            set<JsonNode>("kdf", kdf)
            set<JsonNode>("cipher", cipherNode)
        }
        val key = deriveKey(password, salt, ExportCost(memoryKib, iterations, parallelism))
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(additionalData(header))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: GeneralSecurityException) {
            // GCM cannot tell a wrong password from a changed file, so an authentication failure is reported as a password failure.
            throw SettingsExportException(REFUSED_MESSAGE, reason = REASON_PASSWORD)
        }
    }

    private fun header(createdAt: String, cost: ExportCost, salt: ByteArray, nonce: ByteArray): ObjectNode {
        val encoder = Base64.getEncoder()
        return JsonNodeFactory.instance.objectNode().apply {
            put("format", FORMAT)
            put("version", VERSION)
            put("createdAt", createdAt)
            set<JsonNode>(
                "kdf",
                JsonNodeFactory.instance.objectNode()
                    .put("algorithm", "argon2id")
                    .put("memoryKiB", cost.memoryKib)
                    .put("iterations", cost.iterations)
                    .put("parallelism", cost.parallelism)
                    .put("salt", encoder.encodeToString(salt)),
            )
            set<JsonNode>(
                "cipher",
                JsonNodeFactory.instance.objectNode()
                    .put("algorithm", "AES-256-GCM")
                    .put("nonce", encoder.encodeToString(nonce)),
            )
        }
    }

    /** The header in sorted-key JSON, so the same header always gives the same bytes. */
    private fun additionalData(header: JsonNode): ByteArray =
        canonicalWriter.writeValueAsBytes(mapper.convertValue(header, Any::class.java))

    private fun deriveKey(password: String, salt: ByteArray, cost: ExportCost): ByteArray {
        val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(cost.iterations)
            .withMemoryAsKB(cost.memoryKib)
            .withParallelism(cost.parallelism)
            .withSalt(salt)
            .build()
        val generator = Argon2BytesGenerator()
        generator.init(parameters)
        return ByteArray(KEY_LENGTH).also { generator.generateBytes(password.toByteArray(Charsets.UTF_8), it) }
    }

    private fun decode(value: String): ByteArray? =
        try {
            Base64.getDecoder().decode(value)
        } catch (_: IllegalArgumentException) {
            null
        }

    companion object {
        const val FORMAT = "ingot-settings-export"
        const val VERSION = 1
        const val MIN_PASSWORD_LENGTH = 12

        /** The one message of every import failure, see [SettingsExportException]. */
        const val REASON_PASSWORD = "password"
        const val REASON_FILE = "file"

        const val REFUSED_MESSAGE = "The settings file cannot be opened: the password is wrong or the file was changed"

        /** Bounds of the cost a file may declare, so an import cannot make the server run an unbounded derivation. */
        const val MIN_MEMORY_KIB = 8
        const val MAX_MEMORY_KIB = 262_144
        const val MAX_ITERATIONS = 10
        const val MAX_PARALLELISM = 4

        private const val SALT_LENGTH = 16
        private const val NONCE_LENGTH = 12
        private const val KEY_LENGTH = 32
        private const val TAG_BITS = 128
        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        private val secureRandom = SecureRandom()
        private val mapper = ObjectMapper()
        private val canonicalWriter = mapper.writer().with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
    }

}
