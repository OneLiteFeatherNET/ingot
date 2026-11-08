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

/** What a `Range` header means for a representation of a known size (RFC 9110, section 14). */
internal sealed interface RangeDecision {

    /** Serve the whole representation with 200: no range, one that is not understood, or several of them. */
    data object Full : RangeDecision

    /** Serve bytes [start] to [endInclusive] with 206. */
    data class Partial(val start: Long, val endInclusive: Long) : RangeDecision {
        val length: Long
            get() = endInclusive - start + 1
    }

    /** Answer 416 with `Content-Range: bytes * / size`. */
    data object Unsatisfiable : RangeDecision

}

internal object ByteRanges {

    private const val UNIT = "bytes"

    /**
     * Only a single `bytes` range is served as a partial response. Other units, malformed values and several ranges are
     * answered with the whole representation, which RFC 9110 explicitly allows and which keeps every client working.
     */
    fun resolve(header: String?, size: Long): RangeDecision {
        if (header.isNullOrBlank() || size < 0) {
            return RangeDecision.Full
        }

        val trimmed = header.trim()
        val separator = trimmed.indexOf('=')
        if (separator < 0 || !trimmed.substring(0, separator).trim().equals(UNIT, ignoreCase = true)) {
            return RangeDecision.Full
        }

        val spec = trimmed.substring(separator + 1).trim()
        if (spec.isEmpty() || spec.contains(',')) {
            return RangeDecision.Full
        }

        val dash = spec.indexOf('-')
        if (dash < 0 || spec.indexOf('-', dash + 1) >= 0) {
            return RangeDecision.Full
        }

        val first = spec.substring(0, dash).trim()
        val last = spec.substring(dash + 1).trim()

        return when {
            first.isEmpty() -> suffix(last, size)
            else -> bounded(first, last, size)
        }
    }

    private fun suffix(length: String, size: Long): RangeDecision {
        val count = length.toLongOrNullDigits() ?: return RangeDecision.Full
        return when {
            count == 0L || size == 0L -> RangeDecision.Unsatisfiable
            else -> RangeDecision.Partial(start = maxOf(size - count, 0), endInclusive = size - 1)
        }
    }

    private fun bounded(first: String, last: String, size: Long): RangeDecision {
        val start = first.toLongOrNullDigits() ?: return RangeDecision.Full
        val end = when {
            last.isEmpty() -> Long.MAX_VALUE
            else -> last.toLongOrNullDigits() ?: return RangeDecision.Full
        }

        return when {
            end < start -> RangeDecision.Full
            start >= size -> RangeDecision.Unsatisfiable
            else -> RangeDecision.Partial(start, minOf(end, size - 1))
        }
    }

    private fun String.toLongOrNullDigits(): Long? =
        takeIf { it.isNotEmpty() && it.all { char -> char in '0'..'9' } }?.toLongOrNull()

}
