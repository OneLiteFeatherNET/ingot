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

package com.reposilite.shared.http

import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit.SECONDS

/** Conditional request rules of RFC 9110, section 13, for artifact downloads. */
internal object ConditionalRequests {

    private const val WEAK_PREFIX = "W/"

    /**
     * A strong entity tag from the size and the modification time, which the storage already knows. It avoids hashing
     * the artifact on every request, and a redeployment changes at least one of the two. `null` when either is unknown.
     */
    fun etag(size: Long, lastModified: Instant?): String? =
        when {
            size < 0 || lastModified == null -> null
            else -> "\"${size.toString(16)}-${lastModified.toEpochMilli().toString(16)}\""
        }

    /** `true` when a GET or HEAD may be answered with 304 (section 13.2.2: `If-None-Match` wins over `If-Modified-Since`). */
    fun isNotModified(ifNoneMatch: String?, ifModifiedSince: String?, etag: String?, lastModified: Instant?): Boolean =
        when {
            !ifNoneMatch.isNullOrBlank() -> etag != null && matchesAny(ifNoneMatch, etag)
            !ifModifiedSince.isNullOrBlank() -> {
                val since = parseDate(ifModifiedSince)
                since != null && lastModified != null && !lastModified.truncatedTo(SECONDS).isAfter(since)
            }
            else -> false
        }

    /** `false` when `If-Range` names a validator that no longer matches, so the range must be ignored (section 13.1.5). */
    fun rangeApplies(ifRange: String?, etag: String?, lastModified: Instant?): Boolean {
        if (ifRange.isNullOrBlank()) {
            return true
        }

        val value = ifRange.trim()
        return when {
            value.startsWith(WEAK_PREFIX) -> false
            value.startsWith("\"") -> etag != null && value == etag
            else -> parseDate(value)?.let { it == lastModified?.truncatedTo(SECONDS) } ?: false
        }
    }

    private fun matchesAny(header: String, etag: String): Boolean =
        header.split(',')
            .map { it.trim() }
            .any { candidate -> candidate == "*" || candidate.removePrefix(WEAK_PREFIX) == etag }

    private fun parseDate(value: String): Instant? =
        try {
            ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
        } catch (_: Exception) {
            null
        }

}
