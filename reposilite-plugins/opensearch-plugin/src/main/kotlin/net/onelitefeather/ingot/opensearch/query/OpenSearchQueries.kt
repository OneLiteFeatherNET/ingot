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
package net.onelitefeather.ingot.opensearch.query

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import net.onelitefeather.ingot.search.ArtifactFilesQuery
import net.onelitefeather.ingot.search.ArtifactSearchPage
import net.onelitefeather.ingot.search.ArtifactSearchQuery
import net.onelitefeather.ingot.search.InvalidSearchQueryException
import net.onelitefeather.ingot.search.OriginFilter
import net.onelitefeather.ingot.search.SearchOrder
import net.onelitefeather.ingot.search.SearchSort
import net.onelitefeather.ingot.search.SearchTokens
import net.onelitefeather.ingot.search.SnapshotFilter
import net.onelitefeather.ingot.search.access.PathPredicate
import net.onelitefeather.ingot.search.access.PrecedenceRule
import net.onelitefeather.ingot.search.access.RepositoryAccess
import net.onelitefeather.ingot.search.access.SearchAccess
import java.util.Locale

/** The filter kinds of a group search. A facet leaves out the kind it counts (artifact search spec, 4.4). */
internal enum class FilterKind { TEXT, GROUP, ARTIFACT, VERSION, REPOSITORY, EXTENSION, CLASSIFIER, SHA1, SNAPSHOT, ORIGIN, MODIFIED, SIZE }

/**
 * The OpenSearch bodies of the artifact search (design D5). Each body is built as a tree of maps and lists from the request and
 * the caller's [SearchAccess]; no request value is concatenated into JSON text, so no value can change the structure of a body.
 *
 * The access value is the last clause of every query. A repository with every path is one `term`; any other repository is its
 * name and its path condition. The path conditions use the same rules as [SearchAccess.matches].
 */
internal object OpenSearchQueries {

    private val MAPPER = ObjectMapper()
    private val TOTAL_BUCKETS: Int = (ArtifactSearchPage.TOTAL_CAP + 1).toInt()
    private const val MAX_QUERY_LENGTH = 200
    private const val MAX_TOKENS = 5
    private const val MAX_COORDINATE_PARTS = 3
    private const val MAX_CONSTRAINTS = 8
    private const val MAX_WINDOW = 10_100L
    private const val MAX_FILES_SIZE = 200
    private const val FACET_BUCKETS = 20
    private const val MAX_VERSIONS_PER_ARTIFACT = 5_000
    private const val NO_CLASSIFIER = "-"
    private val WHITESPACE = Regex("\\s+")
    private val SHA1 = Regex("[0-9a-fA-F]{40}")
    private val MATCH_ALL: Map<String, Any> = mapOf("match_all" to emptyMap<String, Any>())
    private val MATCH_NONE: Map<String, Any> = mapOf("match_none" to emptyMap<String, Any>())

    /** The filter of a request: its filters that [except] does not name, and the access value. */
    fun queryFilter(query: ArtifactSearchQuery, access: SearchAccess, except: FilterKind? = null): ObjectNode =
        tree(filterMap(query, access, except))

    /** The access value as one clause: the repositories it names, each with its path condition. */
    fun accessFilter(access: SearchAccess): ObjectNode = tree(accessClause(access))

    /** The bucket aggregation of one facet, with the filters of the other kinds (4.4). */
    fun facet(kind: FilterKind, query: ArtifactSearchQuery, access: SearchAccess): ObjectNode {
        val terms = linkedMapOf<String, Any>(
            "field" to facetField(kind),
            "size" to FACET_BUCKETS,
            "order" to listOf(mapOf("_count" to "desc"), mapOf("_key" to "asc")),
        )
        if (kind == FilterKind.CLASSIFIER) terms["missing"] = NO_CLASSIFIER
        return tree(
            linkedMapOf<String, Any>(
                "filter" to filterMap(query, access, kind),
                "aggs" to mapOf("buckets" to mapOf("terms" to terms)),
            ),
        )
    }

    /**
     * One window of groups in the requested order. Sorts by a key with a field of its own (group, artifact or version) use a
     * composite aggregation, whose sources are the sort order. Sorts by an aggregate (modification, size) order the terms
     * aggregation on the GAV key by that aggregate. The window is `page * size + size`, which the plugin then sorts with
     * [net.onelitefeather.ingot.search.GroupOrder] and slices.
     */
    fun groupWindow(query: ArtifactSearchQuery, access: SearchAccess): ObjectNode {
        val window = windowOf(query.page, query.size)
        val direction = directionOf(query.order)
        val groups = when (query.sort) {
            SearchSort.GROUP_ID, SearchSort.ARTIFACT_ID, SearchSort.VERSION -> compositeGroups(query.sort, direction, window)
            SearchSort.MODIFIED -> metricGroups("lastModified", direction, window)
            SearchSort.SIZE -> metricGroups("sizeBytes", direction, window)
        }
        return tree(
            linkedMapOf<String, Any>(
                "size" to 0,
                "query" to filterMap(query, access, null),
                "aggs" to mapOf("groups" to groups),
            ),
        )
    }

    /** The number of groups, bucketed up to one above the total cap, so the plugin can tell that more exist. */
    fun total(query: ArtifactSearchQuery, access: SearchAccess): ObjectNode = tree(
        linkedMapOf<String, Any>(
            "size" to 0,
            "query" to filterMap(query, access, null),
            "aggs" to mapOf("groups" to mapOf("terms" to mapOf("field" to "gav", "size" to TOTAL_BUCKETS))),
        ),
    )

    /**
     * The versions of each artifact key, `groupIdLc:artifactIdLc`, as the writer stores the `artifact_key` field. The request
     * filters do not apply to the latest version (4.6), only the access value does.
     */
    fun latestVersions(artifactKeys: List<String>, access: SearchAccess): ObjectNode {
        require(artifactKeys.isNotEmpty()) { "at least one artifact key is required" }
        val filter = listOf(termsOf("artifact_key", artifactKeys), accessClause(access))
        val versions = mapOf("terms" to mapOf("field" to "version_lc", "size" to MAX_VERSIONS_PER_ARTIFACT + 1))
        val artifacts = mapOf(
            "terms" to mapOf("field" to "artifact_key", "size" to artifactKeys.size),
            "aggs" to mapOf("versions" to versions),
        )
        return tree(
            linkedMapOf<String, Any>(
                "size" to 0,
                "query" to filterOf(filter),
                "aggs" to mapOf("artifacts" to artifacts),
            ),
        )
    }

    /** The files of one version, in path order, with the origin and repository filters and the access value. */
    fun files(query: ArtifactFilesQuery, access: SearchAccess): ObjectNode {
        listOf("groupId" to query.groupId, "artifactId" to query.artifactId, "version" to query.version)
            .firstOrNull { it.second.isBlank() }
            ?.let { throw InvalidSearchQueryException(it.first, "${it.first} is required") }
        if (query.size !in 1..MAX_FILES_SIZE) throw InvalidSearchQueryException("size", "size must be between 1 and $MAX_FILES_SIZE")
        if (query.page < 0) throw InvalidSearchQueryException("page", "page must not be negative")
        windowOf(query.page, query.size)

        val clauses = mutableListOf(
            termOf("group_id_lc", query.groupId.lowercase(Locale.ROOT)),
            termOf("artifact_id_lc", query.artifactId.lowercase(Locale.ROOT)),
            termOf("version_lc", query.version.lowercase(Locale.ROOT)),
        )
        when (query.origin) {
            OriginFilter.HOSTED -> clauses += termOf("origin", OriginFilter.HOSTED.name)
            OriginFilter.MIRROR -> clauses += termOf("origin", OriginFilter.MIRROR.name)
            OriginFilter.ALL -> Unit
        }
        if (query.repositories.isNotEmpty()) clauses += termsOf("repository", query.repositories)
        clauses += accessClause(access)
        return tree(
            linkedMapOf<String, Any>(
                "from" to query.page * query.size,
                "size" to query.size,
                "track_total_hits" to true,
                "query" to filterOf(clauses),
                "sort" to listOf(mapOf("path" to mapOf("order" to "asc")), mapOf("repository" to mapOf("order" to "asc"))),
            ),
        )
    }

    /**
     * The request's access value in a log line: the number of repositories and of path conditions, never a path (the access
     * value is the caller's rights, and the log is not the place for them).
     */
    fun describe(access: SearchAccess): String =
        "repositories=${access.repositories.size} terms=${access.repositories.sumOf { termCount(it.path) }}"

    // ---- the filters of a request -------------------------------------------------------------------------------------

    private fun filterMap(query: ArtifactSearchQuery, access: SearchAccess, except: FilterKind?): Map<String, Any> {
        val clauses = parts(query).filterKeys { it != except }.values.flatten() + accessClause(access)
        return filterOf(clauses)
    }

    /** The clauses of the request per kind, in the order of [FilterKind]. A kind without a clause is absent. */
    private fun parts(query: ArtifactSearchQuery): Map<FilterKind, List<Map<String, Any>>> {
        val parts = LinkedHashMap<FilterKind, List<Map<String, Any>>>()
        fun add(kind: FilterKind, clauses: List<Map<String, Any>>) {
            if (clauses.isNotEmpty()) parts[kind] = clauses
        }
        add(FilterKind.TEXT, textClauses(query.q))
        add(FilterKind.GROUP, listOfNotNull(nonBlank(query.groupId)?.let { prefixOf("group_id_lc", it.lowercase(Locale.ROOT)) }))
        add(FilterKind.ARTIFACT, listOfNotNull(nonBlank(query.artifactId)?.let { prefixOf("artifact_id_lc", it.lowercase(Locale.ROOT)) }))
        add(FilterKind.VERSION, listOfNotNull(versionClause(query)))
        if (query.repositories.isNotEmpty()) add(FilterKind.REPOSITORY, listOf(termsOf("repository", query.repositories)))
        if (query.extensions.isNotEmpty()) {
            add(FilterKind.EXTENSION, listOf(termsOf("extension", query.extensions.map { it.lowercase(Locale.ROOT) })))
        }
        add(FilterKind.CLASSIFIER, listOfNotNull(classifierClause(query.classifiers)))
        add(FilterKind.SHA1, listOfNotNull(nonBlank(query.sha1)?.let { termOf("sha1", checksum(it).lowercase(Locale.ROOT)) }))
        add(FilterKind.SNAPSHOT, listOfNotNull(snapshotClause(query.snapshot)))
        add(FilterKind.ORIGIN, listOfNotNull(originClause(query.origin)))
        add(FilterKind.MODIFIED, listOfNotNull(modifiedClause(query)))
        add(FilterKind.SIZE, listOfNotNull(sizeClause(query)))
        return parts
    }

    private fun textClauses(q: String?): List<Map<String, Any>> {
        val text = q?.trim() ?: return emptyList()
        if (text.length > MAX_QUERY_LENGTH) throw InvalidSearchQueryException("q", "q is limited to $MAX_QUERY_LENGTH characters")
        val words = text.split(WHITESPACE).filter { it.isNotEmpty() }.map { it.lowercase(Locale.ROOT) }
        if (words.size > MAX_TOKENS) throw InvalidSearchQueryException("q", "q is limited to $MAX_TOKENS tokens")
        return words.mapIndexed { index, word ->
            if (':' in word) coordinates(word) else plainWord(word, last = index == words.lastIndex)
        }
    }

    /** A coordinates token: one filter of its parts, each part optional. */
    private fun coordinates(token: String): Map<String, Any> {
        val parts = token.split(':')
        if (parts.size > MAX_COORDINATE_PARTS) {
            throw InvalidSearchQueryException("q", "a coordinates token has at most groupId:artifactId:version")
        }
        val conditions = listOfNotNull(
            parts[0].takeIf { it.isNotEmpty() }?.let { prefixOf("group_id_lc", it) },
            parts.getOrNull(1)?.takeIf { it.isNotEmpty() }?.let { prefixOf("artifact_id_lc", it) },
            parts.getOrNull(2)?.takeIf { it.isNotEmpty() }?.let { termOf("version_lc", it) },
        )
        if (conditions.isEmpty()) {
            throw InvalidSearchQueryException("q", "a coordinates token needs a groupId, an artifactId or a version")
        }
        return filterOf(conditions)
    }

    /**
     * A plain word: a groupId prefix, an artifactId prefix or the exact version, or, when the word splits into tokens, every token
     * of the word in the token field. The last token of the query and its last part are prefixes.
     */
    private fun plainWord(word: String, last: Boolean): Map<String, Any> = should(
        listOfNotNull(
            prefixOf("group_id_lc", word),
            prefixOf("artifact_id_lc", word),
            termOf("version_lc", word),
            wordMatch(word, last),
        ),
    )

    private fun wordMatch(word: String, last: Boolean): Map<String, Any>? {
        if ('.' in word) return null
        val parts = SearchTokens.partsOf(word)
        if (parts.isEmpty() || parts.size > MAX_CONSTRAINTS) return null
        return filterOf(
            parts.mapIndexed { index, part ->
                if (last && index == parts.lastIndex) prefixOf("tokens", part) else termOf("tokens", part)
            },
        )
    }

    private fun versionClause(query: ArtifactSearchQuery): Map<String, Any>? {
        val exact = nonBlank(query.version)
        val prefix = nonBlank(query.versionPrefix)
        if (exact != null && prefix != null) {
            throw InvalidSearchQueryException("versionPrefix", "version and versionPrefix cannot be combined")
        }
        return exact?.let { termOf("version_lc", it.lowercase(Locale.ROOT)) }
            ?: prefix?.let { prefixOf("version_lc", it.lowercase(Locale.ROOT)) }
    }

    /** `-` selects files without a classifier; the other values are exact and case-sensitive. */
    private fun classifierClause(classifiers: List<String>): Map<String, Any>? {
        if (classifiers.isEmpty()) return null
        val none: Map<String, Any>? = if (NO_CLASSIFIER in classifiers) {
            mapOf("bool" to mapOf("must_not" to listOf(mapOf("exists" to mapOf("field" to "classifier")))))
        } else {
            null
        }
        val named = classifiers.filter { it != NO_CLASSIFIER }.takeIf { it.isNotEmpty() }?.let { termsOf("classifier", it) }
        val alternatives = listOfNotNull(none, named)
        return if (alternatives.size == 1) alternatives.single() else should(alternatives)
    }

    private fun snapshotClause(filter: SnapshotFilter): Map<String, Any>? = when (filter) {
        SnapshotFilter.TRUE -> termOf("snapshot", true)
        SnapshotFilter.FALSE -> termOf("snapshot", false)
        SnapshotFilter.ALL -> null
    }

    private fun originClause(filter: OriginFilter): Map<String, Any>? = when (filter) {
        OriginFilter.HOSTED, OriginFilter.MIRROR -> termOf("origin", filter.name)
        OriginFilter.ALL -> null
    }

    /** The lower bound is inclusive, the upper bound exclusive. */
    private fun modifiedClause(query: ArtifactSearchQuery): Map<String, Any>? {
        val bounds = linkedMapOf<String, Any>()
        query.modifiedAfter?.let { bounds["gte"] = it.toEpochMilli() }
        query.modifiedBefore?.let { bounds["lt"] = it.toEpochMilli() }
        return rangeOf("last_modified", bounds)
    }

    private fun sizeClause(query: ArtifactSearchQuery): Map<String, Any>? {
        query.sizeMin?.takeIf { it < 0 }?.let { throw InvalidSearchQueryException("sizeMin", "sizeMin cannot be negative") }
        query.sizeMax?.takeIf { it < 0 }?.let { throw InvalidSearchQueryException("sizeMax", "sizeMax cannot be negative") }
        val bounds = linkedMapOf<String, Any>()
        query.sizeMin?.let { bounds["gte"] = it }
        query.sizeMax?.let { bounds["lte"] = it }
        return rangeOf("size_bytes", bounds)
    }

    private fun checksum(value: String): String {
        if (!SHA1.matches(value)) throw InvalidSearchQueryException("sha1", "sha1 must be 40 hexadecimal characters")
        return value
    }

    // ---- the access value ---------------------------------------------------------------------------------------------

    private fun accessClause(access: SearchAccess): Map<String, Any> {
        if (access.repositories.isEmpty()) return MATCH_NONE
        return should(access.repositories.map(::repositoryClause))
    }

    private fun repositoryClause(access: RepositoryAccess): Map<String, Any> {
        val repository = termOf("repository", access.repository)
        if (access.path == PathPredicate.All) return repository
        return filterOf(listOf(repository, pathClause(access.path)))
    }

    private fun pathClause(path: PathPredicate): Map<String, Any> = when (path) {
        PathPredicate.All -> MATCH_ALL
        PathPredicate.Never -> MATCH_NONE
        is PathPredicate.Exact -> segmentClause(path.prefix)
        is PathPredicate.Subtree -> if (path.segments.isEmpty()) MATCH_ALL else segmentClause(path.segments.joinToString("/"))
        is PathPredicate.Legacy -> prefixOf("path_lc", path.prefix.lowercase(Locale.ROOT))
        is PathPredicate.Conjunction -> if (path.parts.isEmpty()) MATCH_ALL else filterOf(path.parts.map(::pathClause))
        is PathPredicate.Precedence -> precedence(path.rules)
    }

    /** The path equals [base] or lies below it, segment-aware (`path = p OR path prefix "p/"`). */
    private fun segmentClause(base: String): Map<String, Any> =
        should(listOf(termOf("path", base), prefixOf("path", "$base/")))

    /**
     * The first rule whose condition holds decides. A path is visible when, for some allowed rule, its condition holds and no
     * earlier rule's condition holds. A list without an allowed rule shows nothing.
     */
    private fun precedence(rules: List<PrecedenceRule>): Map<String, Any> {
        val earlier = mutableListOf<Map<String, Any>>()
        val visible = mutableListOf<Map<String, Any>>()
        for (rule in rules) {
            val condition = pathClause(rule.condition)
            if (rule.allowed) {
                visible += if (earlier.isEmpty()) {
                    filterOf(listOf(condition))
                } else {
                    mapOf("bool" to mapOf("filter" to listOf(condition), "must_not" to earlier.toList()))
                }
            }
            earlier += condition
        }
        return if (visible.isEmpty()) MATCH_NONE else should(visible)
    }

    private fun termCount(path: PathPredicate): Int = when (path) {
        PathPredicate.All, PathPredicate.Never -> 0
        is PathPredicate.Exact, is PathPredicate.Subtree, is PathPredicate.Legacy -> 1
        is PathPredicate.Conjunction -> path.parts.sumOf(::termCount)
        is PathPredicate.Precedence -> path.rules.sumOf { termCount(it.condition) }
    }

    // ---- aggregations -------------------------------------------------------------------------------------------------

    private fun facetField(kind: FilterKind): String = when (kind) {
        FilterKind.REPOSITORY -> "repository"
        FilterKind.EXTENSION -> "extension"
        FilterKind.CLASSIFIER -> "classifier"
        FilterKind.ORIGIN -> "origin"
        FilterKind.SNAPSHOT -> "snapshot"
        else -> throw IllegalArgumentException("$kind is not a facet")
    }

    private fun compositeGroups(sort: SearchSort, direction: String, window: Int): Map<String, Any> {
        val tie = opposite(direction)
        val sortSource = when (sort) {
            SearchSort.ARTIFACT_ID -> "a"
            SearchSort.VERSION -> "v"
            else -> "g"
        }
        // The sort key first, then groupId, artifactId and version; a source that repeats an earlier one adds nothing.
        val sources = listOf(sortSource to direction, "g" to direction, "a" to direction, "v" to tie)
            .distinctBy { it.first }
            .map { (name, order) ->
                mapOf(name to mapOf("terms" to mapOf("field" to KEY_FIELDS.getValue(name), "order" to order)))
            }
        return mapOf(
            "composite" to mapOf("size" to window, "sources" to sources),
            "aggs" to groupAggregations(),
        )
    }

    private fun metricGroups(metric: String, direction: String, window: Int): Map<String, Any> = mapOf(
        "terms" to mapOf(
            "field" to "gav",
            "size" to window,
            "order" to listOf(mapOf(metric to direction), mapOf("_key" to "asc")),
        ),
        "aggs" to groupAggregations(),
    )

    /**
     * The values of one group. The display coordinates come from one hit: the display fields have no doc values, so a `min`
     * cannot read them. The file count is the document count of the bucket.
     */
    private fun groupAggregations(): Map<String, Any> = mapOf(
        "display" to mapOf(
            "top_hits" to mapOf("size" to 1, "_source" to mapOf("includes" to listOf("group_id", "artifact_id", "version"))),
        ),
        "lastModified" to mapOf("max" to mapOf("field" to "last_modified")),
        "sizeBytes" to mapOf("sum" to mapOf("field" to "size_bytes")),
    )

    // ---- helpers ------------------------------------------------------------------------------------------------------

    private val KEY_FIELDS = mapOf("g" to "group_id_lc", "a" to "artifact_id_lc", "v" to "version_lc")

    private fun windowOf(page: Int, size: Int): Int {
        val window = page.toLong() * size + size
        if (window > MAX_WINDOW) throw InvalidSearchQueryException("page", "Result window is limited to $MAX_WINDOW entries, narrow the filters")
        return window.toInt()
    }

    private fun directionOf(order: SearchOrder): String = if (order == SearchOrder.DESC) "desc" else "asc"

    private fun opposite(direction: String): String = if (direction == "desc") "asc" else "desc"

    private fun nonBlank(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }

    private fun termOf(field: String, value: Any): Map<String, Any> = mapOf("term" to mapOf(field to value))

    private fun termsOf(field: String, values: List<String>): Map<String, Any> = mapOf("terms" to mapOf(field to values))

    private fun prefixOf(field: String, value: String): Map<String, Any> = mapOf("prefix" to mapOf(field to value))

    private fun rangeOf(field: String, bounds: Map<String, Any>): Map<String, Any>? =
        bounds.takeIf { it.isNotEmpty() }?.let { mapOf("range" to mapOf(field to it)) }

    private fun filterOf(clauses: List<Map<String, Any>>): Map<String, Any> = mapOf("bool" to mapOf("filter" to clauses))

    private fun should(clauses: List<Map<String, Any>>): Map<String, Any> =
        mapOf("bool" to mapOf("should" to clauses, "minimum_should_match" to 1))

    private fun tree(value: Map<String, Any>): ObjectNode = MAPPER.valueToTree(value)

}
