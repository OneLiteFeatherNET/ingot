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

@file:OptIn(ExperimentalIngotSearchApi::class)

package net.onelitefeather.ingot.meilisearch

import com.fasterxml.jackson.databind.JsonNode
import net.onelitefeather.ingot.search.ArtifactFileItem
import net.onelitefeather.ingot.search.ArtifactFilePage
import net.onelitefeather.ingot.search.ArtifactFilesQuery
import net.onelitefeather.ingot.search.ArtifactSearchItem
import net.onelitefeather.ingot.search.ArtifactSearchPage
import net.onelitefeather.ingot.search.ArtifactSearchQuery
import net.onelitefeather.ingot.search.ArtifactVersions
import net.onelitefeather.ingot.search.AttributeSort
import net.onelitefeather.ingot.search.FacetBucket
import net.onelitefeather.ingot.search.GroupOrder
import net.onelitefeather.ingot.search.IndexStatus
import net.onelitefeather.ingot.search.InvalidSearchQueryException
import net.onelitefeather.ingot.search.Origin
import net.onelitefeather.ingot.search.RepositoryPresence
import net.onelitefeather.ingot.search.SearchFacets
import net.onelitefeather.ingot.search.SearchSort
import net.onelitefeather.ingot.search.SearchUnavailableException
import net.onelitefeather.ingot.search.access.SearchAccess
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.OriginFilter
import net.onelitefeather.ingot.search.SnapshotFilter
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Locale

/**
 * The reads of the backend (design D6): search groups, facets and files, each one or a few requests to the index. Meilisearch has
 * no grouping, so a group is computed from its file documents: the distinct window gives one document per GAV, the files of the
 * groups give the repositories, the counts and the newest modification, and the order is the shared comparator of the database.
 */
internal class MeilisearchReader(
    private val client: MeilisearchClient,
    private val index: String,
    private val aggregateSortLimit: Int,
    private val indexStatus: () -> IndexStatus,
) {

    /** One group of the window, with the keys the database orders by. */
    private data class GroupRow(
        val key: GroupOrder.Key,
        val gav: String,
        val groupId: String,
        val artifactId: String,
        val version: String,
    )

    private val documentsPath = "/indexes/$index/search"

    fun search(query: ArtifactSearchQuery, access: SearchAccess): ArtifactSearchPage {
        checkWindow(query.page, query.size)
        val base = MeilisearchFilters.access(access) ?: return emptyPage(query)
        val conditions = listOf(base) + conditionsOf(query).map { it.second }
        val words = MeilisearchFilters.wordsOf(query.q)
        val window = search(conditions + MeilisearchFilters.wordConditions(query.q), null, distinct = GAV, hitsPerPage = WINDOW_FETCH)
        val windowTotal = window.path("totalHits").asLong(0L)
        val rows = window.path("hits").map(::groupOf).filter { row ->
            query.versionPrefix?.let { row.version.lowercase().startsWith(it.lowercase()) } ?: true
        }
        // The total is the count of the rows after the in-memory rule of the version prefix, where the server's count cannot apply it.
        val total = if (query.versionPrefix != null) rows.size.toLong() else windowTotal
        val aggregates = aggregatesOf(rows, query, base, conditions.drop(1), sortsByAggregate(query.sort) || attributeSortOf(query) != null)
        val order = GroupOrder.comparator(query.sort, query.order)
        val attributeSort = query.attributeSort
        // The groups the query is a coordinate prefix of come first, in the order of the database; the word-only groups follow.
        val (main, wordOnly) = rows.partition { row -> isMain(row, words) }
        val sortedBy = { list: List<GroupRow> ->
            list.sortedWith { a, b ->
                val byAttribute = attributeSort?.let { compareByAttribute(it, aggregates[a.gav]?.attributeValue, aggregates[b.gav]?.attributeValue) } ?: 0
                if (byAttribute != 0) byAttribute else order.compare(a.key.with(aggregates[a.gav]), b.key.with(aggregates[b.gav]))
            }
        }
        val sorted = sortedBy(main) + sortedBy(wordOnly)
        val start = query.page * query.size
        val page = sorted.drop(start).take(query.size)
        val latest = latestOf(page, base)
        val items = page.mapNotNull { row ->
            val aggregate = aggregates[row.gav] ?: return@mapNotNull null
            if (aggregate.repositories.isEmpty()) return@mapNotNull null
            ArtifactSearchItem(
                groupId = row.groupId,
                artifactId = row.artifactId,
                version = row.version,
                latestVersion = latest[row.key.artifactKey()],
                snapshot = row.version.endsWith(SNAPSHOT_SUFFIX, ignoreCase = true),
                repositories = aggregate.repositories,
                fileCount = aggregate.fileCount,
                lastModified = Instant.ofEpochMilli(aggregate.lastModified),
                browseLink = browseLink(aggregate.repositories.first().name, "${row.groupId.replace('.', '/')}/${row.artifactId}/${row.version}"),
            )
        }
        return ArtifactSearchPage(
            items = items,
            page = query.page,
            size = query.size,
            total = minOf(total, ArtifactSearchPage.TOTAL_CAP),
            totalCapped = total > ArtifactSearchPage.TOTAL_CAP,
            indexStatus = indexStatus(),
        )
    }

    fun facets(query: ArtifactSearchQuery, access: SearchAccess): SearchFacets {
        val base = MeilisearchFilters.access(access) ?: return SearchFacets(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        if (query.versionPrefix != null) {
            throw SearchUnavailableException("a version prefix is not supported for facets by the meilisearch backend, drop it")
        }
        val all = conditionsOf(query)
        val wordConditions = MeilisearchFilters.wordConditions(query.q)
        fun buckets(attribute: String): List<FacetBucket> {
            val conditions = listOf(base) + all.filter { it.first != attribute }.map { it.second } + wordConditions
            val answer = search(conditions, null, facets = listOf(attribute), hitsPerPage = 0)
            return answer.path("facetDistribution").path(attribute).fields().asSequence()
                .map { (value, count) -> FacetBucket(value, count.asLong()) }
                .sortedBy { it.value }
                .toList()
        }
        // A facet of an attribute counts the files that match every other filter, so a selected value keeps its alternatives.
        fun attributeBuckets(key: String): List<FacetBucket> {
            val path = MeilisearchIndexSettings.attributePath(key)
            val conditions = listOf(base) + all.filter { it.first != attributeScope(key) }.map { it.second } + wordConditions
            return search(conditions, null, facets = listOf(path), hitsPerPage = 0)
                .path("facetDistribution").path(path).fields().asSequence()
                .map { (value, count) -> FacetBucket(value, count.asLong()) }
                .sortedWith(compareByDescending<FacetBucket> { it.count }.thenBy { it.value })
                .take(ATTRIBUTE_FACET_BUCKETS)
                .toList()
        }
        return SearchFacets(
            repository = buckets("repository"),
            extension = buckets("extension"),
            classifier = buckets("classifier"),
            origin = buckets("origin"),
            snapshot = buckets("snapshot"),
            attributes = query.attributeFacets.takeIf { it.isNotEmpty() }?.associateWith(::attributeBuckets),
        )
    }

    fun files(query: ArtifactFilesQuery, access: SearchAccess): ArtifactFilePage {
        checkWindow(query.page, query.size)
        val base = MeilisearchFilters.access(access) ?: return ArtifactFilePage(emptyList(), query.page, query.size, 0, indexStatus())
        val conditions = listOf(
            base,
            "group_id = ${MeilisearchFilters.quote(query.groupId)}",
            "artifact_id = ${MeilisearchFilters.quote(query.artifactId)}",
            "version = ${MeilisearchFilters.quote(query.version)}",
        ) + originCondition(query.origin) + repositoriesCondition(query.repositories)
        val answer = client.json(
            "POST",
            documentsPath,
            mapOf(
                "filter" to conditions.joinToString(" AND "),
                "sort" to listOf("repository:asc", "path:asc"),
                "page" to query.page + 1,
                "hitsPerPage" to query.size,
            ),
        )
        return ArtifactFilePage(
            items = answer.path("hits").map(::fileItem),
            page = query.page,
            size = query.size,
            total = answer.path("totalHits").asLong(0L),
            indexStatus = indexStatus(),
        )
    }

    // ---- the requests ------------------------------------------------------------------------------------------------

    private fun search(
        conditions: List<String>,
        q: String?,
        distinct: String? = null,
        facets: List<String> = emptyList(),
        hitsPerPage: Int,
        attributes: List<String>? = null,
    ): JsonNode {
        val body = linkedMapOf<String, Any>(
            "q" to (q?.trim().orEmpty()),
            "filter" to conditions.joinToString(" AND "),
            "hitsPerPage" to hitsPerPage,
            "page" to 1,
            "matchingStrategy" to "all",
        )
        distinct?.let { body["distinct"] = it }
        if (facets.isNotEmpty()) body["facets"] = facets
        attributes?.let { body["attributesToRetrieve"] = it }
        return client.json("POST", documentsPath, body)
    }

    /**
     * The file counts, the newest modification, the sizes and the repositories of the groups of [rows]. For a sort by modification
     * or size every group of the window is read, bounded by the aggregate sort limit (gap G4); otherwise only the page is read.
     */
    private fun aggregatesOf(
        rows: List<GroupRow>,
        query: ArtifactSearchQuery,
        base: String,
        conditions: List<String>,
        wholeWindow: Boolean,
    ): Map<String, Aggregate> {
        if (rows.isEmpty()) return emptyMap()
        val keys = rows.map { it.gav }.distinct()
        val sortKey = attributeSortOf(query)?.key
        val fields = if (sortKey == null) SCAN_FIELDS else SCAN_FIELDS + "attributes"
        val files = scan(listOf(base) + conditions + "gav IN ${MeilisearchFilters.inList(keys)}", wholeWindow, fields)
        return files.groupBy { it.path("gav").asText() }.mapValues { (_, group) ->
            val repositories = group.groupBy { it.path("repository").asText() }.map { (name, files) ->
                val origins = files.map { it.path("origin").asText() }
                val origin = if (Origin.HOSTED.name in origins || origins.isEmpty()) Origin.HOSTED else Origin.valueOf(origins.first())
                RepositoryPresence(name, origin)
            }.sortedBy { it.name }
            Aggregate(
                fileCount = group.size.toLong(),
                lastModified = group.maxOf { it.path("last_modified").asLong(0L) },
                sizeBytes = group.sumOf { it.path("size_bytes").asLong(0L) },
                repositories = repositories,
                attributeValue = attributeSortOf(query)?.let { sort -> extremeValue(group, sort) },
            )
        }
    }

    /** Every file of the given filter, read by pages. A window larger than the aggregate limit is refused with a hint. */
    private fun scan(conditions: List<String>, wholeWindow: Boolean, fields: List<String>): List<JsonNode> {
        val limit = if (wholeWindow) aggregateSortLimit else Int.MAX_VALUE
        val files = mutableListOf<JsonNode>()
        var page = 1
        while (true) {
            val answer = client.json(
                "POST",
                documentsPath,
                mapOf(
                    "filter" to conditions.joinToString(" AND "),
                    "attributesToRetrieve" to fields,
                    "page" to page,
                    "hitsPerPage" to SCAN_PAGE,
                ),
            )
            files.addAll(answer.path("hits").toList())
            if (files.size > limit) {
                throw SearchUnavailableException("the group sort reads more than $aggregateSortLimit files, narrow the filters")
            }
            if (answer.path("hits").size() < SCAN_PAGE) break
            page += 1
        }
        return files
    }

    /** The latest version of each artifact of [rows], over the access value only; null above the version limit. */
    private fun latestOf(rows: List<GroupRow>, base: String): Map<String, String?> {
        return rows.map { it.key.artifactKey() to it }.distinctBy { it.first }.associate { (key, row) ->
            val versions = search(
                listOf(base, "group_id_lc = ${MeilisearchFilters.quote(row.groupId.lowercase())}", "artifact_id_lc = ${MeilisearchFilters.quote(row.artifactId.lowercase())}"),
                q = null,
                facets = listOf("version"),
                hitsPerPage = 0,
            ).path("facetDistribution").path("version")
            val values = versions.fieldNames().asSequence().toList()
            key to if (values.size > MAX_VERSIONS_PER_ARTIFACT) null else ArtifactVersions.latestOf(values)
        }
    }

    // ---- mapping -----------------------------------------------------------------------------------------------------

    /**
     * Whether a group is a main group of the query, the database rule: every word of the text is a main match, which is a prefix of
     * the groupId or the artifactId, or the exact version. Every other group is matched only by a word.
     */
    private fun isMain(row: GroupRow, words: List<String>): Boolean = words.all { word ->
        if (':' in word) {
            // A coordinate word: the groupId prefix, the artifactId prefix and the exact version, as the database matches it.
            val (group, artifact, version) = MeilisearchFilters.coordinatePartsOf(word)
            (group.isEmpty() || row.key.groupLc.startsWith(group)) &&
                (artifact.isEmpty() || row.key.artifactLc.startsWith(artifact)) &&
                (version.isEmpty() || row.key.versionLc == version)
        } else {
            row.key.groupLc.startsWith(word) || row.key.artifactLc.startsWith(word) || row.key.versionLc == word
        }
    }

    private fun groupOf(hit: JsonNode): GroupRow = GroupRow(
        key = GroupOrder.Key(
            groupLc = hit.path("group_id_lc").asText(),
            artifactLc = hit.path("artifact_id_lc").asText(),
            versionLc = hit.path("version_lc").asText(),
            lastModified = 0L,
            sizeBytes = 0L,
        ),
        gav = hit.path("gav").asText(),
        groupId = hit.path("group_id").asText(),
        artifactId = hit.path("artifact_id").asText(),
        version = hit.path("version").asText(),
    )

    private fun fileItem(source: JsonNode): ArtifactFileItem {
        val path = source.path("path").asText()
        return ArtifactFileItem(
            repository = source.path("repository").asText(),
            path = path,
            name = path.substringAfterLast('/'),
            classifier = source.textOrNull("classifier"),
            extension = source.path("extension").asText(),
            size = source.path("size_bytes").asLong(),
            lastModified = Instant.ofEpochMilli(source.path("last_modified").asLong()),
            origin = Origin.valueOf(source.path("origin").asText()),
            sourceHost = source.textOrNull("source_host"),
            sha1 = source.textOrNull("sha1"),
            browseLink = browseLink(source.path("repository").asText(), path),
        )
    }

    private fun JsonNode.textOrNull(field: String): String? = path(field).takeUnless { it.isNull || it.isMissingNode }?.asText()

    private fun emptyPage(query: ArtifactSearchQuery): ArtifactSearchPage = ArtifactSearchPage(
        items = emptyList(),
        page = query.page,
        size = query.size,
        total = 0L,
        totalCapped = false,
        indexStatus = indexStatus(),
    )

    /** One filter per query value, keyed by the facet it belongs to, so that a facet ignores its own value. */
    private fun conditionsOf(query: ArtifactSearchQuery): List<Pair<String, String>> {
        val conditions = mutableListOf<Pair<String, String>>()
        if (query.repositories.isNotEmpty()) conditions += "repository" to "repository IN ${MeilisearchFilters.inList(query.repositories)}"
        if (query.extensions.isNotEmpty()) conditions += "extension" to "extension IN ${MeilisearchFilters.inList(query.extensions.map { it.lowercase() })}"
        if (query.classifiers.isNotEmpty()) conditions += "classifier" to "classifier IN ${MeilisearchFilters.inList(query.classifiers)}"
        query.sha1?.let { conditions += "sha1" to "sha1 = ${MeilisearchFilters.quote(it)}" }
        when (query.snapshot) {
            SnapshotFilter.TRUE -> conditions += "snapshot" to "snapshot = true"
            SnapshotFilter.FALSE -> conditions += "snapshot" to "snapshot = false"
            SnapshotFilter.ALL -> Unit
        }
        originCondition(query.origin).forEach { conditions += "origin" to it }
        query.modifiedAfter?.let { conditions += "modified" to "last_modified >= ${it.toEpochMilli()}" }
        query.modifiedBefore?.let { conditions += "modified" to "last_modified <= ${it.toEpochMilli()}" }
        query.sizeMin?.let { conditions += "size" to "size_bytes >= $it" }
        query.sizeMax?.let { conditions += "size" to "size_bytes <= $it" }
        query.groupId?.let { conditions += "group" to "group_id_lc = ${MeilisearchFilters.quote(it.lowercase())}" }
        query.artifactId?.let { conditions += "artifact" to "artifact_id_lc = ${MeilisearchFilters.quote(it.lowercase())}" }
        query.version?.let { conditions += "version" to "version_lc = ${MeilisearchFilters.quote(it.lowercase())}" }
        query.attributeFilters.groupBy({ it.key }, { it.values }).forEach { (key, values) ->
            val wanted = values.flatten().distinctBy { it.lowercase(Locale.ROOT) }
            conditions += attributeScope(key) to "${MeilisearchIndexSettings.attributePath(key)} IN ${MeilisearchFilters.inList(wanted)}"
        }
        return conditions
    }

    /** The scope of the filter of an attribute key, which its own facet leaves out. No fixed facet uses this prefix. */
    private fun attributeScope(key: String): String = "attribute:$key"

    private fun originCondition(origin: OriginFilter): List<String> = when (origin) {
        OriginFilter.ALL -> emptyList()
        OriginFilter.HOSTED, OriginFilter.MIRROR -> listOf("origin = ${MeilisearchFilters.quote(origin.name)}")
    }

    private fun repositoriesCondition(repositories: List<String>): List<String> =
        if (repositories.isEmpty()) emptyList() else listOf("repository IN ${MeilisearchFilters.inList(repositories)}")

    /** Whether the sort reads the aggregates of the whole window: modification and size are sums over the files of a group. */
    private fun sortsByAggregate(sort: SearchSort): Boolean = sort == SearchSort.MODIFIED || sort == SearchSort.SIZE

    private fun attributeSortOf(query: ArtifactSearchQuery): AttributeSort? = query.attributeSort

    /**
     * The value a group sorts by: the smallest lower-cased value of its files ascending and the largest descending, or null for a group
     * whose files hold no value of the key (search attribute facets, D4), as the database reads it.
     */
    private fun extremeValue(files: List<JsonNode>, sort: AttributeSort): String? {
        val values = files.flatMap { file -> file.path("attributes").path(sort.key).map { it.asText().lowercase(Locale.ROOT) } }
        return if (sort.descending) values.maxOrNull() else values.minOrNull()
    }

    /** The order of two groups by their sort values: the groups without a value come last in both directions. */
    private fun compareByAttribute(sort: AttributeSort, a: String?, b: String?): Int = when {
        a == null && b == null -> 0
        a == null -> 1
        b == null -> -1
        sort.descending -> b.compareTo(a)
        else -> a.compareTo(b)
    }

    private data class Aggregate(
        val fileCount: Long,
        val lastModified: Long,
        val sizeBytes: Long,
        val repositories: List<RepositoryPresence>,
        val attributeValue: String? = null,
    )

    private fun GroupOrder.Key.with(aggregate: Aggregate?): GroupOrder.Key =
        copy(lastModified = aggregate?.lastModified ?: 0L, sizeBytes = aggregate?.sizeBytes ?: 0L)

    private fun GroupOrder.Key.artifactKey(): String = "$groupLc:$artifactLc"

    companion object {
        const val GAV = "gav"
        /** The bound of a page, as the database engine validates it. */
        const val WINDOW = 10_000

        /** The distinct groups fetched for one request: the design's window, one above the total cap, so the count is exact. */
        const val WINDOW_FETCH = 10_101
        const val MAX_SEARCH_SIZE = 100

        /** The buckets of one attribute facet, as the database returns them. */
        const val ATTRIBUTE_FACET_BUCKETS = 20
        const val SCAN_PAGE = 1000
        const val MAX_VERSIONS_PER_ARTIFACT = 5_000
        const val SNAPSHOT_SUFFIX = "-SNAPSHOT"
        val SCAN_FIELDS = listOf("gav", "repository", "origin", "last_modified", "size_bytes")

        /** The bounds of the database engine's paging, mirrored: a page of at most 100 groups and a window of 10 000. */
        fun checkWindow(page: Int, size: Int) {
            if (page < 0) throw InvalidSearchQueryException("page", "page must not be negative")
            if (size !in 1..MAX_SEARCH_SIZE) throw InvalidSearchQueryException("size", "size must be between 1 and $MAX_SEARCH_SIZE")
            if (page.toLong() * size + size > WINDOW) {
                throw InvalidSearchQueryException("page", "Result window is limited to $WINDOW entries, narrow the filters")
            }
        }

        fun browseLink(repository: String, path: String): String =
            (listOf(repository) + path.split('/')).joinToString(separator = "/", prefix = "/browse/") { segment ->
                URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20")
            }
    }

}
