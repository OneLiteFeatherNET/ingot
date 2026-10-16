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

package net.onelitefeather.ingot.search.application

import com.reposilite.configuration.shared.api.Doc
import com.reposilite.configuration.shared.api.SharedSettings
import com.reposilite.configuration.shared.api.ValidatedSettings
import io.javalin.openapi.JsonSchema

/**
 * Behaviour of artifact search that can change at runtime (artifact search spec, 3.9 and 7.3). Whether the feature exists
 * at all is a local setting, `searchEnabled`.
 */
@JsonSchema(requireNonNulls = false)
@Doc(title = "Search", description = "Artifact search keeps an index of the stored files and checks it against storage.")
data class SearchSettings(
    @get:Doc(title = "Verify enabled", description = "Run the nightly check that compares a sample of index rows with storage and repairs what differs.")
    val verifyEnabled: Boolean = true,
    @get:Doc(title = "Verify at", description = "Time of day (UTC, HH:mm) at which the nightly verify run starts.")
    val verifyAt: String = "02:30",
    @get:Doc(title = "Verify sample size", description = "Number of index rows one verify run compares with storage.")
    val verifySampleSize: Int = 2000,
    @get:Doc(title = "Rebuild schedule", description = "A full walk of storage on a fixed number of days. Off by default, because a full walk is expensive.")
    val rebuildSchedule: RebuildSchedule = RebuildSchedule(),
    @get:Doc(title = "Gap rebuild", description = "Start a rebuild after a start that finds a gap in the heartbeat, for example after a downgrade.")
    val gapRebuild: Boolean = true,
) : ValidatedSettings {

    override fun violations(): List<String> = buildList {
        if (!TIME_OF_DAY.matches(verifyAt)) add("verifyAt must be a time of day such as 02:30")
        if (verifySampleSize < 1) add("verifySampleSize must be at least 1")
        addAll(rebuildSchedule.violations())
    }

    private companion object {
        val TIME_OF_DAY = Regex("([01]\\d|2[0-3]):[0-5]\\d")
    }

}

@Doc(title = "Rebuild Schedule", description = "A scheduled full rebuild of the index.")
data class RebuildSchedule(
    @get:Doc(title = "Enabled", description = "Start the rebuild on the schedule.")
    val enabled: Boolean = false,
    @get:Doc(title = "At", description = "Time of day (UTC, HH:mm) at which a scheduled rebuild starts.")
    val at: String = "03:30",
    @get:Doc(title = "Every days", description = "Days between two scheduled rebuilds.")
    val everyDays: Int = 7,
) {

    fun violations(): List<String> = buildList {
        if (!Regex("([01]\\d|2[0-3]):[0-5]\\d").matches(at)) add("rebuildSchedule.at must be a time of day such as 03:30")
        if (everyDays < 1) add("rebuildSchedule.everyDays must be at least 1")
    }

}
