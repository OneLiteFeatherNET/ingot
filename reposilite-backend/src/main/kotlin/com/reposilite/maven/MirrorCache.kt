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

import com.reposilite.maven.api.Checksum
import com.reposilite.maven.api.METADATA_FILE
import com.reposilite.storage.api.Location
import java.net.URI
import java.net.URISyntaxException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** The longest version path the ledger keys. Longer paths are not recorded, so cleanup never considers them. */
internal const val MAX_PROVENANCE_PATH_LENGTH = 512

/** The longest repository id the ledger keys. Longer ids are not recorded. */
internal const val MAX_PROVENANCE_REPOSITORY_LENGTH = 128

/** The longest upstream host name the ledger keeps. Longer names are cut to this length. */
internal const val MAX_PROVENANCE_HOST_LENGTH = 255

/** The last access of a cached copy is written at most once per this interval and path. */
internal val MIRROR_CACHE_ACCESS_INTERVAL: Duration = Duration.ofHours(1)

/**
 * Storage of the provenance of mirror copies: one row per version directory that a mirror wrote into.
 *
 * A row is only ever added by a mirror store and removed by a deployment or a deletion in that directory, so a
 * directory without a row is never treated as a cached copy.
 */
internal interface MirrorCacheLedger {

    /**
     * Adds a row for [path] with [now] as store time and last access, unless the row already exists. [sourceHost] is
     * the upstream host name the copy came from, or null when it is unknown. An existing row keeps its host.
     */
    fun insertIfAbsent(repository: String, path: String, now: Instant, sourceHost: String? = null)

    fun contains(repository: String, path: String): Boolean

    /** The upstream host name that stored [path], or null when there is no row or the row has no host. */
    fun sourceHost(repository: String, path: String): String?

    /** The upstream host names of the version directories of [repository]. A directory without a host maps to null. */
    fun sourceHosts(repository: String): Map<String, String?>

    /**
     * Moves the last access of an existing row forward to [now]. A [now] older than the stored access leaves the row
     * as it is. Returns true when a row was updated.
     */
    fun markAccessed(repository: String, path: String, now: Instant): Boolean

    /** Removes the row of exactly [path]. */
    fun deleteVersion(repository: String, path: String)

    /** Removes the row of [path] and the rows of every path below it. */
    fun deleteSubtree(repository: String, path: String)

    /** The version directories of [repository] with their last access. */
    fun find(repository: String): Map<String, Instant>

}

/**
 * Records which version directories hold mirror copies and when they were last served.
 *
 * Access writes are throttled: a served copy writes its last access at most once per [MIRROR_CACHE_ACCESS_INTERVAL].
 */
internal class MirrorCacheService(
    private val ledger: MirrorCacheLedger,
    private val clock: Clock,
) {

    private val lastWritten = ConcurrentHashMap<Pair<String, String>, Instant>()

    /**
     * Called after a mirror stored [versionPath]. A directory that already held deployed artifacts is not marked:
     * a mirror copy written next to them must not make the deployed files look like copies. [sourceHost] is only
     * written when the directory gets its row; the first copy decides the host of the directory.
     */
    fun recordStored(repository: String, versionPath: String, holdsHostedArtifacts: Boolean, sourceHost: String? = null) {
        if (!isRecordable(repository, versionPath)) return
        if (holdsHostedArtifacts && !ledger.contains(repository, versionPath)) return
        ledger.insertIfAbsent(repository, versionPath, clock.instant(), sourceHost)
    }

    /** Called when a file of [versionPath] is served. Writes the last access unless one was written within the interval. */
    fun touch(repository: String, versionPath: String) {
        if (!isRecordable(repository, versionPath)) return

        val now = clock.instant()
        val key = repository to versionPath
        val previous = lastWritten[key]
        if (previous != null && previous.plus(MIRROR_CACHE_ACCESS_INTERVAL).isAfter(now)) return

        if (ledger.markAccessed(repository, versionPath, now)) {
            lastWritten[key] = now
        }
    }

    /** Called after a deployment into [versionPath]: the directory now holds hosted content and is no longer a copy. */
    fun forgetVersion(repository: String, versionPath: String) {
        ledger.deleteVersion(repository, versionPath)
        lastWritten.remove(repository to versionPath)
    }

    /** Called after [path] was deleted: the records of the path and of everything below it are dropped. */
    fun forgetSubtree(repository: String, path: String) {
        ledger.deleteSubtree(repository, path)
        lastWritten.keys.removeIf { (repo, entry) -> repo == repository && (entry == path || entry.startsWith("$path/")) }
    }

    /** The version directories that hold mirror copies of [repository], with their last access. */
    fun cachedVersions(repository: String): Map<String, Instant> =
        ledger.find(repository)

    private fun isRecordable(repository: String, versionPath: String): Boolean =
        versionPath.isNotEmpty() &&
            versionPath.length <= MAX_PROVENANCE_PATH_LENGTH &&
            repository.length <= MAX_PROVENANCE_REPOSITORY_LENGTH

}

/**
 * The upstream host name as the ledger keeps it: the host, plus the port when it is not the default of the scheme.
 * Credentials, path and query never enter the ledger. Null when the URL is invalid or names no host.
 */
internal fun upstreamSourceHost(upstream: String): String? {
    val uri = try {
        URI(upstream)
    } catch (_: URISyntaxException) {
        return null
    }
    val host = uri.host ?: return null
    val defaultPort = when (uri.scheme?.lowercase()) {
        "http" -> 80
        "https" -> 443
        else -> -1
    }
    val named = if (uri.port == -1 || uri.port == defaultPort) host else "$host:${uri.port}"
    return named.take(MAX_PROVENANCE_HOST_LENGTH)
}

/** The key of a location in the ledger: the path without a leading slash, as the storage walk reports it. */
internal fun Location.provenancePath(): String =
    toString().trimStart('/')

/** Metadata and checksum files are neutral for provenance: they do not make a directory a version. */
internal fun String.isMetadataOrChecksumFileName(): Boolean =
    startsWith(METADATA_FILE) || Checksum.entries.any { endsWith(".${it.extension}") }
