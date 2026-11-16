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

package net.onelitefeather.ingot.lucene

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
import net.onelitefeather.ingot.search.backend.SampledRow
import org.apache.lucene.document.Document
import org.apache.lucene.index.LeafReaderContext
import org.apache.lucene.index.Term
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.MatchAllDocsQuery
import org.apache.lucene.search.PrefixQuery
import org.apache.lucene.search.Query
import org.apache.lucene.search.ScoreMode
import org.apache.lucene.search.SimpleCollector
import org.apache.lucene.search.TermQuery
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.HexFormat

/**
 * The reads of the Lucene index (design D7): groups, facets and files, each answered from the searcher of one commit. The groups
 * follow the database engine (artifact search spec, 4.3): main's groups first, in main's order, then the groups only a word match
 * returns. The presence of the repositories and the latest versions are read with the access value only, as on the other engines.
 *
 * The groups are collected in memory, one entry for each matching group, so the memory of a search grows with the number of groups it
 * matches. The design allowed a bounded window; the window cannot be exact for the sorts by modification and size, so this version
 * keeps every group of the request and says so in the reference page.
 */
internal class LuceneReader(private val indexStatus: () -> IndexStatus) {

    /** The values of one group, collected from the stored fields of its files. [fileCount] counts the files the filters match. */
    private class GroupAcc(
        val gav: String,
        val groupLc: String,
        val artifactLc: String,
        val versionLc: String,
        var group: String,
        var artifact: String,
        var version: String,
        var lastModified: Long,
        var sizeBytes: Long,
        var fileCount: Long,
    ) {
        fun key(): GroupOrder.Key = GroupOrder.Key(groupLc, artifactLc, versionLc, lastModified, sizeBytes)
    }

    fun search(query: ArtifactSearchQuery, access: SearchAccess, searcher: IndexSearcher): ArtifactSearchPage {
        LuceneQueries.checkWindow(query.page, query.size)
        val order = GroupOrder.comparator(query.sort, query.order)
        val mainGroups = groups(searcher, LuceneQueries.group(query, access, GroupPart.MAIN)).sortedWith { a, b -> order.compare(a.key(), b.key()) }
        val wordGroups = if (LuceneQueries.usesWordMatch(query)) {
            groups(searcher, LuceneQueries.group(query, access, GroupPart.WORDS)).sortedWith { a, b -> order.compare(a.key(), b.key()) }
        } else {
            emptyList()
        }
        val mainCount = mainGroups.size.toLong()
        val wordCount = wordGroups.size.toLong()
        val start = query.page.toLong() * query.size
        val end = start + query.size
        val fromMain = if (start < mainCount) mainGroups.subList(start.toInt(), minOf(end, mainCount).toInt()) else emptyList()
        val wordStart = maxOf(0L, start - mainCount)
        val wordEnd = minOf(maxOf(0L, end - mainCount), wordCount)
        val fromWords = if (wordEnd > wordStart) wordGroups.subList(wordStart.toInt(), wordEnd.toInt()) else emptyList()
        val page = fromMain + fromWords

        val presence = presenceOf(page.map { it.gav }.distinct(), access, searcher)
        val latest = latestOf(page.map { "${it.groupLc}:${it.artifactLc}" }.distinct(), access, searcher)
        val items = page.mapNotNull { group ->
            val repositories = presence[group.gav].orEmpty().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            ArtifactSearchItem(
                groupId = group.group,
                artifactId = group.artifact,
                version = group.version,
                latestVersion = latest["${group.groupLc}:${group.artifactLc}"],
                snapshot = group.version.endsWith(ArtifactVersions.SNAPSHOT_SUFFIX, ignoreCase = true),
                repositories = repositories,
                fileCount = group.fileCount,
                lastModified = Instant.ofEpochMilli(group.lastModified),
                browseLink = browseLink(repositories.first().name, "${groupDirectory(group.group)}/${group.artifact}/${group.version}"),
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

    fun facets(query: ArtifactSearchQuery, access: SearchAccess, searcher: IndexSearcher): SearchFacets = SearchFacets(
        repository = bucketsOf(searcher, query, access, FilterKind.REPOSITORY, LuceneDocument.REPOSITORY),
        extension = bucketsOf(searcher, query, access, FilterKind.EXTENSION, LuceneDocument.EXTENSION),
        classifier = bucketsOf(searcher, query, access, FilterKind.CLASSIFIER, LuceneDocument.CLASSIFIER),
        origin = bucketsOf(searcher, query, access, FilterKind.ORIGIN, LuceneDocument.ORIGIN),
        snapshot = bucketsOf(searcher, query, access, FilterKind.SNAPSHOT, LuceneDocument.SNAPSHOT),
    )

    fun files(query: ArtifactFilesQuery, access: SearchAccess, searcher: IndexSearcher): ArtifactFilePage {
        val documents = documentsOf(searcher, LuceneQueries.files(query, access))
            .sortedWith(compareBy<Document>({ it.get(LuceneDocument.PATH) }, { it.get(LuceneDocument.REPOSITORY) }))
        val from = minOf(query.page.toLong() * query.size, documents.size.toLong()).toInt()
        val to = minOf(from + query.size, documents.size)
        return ArtifactFilePage(
            items = documents.subList(from, to).map(::fileItem),
            page = query.page,
            size = query.size,
            total = documents.size.toLong(),
            indexStatus = indexStatus(),
        )
    }

    /** The names of the repositories that hold at least one row, sorted. */
    fun repositories(searcher: IndexSearcher): List<String> =
        documentsOf(searcher, MatchAllDocsQuery()).mapNotNull { it.get(LuceneDocument.REPOSITORY) }.toSortedSet().toList()

    /** The paths of the rows at [prefix] and below it, in path order, after [after], at most [limit]. */
    fun pathsAt(searcher: IndexSearcher, repository: String, prefix: String, after: String?, limit: Int): List<String> {
        val below = BooleanQuery.Builder()
            .add(TermQuery(Term(LuceneDocument.PATH, prefix)), BooleanClause.Occur.SHOULD)
            .add(PrefixQuery(Term(LuceneDocument.PATH, "$prefix/")), BooleanClause.Occur.SHOULD)
            .build()
        val query = BooleanQuery.Builder()
            .add(TermQuery(Term(LuceneDocument.REPOSITORY, repository)), BooleanClause.Occur.FILTER)
            .add(below, BooleanClause.Occur.FILTER)
            .build()
        return documentsOf(searcher, query).mapNotNull { it.get(LuceneDocument.PATH) }.sorted()
            .filter { path -> after == null || path > after }
            .take(limit)
    }

    /** Every row of the index as a sample row, in the order of repository and path. */
    fun sampleRows(searcher: IndexSearcher): List<SampledRow> =
        documentsOf(searcher, MatchAllDocsQuery()).map { document ->
            SampledRow(
                repository = checkNotNull(document.get(LuceneDocument.REPOSITORY)),
                path = checkNotNull(document.get(LuceneDocument.PATH)),
                sizeBytes = LuceneDocument.numberOf(document, LuceneDocument.SIZE_BYTES),
                lastModified = LuceneDocument.numberOf(document, LuceneDocument.LAST_MODIFIED),
                origin = Origin.valueOf(checkNotNull(document.get(LuceneDocument.ORIGIN))),
                sourceHost = document.get(LuceneDocument.SOURCE_HOST),
            )
        }.sortedWith(compareBy<SampledRow>({ it.repository }, { it.path }))

    // ---- collection -----------------------------------------------------------------------------------------------------

    /** The groups of [query], one accumulator per GAV key, with the display values of the smallest spelling. */
    private fun groups(searcher: IndexSearcher, query: Query): List<GroupAcc> {
        val byGav = LinkedHashMap<String, GroupAcc>()
        forEachMatch(searcher, query) { document ->
            val gav = checkNotNull(document.get(LuceneDocument.GAV))
            val group = byGav.getOrPut(gav) {
                GroupAcc(
                    gav = gav,
                    groupLc = checkNotNull(document.get(LuceneDocument.GROUP_ID_LC)),
                    artifactLc = checkNotNull(document.get(LuceneDocument.ARTIFACT_ID_LC)),
                    versionLc = checkNotNull(document.get(LuceneDocument.VERSION_LC)),
                    group = checkNotNull(document.get(LuceneDocument.GROUP_ID)),
                    artifact = checkNotNull(document.get(LuceneDocument.ARTIFACT_ID)),
                    version = checkNotNull(document.get(LuceneDocument.VERSION)),
                    lastModified = Long.MIN_VALUE,
                    sizeBytes = 0L,
                    fileCount = 0L,
                )
            }
            group.group = minOf(group.group, checkNotNull(document.get(LuceneDocument.GROUP_ID)))
            group.artifact = minOf(group.artifact, checkNotNull(document.get(LuceneDocument.ARTIFACT_ID)))
            group.version = minOf(group.version, checkNotNull(document.get(LuceneDocument.VERSION)))
            group.lastModified = maxOf(group.lastModified, LuceneDocument.numberOf(document, LuceneDocument.LAST_MODIFIED))
            group.sizeBytes += LuceneDocument.numberOf(document, LuceneDocument.SIZE_BYTES)
            group.fileCount++
        }
        return byGav.values.toList()
    }

    /** The repositories of each group over the access value only (4.3): hosted when one copy is hosted, sorted by name. */
    private fun presenceOf(gavs: List<String>, access: SearchAccess, searcher: IndexSearcher): Map<String, List<RepositoryPresence>> {
        if (gavs.isEmpty()) return emptyMap()
        val query = LuceneQueries.presence(gavs, access)
        val origins = HashMap<String, HashMap<String, MutableSet<String>>>()
        forEachMatch(searcher, query) { document ->
            val repository = checkNotNull(document.get(LuceneDocument.REPOSITORY))
            origins.getOrPut(checkNotNull(document.get(LuceneDocument.GAV))) { HashMap() }
                .getOrPut(repository) { mutableSetOf() }
                .add(checkNotNull(document.get(LuceneDocument.ORIGIN)))
        }
        return origins.mapValues { (_, repositories) ->
            repositories.map { (name, kinds) ->
                val origin = if (Origin.HOSTED.name in kinds || kinds.isEmpty()) Origin.HOSTED else Origin.MIRROR
                RepositoryPresence(name, origin)
            }.sortedBy { it.name }
        }
    }

    /** The latest version of each artifact over the access value only (4.6): null above the version limit. */
    private fun latestOf(artifactKeys: List<String>, access: SearchAccess, searcher: IndexSearcher): Map<String, String?> {
        if (artifactKeys.isEmpty()) return emptyMap()
        val versions = HashMap<String, HashMap<String, String>>()
        forEachMatch(searcher, LuceneQueries.latest(artifactKeys, access)) { document ->
            val display = checkNotNull(document.get(LuceneDocument.VERSION))
            val byVersion = versions.getOrPut(checkNotNull(document.get(LuceneDocument.ARTIFACT_KEY))) { HashMap() }
            byVersion.merge(checkNotNull(document.get(LuceneDocument.VERSION_LC)), display) { a, b -> minOf(a, b) }
        }
        return artifactKeys.associateWith { key ->
            versions[key]?.let { byVersion ->
                if (byVersion.size > MAX_VERSIONS_PER_ARTIFACT) null else ArtifactVersions.latestOf(byVersion.values.toList())
            }
        }
    }

    private fun bucketsOf(
        searcher: IndexSearcher,
        query: ArtifactSearchQuery,
        access: SearchAccess,
        kind: FilterKind,
        field: String,
    ): List<FacetBucket> {
        val counts = HashMap<String, Long>()
        forEachMatch(searcher, LuceneQueries.filter(query, access, except = kind)) { document ->
            counts.merge(document.get(field) ?: NO_CLASSIFIER, 1L, Long::plus)
        }
        return counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
            .take(FACET_BUCKETS)
            .map { FacetBucket(it.key, it.value) }
    }

    private fun documentsOf(searcher: IndexSearcher, query: Query): List<Document> {
        val documents = mutableListOf<Document>()
        forEachMatch(searcher, query) { document -> documents += document }
        return documents
    }

    private fun fileItem(document: Document): ArtifactFileItem {
        val path = checkNotNull(document.get(LuceneDocument.PATH))
        val repository = checkNotNull(document.get(LuceneDocument.REPOSITORY))
        return ArtifactFileItem(
            repository = repository,
            path = path,
            name = path.substringAfterLast('/'),
            classifier = document.get(LuceneDocument.CLASSIFIER),
            extension = checkNotNull(document.get(LuceneDocument.EXTENSION)),
            size = LuceneDocument.numberOf(document, LuceneDocument.SIZE_BYTES),
            lastModified = Instant.ofEpochMilli(LuceneDocument.numberOf(document, LuceneDocument.LAST_MODIFIED)),
            origin = Origin.valueOf(checkNotNull(document.get(LuceneDocument.ORIGIN))),
            sourceHost = document.get(LuceneDocument.SOURCE_HOST),
            sha1 = document.get(LuceneDocument.SHA1),
            browseLink = browseLink(repository, path),
        )
    }

    /** Calls [visit] with the stored fields of every document that [query] matches, in no particular order. */
    private fun forEachMatch(searcher: IndexSearcher, query: Query, visit: (Document) -> Unit) {
        searcher.search(query, object : SimpleCollector() {
            private var base = 0

            override fun doSetNextReader(context: LeafReaderContext) {
                base = context.docBase
            }

            override fun collect(doc: Int) {
                visit(searcher.storedFields().document(base + doc))
            }

            override fun scoreMode(): ScoreMode = ScoreMode.COMPLETE_NO_SCORES
        })
    }

    private companion object {
        const val FACET_BUCKETS = 20
        const val MAX_VERSIONS_PER_ARTIFACT = 5_000
        const val NO_CLASSIFIER = "-"

        fun groupDirectory(groupId: String): String = groupId.replace('.', '/')

        /** `/browse/<repository>/<path>`, every segment percent-encoded (6.1), as the database engine builds it. */
        fun browseLink(repository: String, path: String): String =
            (listOf(repository) + path.split('/')).joinToString(separator = "/", prefix = "/browse/") { segment ->
                URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20")
            }
    }

}
