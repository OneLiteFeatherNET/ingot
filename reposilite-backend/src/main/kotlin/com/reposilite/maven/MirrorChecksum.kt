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

package com.reposilite.maven

import com.fasterxml.jackson.annotation.JsonProperty
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/**
 * What a mirror with `store` enabled does with the checksum of an artifact it fetches.
 * - [IGNORE] stores the copy without any check, as mirrors always did.
 * - [WARN] stores the copy and logs a mismatch or a missing checksum file.
 * - [FAIL] stores a copy only when it matches the checksum file of the same host, and refuses the host otherwise.
 */
enum class ChecksumPolicy {
    @JsonProperty("ignore") IGNORE,
    @JsonProperty("warn") WARN,
    @JsonProperty("fail") FAIL,
}

/** The checksum files that are read from an upstream, in the order they are tried. */
internal enum class ChecksumAlgorithm(val extension: String, val javaName: String, val hexLength: Int) {
    SHA256(".sha256", "SHA-256", 64),
    SHA1(".sha1", "SHA-1", 40),
}

internal data class ExpectedChecksum(val algorithm: ChecksumAlgorithm, val hex: String)

/**
 * The digest in a checksum file: the first word of its content, in lower case. The content may be the bare digest or a
 * digest followed by a file name, as the `sha256sum` tool writes it. Null when the first word is not a digest of [algorithm].
 */
internal fun parseChecksumFile(content: String, algorithm: ChecksumAlgorithm): String? =
    content.trim()
        .split(WHITESPACE)
        .firstOrNull()
        ?.lowercase()
        ?.takeIf { it.length == algorithm.hexLength && it.all { character -> character in '0'..'9' || character in 'a'..'f' } }

/**
 * Passes the bytes of [source] through and digests them. At the end of the stream the digests are complete. When
 * [expected] is set and its digest differs, the end of the stream fails with an [IOException], so that a storage that
 * commits only after a complete read never commits the copy.
 */
internal class DigestingInputStream(
    source: InputStream,
    private val expected: ExpectedChecksum? = null,
) : FilterInputStream(source) {

    private val digests = ChecksumAlgorithm.entries.associateWith { MessageDigest.getInstance(it.javaName) }
    private var computed: Map<ChecksumAlgorithm, String>? = null

    /** The digest of the whole stream, or null while the stream has not been read to its end. */
    fun digestOf(algorithm: ChecksumAlgorithm): String? = computed?.get(algorithm)

    override fun read(): Int {
        val value = super.read()
        if (value == -1) {
            finish()
        } else {
            digests.values.forEach { it.update(value.toByte()) }
        }
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val count = super.read(buffer, offset, length)
        if (count == -1) {
            finish()
        } else {
            digests.values.forEach { it.update(buffer, offset, count) }
        }
        return count
    }

    private fun finish() {
        if (computed != null) {
            return
        }
        computed = digests.mapValues { (_, digest) -> digest.digest().joinToString("") { "%02x".format(it) } }
        val check = expected ?: return
        val actual = computed?.get(check.algorithm)
        if (actual != check.hex) {
            throw IOException("upstream copy does not match its ${check.algorithm.extension} checksum")
        }
    }

}

private val WHITESPACE = Regex("\\s+")
