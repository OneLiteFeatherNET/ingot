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

package com.reposilite.cleanup

import com.reposilite.cleanup.application.CleanupPolicy
import java.time.Clock
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * Starts enabled policies at their daily time.
 *
 * [tick] is called periodically. A policy starts when the current minute is its scheduled minute in the zone of the
 * clock, and it starts at most once per day. A tick that comes after the scheduled minute does not catch up, so a
 * server that is down at the scheduled time skips that day's run.
 */
internal class CleanupScheduler(
    private val clock: Clock,
    private val policies: () -> List<CleanupPolicy>,
    private val start: (CleanupPolicy) -> Unit,
) {

    private val startedOn = ConcurrentHashMap<String, LocalDate>()

    fun tick() {
        val now = ZonedDateTime.ofInstant(clock.instant(), clock.zone)
        val today = now.toLocalDate()

        policies().forEach { policy ->
            if (!policy.enabled) return@forEach
            val due = policy.scheduledTime() ?: return@forEach
            if (due.hour != now.hour || due.minute != now.minute) return@forEach
            if (startedOn.put(policy.id, today) == today) return@forEach
            start(policy)
        }
    }

}
