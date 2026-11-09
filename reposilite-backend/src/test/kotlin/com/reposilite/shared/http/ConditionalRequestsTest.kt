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

import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class ConditionalRequestsTest {

    private val modified = Instant.parse("2026-03-01T10:00:00.250Z")
    private val etag = ConditionalRequests.etag(1000, modified)!!

    @Test
    fun `should derive a quoted strong entity tag`() {
        assertThat(etag).matches("\"[0-9a-f]+-[0-9a-f]+\"")
        assertThat(etag).doesNotStartWith("W/")
    }

    @Test
    fun `should derive a different entity tag when the size or the modification time changes`() {
        assertThat(ConditionalRequests.etag(1001, modified)).isNotEqualTo(etag)
        assertThat(ConditionalRequests.etag(1000, modified.plusSeconds(1))).isNotEqualTo(etag)
    }

    @Test
    fun `should derive the same entity tag for the same size and modification time`() {
        assertThat(ConditionalRequests.etag(1000, modified)).isEqualTo(etag)
    }

    @Test
    fun `should derive no entity tag without a modification time or a known size`() {
        assertThat(ConditionalRequests.etag(1000, null)).isNull()
        assertThat(ConditionalRequests.etag(-1, modified)).isNull()
    }

    @Test
    fun `should report not modified when if-none-match names the current tag`() {
        assertThat(ConditionalRequests.isNotModified(etag, null, etag, modified)).isTrue
    }

    @Test
    fun `should report not modified when one of several tags matches`() {
        assertThat(ConditionalRequests.isNotModified("\"other\", $etag", null, etag, modified)).isTrue
    }

    @Test
    fun `should report not modified for a weak form of the current tag`() {
        assertThat(ConditionalRequests.isNotModified("W/$etag", null, etag, modified)).isTrue
    }

    @Test
    fun `should report not modified for a wildcard`() {
        assertThat(ConditionalRequests.isNotModified("*", null, etag, modified)).isTrue
    }

    @Test
    fun `should report modified when if-none-match names another tag`() {
        assertThat(ConditionalRequests.isNotModified("\"other\"", null, etag, modified)).isFalse
    }

    @Test
    fun `should ignore if-modified-since when if-none-match is present`() {
        val later = "Sun, 01 Mar 2026 11:00:00 GMT"
        assertThat(ConditionalRequests.isNotModified("\"other\"", later, etag, modified)).isFalse
    }

    @Test
    fun `should report not modified when the resource is not newer than if-modified-since`() {
        assertThat(ConditionalRequests.isNotModified(null, "Sun, 01 Mar 2026 10:00:00 GMT", etag, modified)).isTrue
        assertThat(ConditionalRequests.isNotModified(null, "Sun, 01 Mar 2026 11:00:00 GMT", etag, modified)).isTrue
    }

    @Test
    fun `should report modified when the resource is newer than if-modified-since`() {
        assertThat(ConditionalRequests.isNotModified(null, "Sun, 01 Mar 2026 09:59:59 GMT", etag, modified)).isFalse
    }

    @Test
    fun `should report modified when if-modified-since is not a date`() {
        assertThat(ConditionalRequests.isNotModified(null, "yesterday", etag, modified)).isFalse
    }

    @Test
    fun `should report modified without any precondition`() {
        assertThat(ConditionalRequests.isNotModified(null, null, etag, modified)).isFalse
        assertThat(ConditionalRequests.isNotModified("  ", null, etag, modified)).isFalse
    }

    @Test
    fun `should apply a range when if-range is absent`() {
        assertThat(ConditionalRequests.rangeApplies(null, etag, modified)).isTrue
    }

    @Test
    fun `should apply a range when if-range holds the current tag`() {
        assertThat(ConditionalRequests.rangeApplies(etag, etag, modified)).isTrue
    }

    @Test
    fun `should not apply a range when if-range holds a stale tag`() {
        assertThat(ConditionalRequests.rangeApplies("\"stale\"", etag, modified)).isFalse
    }

    @Test
    fun `should not apply a range when if-range holds a weak tag`() {
        assertThat(ConditionalRequests.rangeApplies("W/$etag", etag, modified)).isFalse
    }

    @Test
    fun `should apply a range when if-range holds the exact modification date`() {
        assertThat(ConditionalRequests.rangeApplies("Sun, 01 Mar 2026 10:00:00 GMT", etag, modified)).isTrue
    }

    @Test
    fun `should not apply a range when if-range holds another date`() {
        assertThat(ConditionalRequests.rangeApplies("Sun, 01 Mar 2026 09:00:00 GMT", etag, modified)).isFalse
    }

    @Test
    fun `should not apply a range when if-range is not a validator`() {
        assertThat(ConditionalRequests.rangeApplies("garbage", etag, modified)).isFalse
    }

}
