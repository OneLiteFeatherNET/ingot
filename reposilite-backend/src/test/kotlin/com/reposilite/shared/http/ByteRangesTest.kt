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

@file:Suppress("FunctionName")

package com.reposilite.shared.http

import com.reposilite.shared.http.RangeDecision.Full
import com.reposilite.shared.http.RangeDecision.Partial
import com.reposilite.shared.http.RangeDecision.Unsatisfiable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class ByteRangesTest {

    @Test
    fun `should serve the whole representation when no range is requested`() {
        assertThat(ByteRanges.resolve(null, 1000)).isEqualTo(Full)
        assertThat(ByteRanges.resolve("  ", 1000)).isEqualTo(Full)
    }

    @Test
    fun `should resolve a closed range`() {
        assertThat(ByteRanges.resolve("bytes=0-99", 1000)).isEqualTo(Partial(0, 99))
    }

    @Test
    fun `should clamp the end of a closed range to the last byte`() {
        assertThat(ByteRanges.resolve("bytes=900-5000", 1000)).isEqualTo(Partial(900, 999))
    }

    @Test
    fun `should resolve an open range up to the last byte`() {
        assertThat(ByteRanges.resolve("bytes=100-", 1000)).isEqualTo(Partial(100, 999))
    }

    @Test
    fun `should resolve a suffix range to the last bytes`() {
        assertThat(ByteRanges.resolve("bytes=-100", 1000)).isEqualTo(Partial(900, 999))
    }

    @Test
    fun `should resolve a suffix longer than the representation to the whole of it`() {
        assertThat(ByteRanges.resolve("bytes=-5000", 1000)).isEqualTo(Partial(0, 999))
    }

    @Test
    fun `should report a range that starts behind the end as unsatisfiable`() {
        assertThat(ByteRanges.resolve("bytes=1000-", 1000)).isEqualTo(Unsatisfiable)
        assertThat(ByteRanges.resolve("bytes=1500-1600", 1000)).isEqualTo(Unsatisfiable)
    }

    @Test
    fun `should report an empty suffix as unsatisfiable`() {
        assertThat(ByteRanges.resolve("bytes=-0", 1000)).isEqualTo(Unsatisfiable)
    }

    @Test
    fun `should report any range of an empty representation as unsatisfiable`() {
        assertThat(ByteRanges.resolve("bytes=0-10", 0)).isEqualTo(Unsatisfiable)
    }

    @Test
    fun `should serve the whole representation for several ranges`() {
        assertThat(ByteRanges.resolve("bytes=0-9,20-29", 1000)).isEqualTo(Full)
    }

    @Test
    fun `should ignore a range in an unknown unit`() {
        assertThat(ByteRanges.resolve("items=0-9", 1000)).isEqualTo(Full)
    }

    @Test
    fun `should ignore a malformed range`() {
        listOf("bytes=", "bytes=abc", "bytes=5-2", "bytes=-", "bytes=1-2-3", "bytes=a-b", "0-9").forEach { header ->
            assertThat(ByteRanges.resolve(header, 1000)).describedAs(header).isEqualTo(Full)
        }
    }

    @Test
    fun `should accept the unit in any case and surrounding spaces`() {
        assertThat(ByteRanges.resolve("  Bytes=0-9 ", 1000)).isEqualTo(Partial(0, 9))
    }

    @Test
    fun `should serve the whole representation when the length is unknown`() {
        assertThat(ByteRanges.resolve("bytes=0-9", -1)).isEqualTo(Full)
    }

    @Test
    fun `should count the bytes of a partial range`() {
        assertThat(Partial(100, 199).length).isEqualTo(100)
    }

}
