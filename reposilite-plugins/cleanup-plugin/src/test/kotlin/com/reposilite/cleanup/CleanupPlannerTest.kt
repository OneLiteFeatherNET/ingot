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
import com.reposilite.cleanup.application.CleanupScope.MIRROR_CACHE
import com.reposilite.cleanup.application.CleanupScope.RELEASES
import com.reposilite.cleanup.application.CleanupScope.SNAPSHOTS
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * The planner is pure: every case builds its inventory in memory and passes a fixed clock.
 * Ages are written as "days ago" relative to [NOW].
 */
internal class CleanupPlannerTest {

    private val now: Instant = Instant.parse("2026-10-15T12:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

    private fun daysAgo(days: Int): Instant =
        now.minus(Duration.ofDays(days.toLong()))

    /** A version directory `artifact/version` in [repository], last modified [ageDays] ago. */
    private fun version(
        version: String,
        ageDays: Int,
        artifact: String = "net/example/app",
        repository: String = "releases",
        sizeBytes: Long = 100,
        accessedDaysAgo: Int? = null,
        cached: Boolean = false,
    ) = ArtifactVersion(
        repository = repository,
        artifact = artifact,
        version = version,
        path = "$artifact/$version",
        sizeBytes = sizeBytes,
        lastModified = daysAgo(ageDays),
        lastAccessed = accessedDaysAgo?.let { daysAgo(it) },
        cached = cached,
    )

    private fun policy(
        scope: CleanupScope = RELEASES,
        keepLatest: Int? = null,
        olderThanDays: Int? = null,
        notDownloadedForDays: Int? = null,
        groupFilter: String? = null,
        repositories: List<String> = listOf("releases"),
    ) = CleanupPolicy(
        id = "policy",
        enabled = true,
        repositories = repositories,
        scope = scope,
        keepLatest = keepLatest,
        olderThanDays = olderThanDays,
        notDownloadedForDays = notDownloadedForDays,
        groupFilter = groupFilter,
    )

    private fun plan(policy: CleanupPolicy, vararg inventory: ArtifactVersion): List<String> =
        CleanupPlanner.plan(policy, inventory.toList(), clock).map { it.version }

    // keepLatest: the newest N versions of an artifact survive

    @Test
    fun `should delete every version except the newest one when keepLatest is 1`() {
        // given: three releases without any age limit
        val inventory = arrayOf(version("1.0", 400), version("1.1", 300), version("1.2", 200))

        // when: the policy keeps the latest version
        val deleted = plan(policy(keepLatest = 1), *inventory)

        // then: the two older releases are planned for deletion
        assertEquals(listOf("1.0", "1.1"), deleted)
    }

    @Test
    fun `should protect the newest N versions even when they are older than the age limit`() {
        // given: three releases that are all older than 30 days
        val inventory = arrayOf(version("1.0", 200), version("1.1", 200), version("1.2", 200))

        // when: two versions are kept and the age limit applies
        val deleted = plan(policy(keepLatest = 2, olderThanDays = 30), *inventory)

        // then: only the oldest release goes, the two newest survive regardless of their age
        assertEquals(listOf("1.0"), deleted)
    }

    @Test
    fun `should keep a version that is outside keepLatest while it is younger than the age limit`() {
        // given: a release outside the newest-one window that was published 10 days ago
        val inventory = arrayOf(version("1.0", 200), version("1.1", 10), version("1.2", 5))

        // when: the newest version is kept and deletion needs the version to be older than 30 days
        val deleted = plan(policy(keepLatest = 1, olderThanDays = 30), *inventory)

        // then: 1.1 is kept because it is young, only 1.0 is planned
        assertEquals(listOf("1.0"), deleted)
    }

    @Test
    fun `should keep the newest version even when it matches the age limit`() {
        // given: the only candidate that matches the age limit is also the newest version
        val inventory = arrayOf(version("1.0", 300), version("1.1", 100))

        // when: the policy keeps the latest version and deletes old ones
        val deleted = plan(policy(keepLatest = 1, olderThanDays = 30), *inventory)

        // then: the newest version is not deleted
        assertEquals(listOf("1.0"), deleted)
    }

    @Test
    fun `should keep the newest versions by version order rather than by age`() {
        // given: versions whose names sort differently from their modification times
        val inventory = arrayOf(version("1.10", 500), version("1.9", 1), version("1.2", 2))

        // when: only the newest version is kept
        val deleted = plan(policy(keepLatest = 1), *inventory)

        // then: 1.10 is the newest version and 1.9 and 1.2 are planned
        assertEquals(listOf("1.2", "1.9"), deleted)
    }

    @Test
    fun `should delete every version beyond keepLatest when no age limit is set`() {
        // given: four releases, all of them recent
        val inventory = arrayOf(version("1.0", 1), version("1.1", 1), version("1.2", 1), version("1.3", 1))

        // when: two versions are kept and no age limit is set
        val deleted = plan(policy(keepLatest = 2), *inventory)

        // then: the two oldest releases are planned, whatever their age
        assertEquals(listOf("1.0", "1.1"), deleted)
    }

    // olderThanDays: only versions last modified before the cut-off are deleted

    @Test
    fun `should delete only versions older than the age limit when keepLatest is not set`() {
        // given: releases published 10, 40 and 90 days ago
        val inventory = arrayOf(version("1.0", 10), version("1.1", 40), version("1.2", 90))

        // when: releases older than 30 days are selected
        val deleted = plan(policy(olderThanDays = 30), *inventory)

        // then: only the releases older than 30 days are planned
        assertEquals(listOf("1.1", "1.2"), deleted)
    }

    @Test
    fun `should delete nothing when every version is younger than the age limit`() {
        // given: releases published one day ago
        val inventory = arrayOf(version("1.0", 1), version("1.1", 1))

        // when: the age limit is 30 days
        val deleted = plan(policy(olderThanDays = 30), *inventory)

        // then: nothing is planned
        assertTrue(deleted.isEmpty())
    }

    @Test
    fun `should treat a version exactly at the cut-off as too young to delete`() {
        // given: a release last modified exactly 30 days ago, and an older one so that the artifact keeps a version
        val inventory = arrayOf(version("1.0", 30), version("1.1", 60))

        // when: the age limit is 30 days
        val deleted = plan(policy(olderThanDays = 30), *inventory)

        // then: the release at the cut-off stays, the older one is planned
        assertEquals(listOf("1.1"), deleted)
    }

    // notDownloadedForDays: the access time decides, the modification time is the fallback

    @Test
    fun `should use the last access time when it is known`() {
        // given: one version modified long ago but accessed recently, and one modified recently but not accessed for 100 days
        val inventory = arrayOf(
            version("1.0", ageDays = 200, accessedDaysAgo = 5),
            version("1.1", ageDays = 5, accessedDaysAgo = 100),
        )

        // when: versions that have not been downloaded for 30 days are selected
        val deleted = plan(policy(notDownloadedForDays = 30), *inventory)

        // then: only the version that has not been accessed is planned
        assertEquals(listOf("1.1"), deleted)
    }

    @Test
    fun `should fall back to the last modification time when no access time is known`() {
        // given: a version last modified 60 days ago and without any access record
        val inventory = arrayOf(version("1.0", 60), version("1.1", 1))

        // when: versions not downloaded for 30 days are selected
        val deleted = plan(policy(notDownloadedForDays = 30), *inventory)

        // then: the modification time stands in for the access time
        assertEquals(listOf("1.0"), deleted)
    }

    @Test
    fun `should require every configured age criterion to match`() {
        // given: a version older than 30 days but accessed five days ago
        val inventory = arrayOf(version("1.0", 100, accessedDaysAgo = 5), version("1.1", 1))

        // when: both criteria are set, so the version must be old and unused
        val deleted = plan(policy(olderThanDays = 30, notDownloadedForDays = 30), *inventory)

        // then: the recent access keeps it
        assertTrue(deleted.isEmpty())
    }

    // scope: which version kinds a policy may touch

    @Test
    fun `should only consider release versions for the RELEASES scope`() {
        // given: an old snapshot and an old release of the same artifact
        val inventory = arrayOf(version("1.0-SNAPSHOT", 60), version("1.0", 60), version("2.0", 1))

        // when: the RELEASES scope applies
        val deleted = plan(policy(scope = RELEASES, olderThanDays = 30), *inventory)

        // then: only the old release is planned and the snapshot is left alone
        assertEquals(listOf("1.0"), deleted)
    }

    @Test
    fun `should only consider snapshot versions for the SNAPSHOTS scope`() {
        // given: an old release and an old snapshot of an older line
        val inventory = arrayOf(version("0.9-SNAPSHOT", 60), version("1.0", 60), version("2.0", 1))

        // when: the SNAPSHOTS scope applies
        val deleted = plan(policy(scope = SNAPSHOTS, olderThanDays = 30), *inventory)

        // then: only the snapshot is planned and the release is never touched
        assertEquals(listOf("0.9-SNAPSHOT"), deleted)
    }

    @Test
    fun `should never delete a snapshot that is newer than the newest release`() {
        // given: a release 1.0 and snapshots of 0.9 (superseded) and 2.0 (in development), all old
        val inventory = arrayOf(version("1.0", 200), version("0.9-SNAPSHOT", 60), version("2.0-SNAPSHOT", 60))

        // when: old snapshots are selected
        val deleted = plan(policy(scope = SNAPSHOTS, olderThanDays = 30), *inventory)

        // then: the superseded snapshot goes and the snapshot of the next version stays
        assertEquals(listOf("0.9-SNAPSHOT"), deleted)
    }

    @Test
    fun `should delete a snapshot of the release it has been superseded by`() {
        // given: a release 1.0 and the snapshot of exactly that version
        val inventory = arrayOf(version("1.0", 200), version("1.0-SNAPSHOT", 60))

        // when: old snapshots are selected
        val deleted = plan(policy(scope = SNAPSHOTS, olderThanDays = 30), *inventory)

        // then: the snapshot of an already released version is deleted
        assertEquals(listOf("1.0-SNAPSHOT"), deleted)
    }

    @Test
    fun `should not apply the newest release guard when the artifact has no release`() {
        // given: an artifact with snapshots only
        val inventory = arrayOf(version("2.0-SNAPSHOT", 60), version("2.1-SNAPSHOT", 1))

        // when: old snapshots are selected
        val deleted = plan(policy(scope = SNAPSHOTS, olderThanDays = 30), *inventory)

        // then: the old snapshot is planned because no release exists to compare with
        assertEquals(listOf("2.0-SNAPSHOT"), deleted)
    }

    @Test
    fun `should only delete mirror cache entries for the MIRROR_CACHE scope`() {
        // given: a cached copy of 1.0 that was not downloaded for 90 days, and a locally deployed 1.1 that is not cached
        val inventory = arrayOf(
            version("1.0", 90, cached = true),
            version("1.1", 90, cached = false),
        )

        // when: unused mirror cache entries are selected
        val deleted = plan(policy(scope = MIRROR_CACHE, notDownloadedForDays = 30), *inventory)

        // then: only the cached copy is planned
        assertEquals(listOf("1.0"), deleted)
    }

    // never delete the only version

    @Test
    fun `should never delete the only version of an artifact`() {
        // given: a single release far older than the limit
        val inventory = arrayOf(version("1.0", 400))

        // when: the age limit applies
        val deleted = plan(policy(olderThanDays = 30), *inventory)

        // then: the artifact keeps its only version
        assertTrue(deleted.isEmpty())
    }

    @Test
    fun `should keep the newest version when every version would otherwise be deleted`() {
        // given: two releases that both match the age limit
        val inventory = arrayOf(version("1.0", 200), version("1.1", 100))

        // when: the age limit applies and no count is kept
        val deleted = plan(policy(olderThanDays = 30), *inventory)

        // then: the newest version survives and the other one is planned
        assertEquals(listOf("1.0"), deleted)
    }

    @Test
    fun `should keep the artifact alive when the only surviving version is in another scope`() {
        // given: a release that matches the age limit and a snapshot that is out of scope
        val inventory = arrayOf(version("1.0", 200), version("1.1-SNAPSHOT", 1))

        // when: the RELEASES scope applies
        val deleted = plan(policy(scope = RELEASES, olderThanDays = 30), *inventory)

        // then: the release is planned, which leaves the snapshot as the artifact's version
        assertEquals(listOf("1.0"), deleted)
    }

    // groupFilter: only matching artifacts are touched

    @Test
    fun `should only touch artifacts matching the group filter`() {
        // given: old releases of an artifact inside the filter and of one outside it
        val inventory = arrayOf(
            version("1.0", 200, artifact = "net/example/app"),
            version("1.1", 200, artifact = "net/example/app"),
            version("1.0", 200, artifact = "org/other/lib"),
            version("1.1", 200, artifact = "org/other/lib"),
        )

        // when: the group filter selects net/example
        val deleted = CleanupPlanner.plan(policy(olderThanDays = 30, groupFilter = "net/example/.*"), inventory.toList(), clock)

        // then: only the matching artifact is planned, and it keeps its newest version
        assertEquals(listOf("net/example/app/1.0"), deleted.map { it.path })
    }

    @Test
    fun `should match the group filter against the whole artifact path`() {
        // given: an old release of net/example/app
        val inventory = arrayOf(version("1.0", 200), version("1.1", 200))

        // when: a filter that only matches a suffix of the path is used
        val deleted = plan(policy(olderThanDays = 30, groupFilter = "example/app"), *inventory)

        // then: the filter does not match and nothing is planned
        assertTrue(deleted.isEmpty())
    }

    @Test
    fun `should treat an empty group filter as no filter`() {
        // given: an old release
        val inventory = arrayOf(version("1.0", 200), version("1.1", 200))

        // when: the group filter is empty
        val deleted = plan(policy(olderThanDays = 30, groupFilter = ""), *inventory)

        // then: every artifact is considered
        assertEquals(listOf("1.0"), deleted)
    }

    // repositories: only the repositories a policy lists are touched

    @Test
    fun `should ignore repositories the policy does not list`() {
        // given: an old release in a repository the policy does not name
        val inventory = arrayOf(version("1.0", 200, repository = "snapshots"), version("1.1", 200, repository = "snapshots"))

        // when: the policy lists only releases
        val deleted = plan(policy(olderThanDays = 30, repositories = listOf("releases")), *inventory)

        // then: nothing in snapshots is planned
        assertTrue(deleted.isEmpty())
    }

    @Test
    fun `should plan nothing when the policy lists no repository`() {
        // given: an old release
        val inventory = arrayOf(version("1.0", 200), version("1.1", 200))

        // when: the policy lists no repository
        val deleted = plan(policy(keepLatest = 1, repositories = emptyList()), *inventory)

        // then: nothing is planned
        assertTrue(deleted.isEmpty())
    }

    // reporting

    @Test
    fun `should report the size and the reasons of each planned deletion`() {
        // given: an old release of 2048 bytes that has not been downloaded for 60 days
        val inventory = arrayOf(version("1.0", 200, sizeBytes = 2048, accessedDaysAgo = 60), version("1.1", 1))

        // when: the policy combines an age limit with an unused limit
        val deletion = CleanupPlanner.plan(policy(olderThanDays = 30, notDownloadedForDays = 30), inventory.toList(), clock).single()

        // then: the deletion carries the path, the version, the size and both reasons
        assertEquals("net/example/app/1.0", deletion.path)
        assertEquals("releases", deletion.repository)
        assertEquals("1.0", deletion.version)
        assertEquals(2048L, deletion.sizeBytes)
        assertTrue(deletion.reason.contains("older than 30 days"), deletion.reason)
        assertTrue(deletion.reason.contains("not downloaded for 30 days"), deletion.reason)
    }

    @Test
    fun `should name the count limit in the reason when keepLatest caused the deletion`() {
        // given: three releases and a keepLatest of 1
        val inventory = arrayOf(version("1.0", 1), version("1.1", 1), version("1.2", 1))

        // when: the policy keeps the newest version
        val deletion = CleanupPlanner.plan(policy(keepLatest = 1), inventory.toList(), clock).first()

        // then: the reason names the count limit
        assertTrue(deletion.reason.contains("outside the newest 1 version"), deletion.reason)
    }

    @Test
    fun `should return the deletions sorted by repository and path`() {
        // given: an old and a recent release of two artifacts in two repositories
        val inventory = arrayOf(
            version("1.0", 200, artifact = "org/b", repository = "releases"),
            version("1.1", 1, artifact = "org/b", repository = "releases"),
            version("1.0", 200, artifact = "org/a", repository = "releases"),
            version("1.1", 1, artifact = "org/a", repository = "releases"),
            version("1.0", 200, artifact = "org/a", repository = "other"),
            version("1.1", 1, artifact = "org/a", repository = "other"),
        )

        // when: the policy selects both repositories
        val deleted = CleanupPlanner.plan(policy(olderThanDays = 30, repositories = listOf("releases", "other")), inventory.toList(), clock)

        // then: deletions are ordered by repository, then by path
        assertEquals(
            listOf("other:org/a/1.0", "releases:org/a/1.0", "releases:org/b/1.0"),
            deleted.map { "${it.repository}:${it.path}" }
        )
    }

}
