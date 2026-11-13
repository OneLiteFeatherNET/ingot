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

@file:OptIn(net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi::class)

package net.onelitefeather.ingot.search

import com.reposilite.audit.MutableClock
import net.onelitefeather.ingot.search.backend.KeyBatch
import net.onelitefeather.ingot.search.backend.SkippedKey
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

/** The backfill sweep of rows without a key (full text change, D6). Fixed clock, synchronous execution: no wall clock, no sleep. */
internal class TokenSweepTest {

    private val start = Instant.parse("2026-11-11T10:00:00Z")

    /**
     * A store of unkeyed rows with the given ids. Like the database, a batch takes the unkeyed rows after the cursor, in id
     * order, and reports the id of its last row. Every cursor it is asked from is recorded.
     */
    private class UnkeyedRows(ids: List<Long>, private val refused: Set<Long> = emptySet()) {
        private val unkeyed = ids.toMutableList()
        val cursors = mutableListOf<Long>()
        val productiveBatches = mutableListOf<Int>()
        val remaining: Int get() = unkeyed.size

        /** A refused row stays unkeyed, as a row whose key write the database rejects does. */
        fun key(after: Long, batchSize: Int): KeyBatch? {
            cursors += after
            val batch = unkeyed.filter { it > after }.take(batchSize)
            if (batch.isEmpty()) return null
            val (skipped, keyed) = batch.partition { it in refused }
            unkeyed.removeAll(keyed.toSet())
            productiveBatches += keyed.size
            return KeyBatch(
                keyed = keyed.size,
                lastId = batch.last(),
                skipped = skipped.map { SkippedKey(id = it, reason = "the database refused the key") },
            )
        }
    }

    private fun sweep(
        clock: MutableClock,
        rows: UnkeyedRows,
        batchSize: Int = 2,
        readOnly: Boolean = false,
        onSkipped: (String) -> Unit = { throw AssertionError(it) },
    ) = TokenSweep(
        clock = clock,
        batchSize = batchSize,
        interval = Duration.ofHours(SIX_HOURS),
        keyRows = rows::key,
        readOnly = { readOnly },
        execute = { job -> job() },
        onFailure = { throw AssertionError(it) },
        onSkipped = onSkipped,
    )

    @Test
    fun `a row the database refuses is reported once and the sweep still finishes its pass`() {
        val clock = MutableClock(start)
        val rows = UnkeyedRows(listOf(1, 2, 3, 4, 5), refused = setOf(2))
        val reports = mutableListOf<String>()
        val sweep = sweep(clock, rows, onSkipped = { reports += it })

        sweep.tick()
        clock.advance(Duration.ofHours(SIX_HOURS))
        sweep.tick()

        assertThat(reports)
            .`as`("reports of the refused row after two passes")
            .hasSize(1)
            .allMatch { it.contains("2") }
        assertThat(rows.cursors.count { it == 0L })
            .`as`("passes that started from the first row, one per tick that was due")
            .isEqualTo(2)
    }

    @Test
    fun `a sweep keys five unkeyed rows in three batches of two`() {
        val clock = MutableClock(start)
        val rows = UnkeyedRows(listOf(1, 2, 3, 4, 5))

        sweep(clock, rows).tick()

        assertThat(rows.productiveBatches)
            .`as`("rows keyed per batch that keyed any row")
            .containsExactly(2, 2, 1)
        assertThat(rows.remaining).`as`("rows left unkeyed after the sweep").isZero()
    }

    @Test
    fun `each batch starts after the last id of the batch before`() {
        val clock = MutableClock(start)
        val rows = UnkeyedRows(listOf(1, 2, 3, 4, 5))

        sweep(clock, rows).tick()

        assertThat(rows.cursors)
            .`as`("cursors the batches were asked from: zero, then the last id of each batch, then none left")
            .containsExactly(0L, 2L, 4L, 5L)
    }

    @Test
    fun `a pass that finds nothing starts the next pass from zero`() {
        val clock = MutableClock(start)
        val rows = UnkeyedRows(listOf(1, 2))
        val sweep = sweep(clock, rows)
        sweep.tick()
        val callsAfterFirstPass = rows.cursors.size

        clock.advance(Duration.ofHours(SIX_HOURS))
        sweep.tick()

        assertThat(rows.cursors.drop(callsAfterFirstPass).first())
            .`as`("cursor of the first batch of the second pass")
            .isZero()
    }

    @Test
    fun `a read-only instance runs no sweep`() {
        val clock = MutableClock(start)
        val rows = UnkeyedRows(listOf(1, 2, 3, 4, 5))

        sweep(clock, rows, readOnly = true).tick()

        assertThat(rows.cursors).`as`("calls to the store on a read-only instance").isEmpty()
        assertThat(rows.remaining).`as`("rows left unkeyed on a read-only instance").isEqualTo(5)
    }

    @Test
    fun `the first tick after a start runs the sweep`() {
        val clock = MutableClock(start)
        val rows = UnkeyedRows(listOf(1))

        sweep(clock, rows).tick()

        assertThat(rows.remaining).`as`("rows keyed by the first tick").isZero()
    }

    @Test
    fun `a sweep does not run again before six hours have passed`() {
        val clock = MutableClock(start)
        val rows = UnkeyedRows(emptyList())
        val sweep = sweep(clock, rows)
        sweep.tick()
        val callsAfterFirst = rows.cursors.size

        clock.advance(Duration.ofHours(SIX_HOURS).minusMinutes(1))
        sweep.tick()

        assertThat(rows.cursors.size).`as`("calls made before six hours have passed").isEqualTo(callsAfterFirst)
    }

    @Test
    fun `a sweep runs again once six hours have passed`() {
        val clock = MutableClock(start)
        val rows = UnkeyedRows(emptyList())
        val sweep = sweep(clock, rows)
        sweep.tick()
        val callsAfterFirst = rows.cursors.size

        clock.advance(Duration.ofHours(SIX_HOURS))
        sweep.tick()

        assertThat(rows.cursors.size).`as`("calls made after six hours have passed").isGreaterThan(callsAfterFirst)
    }

    private companion object {
        const val SIX_HOURS = 6L
    }

}
