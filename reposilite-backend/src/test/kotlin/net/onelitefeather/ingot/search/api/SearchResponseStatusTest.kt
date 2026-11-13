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

package net.onelitefeather.ingot.search.api

import net.onelitefeather.ingot.search.IndexState
import net.onelitefeather.ingot.search.IndexStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The index status of a search response is the composition of the core, the same as the Maintenance page (4.8). */
internal class SearchResponseStatusTest {

    private val composedWithOutboxLag = IndexStatus(
        state = IndexState.DEGRADED,
        lagSeconds = 42L,
        lastFullRebuild = null,
        deadLetters = 3L,
        droppedEnqueues = 0L,
    )

    @Test
    fun `should report the composed status with the outbox of the core`() {
        // when: a page with results is answered
        val status = searchResponseStatus(total = 5L, rowsOf = { error("the rows are not counted for a page with results") }) {
            composedWithOutboxLag
        }

        // then: the lag and the dead letters are the core's, not the zero values a backend may report on its own
        assertThat(status.lagSeconds).`as`("lag of the oldest outbox row").isEqualTo(42L)
        assertThat(status.deadLetters).`as`("dead letters of the outbox").isEqualTo(3L)
    }

    @Test
    fun `should tell the composition that rows exist when the page has results`() {
        // given: the composition records what it is told
        var told: Boolean? = null

        // when: a page with results is answered
        searchResponseStatus(total = 1L, rowsOf = { 0L }) { rowsExist ->
            told = rowsExist
            composedWithOutboxLag
        }

        // then: the index holds rows, because the page has results
        assertThat(told).`as`("rows exist").isTrue()
    }

    @Test
    fun `should count the rows of the index when the page is empty`() {
        // given: the composition records what it is told
        var told: Boolean? = null

        // when: an empty page is answered and the index holds rows
        searchResponseStatus(total = 0L, rowsOf = { 7L }) { rowsExist ->
            told = rowsExist
            composedWithOutboxLag
        }

        // then: the rows of the index decide, not the page
        assertThat(told).`as`("rows exist").isTrue()
    }

    @Test
    fun `should tell the composition that no rows exist when the page and the index are empty`() {
        // given: the composition records what it is told
        var told: Boolean? = null

        // when: an empty page is answered and the index holds no rows
        searchResponseStatus(total = 0L, rowsOf = { 0L }) { rowsExist ->
            told = rowsExist
            composedWithOutboxLag
        }

        // then: no rows exist
        assertThat(told).`as`("rows exist").isFalse()
    }

}
