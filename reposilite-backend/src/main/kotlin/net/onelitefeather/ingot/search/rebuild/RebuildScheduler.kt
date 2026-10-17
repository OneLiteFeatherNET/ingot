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

package net.onelitefeather.ingot.search.rebuild

import com.reposilite.scheduling.ScheduledJobClaims
import com.reposilite.scheduling.utcDayKey
import net.onelitefeather.ingot.search.application.SearchSettings
import net.onelitefeather.ingot.search.runs.RunHandle
import net.onelitefeather.ingot.search.runs.RunTrigger
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Decides when this instance starts a rebuild (artifact search spec, 3.7 and 3.9). [tick] is called periodically and does,
 * in this order, at most one thing:
 *
 * 1. takes over a rebuild whose instance died and continues it from its cursor;
 * 2. starts the rebuild the startup rules asked for ([startup], once);
 * 3. starts the scheduled rebuild when `rebuildSchedule` is enabled, the UTC time of day is at or after `at`, at least
 *    `everyDays` days have passed since the last full rebuild, and this instance wins the claim
 *    `search-rebuild:<UTC date>`. A restart after `at` does not skip the day.
 *
 * Whichever instance wins the claim still has to win the `rebuild.active` lock, so one rebuild runs at a time. The run
 * itself is handed to [execute], so a long rebuild does not block the thread that ticks. One run per instance at a time.
 */
internal class RebuildScheduler(
    private val clock: Clock,
    private val settings: () -> SearchSettings,
    private val claims: ScheduledJobClaims,
    private val rebuild: RebuildService,
    startup: RunTrigger?,
    private val execute: (() -> Unit) -> Unit = { it() },
    private val onFailure: (String) -> Unit = {},
) {

    private val pendingStartup = AtomicReference(startup)
    private val running = AtomicBoolean(false)
    private var attemptedOn: LocalDate? = null

    fun tick() {
        if (running.get()) return
        rebuild.takeOver()?.let { return dispatch(it) }
        pendingStartup.getAndSet(null)?.let { trigger ->
            rebuild.start(trigger)?.let { dispatch(it) }
            return
        }
        scheduledStart()?.let { dispatch(it) }
    }

    private fun scheduledStart(): RunHandle? {
        val schedule = settings().rebuildSchedule
        if (!schedule.enabled) return null
        val now = clock.instant().atZone(ZoneOffset.UTC)
        val today = now.toLocalDate()
        if (now.toLocalTime() < LocalTime.parse(schedule.at)) return null
        if (attemptedOn == today) return null

        val last = rebuild.status().lastFullRebuild?.atZone(ZoneOffset.UTC)?.toLocalDate()
        if (last != null && ChronoUnit.DAYS.between(last, today) < schedule.everyDays) return null

        attemptedOn = today
        if (!claims.claim(utcDayKey("search-rebuild", clock))) return null
        return rebuild.start(RunTrigger.SCHEDULED)
    }

    private fun dispatch(handle: RunHandle) {
        running.set(true)
        execute {
            try {
                val finished = rebuild.execute(handle)
                finished.lastError?.let { onFailure("rebuild ${finished.id} ended ${finished.status}: $it") }
            } finally {
                running.set(false)
            }
        }
    }

}
