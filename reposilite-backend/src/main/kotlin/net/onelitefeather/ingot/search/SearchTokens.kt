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

package net.onelitefeather.ingot.search

import java.security.MessageDigest
import java.util.Locale

/**
 * The tokens of a GAV for word matching (full text change, D1 and D2). Shared by every search backend, so all of them
 * produce the same tokens and match the same words. Pure: no database and no clock.
 */
internal object SearchTokens {

    /** The longest token that is stored. A longer one is dropped. */
    const val MAX_TOKEN_LENGTH = 64

    /** The most tokens stored for one GAV. */
    const val MAX_TOKENS_PER_GAV = 32

    /**
     * The tokens of the coordinates: the letter and digit runs of groupId, artifactId and version, lower-cased, in that
     * order, without duplicates, without tokens longer than [MAX_TOKEN_LENGTH], at most [MAX_TOKENS_PER_GAV].
     */
    fun of(groupId: String, artifactId: String, version: String): List<String> =
        (split(groupId) + split(artifactId) + split(version))
            .distinct()
            .filter { it.length <= MAX_TOKEN_LENGTH }
            .take(MAX_TOKENS_PER_GAV)

    /** The parts of one query word: its letter and digit runs, lower-cased. A word of punctuation alone has none. */
    fun partsOf(word: String): List<String> = split(word)

    private val SEPARATOR = Regex("[^\\p{L}\\p{Nd}]+")

    private fun split(value: String): List<String> =
        value.lowercase(Locale.ROOT).split(SEPARATOR).filter { it.isNotEmpty() }
}

/** Bytes of the SHA-256 that form a GAV key: 128 bits, so a row carries 16 bytes instead of 64 hex characters. */
internal const val GAV_KEY_BYTES = 16

/**
 * The key of a GAV: the first [GAV_KEY_BYTES] bytes of the SHA-256 of its lower-case groupId, artifactId and version, joined
 * with NUL (full text change, D1). The NUL separator keeps coordinates that run together apart. Every row of one GAV has the
 * same key, and the tokens of the GAV are keyed by it. At 128 bits a collision between two GAVs of a real index is not
 * expected; the key is a row identifier, not a proof of equality.
 */
internal fun gavKeyOf(groupId: String, artifactId: String, version: String): ByteArray {
    val joined = listOf(groupId, artifactId, version).joinToString("\u0000") { it.lowercase(java.util.Locale.ROOT) }
    return MessageDigest.getInstance("SHA-256")
        .digest(joined.toByteArray(Charsets.UTF_8))
        .copyOf(GAV_KEY_BYTES)
}
