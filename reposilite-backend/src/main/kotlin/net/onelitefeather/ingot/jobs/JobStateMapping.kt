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
package net.onelitefeather.ingot.jobs

import net.onelitefeather.ingot.search.runs.RunStatus
import net.onelitefeather.ingot.search.runs.RunTrigger

/** Maps the states and triggers of the existing run tables to the shared shape of the Jobs page. */
object JobStateMapping {

    fun index(status: RunStatus): JobState = when (status) {
        RunStatus.RUNNING -> JobState.RUNNING
        RunStatus.COMPLETED -> JobState.SUCCEEDED
        RunStatus.FAILED -> JobState.FAILED
        RunStatus.CANCELLED -> JobState.CANCELLED
    }

    /**
     * The shared trigger, and the raw trigger as detail when the shared trigger is [JobTrigger.SYSTEM]. [RunTrigger] is
     * extensible, so every trigger other than manual and scheduled (startup, gap, backend and later ones) is a system run.
     */
    fun indexTrigger(trigger: RunTrigger): Pair<JobTrigger, String?> = when (trigger) {
        RunTrigger.MANUAL -> JobTrigger.MANUAL to null
        RunTrigger.SCHEDULED -> JobTrigger.SCHEDULED to null
        else -> JobTrigger.SYSTEM to trigger.name
    }

}
