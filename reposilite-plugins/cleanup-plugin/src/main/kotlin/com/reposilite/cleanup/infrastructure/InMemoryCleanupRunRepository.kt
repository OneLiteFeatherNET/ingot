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

package com.reposilite.cleanup.infrastructure

import com.reposilite.cleanup.CleanupRun
import com.reposilite.cleanup.CleanupRunRepository

/**
 * Keeps the run history in memory. Used when no database is configured, so the history does not survive a restart.
 */
internal class InMemoryCleanupRunRepository : CleanupRunRepository {

    private val runs = mutableListOf<CleanupRun>()

    override fun insert(run: CleanupRun) =
        synchronized(runs) { runs += run }

    override fun find(policyId: String?): List<CleanupRun> =
        synchronized(runs) {
            newestFirst(runs.filter { policyId == null || it.policyId == policyId })
        }

    override fun keepNewest(policyId: String, count: Int) {
        synchronized(runs) {
            val stale = newestFirst(runs.filter { it.policyId == policyId }).drop(count)
            runs.removeAll(stale.toSet())
        }
    }

    /** Runs that started at the same instant keep their insertion order reversed, so the latest insert comes first. */
    private fun newestFirst(list: List<CleanupRun>): List<CleanupRun> =
        list.asReversed().sortedByDescending { it.startedAt }

}
