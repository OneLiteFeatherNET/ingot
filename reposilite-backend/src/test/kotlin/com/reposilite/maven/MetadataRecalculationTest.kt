/*
 * Copyright (c) 2023 dzikoysk
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

package com.reposilite.maven

import com.reposilite.maven.api.Metadata
import com.reposilite.maven.api.Snapshot
import com.reposilite.maven.api.SnapshotVersion
import com.reposilite.maven.api.Versioning
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class MetadataRecalculationTest {

    private val fixedInstant = Instant.parse("2026-10-15T10:20:30Z")
    private val lastUpdated = lastUpdatedTimestamp(Clock.fixed(fixedInstant, ZoneId.of("UTC")))

    private fun artifactMetadata(vararg versions: String, latest: String? = null, release: String? = null) = Metadata(
        groupId = "com.example",
        artifactId = "app",
        versioning = Versioning(latest = latest, release = release, _versions = versions.toList(), lastUpdated = "20260101000000")
    )

    @Test
    fun `removing a version drops it from the version list`() {
        // given: artifact metadata listing three versions
        val metadata = artifactMetadata("1.0", "1.1", "2.0-SNAPSHOT", latest = "2.0-SNAPSHOT", release = "1.1")

        // when: the version 1.1 is removed
        val result = metadata.removeVersion("1.1", lastUpdated)

        // then: the remaining versions are listed in Maven order
        assertThat(result.versioning?.versions).containsExactly("1.0", "2.0-SNAPSHOT")
    }

    @Test
    fun `removing the newest release recomputes latest and release`() {
        // given: artifact metadata where 1.1 is both the newest release and not the latest
        val metadata = artifactMetadata("1.0", "1.1", "2.0-SNAPSHOT", latest = "2.0-SNAPSHOT", release = "1.1")

        // when: the version 1.1 is removed
        val result = metadata.removeVersion("1.1", lastUpdated)

        // then: latest stays the snapshot 2.0-SNAPSHOT and release falls back to 1.0
        assertThat(result.versioning?.latest).`as`("latest after removing 1.1").isEqualTo("2.0-SNAPSHOT")
        assertThat(result.versioning?.release).`as`("release after removing 1.1").isEqualTo("1.0")
    }

    @Test
    fun `latest and release follow Maven version ordering rather than string ordering`() {
        // given: versions where string ordering would pick 1.9 over 1.10
        val metadata = artifactMetadata("1.9", "1.10", "2.0-SNAPSHOT", latest = "2.0-SNAPSHOT", release = "1.10")

        // when: the snapshot is removed
        val result = metadata.removeVersion("2.0-SNAPSHOT", lastUpdated)

        // then: 1.10 is the newest remaining version
        assertThat(result.versioning?.latest).isEqualTo("1.10")
        assertThat(result.versioning?.release).isEqualTo("1.10")
    }

    @Test
    fun `release is absent when only snapshots remain`() {
        // given: a release 1.0 and a snapshot 2.0-SNAPSHOT
        val metadata = artifactMetadata("1.0", "2.0-SNAPSHOT", latest = "2.0-SNAPSHOT", release = "1.0")

        // when: the release 1.0 is removed
        val result = metadata.removeVersion("1.0", lastUpdated)

        // then: the snapshot is still latest, but there is no release any more
        assertThat(result.versioning?.latest).isEqualTo("2.0-SNAPSHOT")
        assertThat(result.versioning?.release).`as`("release without any non-snapshot version").isNull()
    }

    @Test
    fun `removing the only remaining version leaves an empty version list`() {
        // given: metadata with a single version
        val metadata = artifactMetadata("1.0", latest = "1.0", release = "1.0")

        // when: that version is removed
        val result = metadata.removeVersion("1.0", lastUpdated)

        // then: no versions are left, which tells the caller to drop the metadata file
        assertThat(result.versioning?.versions).isEmpty()
    }

    @Test
    fun `removing an unknown version keeps the version list unchanged`() {
        // given: metadata with two versions
        val metadata = artifactMetadata("1.0", "1.1", latest = "1.1", release = "1.1")

        // when: a version that is not listed is removed
        val result = metadata.removeVersion("9.9", lastUpdated)

        // then: nothing is dropped
        assertThat(result.versioning?.versions).containsExactly("1.0", "1.1")
    }

    @Test
    fun `lastUpdated is taken from the injected clock`() {
        // given: a clock fixed at a known instant
        val clock = Clock.fixed(fixedInstant, ZoneId.of("UTC"))

        // when: the timestamp is rendered
        val timestamp = lastUpdatedTimestamp(clock)

        // then: it is the Maven yyyyMMddHHmmss form of that instant
        assertThat(timestamp).isEqualTo("20261015102030")
    }

    @Test
    fun `lastUpdated is rendered in UTC whatever zone the clock uses`() {
        // given: the same instant seen from Berlin, which is two hours ahead of UTC in October
        val clock = Clock.fixed(fixedInstant, ZoneId.of("Europe/Berlin"))

        // when: the timestamp is rendered
        val timestamp = lastUpdatedTimestamp(clock)

        // then: Maven expects UTC, so the hour stays 10
        assertThat(timestamp).isEqualTo("20261015102030")
    }

    @Test
    fun `pruning snapshot versions drops only the entries rejected by the predicate`() {
        // given: jar and pom entries for two timestamped builds
        val firstJar = snapshotVersion("jar", "2.0-20261015.101010-1", "20261015101010")
        val secondJar = snapshotVersion("jar", "2.0-20261015.111111-2", "20261015111111")
        val secondPom = snapshotVersion("pom", "2.0-20261015.111111-2", "20261015111111")
        val metadata = snapshotMetadata(
            snapshot = Snapshot(timestamp = "20261015.111111", buildNumber = 2),
            entries = listOf(firstJar, secondJar, secondPom),
        )

        // when: the jar of the second build is gone
        val result = metadata.pruneSnapshotVersions(isPresent = { it != secondJar }, lastUpdated = lastUpdated)

        // then: only the jar entry of the second build is removed, the pom of that build stays
        assertThat(result.versioning?.snapshotVersions).containsExactly(firstJar, secondPom)
    }

    @Test
    fun `pruning keeps the snapshot element while its build is still referenced`() {
        // given: the latest build 2 is still referenced by its pom
        val firstJar = snapshotVersion("jar", "2.0-20261015.101010-1", "20261015101010")
        val secondJar = snapshotVersion("jar", "2.0-20261015.111111-2", "20261015111111")
        val secondPom = snapshotVersion("pom", "2.0-20261015.111111-2", "20261015111111")
        val metadata = snapshotMetadata(
            snapshot = Snapshot(timestamp = "20261015.111111", buildNumber = 2),
            entries = listOf(firstJar, secondJar, secondPom),
        )

        // when: only the jar of build 2 is removed
        val result = metadata.pruneSnapshotVersions(isPresent = { it != secondJar }, lastUpdated = lastUpdated)

        // then: the snapshot element still describes build 2
        assertThat(result.versioning?.snapshot).isEqualTo(Snapshot(timestamp = "20261015.111111", buildNumber = 2))
    }

    @Test
    fun `pruning moves the snapshot element to the newest remaining build`() {
        // given: build 2 is the latest build and has only a jar entry
        val firstJar = snapshotVersion("jar", "2.0-20261015.101010-1", "20261015101010")
        val secondJar = snapshotVersion("jar", "2.0-20261015.111111-2", "20261015111111")
        val metadata = snapshotMetadata(
            snapshot = Snapshot(timestamp = "20261015.111111", buildNumber = 2),
            entries = listOf(firstJar, secondJar),
        )

        // when: the jar of build 2 is removed
        val result = metadata.pruneSnapshotVersions(isPresent = { it != secondJar }, lastUpdated = lastUpdated)

        // then: the snapshot element points to build 1, the newest build left
        assertThat(result.versioning?.snapshot).isEqualTo(Snapshot(timestamp = "20261015.101010", buildNumber = 1))
    }

    @Test
    fun `pruning drops the snapshot element when no snapshot version is left`() {
        // given: a single timestamped build
        val jar = snapshotVersion("jar", "2.0-20261015.111111-2", "20261015111111")
        val metadata = snapshotMetadata(
            snapshot = Snapshot(timestamp = "20261015.111111", buildNumber = 2),
            entries = listOf(jar),
        )

        // when: every entry is rejected
        val result = metadata.pruneSnapshotVersions(isPresent = { false }, lastUpdated = lastUpdated)

        // then: no snapshot version and no snapshot element are left
        assertThat(result.versioning?.snapshotVersions).isEmpty()
        assertThat(result.versioning?.snapshot).isNull()
    }

    @Test
    fun `pruning stamps lastUpdated with the injected clock`() {
        // given: version-level metadata with an old lastUpdated
        val jar = snapshotVersion("jar", "2.0-20261015.111111-2", "20261015111111")
        val metadata = snapshotMetadata(snapshot = null, entries = listOf(jar))

        // when: a snapshot file is rejected
        val result = metadata.pruneSnapshotVersions(isPresent = { false }, lastUpdated = lastUpdated)

        // then: lastUpdated carries the new timestamp
        assertThat(result.versioning?.lastUpdated).isEqualTo("20261015102030")
    }

    @Test
    fun `snapshot file name combines artifact, value, classifier and extension`() {
        // given: a snapshot entry with a classifier
        val entry = SnapshotVersion(classifier = "sources", extension = "jar", value = "2.0-20261015.111111-2")

        // when: the file name is derived
        val name = snapshotFileName("app", entry)

        // then: it matches the timestamped file Maven deploys
        assertThat(name).isEqualTo("app-2.0-20261015.111111-2-sources.jar")
    }

    @Test
    fun `snapshot file name without classifier omits the classifier segment`() {
        // given: a snapshot entry without classifier
        val entry = SnapshotVersion(extension = "pom", value = "2.0-20261015.111111-2")

        // when: the file name is derived
        val name = snapshotFileName("app", entry)

        // then: the classifier segment is absent
        assertThat(name).isEqualTo("app-2.0-20261015.111111-2.pom")
    }

    @Test
    fun `snapshot file name treats an empty classifier element as no classifier`() {
        // given: an entry whose classifier was read back from an empty XML element
        val entry = SnapshotVersion(classifier = "", extension = "jar", value = "2.0-20261015.111111-2")

        // when: the file name is derived
        val name = snapshotFileName("app", entry)

        // then: no dangling dash is added before the extension
        assertThat(name).isEqualTo("app-2.0-20261015.111111-2.jar")
    }

    @Test
    fun `snapshot file name is unknown for an entry without extension`() {
        // given: a snapshot entry without extension
        val entry = SnapshotVersion(value = "2.0-20261015.111111-2")

        // when: the file name is derived
        val name = snapshotFileName("app", entry)

        // then: no file can be checked, so the name is unknown
        assertThat(name).`as`("file name of an entry without extension").isNull()
    }

    private fun snapshotVersion(extension: String, value: String, updated: String) =
        SnapshotVersion(extension = extension, value = value, updated = updated)

    private fun snapshotMetadata(snapshot: Snapshot?, entries: List<SnapshotVersion>) = Metadata(
        groupId = "com.example",
        artifactId = "app",
        version = "2.0-SNAPSHOT",
        versioning = Versioning(snapshot = snapshot, _snapshotVersions = entries, lastUpdated = "20260101000000")
    )

}
