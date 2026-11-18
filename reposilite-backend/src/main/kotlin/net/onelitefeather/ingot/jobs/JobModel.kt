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

import java.time.Instant

enum class JobState { QUEUED, RUNNING, PAUSED, SUCCEEDED, PARTIAL, FAILED, CANCELLED }

enum class JobTrigger { MANUAL, SCHEDULED, SYSTEM }

data class JobCount(val key: String, val label: String, val value: Long)

data class JobFailure(val message: String)

/** The shape every source shares. The id is `<source>:<local id>`. */
data class JobRun(
    val id: String,
    val source: String,
    val kind: String,
    val label: String,
    val trigger: JobTrigger,
    val state: JobState,
    val startedAt: Instant,
    val triggerDetail: String? = null,
    val finishedAt: Instant? = null,
    val durationMs: Long? = null,
    val counts: List<JobCount> = emptyList(),
    val failures: List<JobFailure> = emptyList(),
    val failuresTruncated: Boolean = false,
) {

    val localId: String
        get() = id.substringAfter(':')

    val cursor: JobCursor
        get() = JobCursor(startedAt, source, localId)

}

/** The position of the last item of a page. */
data class JobCursor(val startedAt: Instant, val source: String, val localId: String)
