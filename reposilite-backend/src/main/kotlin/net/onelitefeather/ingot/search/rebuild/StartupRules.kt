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

import net.onelitefeather.ingot.search.runs.RunTrigger
import java.time.Duration
import java.time.Instant

/** The rules that queue a rebuild when an instance starts (artifact search spec, 3.7), as a decision on plain values. */
internal object StartupRules {

    /** A heartbeat older than this means no instance indexed for a while. */
    val HEARTBEAT_STALE: Duration = Duration.ofMinutes(10)

    /**
     * Returns the trigger of the rebuild to queue, or null for none.
     *
     * - An empty index that never completed a rebuild is the first boot after the upgrade: [RunTrigger.STARTUP], whatever
     *   the gap setting says.
     * - An index with rows whose heartbeat is absent or older than [HEARTBEAT_STALE] may have missed writes made by an
     *   older version: [RunTrigger.GAP], unless [gapRebuild] is off.
     */
    fun decide(rowCount: Long, lastFullRebuild: Instant?, heartbeat: Instant?, gapRebuild: Boolean, now: Instant): RunTrigger? {
        if (rowCount == 0L) {
            return if (lastFullRebuild == null) RunTrigger.STARTUP else null
        }
        if (!gapRebuild) return null
        val stale = heartbeat == null || Duration.between(heartbeat, now) > HEARTBEAT_STALE
        return if (stale) RunTrigger.GAP else null
    }

}
