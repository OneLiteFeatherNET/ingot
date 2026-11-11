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
    private val keyRows: (Int) -> Int,
    private val readOnly: () -> Boolean,
    private val execute: (() -> Unit) -> Unit,
    private val onFailure: (String) -> Unit,
) {

    private val running = AtomicBoolean(false)

    @Volatile
    private var lastRunAt: Instant? = null

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

    /** Keys batches until one batch keys no row. Returns nothing: the store is the record of what was keyed. */
    private fun sweep() {
        while (keyRows(batchSize) > 0) {
            // each batch is its own transaction in the store; the loop ends with the first empty batch
        }
    }

}
