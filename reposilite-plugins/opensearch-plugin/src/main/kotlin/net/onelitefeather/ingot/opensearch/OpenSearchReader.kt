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

package net.onelitefeather.ingot.opensearch

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.reposilite.ReposiliteObjectMapper
import net.onelitefeather.ingot.opensearch.query.FilterKind
import net.onelitefeather.ingot.opensearch.query.GroupPart
import net.onelitefeather.ingot.opensearch.query.OpenSearchQueries
import net.onelitefeather.ingot.search.ArtifactFileItem
import net.onelitefeather.ingot.search.ArtifactFilePage
import net.onelitefeather.ingot.search.ArtifactFilesQuery
import net.onelitefeather.ingot.search.ArtifactSearchItem
import net.onelitefeather.ingot.search.ArtifactSearchPage
import net.onelitefeather.ingot.search.ArtifactSearchQuery
import net.onelitefeather.ingot.search.ArtifactVersions
import net.onelitefeather.ingot.search.FacetBucket
import net.onelitefeather.ingot.search.GroupOrder
import net.onelitefeather.ingot.search.IndexStatus
import net.onelitefeather.ingot.search.Origin
import net.onelitefeather.ingot.search.RepositoryPresence
import net.onelitefeather.ingot.search.SearchFacets
import net.onelitefeather.ingot.search.access.SearchAccess
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.gavKeyOf
import java.time.Instant
import java.util.HexFormat
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * The reads of the backend (design D5): groups, facets and files, each one or a few requests to the alias. The groups follow the
 * database engine (artifact search spec, 4.3): main's groups first, in main's order, then the groups only a word match returns;
 * the presence of the repositories and the latest versions are requests of their own, without the filters of the caller.
 */
internal class OpenSearchReader(
    private val client: OpenSearchClient,
    private val alias: String,
    private val indexStatus: () -> IndexStatus,
) {

    private val mapper = ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER

    /** One group of a window, with the values the database reads. [fileCount] is the number of files the filters match. */
    private data class GroupRow(val key: GroupOrder.Key, val groupId: String, val artifactId: String, val version: String, val fileCount: Long)

    fun search(query: ArtifactSearchQuery, access: SearchAccess): ArtifactSearchPage {
        OpenSearchQueries.checkWindow(query.page, query.size)
        val start = query.page.toLong() * query.size
        val end = start + query.size
        val mainCount = count(query, access, GroupPart.MAIN)
        val wordCount = if (OpenSearchQueries.usesWordMatch(query)) count(query, access, GroupPart.WORDS) else 0L

        val fromMain = if (start < mainCount) {
            window(query, access, GroupPart.MAIN, end).drop(start.toInt()).take(query.size)
        } else {
            emptyList()
        }
        val wordStart = maxOf(0L, start - mainCount)
        val wordEnd = maxOf(0L, end - mainCount)
        val fromWords = if (wordCount > 0L && wordEnd > wordStart) {
            window(query, access, GroupPart.WORDS, wordEnd).drop(wordStart.toInt()).take((wordEnd - wordStart).toInt())
        } else {
            emptyList()
        }
        val groups = fromMain + fromWords

        val presence = presenceOf(groups, access)
        val latest = latestOf(groups, access)
        val items = groups.mapNotNull { group ->
            val repositories = presence[group.key.gavKey()].orEmpty().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            ArtifactSearchItem(
                groupId = group.groupId,
                artifactId = group.artifactId,
                version = group.version,
                latestVersion = latest[group.key.artifactKey()],
                snapshot = group.version.endsWith(SNAPSHOT_SUFFIX, ignoreCase = true),
                repositories = repositories,
                fileCount = group.fileCount,
                lastModified = Instant.ofEpochMilli(group.key.lastModified),
                browseLink = browseLink(repositories.first().name, "${groupDirectory(group.groupId)}/${group.artifactId}/${group.version}"),
            )
        }
        val total = mainCount + wordCount
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
        val aggregations = mapper.createObjectNode()
        val kinds = mapOf(
            "repository" to FilterKind.REPOSITORY,
            "extension" to FilterKind.EXTENSION,
            "classifier" to FilterKind.CLASSIFIER,
            "origin" to FilterKind.ORIGIN,
            "snapshot" to FilterKind.SNAPSHOT,
        )
        kinds.forEach { (name, kind) -> aggregations.set<ObjectNode>(name, OpenSearchQueries.facet(kind, query, access)) }
        val body = mapper.createObjectNode().put("size", 0)
        body.set<ObjectNode>("aggs", aggregations)
        val answer = client.read("POST", "/$alias/_search", body).path("aggregations")
        return SearchFacets(
            repository = buckets(answer, "repository"),
            extension = buckets(answer, "extension"),
            classifier = buckets(answer, "classifier"),
            origin = buckets(answer, "origin"),
            snapshot = buckets(answer, "snapshot"),
        )
    }

    fun files(query: ArtifactFilesQuery, access: SearchAccess): ArtifactFilePage {
        val answer = client.read("POST", "/$alias/_search", OpenSearchQueries.files(query, access))
        val hits = answer.path("hits")
        return ArtifactFilePage(
            items = hits.path("hits").map { hit -> fileItem(hit.path("_source")) },
            page = query.page,
            size = query.size,
            total = hits.path("total").path("value").asLong(),
            indexStatus = indexStatus(),
        )
    }

    /** The number of groups of [part]: the buckets of a terms aggregation, bucketed up to one above the total cap. */
    private fun count(query: ArtifactSearchQuery, access: SearchAccess, part: GroupPart): Long =
        client.read("POST", "/$alias/_search", OpenSearchQueries.groupTotal(query, access, part))
            .path("aggregations").path("groups").path("buckets").size().toLong()

    /** The first [window] groups of [part] in the requested order, sorted by [GroupOrder] in memory. */
    private fun window(query: ArtifactSearchQuery, access: SearchAccess, part: GroupPart, window: Long): List<GroupRow> {
        val rows = client.read("POST", "/$alias/_search", OpenSearchQueries.groupWindow(query, access, part, window.toInt()))
            .path("aggregations").path("groups").path("buckets").map(::groupOf)
        val order = GroupOrder.comparator(query.sort, query.order)
        return rows.sortedWith { a, b -> order.compare(a.key, b.key) }
    }

    private fun groupOf(bucket: JsonNode): GroupRow = GroupRow(
        key = GroupOrder.Key(
            groupLc = firstKey(bucket, "groupLc"),
            artifactLc = firstKey(bucket, "artifactLc"),
            versionLc = firstKey(bucket, "versionLc"),
            lastModified = bucket.path("lastModified").path("value").asLong(0L),
            sizeBytes = bucket.path("sizeBytes").path("value").asLong(0L),
        ),
        groupId = firstKey(bucket, "group"),
        artifactId = firstKey(bucket, "artifact"),
        version = firstKey(bucket, "version"),
        fileCount = bucket.path("doc_count").asLong(),
    )

    private fun firstKey(bucket: JsonNode, name: String): String =
        bucket.path(name).path("buckets").firstOrNull()?.path("key")?.asText().orEmpty()

    /**
     * The repositories of each group, over the access value only (4.3). A repository with both origins shows as hosted, and the
     * list is sorted by name, as the database returns it.
     */
    private fun presenceOf(groups: List<GroupRow>, access: SearchAccess): Map<String, List<RepositoryPresence>> {
        if (groups.isEmpty()) return emptyMap()
        val keys = groups.map { it.key.gavKey() }.distinct()
        val answer = client.read("POST", "/$alias/_search", OpenSearchQueries.presence(keys, access))
        return answer.path("aggregations").path("gavs").path("buckets").associate { gav ->
            gav.path("key").asText() to gav.path("repositories").path("buckets").map { repository ->
                val origins = repository.path("origins").path("buckets").map { it.path("key").asText() }
                val origin = if (Origin.HOSTED.name in origins || origins.isEmpty()) Origin.HOSTED else Origin.valueOf(origins.first())
                RepositoryPresence(repository.path("key").asText(), origin)
            }.sortedBy { it.name }
        }
    }

    /** The latest version of each artifact of the groups, over the access value only (4.6); null above the version limit. */
    private fun latestOf(groups: List<GroupRow>, access: SearchAccess): Map<String, String?> {
        if (groups.isEmpty()) return emptyMap()
        val keys = groups.map { it.key.artifactKey() }.distinct()
        val answer = client.read("POST", "/$alias/_search", OpenSearchQueries.latestVersions(keys, access))
        val buckets = answer.path("aggregations").path("artifacts").path("buckets").associate { artifact ->
            val versions = artifact.path("versions").path("buckets")
            val latest = if (versions.size() > MAX_VERSIONS_PER_ARTIFACT) {
                null
            } else {
                ArtifactVersions.latestOf(versions.map { it.path("display").path("buckets").firstOrNull()?.path("key")?.asText().orEmpty() })
            }
            artifact.path("key").asText() to latest
        }
        return keys.associateWith { buckets[it] }
    }

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

    private fun buckets(aggregations: JsonNode, name: String): List<FacetBucket> =
        aggregations.path(name).path("buckets").path("buckets").map { bucket ->
            val value = if (bucket.has("key_as_string")) bucket.path("key_as_string").asText() else bucket.path("key").asText()
            FacetBucket(value, bucket.path("doc_count").asLong())
        }

    private fun GroupOrder.Key.gavKey(): String = HexFormat.of().formatHex(gavKeyOf(groupLc, artifactLc, versionLc))

    private fun GroupOrder.Key.artifactKey(): String = "$groupLc:$artifactLc"

    private fun JsonNode.textOrNull(field: String): String? = path(field).takeUnless { it.isNull || it.isMissingNode }?.asText()

    private companion object {
        const val SNAPSHOT_SUFFIX = "-SNAPSHOT"
        const val MAX_VERSIONS_PER_ARTIFACT = 5_000

        fun groupDirectory(groupId: String): String = groupId.replace('.', '/')

        /** `/browse/<repository>/<path>`, every segment percent-encoded (6.1), as the database engine builds it. */
        fun browseLink(repository: String, path: String): String =
            (listOf(repository) + path.split('/')).joinToString(separator = "/", prefix = "/browse/") { segment ->
                URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20")
            }
    }

}
