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

package net.onelitefeather.ingot.support

/**
 * The last check before a bundle is served. It searches the complete output for every known secret. A value of at least
 * [LogScrubber.MIN_LENGTH] characters is searched as a substring, a shorter one only as a whole word, because a short value
 * inside an ordinary word would refuse every bundle. The report names the entries that contain a secret and never the value.
 */
class RedactionGuard(knownSecrets: Collection<String>) {

    private val matchers: List<(String) -> Boolean> = knownSecrets
        .filter { it.isNotEmpty() }
        .distinct()
        .map { secret -> matcherOf(secret) }

    /** The names of the entries whose content contains a known secret. Empty when the output is clean. */
    fun leaks(files: Map<String, ByteArray>): List<String> =
        files.filter { (_, content) ->
            val text = String(content, Charsets.UTF_8)
            matchers.any { matches -> matches(text) }
        }.keys.toList()

    private fun matcherOf(secret: String): (String) -> Boolean =
        if (secret.length >= LogScrubber.MIN_LENGTH) containsSubstring(secret) else containsWord(secret)

    private fun containsSubstring(secret: String): (String) -> Boolean =
        { text -> text.contains(secret) }

    private fun containsWord(secret: String): (String) -> Boolean {
        val word = Regex("(?<![A-Za-z0-9])${Regex.escape(secret)}(?![A-Za-z0-9])")
        return { text -> word.containsMatchIn(text) }
    }
}
