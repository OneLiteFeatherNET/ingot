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
import com.reposilite.cleanup.application.CleanupScope
import com.reposilite.storage.VersionComparator
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * One version directory of an artifact, as the planner sees it.
 *
 * @param repository the repository the version lives in
 * @param artifact the artifact directory relative to the repository root, for example `net/example/app`
 * @param version the name of the version directory, for example `1.0` or `1.0-SNAPSHOT`
 * @param path the version directory relative to the repository root, for example `net/example/app/1.0`
 * @param sizeBytes the sum of the sizes of all files in the version directory
 * @param lastModified the newest modification time of the files in the version directory
 * @param lastAccessed the last download, or null when no download record exists
 * @param cached true when the repository stores copies of mirrored artifacts and this version is one of them
 */
data class ArtifactVersion(
    val repository: String,
    val artifact: String,
    val version: String,
    val path: String,
    val sizeBytes: Long,
    val lastModified: Instant,
    val lastAccessed: Instant? = null,
    val cached: Boolean = false,
)

/**
 * A version a policy would delete.
 *
 * @param reason the criteria that made the version eligible, in words, for example "older than 30 days; not downloaded for 30 days"
 */
data class PlannedDeletion(
    val repository: String,
    val path: String,
    val version: String,
    val sizeBytes: Long,
    val reason: String,
)

/**
 * Decides which versions a policy deletes. It is pure: the inventory and the time come in as values.
 *
 * For each artifact of the policy's repositories, the decision runs in this order:
 *
 * 1. Scope. Only versions in the policy's scope are candidates (see [CleanupScope]).
 * 2. Keep rules. The newest `keepLatest` candidates are protected. Within SNAPSHOTS, a snapshot whose base version
 *    is newer than the newest release of the artifact is protected. Protection always wins.
 * 3. Delete rules. A candidate that is not protected is deleted when it matches every configured age criterion:
 *    `olderThanDays` compares the modification time, and `notDownloadedForDays` compares the last access time, or
 *    the modification time when no access record exists. A criterion that is not set does not have to match, and
 *    a policy with only `keepLatest` deletes every unprotected candidate.
 * 4. Last version. If the decision would leave an artifact without any version, its newest version is kept.
 *
 * A version exactly at the age cut-off is not older than the limit and therefore survives.
 */
internal object CleanupPlanner {

    private val byVersion: Comparator<String> = VersionComparator<String> { VersionComparator.asVersion(it) }
    private const val SNAPSHOT_SUFFIX = "-SNAPSHOT"

    fun plan(policy: CleanupPolicy, inventory: List<ArtifactVersion>, clock: Clock): List<PlannedDeletion> {
        val now = clock.instant()
        val groupFilter = policy.groupFilter?.takeIf { it.isNotBlank() }?.let { Regex(it) }
        val repositories = policy.repositories.toSet()

        return inventory
            .filter { it.repository in repositories }
            .filter { groupFilter == null || groupFilter.matches(it.artifact) }
            .groupBy { it.repository to it.artifact }
            .flatMap { (_, versions) -> planArtifact(policy, versions, now) }
            .sortedWith(compareBy({ it.repository }, { it.path }))
    }

    private fun planArtifact(policy: CleanupPolicy, versions: List<ArtifactVersion>, now: Instant): List<PlannedDeletion> {
        val candidates = versions.filter { policy.scope.contains(it) }
        val protectedPaths = mutableSetOf<String>()

        // keep rule: the newest N candidates
        val newestFirst = candidates.sortedWith(Comparator { a, b -> byVersion.compare(b.version, a.version) })
        policy.keepLatest?.let { count -> newestFirst.take(count).forEach { protectedPaths += it.path } }

        // keep rule: snapshots of work that is newer than the newest release
        if (policy.scope == CleanupScope.SNAPSHOTS) {
            val newestRelease = versions
                .filterNot { isSnapshot(it.version) }
                .maxWithOrNull(Comparator { a, b -> byVersion.compare(a.version, b.version) })
                ?.version
            if (newestRelease != null) {
                candidates
                    .filter { byVersion.compare(baseVersion(it.version), newestRelease) > 0 }
                    .forEach { protectedPaths += it.path }
            }
        }

        val deletions = candidates
            .filterNot { it.path in protectedPaths }
            .mapNotNull { version ->
                reasonFor(policy, version, now)?.let { PlannedDeletion(version.repository, version.path, version.version, version.sizeBytes, it) }
            }

        // the last version of an artifact is never deleted
        if (deletions.size == versions.size) {
            val newest = versions.maxWith(Comparator { a, b -> byVersion.compare(a.version, b.version) })
            return deletions.filterNot { it.path == newest.path }
        }
        return deletions
    }

    /** The reason when the version matches every configured criterion, null otherwise. */
    private fun reasonFor(policy: CleanupPolicy, version: ArtifactVersion, now: Instant): String? {
        val reasons = mutableListOf<String>()
        val criteria = mutableListOf<Boolean>()

        policy.keepLatest?.let { count ->
            reasons += "outside the newest $count ${if (count == 1) "version" else "versions"}"
        }
        policy.olderThanDays?.let { days ->
            val matches = version.lastModified.isBefore(now.minus(Duration.ofDays(days.toLong())))
            criteria += matches
            if (matches) reasons += "older than $days days"
        }
        policy.notDownloadedForDays?.let { days ->
            val matches = (version.lastAccessed ?: version.lastModified).isBefore(now.minus(Duration.ofDays(days.toLong())))
            criteria += matches
            if (matches) reasons += "not downloaded for $days days"
        }

        return if (criteria.all { it }) reasons.joinToString("; ").ifEmpty { "in scope of the policy" } else null
    }

    private fun CleanupScope.contains(version: ArtifactVersion): Boolean =
        when (this) {
            CleanupScope.RELEASES -> !isSnapshot(version.version)
            CleanupScope.SNAPSHOTS -> isSnapshot(version.version)
            CleanupScope.MIRROR_CACHE -> version.cached
        }

    private fun isSnapshot(version: String): Boolean =
        version.endsWith(SNAPSHOT_SUFFIX)

    private fun baseVersion(version: String): String =
        version.removeSuffix(SNAPSHOT_SUFFIX)

}
