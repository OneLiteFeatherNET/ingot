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

package net.onelitefeather.ingot.search.verify

import com.reposilite.scheduling.ScheduledJobClaims
import com.reposilite.scheduling.utcDayKey
import net.onelitefeather.ingot.search.application.SearchSettings
import net.onelitefeather.ingot.search.runs.RunHandle
import net.onelitefeather.ingot.search.runs.RunKind
import net.onelitefeather.ingot.search.runs.RunStore
import net.onelitefeather.ingot.search.runs.RunTrigger
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Starts the nightly verify run (artifact search spec, 3.8 and 3.9). [tick] is called periodically. When `verifyEnabled` is
 * on and the UTC time of day is at or after `verifyAt`, the instance claims `search-verify:<UTC date>` once per date, and
 * only the instance that gets the claim starts the run. A tick after `verifyAt` still verifies that date, so a restart at
 * the scheduled minute does not skip the day.
 *
 * While a rebuild or another verify run is live the tick waits and does not touch the claim, so the date is not lost.
 * The run is handed to [execute], so it does not block the thread that ticks.
 */
internal class VerifyScheduler(
    private val clock: Clock,
    private val settings: () -> SearchSettings,
    private val claims: ScheduledJobClaims,
    private val verify: VerifyService,
    private val runs: RunStore,
    private val execute: (() -> Unit) -> Unit = { it() },
    private val onFailure: (String) -> Unit = {},
) {

    private val running = AtomicBoolean(false)
    private var attemptedOn: LocalDate? = null

    fun tick() {
        if (running.get()) return
        val current = settings()
        if (!current.verifyEnabled) return
        val now = clock.instant().atZone(ZoneOffset.UTC)
        val today = now.toLocalDate()
        if (now.toLocalTime() < LocalTime.parse(current.verifyAt)) return
        if (attemptedOn == today) return
        if (runs.isLive(RunKind.REBUILD) || runs.isLive(RunKind.VERIFY)) return

        attemptedOn = today
        if (!claims.claim(utcDayKey("search-verify", clock))) return
        verify.start(RunTrigger.SCHEDULED)?.let { dispatch(it) }
    }

    private fun dispatch(handle: RunHandle) {
        running.set(true)
        execute {
            try {
                val outcome = verify.execute(handle)
                outcome.run.lastError?.let { onFailure("verify ${outcome.run.id} ended ${outcome.run.status}: $it") }
                outcome.report?.takeIf { it.degraded }?.let {
                    onFailure("verify ${outcome.run.id} found a drift of ${"%.1f".format(it.driftRatio * 100)} percent, a rebuild is recommended")
                }
            } finally {
                running.set(false)
            }
        }
    }

}
