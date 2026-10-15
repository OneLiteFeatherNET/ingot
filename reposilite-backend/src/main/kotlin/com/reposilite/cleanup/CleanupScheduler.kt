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
import com.reposilite.journalist.Journalist
import com.reposilite.scheduling.ScheduledJobClaims
import com.reposilite.scheduling.utcDayKey
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
 *
 * Every instance that shares a database runs this scheduler. Before a policy starts, the instance claims the day's run
 * under `cleanup:<policy>:<UTC date>`, and only the instance whose claim is granted starts it. A claim is taken before
 * the run, so an instance that dies after claiming skips that day's run as well.
 */
internal class CleanupScheduler(
    private val clock: Clock,
    private val policies: () -> List<CleanupPolicy>,
    private val start: (CleanupPolicy) -> Unit,
    private val claims: ScheduledJobClaims,
    private val journalist: Journalist,
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
            val key = utcDayKey("cleanup:${policy.id}", clock)
            if (!claims.claim(key)) {
                journalist.logger.debug("CLEANUP | Scheduled run of ${policy.id} is claimed by another instance, skipping ($key)")
                return@forEach
            }
            start(policy)
        }
    }

}
