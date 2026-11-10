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
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * Starts enabled policies at their daily time.
 *
 * [tick] is called periodically. A policy starts at its scheduled minute in the zone of the clock, and it starts at most
 * once per day. A tick after the scheduled minute does not start a run that was never started, so a server that is down
 * at the scheduled time skips that day's run.
 *
 * Every instance that shares a database runs this scheduler. A run of a day holds the lease `cleanup:<policy>:<UTC date>`
 * until [start] returns, and the day is then marked as done, so no instance runs it twice. A run that was started but
 * not completed, because its instance stopped, is taken over by a later tick once that lease expired.
 *
 * [start] runs the policy and returns when the run has ended.
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
            if (startedOn[policy.id] == today) return@forEach
            val atScheduledMinute = due.hour == now.hour && due.minute == now.minute
            val key = dayKey(policy.id, clock)
            val granted = if (atScheduledMinute) claims.lease(key, DAY_LEASE) else claims.takeover(key, DAY_LEASE)
            if (!granted) {
                if (atScheduledMinute) journalist.logger.debug("CLEANUP | Scheduled run of ${policy.id} is claimed by another instance, skipping ($key)")
                return@forEach
            }
            startedOn[policy.id] = today
            try {
                start(policy)
            } finally {
                claims.complete(key, done = true)
            }
        }
    }

    companion object {

        /** A run that stops renewing its day key for this long lets a later tick take the day over. */
        val DAY_LEASE: Duration = Duration.ofSeconds(60)

        /** The key of the day's run of a policy, shared with the run so that the run can hold it while it deletes. */
        fun dayKey(policyId: String, clock: Clock): String = utcDayKey("cleanup:$policyId", clock)

    }

}
