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
package net.onelitefeather.ingot.nexusimport.jobs

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Ticks the workers of this replica. A tick runs every job that is queued or whose lease has run out, one after the other,
 * and [NexusImportService.run] decides for each one whether this replica may take it. Nothing runs while read-only mode is on,
 * and a job whose lease a live replica holds is never touched. The clock stamps the last tick for the status route; the
 * interval is the caller's, so tests tick explicitly and never wait.
 */
internal class JobScheduler(
    private val service: NexusImportService,
    private val store: JobStore,
    private val clock: Clock,
    private val readOnly: () -> Boolean,
    private val onFailure: (Throwable) -> Unit = {},
) : AutoCloseable {

    @Volatile
    private var lastTick: Instant? = null
    private var executor: ScheduledExecutorService? = null

    /** One pass over the runnable jobs. Returns how many jobs were run or resumed by this pass. */
    fun tick(): Int {
        lastTick = clock.instant()
        if (readOnly()) return 0
        var ran = 0
        for (job in store.runnable()) {
            if (readOnly()) break
            val outcome = service.run(job.id)
            if (outcome != RunOutcome.NOT_RUNNABLE && outcome != RunOutcome.NO_PASSWORD_HERE && outcome != RunOutcome.NOT_ACQUIRED) {
                ran++
            }
        }
        return ran
    }

    /** The instant of the last tick, or null before the first one. */
    fun lastTickAt(): Instant? = lastTick

    /** Starts the periodic tick on a daemon thread. A failing tick is reported and the next one still runs. */
    fun start(interval: Duration) {
        check(executor == null) { "The scheduler is already running" }
        executor = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "nexus-import-scheduler").apply { isDaemon = true }
        }.also {
            it.scheduleWithFixedDelay({
                try {
                    tick()
                } catch (failure: Throwable) {
                    onFailure(failure)
                }
            }, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS)
        }
    }

    override fun close() {
        executor?.shutdownNow()
        executor = null
    }
}
