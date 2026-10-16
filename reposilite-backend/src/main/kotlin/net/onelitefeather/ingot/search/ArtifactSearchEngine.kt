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

package net.onelitefeather.ingot.search

import net.onelitefeather.ingot.search.access.SearchAccess
import java.time.Instant

/**
 * The read side of artifact search (artifact search spec, 7.1). Every read takes the caller's [SearchAccess]; an engine
 * translates it into its own query language and never sees subjects or grants. The write side is the outbox and the
 * index writer, which feed whichever engine is active.
 */
interface ArtifactSearchEngine {

    /** The name of the engine in the local configuration (`searchEngine`). */
    val id: String

    /** One page of groups (GAVs) matching [query] and visible through [access], with the index status (4.8). */
    fun search(query: ArtifactSearchQuery, access: SearchAccess): ArtifactSearchPage

    /** The file counts per facet bucket over the visible rows (4.4). */
    fun facets(query: ArtifactSearchQuery, access: SearchAccess): SearchFacets

    /** One page of the files of one GAV, ordered by path (4.5). */
    fun files(query: ArtifactFilesQuery, access: SearchAccess): ArtifactFilePage

    /** The state of the index, computed per call (4.8). */
    fun status(): IndexStatus

}

/** The request of a group search. Every filter is optional; the defaults match the parameter table of section 4.1. */
data class ArtifactSearchQuery(
    /** Free text, at most 200 characters, split into at most 5 tokens (4.2). */
    val q: String? = null,
    /** Prefix on the lower-cased groupId. */
    val groupId: String? = null,
    /** Prefix on the lower-cased artifactId. */
    val artifactId: String? = null,
    /** Exact version, lower-cased comparison. Cannot be combined with [versionPrefix]. */
    val version: String? = null,
    /** Prefix on the lower-cased version. Cannot be combined with [version]. */
    val versionPrefix: String? = null,
    /** Exact repository names; an OR over the list. */
    val repositories: List<String> = emptyList(),
    /** Exact, lower-cased; an OR over the list. */
    val extensions: List<String> = emptyList(),
    /** Exact and case-sensitive; the value `-` selects files without a classifier. An OR over the list. */
    val classifiers: List<String> = emptyList(),
    /** 40 hexadecimal characters, matched against the stored checksum. */
    val sha1: String? = null,
    val snapshot: SnapshotFilter = SnapshotFilter.ALL,
    val origin: OriginFilter = OriginFilter.ALL,
    /** Inclusive lower bound of the last modification. */
    val modifiedAfter: Instant? = null,
    /** Exclusive upper bound of the last modification. */
    val modifiedBefore: Instant? = null,
    /** Inclusive, in bytes. */
    val sizeMin: Long? = null,
    /** Inclusive, in bytes. */
    val sizeMax: Long? = null,
    val sort: SearchSort = SearchSort.GROUP_ID,
    val order: SearchOrder = SearchOrder.ASC,
    /** 0-based. */
    val page: Int = 0,
    /** At most 100. */
    val size: Int = 20,
)

enum class SnapshotFilter { TRUE, FALSE, ALL }

enum class OriginFilter { HOSTED, MIRROR, ALL }

enum class SearchSort { GROUP_ID, ARTIFACT_ID, VERSION, MODIFIED, SIZE }

enum class SearchOrder { ASC, DESC }

/**
 * One page of groups. [total] counts groups and is capped: when [totalCapped] is true there are at least
 * [TOTAL_CAP] groups and [total] is [TOTAL_CAP]. [indexStatus] is the state of the index at the time of the request.
 */
data class ArtifactSearchPage(
    val items: List<ArtifactSearchItem>,
    val page: Int,
    val size: Int,
    val total: Long,
    val totalCapped: Boolean,
    val indexStatus: IndexStatus,
) {
    companion object {
        /** The number of groups above which [total] stops counting (4.3). */
        const val TOTAL_CAP = 10_001L
    }
}

/**
 * One group (GAV). [fileCount] counts the files that match the request's filters; [repositories] lists every visible
 * repository that holds the version, with its origin. [latestVersion] ignores the filters (4.6) and is null when the
 * artifact is above the read cap.
 */
data class ArtifactSearchItem(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val latestVersion: String?,
    val snapshot: Boolean,
    val repositories: List<RepositoryPresence>,
    val fileCount: Long,
    val lastModified: Instant,
    /** `/browse/<repository>/<directory>` for the first repository of [repositories], segments percent-encoded. */
    val browseLink: String,
)

/** A repository that holds a version, with the origin of its copy. */
data class RepositoryPresence(val name: String, val origin: Origin)

/** The buckets of every facet, each counting files over the visible rows (4.4). */
data class SearchFacets(
    val repository: List<FacetBucket>,
    val extension: List<FacetBucket>,
    val classifier: List<FacetBucket>,
    val origin: List<FacetBucket>,
    val snapshot: List<FacetBucket>,
)

/** One bucket of a facet. Classifier `null` is the bucket `-`. */
data class FacetBucket(val value: String, val count: Long)

/** The request of the files of one GAV (4.5, 6.2). The coordinates are required and compared case-insensitively. */
data class ArtifactFilesQuery(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val origin: OriginFilter = OriginFilter.ALL,
    val repositories: List<String> = emptyList(),
    /** 0-based. */
    val page: Int = 0,
    /** At most 200. */
    val size: Int = 50,
)

data class ArtifactFilePage(
    val items: List<ArtifactFileItem>,
    val page: Int,
    val size: Int,
    val total: Long,
    val indexStatus: IndexStatus,
)

/**
 * One stored file of a GAV. [sourceHost] is set for a mirror copy whose upstream host is known. [browseLink] is the
 * dashboard route of the file.
 */
data class ArtifactFileItem(
    val repository: String,
    val path: String,
    val name: String,
    val classifier: String?,
    val extension: String,
    val size: Long,
    val lastModified: Instant,
    val origin: Origin,
    val sourceHost: String?,
    val sha1: String?,
    val browseLink: String,
)

/** The state of the index (4.8). [lagSeconds] is the age of the oldest pending outbox row, 0 when there is none. */
data class IndexStatus(
    val state: IndexState,
    val lagSeconds: Long,
    val lastFullRebuild: Instant?,
    val deadLetters: Long,
    val droppedEnqueues: Long,
)

enum class IndexState { READY, BUILDING, MISSING, DEGRADED }

/**
 * A request the engine refuses before it reads anything. The REST layer answers 400 and names [parameter] in the body.
 */
class InvalidSearchQueryException(val parameter: String, message: String) : IllegalArgumentException(message)

/**
 * A read that the database did not finish inside its statement timeout. The REST layer answers 503 with `Retry-After`
 * and the hint to narrow the filters.
 */
class SearchUnavailableException(message: String) : RuntimeException(message)
