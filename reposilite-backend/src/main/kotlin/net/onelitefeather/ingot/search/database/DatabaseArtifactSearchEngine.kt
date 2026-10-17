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

package net.onelitefeather.ingot.search.database

import com.fasterxml.jackson.databind.ObjectMapper
import net.onelitefeather.ingot.search.ArtifactFilePage
import net.onelitefeather.ingot.search.ArtifactFileItem
import net.onelitefeather.ingot.search.ArtifactFilesQuery
import net.onelitefeather.ingot.search.ArtifactSearchEngine
import net.onelitefeather.ingot.search.ArtifactSearchItem
import net.onelitefeather.ingot.search.ArtifactSearchPage
import net.onelitefeather.ingot.search.ArtifactSearchQuery
import net.onelitefeather.ingot.search.FacetBucket
import net.onelitefeather.ingot.search.IndexState
import net.onelitefeather.ingot.search.IndexStatus
import net.onelitefeather.ingot.search.InvalidSearchQueryException
import net.onelitefeather.ingot.search.Origin
import net.onelitefeather.ingot.search.OriginFilter
import net.onelitefeather.ingot.search.RepositoryPresence
import net.onelitefeather.ingot.search.SearchOrder
import net.onelitefeather.ingot.search.SearchFacets
import net.onelitefeather.ingot.search.SearchSort
import net.onelitefeather.ingot.search.SearchUnavailableException
import net.onelitefeather.ingot.search.access.SearchAccess
import net.onelitefeather.ingot.search.outbox.OutboxPolicy
import net.onelitefeather.ingot.search.database.SearchFilters.FilterKind
import net.onelitefeather.ingot.search.runs.RunKind
import net.onelitefeather.ingot.search.runs.RunStatus
import org.apache.maven.artifact.versioning.ComparableVersion
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.core.min
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.sql.SQLTimeoutException
import java.time.Clock
import java.time.Instant
import java.util.Locale

/**
 * The default search engine, on the tables of section 2 (artifact search spec, 4 and 7.2). Reads only: the writes are the
 * outbox worker and the index writer, which feed the same tables.
 *
 * Every request runs in one transaction with a statement timeout of [QUERY_TIMEOUT_SECONDS]; a timeout becomes a
 * [SearchUnavailableException]. Requests are validated before any SQL is built.
 */
internal class DatabaseArtifactSearchEngine(
    private val database: Database,
    private val clock: Clock = Clock.systemUTC(),
) : ArtifactSearchEngine {

    override val id: String = ENGINE_ID

    private val tables = SearchTables(SearchDialect.of(database.vendor))
    private val rows = tables.artifact
    private val outbox = tables.outbox
    private val runs = tables.rebuildRun
    private val state = tables.state

    override fun search(query: ArtifactSearchQuery, access: SearchAccess): ArtifactSearchPage {
        checkWindow(query.page, query.size)
        val filters = SearchFilters(query, rows)
        val visible = access.toCondition(rows)
        val where = filters.predicate() and visible
        return readOnly {
            val groups = groupPage(where, query)
            val total = groupCount(where)
            val presence = repositoriesOf(groups, visible)
            val latest = latestVersionsOf(groups, visible)
            ArtifactSearchPage(
                items = groups.map { group ->
                    val repositories = presence[group.key].orEmpty()
                    ArtifactSearchItem(
                        groupId = group.groupId,
                        artifactId = group.artifactId,
                        version = group.version,
                        latestVersion = latest[group.key.artifact()],
                        snapshot = group.version.endsWith(SNAPSHOT_SUFFIX, ignoreCase = true),
                        repositories = repositories,
                        fileCount = group.fileCount,
                        lastModified = Instant.ofEpochMilli(group.lastModified),
                        browseLink = browseLink(repositories.first().name, "${groupDirectory(group.groupId)}/${group.artifactId}/${group.version}"),
                    )
                },
                page = query.page,
                size = query.size,
                total = minOf(total, ArtifactSearchPage.TOTAL_CAP),
                totalCapped = total > ArtifactSearchPage.TOTAL_CAP,
                indexStatus = statusInTransaction(),
            )
        }
    }

    override fun facets(query: ArtifactSearchQuery, access: SearchAccess): SearchFacets {
        val filters = SearchFilters(query, rows)
        val visible = access.toCondition(rows)
        return readOnly {
            SearchFacets(
                repository = buckets(rows.repository, filters.predicate(FilterKind.REPOSITORY) and visible),
                extension = buckets(rows.extension, filters.predicate(FilterKind.EXTENSION) and visible),
                classifier = bucketsOfClassifier(filters.predicate(FilterKind.CLASSIFIER) and visible),
                origin = buckets(rows.origin, filters.predicate(FilterKind.ORIGIN) and visible),
                snapshot = buckets(rows.snapshot, filters.predicate(FilterKind.SNAPSHOT) and visible) { it.toString() },
            )
        }
    }

    override fun files(query: ArtifactFilesQuery, access: SearchAccess): ArtifactFilePage {
        val coordinates = listOf(
            "groupId" to query.groupId,
            "artifactId" to query.artifactId,
            "version" to query.version,
        )
        coordinates.firstOrNull { it.second.isBlank() }?.let {
            throw InvalidSearchQueryException(it.first, "${it.first} is required")
        }
        if (query.size !in 1..MAX_FILES_SIZE) throw InvalidSearchQueryException("size", "size must be between 1 and $MAX_FILES_SIZE")
        checkWindow(query.page, query.size)

        val visible = access.toCondition(rows)
        val origin = when (query.origin) {
            OriginFilter.HOSTED -> rows.origin eq Origin.HOSTED.name
            OriginFilter.MIRROR -> rows.origin eq Origin.MIRROR.name
            OriginFilter.ALL -> null
        }
        val repositories = query.repositories.takeIf { it.isNotEmpty() }?.let { rows.repository inList it }
        val fileCount = rows.id.count()
        val gav = (rows.groupIdLc eq query.groupId.lowercase(Locale.ROOT)) and
            (rows.artifactIdLc eq query.artifactId.lowercase(Locale.ROOT)) and
            (rows.versionLc eq query.version.lowercase(Locale.ROOT))
        val where = listOfNotNull(gav, visible, origin, repositories).reduce { acc, op -> acc and op }

        return readOnly {
            val total = rows.select(fileCount).where { where }.single()[fileCount]
            val items = rows.selectAll()
                .where { where }
                .orderBy(rows.path to SortOrder.ASC, rows.repository to SortOrder.ASC, rows.id to SortOrder.ASC)
                .limit(query.size).offset(query.page.toLong() * query.size)
                .map { row ->
                    ArtifactFileItem(
                        repository = row[rows.repository],
                        path = row[rows.path],
                        name = row[rows.path].substringAfterLast('/'),
                        classifier = row[rows.classifier],
                        extension = row[rows.extension],
                        size = row[rows.sizeBytes],
                        lastModified = Instant.ofEpochMilli(row[rows.lastModified]),
                        origin = Origin.valueOf(row[rows.origin]),
                        sourceHost = row[rows.sourceHost],
                        sha1 = row[rows.sha1],
                        browseLink = browseLink(row[rows.repository], row[rows.path]),
                    )
                }
            ArtifactFilePage(
                items = items,
                page = query.page,
                size = query.size,
                total = total,
                indexStatus = statusInTransaction(),
            )
        }
    }

    override fun status(): IndexStatus = readOnly { statusInTransaction() }

    // ---- group queries -------------------------------------------------------------------------------------------

    /** One group as the `GROUP BY` of 4.3 returns it. [key] holds the lower-cased coordinates. */
    private data class Group(
        val key: GavKey,
        val groupId: String,
        val artifactId: String,
        val version: String,
        val fileCount: Long,
        val lastModified: Long,
    )

    private data class GavKey(val groupLc: String, val artifactLc: String, val versionLc: String) {
        fun artifact() = ArtifactKey(groupLc, artifactLc)
    }

    private data class ArtifactKey(val groupLc: String, val artifactLc: String)

    /** One page of groups. The sort ends in the three grouped columns, so the order is total and paging is stable. */
    private fun groupPage(where: Op<Boolean>, query: ArtifactSearchQuery): List<Group> {
        val g = rows.groupIdLc
        val a = rows.artifactIdLc
        val v = rows.versionLc
        val fileCount = rows.id.count()
        val lastModified = rows.lastModified.max()
        val size = rows.sizeBytes.sum()
        val minGroupId = rows.groupId.min()
        val minArtifactId = rows.artifactId.min()
        val minVersion = rows.version.min()
        val sortKey: Expression<*> = when (query.sort) {
            SearchSort.GROUP_ID -> g
            SearchSort.ARTIFACT_ID -> a
            SearchSort.VERSION -> v
            SearchSort.MODIFIED -> lastModified
            SearchSort.SIZE -> size
        }
        // The tie-breakers follow the direction of the request; with the default ascending order the newest version of a
        // tie comes first, as in the default order of 4.3.
        val direction = if (query.order == SearchOrder.DESC) SortOrder.DESC else SortOrder.ASC
        val tieDirection = if (direction == SortOrder.ASC) SortOrder.DESC else SortOrder.ASC
        return rows.select(g, a, v, minGroupId, minArtifactId, minVersion, fileCount, lastModified)
            .where { where }
            .groupBy(g, a, v)
            .orderBy(sortKey to direction, g to direction, a to direction, v to tieDirection)
            .limit(query.size).offset(query.page.toLong() * query.size)
            .map { row ->
                Group(
                    key = GavKey(row[g], row[a], row[v]),
                    groupId = row[minGroupId].orEmpty(),
                    artifactId = row[minArtifactId].orEmpty(),
                    version = row[minVersion].orEmpty(),
                    fileCount = row[fileCount],
                    lastModified = row[lastModified] ?: 0L,
                )
            }
    }

    /**
     * The number of groups, read up to one above [ArtifactSearchPage.TOTAL_CAP]: a result of that size tells that more exist
     * than the cap, so the count stays bounded (4.3).
     */
    private fun groupCount(where: Op<Boolean>): Long =
        rows.select(rows.groupIdLc, rows.artifactIdLc, rows.versionLc)
            .where { where }
            .groupBy(rows.groupIdLc, rows.artifactIdLc, rows.versionLc)
            .limit((ArtifactSearchPage.TOTAL_CAP + 1).toInt())
            .toList()
            .size
            .toLong()

    /**
     * The repositories that hold each group of the page, with their origin, over the visible rows only. The user's filters
     * do not apply here: a group lists every copy the caller may see. A repository with both origins shows as hosted.
     */
    private fun repositoriesOf(groups: List<Group>, visible: Op<Boolean>): Map<GavKey, List<RepositoryPresence>> {
        if (groups.isEmpty()) return emptyMap()
        val g = rows.groupIdLc
        val a = rows.artifactIdLc
        val v = rows.versionLc
        val anyGroup = groups.map { group ->
            (g eq group.key.groupLc) and (a eq group.key.artifactLc) and (v eq group.key.versionLc)
        }.reduce { acc, term -> acc or term }
        return rows.select(g, a, v, rows.repository, rows.origin)
            .where { visible and anyGroup }
            .withDistinct()
            .map { row ->
                GavKey(row[g], row[a], row[v]) to RepositoryPresence(row[rows.repository], Origin.valueOf(row[rows.origin]))
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, presence) ->
                presence.groupBy { it.name }
                    .map { (_, copies) -> copies.firstOrNull { it.origin == Origin.HOSTED } ?: copies.first() }
                    .sortedBy { it.name }
            }
    }

    /**
     * The highest version of each artifact of the page in the visible rows (4.6), ignoring the request's filters. Reads at
     * most [MAX_LATEST_ROWS] rows; the artifact the read ends in, and any above [MAX_VERSIONS_PER_ARTIFACT] versions, get null.
     */
    private fun latestVersionsOf(groups: List<Group>, visible: Op<Boolean>): Map<ArtifactKey, String?> {
        if (groups.isEmpty()) return emptyMap()
        val g = rows.groupIdLc
        val a = rows.artifactIdLc
        val v = rows.versionLc
        val artifacts = groups.map { it.key.artifact() }.distinct()
        val anyArtifact = artifacts.map { (g eq it.groupLc) and (a eq it.artifactLc) }.reduce { acc, term -> acc or term }
        val minVersion = rows.version.min()
        val read = rows.select(g, a, minVersion)
            .where { visible and anyArtifact }
            .groupBy(g, a, v)
            .orderBy(g to SortOrder.ASC, a to SortOrder.ASC)
            .limit(MAX_LATEST_ROWS + 1)
            .map { row -> ArtifactKey(row[g], row[a]) to row[minVersion].orEmpty() }
        val truncated = read.size > MAX_LATEST_ROWS
        val kept = read.take(MAX_LATEST_ROWS).groupBy({ it.first }, { it.second })
        val lastKey = kept.keys.lastOrNull()
        return artifacts.associateWith { artifact ->
            val versions = kept[artifact].orEmpty()
            val incomplete = truncated && artifact == lastKey
            if (incomplete || versions.size > MAX_VERSIONS_PER_ARTIFACT) null else latestOf(versions)
        }
    }

    /** The highest non-snapshot version by Maven's ordering; if there is none, the highest snapshot. */
    private fun latestOf(versions: List<String>): String? {
        val releases = versions.filterNot { it.endsWith(SNAPSHOT_SUFFIX, ignoreCase = true) }
        return (releases.ifEmpty { versions }).maxByOrNull { ComparableVersion(it) }
    }

    // ---- facets --------------------------------------------------------------------------------------------------

    /** The buckets of a text column: files per value, the 20 largest, ties by value. */
    private fun buckets(column: Column<String>, where: Op<Boolean>): List<FacetBucket> =
        buckets(column, where) { it }

    private fun <T> buckets(column: Column<T>, where: Op<Boolean>, render: (T) -> String): List<FacetBucket> {
        val files = rows.id.count()
        return rows.select(column, files)
            .where { where }
            .groupBy(column)
            .orderBy(files to SortOrder.DESC, column to SortOrder.ASC)
            .limit(FACET_BUCKETS)
            .map { FacetBucket(render(it[column]), it[files]) }
    }

    /** Classifier `null` is the bucket `-`. */
    private fun bucketsOfClassifier(where: Op<Boolean>): List<FacetBucket> {
        val files = rows.id.count()
        return rows.select(rows.classifier, files)
            .where { where }
            .groupBy(rows.classifier)
            .orderBy(files to SortOrder.DESC, rows.classifier to SortOrder.ASC)
            .limit(FACET_BUCKETS)
            .map { FacetBucket(it[rows.classifier] ?: NO_CLASSIFIER, it[files]) }
    }

    // ---- index status --------------------------------------------------------------------------------------------

    /** The state of the index for this request (4.8): three small reads, nothing cached. */
    private fun statusInTransaction(): IndexStatus {
        val lastFull = stateValue(SearchStateKey.LAST_FULL_REBUILD)?.toLongOrNull()
        val dropped = stateValue(SearchStateKey.DROPPED_ENQUEUES)?.toLongOrNull() ?: 0L
        val drift = stateValue(SearchStateKey.VERIFY_LAST_REPORT)?.let(::driftOf)
        val rowsExist = rows.select(rows.id).limit(1).firstOrNull() != null
        val rebuildRunning = runs.select(runs.id)
            .where { (runs.kind eq RunKind.REBUILD.name) and (runs.status eq RunStatus.RUNNING.name) }
            .limit(1)
            .firstOrNull() != null
        val lastRebuildFailed = runs.select(runs.status)
            .where { runs.kind eq RunKind.REBUILD.name }
            .orderBy(runs.id to SortOrder.DESC)
            .limit(1)
            .firstOrNull()?.get(runs.status) == RunStatus.FAILED.name
        val oldestPending = outbox.select(outbox.createdAt)
            .where { outbox.attempts less OutboxPolicy.MAX_ATTEMPTS }
            .orderBy(outbox.id to SortOrder.ASC)
            .limit(1)
            .firstOrNull()
        val lagSeconds = oldestPending?.let { ((clock.millis() - it[outbox.createdAt]) / MILLIS_PER_SECOND).coerceAtLeast(0L) } ?: 0L
        val deadLetters = outbox.selectAll().where { outbox.attempts greaterEq OutboxPolicy.MAX_ATTEMPTS }.count()
        return IndexStatus(
            state = indexStateOf(
                lastFullRebuild = lastFull?.let(Instant::ofEpochMilli),
                rowsExist = rowsExist,
                rebuildRunning = rebuildRunning,
                lastRebuildFailed = lastRebuildFailed,
                lagSeconds = lagSeconds,
                deadLetters = deadLetters,
                droppedEnqueues = dropped,
                drift = drift,
            ),
            lagSeconds = lagSeconds,
            lastFullRebuild = lastFull?.let(Instant::ofEpochMilli),
            deadLetters = deadLetters,
            droppedEnqueues = dropped,
        )
    }

    private fun stateValue(key: String): String? =
        state.select(state.value).where { state.key eq key }.firstOrNull()?.get(state.value)

    private fun driftOf(report: String): Double? =
        runCatching { MAPPER.readTree(report).get("drift")?.asDouble() }.getOrNull()

    // ---- helpers -------------------------------------------------------------------------------------------------

    /** Runs [read] in one transaction with the statement timeout, and turns a timeout into [SearchUnavailableException]. */
    private fun <T> readOnly(read: () -> T): T = try {
        transaction(database) {
            queryTimeout = QUERY_TIMEOUT_SECONDS
            read()
        }
    } catch (failure: Exception) {
        if (generateSequence(failure as Throwable) { it.cause }.any { it is SQLTimeoutException }) {
            throw SearchUnavailableException("The search took longer than $QUERY_TIMEOUT_SECONDS seconds, narrow the filters")
        }
        throw failure
    }

    private fun checkWindow(page: Int, size: Int) {
        if (page < 0) throw InvalidSearchQueryException("page", "page must not be negative")
        if (size !in 1..MAX_SEARCH_SIZE) throw InvalidSearchQueryException("size", "size must be between 1 and $MAX_SEARCH_SIZE")
        if (page.toLong() * size + size > MAX_WINDOW) {
            throw InvalidSearchQueryException("page", "Result window is limited to $MAX_WINDOW entries, narrow the filters")
        }
    }

    private companion object {
        const val ENGINE_ID = "database"
        const val QUERY_TIMEOUT_SECONDS = 10
        const val MAX_WINDOW = 10_000L
        const val MAX_SEARCH_SIZE = 100
        const val MAX_FILES_SIZE = 200
        const val FACET_BUCKETS = 20
        const val MAX_LATEST_ROWS = 100_000
        const val MAX_VERSIONS_PER_ARTIFACT = 5_000
        const val MILLIS_PER_SECOND = 1000L
        const val SNAPSHOT_SUFFIX = "-SNAPSHOT"
        const val NO_CLASSIFIER = "-"
        val MAPPER = ObjectMapper()

        /** The directory of a groupId in the repository, as the browse route expects it. */
        fun groupDirectory(groupId: String): String = groupId.replace('.', '/')

        /** `/browse/<repository>/<path>`, every segment percent-encoded (6.1). */
        fun browseLink(repository: String, path: String): String =
            (listOf(repository) + path.split('/')).joinToString(separator = "/", prefix = "/browse/") { segment ->
                URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20")
            }
    }

}

/**
 * The state of the index from its inputs (4.8). A completed rebuild is not undone by a later one that runs; a failed last
 * rebuild, pending lag over five minutes, dead letters, dropped enqueues or drift over five percent degrade it.
 */
internal fun indexStateOf(
    lastFullRebuild: Instant?,
    rowsExist: Boolean,
    rebuildRunning: Boolean,
    lastRebuildFailed: Boolean,
    lagSeconds: Long,
    deadLetters: Long,
    droppedEnqueues: Long,
    drift: Double?,
): IndexState = when {
    lastFullRebuild == null && !rowsExist && !rebuildRunning -> IndexState.MISSING
    lastFullRebuild == null -> if (lastRebuildFailed && !rebuildRunning) IndexState.DEGRADED else IndexState.BUILDING
    lastRebuildFailed || lagSeconds > MAX_LAG_SECONDS || deadLetters > 0 || droppedEnqueues > 0 || (drift ?: 0.0) > MAX_DRIFT ->
        IndexState.DEGRADED
    else -> IndexState.READY
}

private const val MAX_LAG_SECONDS = 300L
private const val MAX_DRIFT = 0.05
