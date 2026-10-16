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
import com.reposilite.storage.VersionComparator
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val lastUpdatedFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
private val snapshotValuePattern = Regex("^.+-(\\d{8}\\.\\d{6})-(\\d+)$")
internal const val SNAPSHOT_SUFFIX = "-SNAPSHOT"

/**
 * Maven writes `lastUpdated` in UTC, whatever zone the clock is bound to.
 */
internal fun lastUpdatedTimestamp(clock: Clock): String =
    lastUpdatedFormatter.format(Instant.now(clock).atZone(ZoneOffset.UTC))

/**
 * Removes [version] from the artifact-level `<versions>` list and recomputes `<latest>` and `<release>`.
 *
 * `latest` is the newest remaining version by Maven ordering, `release` the newest remaining version that is not a snapshot.
 * An empty version list in the result means that no version is left, the caller decides what happens to the file.
 */
internal fun Metadata.removeVersion(version: String, lastUpdated: String): Metadata {
    val remaining = VersionComparator.sortStrings(
        versioning?.versions.orEmpty().asSequence().filter { it != version }.distinct()
    ).toList()

    return copy(
        versioning = (versioning ?: Versioning()).copy(
            latest = remaining.lastOrNull(),
            release = remaining.lastOrNull { !it.endsWith(SNAPSHOT_SUFFIX) },
            _versions = remaining,
            lastUpdated = lastUpdated,
        )
    )
}

/**
 * Keeps only the `<snapshotVersion>` entries accepted by [isPresent] and updates `<snapshot>` when the build it describes is gone.
 *
 * [isPresent] is asked for every entry whether the file the entry describes still exists in the version directory.
 */
internal fun Metadata.pruneSnapshotVersions(isPresent: (SnapshotVersion) -> Boolean, lastUpdated: String): Metadata {
    val current = versioning ?: Versioning()
    val kept = current.snapshotVersions.orEmpty().filter(isPresent)

    return copy(
        versioning = current.copy(
            _snapshotVersions = kept,
            snapshot = current.snapshot.recomputedFor(kept),
            lastUpdated = lastUpdated,
        )
    )
}

/**
 * File name Maven deploys for a snapshot entry, or null when the entry does not name an extension.
 *
 * An empty `<classifier/>` element is read back as an empty string, so it counts as no classifier.
 */
internal fun snapshotFileName(artifactId: String, entry: SnapshotVersion): String? {
    val extension = entry.extension?.takeIf { it.isNotBlank() } ?: return null
    val value = entry.value ?: return null
    val classifier = entry.classifier?.takeIf { it.isNotBlank() }?.let { "-$it" }.orEmpty()
    return "$artifactId-$value$classifier.$extension"
}

private data class SnapshotBuild(val timestamp: String, val buildNumber: Int)

private fun SnapshotVersion.build(): SnapshotBuild? =
    value
        ?.let { snapshotValuePattern.matchEntire(it) }
        ?.let { SnapshotBuild(timestamp = it.groupValues[1], buildNumber = it.groupValues[2].toInt()) }

/**
 * Whether this entry describes the build that [snapshot] names. Without a [snapshot] no entry matches.
 */
internal fun SnapshotVersion.isBuildOf(snapshot: Snapshot?): Boolean =
    snapshot != null && build()?.let { it.timestamp == snapshot.timestamp && it.buildNumber == snapshot.buildNumber } == true

/**
 * Keeps the `<snapshot>` element while one of the remaining entries still belongs to its build.
 * Otherwise it points at the newest remaining build, or disappears when no entry is left.
 */
private fun Snapshot?.recomputedFor(entries: List<SnapshotVersion>): Snapshot? {
    if (entries.isEmpty()) {
        return null
    }

    val current = this
    if (entries.any { it.isBuildOf(current) }) {
        return current
    }

    val newest = entries
        .mapNotNull { entry -> entry.build()?.let { build -> (entry.updated.orEmpty()) to build } }
        .maxByOrNull { it.first }
        ?.second
        ?: return current

    return Snapshot(timestamp = newest.timestamp, buildNumber = newest.buildNumber)
}
