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
 * Scrubs one log line in two passes. First every known secret value of at least [MIN_LENGTH] characters is replaced, the longest
 * first. Then the credential patterns are replaced: authorization headers, bearer and basic tokens, credentials in URLs, and the
 * assignments of secret-named keys, in plain text and in JSON alike. The line is text, so the format of the line does not matter.
 */
class LogScrubber(values: Collection<String>) {

    companion object {
        const val MIN_LENGTH = 4

        private val AUTHORIZATION = Regex("(?i)(authorization\\s*:\\s*)[^\\r\\n]+")
        private val TOKEN_HEADER = Regex("(?i)\\b(bearer)\\s+[A-Za-z0-9._~+/=-]{6,}|\\b(basic)\\s+[A-Za-z0-9+/]{8,}={0,2}")
        private val URL_CREDENTIALS = Regex("(?i)([a-z][a-z0-9+.-]*://)[^\\s/@:]+:[^\\s/@]+@")
        private val KEY_ASSIGNMENT = Regex(
            "(?i)(\\b(?:secret_key|access_key|apikey|password|passwd|secret|token)\"?\\s*[=:]\\s*)(\"[^\"]*\"|[^\\s,;&}]+)"
        )
    }

    private val secrets: List<String> = values
        .filter { it.length >= MIN_LENGTH }
        .distinct()
        .sortedByDescending { it.length }

    /** The line with every secret and credential replaced. [tally] counts each replacement. */
    fun scrub(line: String, tally: RedactionTally): String {
        var scrubbed = line
        secrets.forEach { secret ->
            val parts = scrubbed.split(secret)
            if (parts.size > 1) {
                tally.add(parts.size - 1)
                scrubbed = parts.joinToString(Redaction.REDACTED)
            }
        }

        scrubbed = AUTHORIZATION.replace(scrubbed) { match ->
            tally.add()
            match.groupValues[1] + Redaction.REDACTED
        }
        scrubbed = TOKEN_HEADER.replace(scrubbed) { match ->
            tally.add()
            val scheme = match.groupValues[1].ifEmpty { match.groupValues[2] }
            "$scheme ${Redaction.REDACTED}"
        }
        scrubbed = URL_CREDENTIALS.replace(scrubbed) { match ->
            tally.add()
            match.groupValues[1] + Redaction.REDACTED + "@"
        }
        scrubbed = KEY_ASSIGNMENT.replace(scrubbed) { match ->
            tally.add()
            val quoted = match.groupValues[2].startsWith("\"")
            match.groupValues[1] + if (quoted) "\"${Redaction.REDACTED}\"" else Redaction.REDACTED
        }
        return scrubbed
    }
}
