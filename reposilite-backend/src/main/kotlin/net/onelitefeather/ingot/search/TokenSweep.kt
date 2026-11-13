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

import net.onelitefeather.ingot.search.backend.KeyBatch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The backfill of rows that have no GAV key (full text change, D6). Ticked by the scheduler; a due sweep runs on the IO pool
 * through [execute], and keys batches until [keyRows] reports that none is left. A read-only instance runs no sweep.
 *
 * The sweep is due on the first tick after a start, and then once every [interval]. The clock is injected, so the due rule
 * is tested without the wall clock.
 */
internal class TokenSweep(
    private val clock: Clock,
    private val batchSize: Int,
    private val interval: Duration,
    private val keyRows: (Long, Int) -> KeyBatch?,
    private val readOnly: () -> Boolean,
    private val execute: (() -> Unit) -> Unit,
    private val onFailure: (String) -> Unit,
    private val onSkipped: (String) -> Unit,
) {

    private val running = AtomicBoolean(false)

    /** The rows already reported as skipped. Kept for the life of the sweep, so a row that stays unkeyed is reported once. */
    private val reported = mutableSetOf<Long>()

    @Volatile
    private var lastRunAt: Instant? = null

    /** The id after which the next batch starts. Kept across runs, so a run that fails resumes where it stopped (full text change, D6). */
    @Volatile
    private var cursor: Long = 0L

    fun tick() {
        if (readOnly()) return
        val last = lastRunAt
        if (last != null && Duration.between(last, clock.instant()) < interval) return
        if (!running.compareAndSet(false, true)) return
        execute {
            try {
                sweep()
                lastRunAt = clock.instant()
            } catch (failure: Exception) {
                onFailure("Token sweep failed, it is retried on the next tick: ${failure.message}")
            } finally {
                running.set(false)
            }
        }
    }

    /**
     * Keys batches from the cursor to the end of the rows, each batch starting after the last id of the one before. When no row
     * is left the pass is complete, and the next pass starts from zero, so rows that are unkeyed again are found.
     */
    private fun sweep() {
        var after = cursor
        while (true) {
            val batch = keyRows(after, batchSize)
            if (batch == null) {
                cursor = 0L
                return
            }
            report(batch)
            after = batch.lastId
            cursor = after
        }
    }

    /**
     * Reports the rows of a batch that the database refused to key. Their cursor is passed like any other row, so they do not
     * block the pass; each is reported once, and a later pass finds it unkeyed again without a second report.
     */
    private fun report(batch: KeyBatch) {
        batch.skipped.filter { reported.add(it.id) }.forEach { skipped ->
            onSkipped(
                "Row ${skipped.id} cannot be keyed and is skipped, its words are not searchable until it is written again: " +
                    skipped.reason,
            )
        }
    }

}
