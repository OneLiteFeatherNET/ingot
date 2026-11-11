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

import com.reposilite.audit.MutableClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

/** The backfill sweep of rows without a key (full text change, D6). Fixed clock, synchronous execution: no wall clock, no sleep. */
internal class TokenSweepTest {

    private val start = Instant.parse("2026-11-11T10:00:00Z")

    /** A store of [unkeyed] rows: each call keys up to the batch size and returns how many it keyed. */
    private class UnkeyedRows(var unkeyed: Int) {
        val productiveBatches = mutableListOf<Int>()
        var calls = 0

        fun key(batchSize: Int): Int {
            calls++
            val keyed = minOf(unkeyed, batchSize)
            unkeyed -= keyed
            if (keyed > 0) productiveBatches += keyed
            return keyed
        }
    }

    private fun sweep(
        clock: MutableClock,
        rows: UnkeyedRows,
        batchSize: Int = 2,
        readOnly: Boolean = false,
    ) = TokenSweep(
        clock = clock,
        batchSize = batchSize,
        interval = Duration.ofHours(SIX_HOURS),
        keyRows = rows::key,
        readOnly = { readOnly },
        execute = { job -> job() },
        onFailure = { throw AssertionError(it) },
    )

    @Test
    fun `a sweep keys five unkeyed rows in three batches of two`() {
        val clock = MutableClock(start)
        val rows = UnkeyedRows(unkeyed = 5)

        sweep(clock, rows).tick()

        assertThat(rows.productiveBatches)
            .`as`("rows keyed per batch that keyed any row")
            .containsExactly(2, 2, 1)
        assertThat(rows.unkeyed).`as`("rows left unkeyed after the sweep").isZero()
    }

    @Test
    fun `a read-only instance runs no sweep`() {
        val clock = MutableClock(start)
        val rows = UnkeyedRows(unkeyed = 5)

        sweep(clock, rows, readOnly = true).tick()

        assertThat(rows.calls).`as`("calls to the store on a read-only instance").isZero()
        assertThat(rows.unkeyed).`as`("rows left unkeyed on a read-only instance").isEqualTo(5)
    }

    @Test
    fun `the first tick after a start runs the sweep`() {
        val clock = MutableClock(start)
        val rows = UnkeyedRows(unkeyed = 1)

        sweep(clock, rows).tick()

        assertThat(rows.unkeyed).`as`("rows keyed by the first tick").isZero()
    }

    @Test
    fun `a sweep does not run again before six hours have passed`() {
        val clock = MutableClock(start)
        val rows = UnkeyedRows(unkeyed = 0)
        val sweep = sweep(clock, rows)
        sweep.tick()
        val callsAfterFirst = rows.calls

        clock.advance(Duration.ofHours(SIX_HOURS).minusMinutes(1))
        sweep.tick()

        assertThat(rows.calls).`as`("calls made before six hours have passed").isEqualTo(callsAfterFirst)
    }

    @Test
    fun `a sweep runs again once six hours have passed`() {
        val clock = MutableClock(start)
        val rows = UnkeyedRows(unkeyed = 0)
        val sweep = sweep(clock, rows)
        sweep.tick()
        val callsAfterFirst = rows.calls

        clock.advance(Duration.ofHours(SIX_HOURS))
        sweep.tick()

        assertThat(rows.calls).`as`("calls made after six hours have passed").isGreaterThan(callsAfterFirst)
    }

    private companion object {
        const val SIX_HOURS = 6L
    }

}
