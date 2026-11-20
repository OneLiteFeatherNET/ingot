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

package com.reposilite.cleanup.application

import com.reposilite.configuration.shared.api.Doc
import com.reposilite.configuration.shared.api.SettingsKey
import com.reposilite.configuration.shared.api.SharedSettings
import com.reposilite.configuration.shared.api.ValidatedSettings
import io.javalin.openapi.JsonSchema
import java.time.LocalTime

/**
 * The cleanup policies of the instance. Nothing is deleted unless a policy is run by hand or enabled with a schedule.
 *
 * The keep and delete rules of a policy are described in the planner (`CleanupPlanner`), which is the single place
 * where a version is judged. The rules in short: a version is deleted only when it is not one of the newest
 * `keepLatest` versions of its artifact, and it matches every configured age criterion.
 */
@JsonSchema(requireNonNulls = false)
@Doc(title = "Cleanup", description = "Cleanup policies delete old versions from repositories. A policy only ever acts on the repositories it lists.")
data class CleanupSettings(
    @get:Doc(title = "Policies", description = "The cleanup policies. Every policy is evaluated on its own.")
    val policies: List<CleanupPolicy> = emptyList(),
) : SharedSettings, ValidatedSettings {

    override fun violations(): List<String> {
        val duplicates = policies
            .groupBy { it.id }
            .filter { (id, sharing) -> id.isNotEmpty() && sharing.size > 1 }
            .keys
            .map { "policy id '$it' is used more than once" }
        return duplicates + policies.flatMap { it.violations() }
    }

}

/**
 * One cleanup policy.
 *
 * Keep rules are checked first: the newest [keepLatest] versions of an artifact are never deleted, and within the
 * SNAPSHOTS scope a snapshot of a version newer than the newest release is never deleted either. The remaining
 * versions are deleted when they match every configured age criterion ([olderThanDays] and
 * [notDownloadedForDays]). At least one of [keepLatest], [olderThanDays] or [notDownloadedForDays] has to be set,
 * and at least one version of every artifact always survives a run.
 */
@Doc(title = "Cleanup Policy", description = "A rule that deletes old versions of artifacts from the listed repositories.")
data class CleanupPolicy(
    @get:SettingsKey
    @get:Doc(title = "Id", description = "Unique id of this policy. Letters, digits, dots, dashes and underscores; the id is used in the API and on the maintenance page.")
    val id: String = "",
    @get:Doc(title = "Enabled", description = "Only enabled policies with a schedule run on their own. Manual runs work either way.")
    val enabled: Boolean = false,
    @get:Doc(title = "Repositories", description = "Ids of the repositories this policy acts on. An empty list means the policy acts on nothing.")
    val repositories: List<String> = emptyList(),
    @get:Doc(
        title = "Scope",
        description = "SNAPSHOTS: snapshot versions, never one newer than the newest release. RELEASES: release versions. MIRROR_CACHE: copies stored by a mirror since they are recorded; copies cached before that are never deleted."
    )
    val scope: CleanupScope = CleanupScope.RELEASES,
    @get:Doc(title = "Keep latest", description = "Keep the newest N versions of each artifact within the scope. Leave empty to set no count limit.")
    val keepLatest: Int? = null,
    @get:Doc(title = "Older than days", description = "Delete only versions last modified more than this many days ago. Leave empty to set no age limit.")
    val olderThanDays: Int? = null,
    @get:Doc(
        title = "Not downloaded for days",
        description = "Delete only versions that have not been downloaded for this many days. Without download records the file modification time is used. Most useful for MIRROR_CACHE."
    )
    val notDownloadedForDays: Int? = null,
    @get:Doc(
        title = "Group filter",
        description = "Regular expression that has to match the whole artifact path, for example net/onelitefeather/.*. Empty matches every artifact."
    )
    val groupFilter: String? = null,
    @get:Doc(title = "Schedule", description = "manual, or a daily time HH:mm in the time zone of the server, for example 04:30.")
    val schedule: String = "manual",
) : SharedSettings {

    /** The daily time of a scheduled policy, or null for a manual policy or an unreadable schedule. */
    fun scheduledTime(): LocalTime? =
        if (schedule == "manual" || !SCHEDULE_PATTERN.matches(schedule)) null
        else LocalTime.parse(schedule)

    fun violations(): List<String> = buildList {
        if (!ID_PATTERN.matches(id)) {
            add("policy id '$id' must be 1 to 64 letters, digits, dots, dashes or underscores, starting with a letter or digit")
        }
        listOf("keepLatest" to keepLatest, "olderThanDays" to olderThanDays, "notDownloadedForDays" to notDownloadedForDays)
            .forEach { (field, value) -> if (value != null && value < 1) add("$field of policy '$id' must be at least 1") }
        if (keepLatest == null && olderThanDays == null && notDownloadedForDays == null) {
            add("policy '$id' needs keepLatest, olderThanDays or notDownloadedForDays, otherwise it would delete every version")
        }
        groupFilter
            ?.takeIf { it.isNotBlank() }
            ?.let { pattern -> runCatching { Regex(pattern) }.onFailure { add("groupFilter of policy '$id' is not a valid regular expression") } }
        if (schedule != "manual" && scheduledTime() == null) {
            add("schedule of policy '$id' must be 'manual' or a time of day such as 04:30")
        }
    }

    private companion object {
        val ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
        val SCHEDULE_PATTERN = Regex("([01]\\d|2[0-3]):[0-5]\\d")
    }

}

/**
 * Which versions a policy may touch.
 *
 * - [SNAPSHOTS]: versions whose name ends with `-SNAPSHOT`. A snapshot of a version newer than the newest release
 *   of the same artifact is kept, because it belongs to work in progress.
 * - [RELEASES]: every version that is not a snapshot.
 * - [MIRROR_CACHE]: copies that a mirror stored into a repository that stores mirrored artifacts. Only directories
 *   with a recorded mirror store are considered. Versions cached before this was recorded have no record and are
 *   never deleted, and a directory that holds deployed artifacts is never a copy. A copy's age is judged by its last
 *   access, which is written at most once an hour when the copy is served.
 */
enum class CleanupScope {
    SNAPSHOTS,
    RELEASES,
    MIRROR_CACHE,
}
