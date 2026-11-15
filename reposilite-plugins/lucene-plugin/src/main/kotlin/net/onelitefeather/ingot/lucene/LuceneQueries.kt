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

import net.onelitefeather.ingot.search.ArtifactFilesQuery
import net.onelitefeather.ingot.search.ArtifactSearchQuery
import net.onelitefeather.ingot.search.InvalidSearchQueryException
import net.onelitefeather.ingot.search.OriginFilter
import net.onelitefeather.ingot.search.SearchTokens
import net.onelitefeather.ingot.search.SnapshotFilter
import net.onelitefeather.ingot.search.access.SearchAccess
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import org.apache.lucene.document.LongPoint
import org.apache.lucene.index.Term
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.FieldExistsQuery
import org.apache.lucene.search.MatchAllDocsQuery
import org.apache.lucene.search.PrefixQuery
import org.apache.lucene.search.Query
import org.apache.lucene.search.TermInSetQuery
import org.apache.lucene.search.TermQuery
import org.apache.lucene.util.BytesRef
import java.util.Locale

/** The filter kinds of a group search. A facet leaves out the kind it counts (artifact search spec, 4.4). */
internal enum class FilterKind { TEXT, GROUP, ARTIFACT, VERSION, REPOSITORY, EXTENSION, CLASSIFIER, SHA1, SNAPSHOT, ORIGIN, MODIFIED, SIZE }

/** Which groups a search reads: the groups main returns, listed first, or the groups only a word match returns, listed after them. */
internal enum class GroupPart { MAIN, WORDS }

/**
 * The Lucene queries of the artifact search (design D7). Each clause means what the Elasticsearch clause of the same request means,
 * so the same request answers the same rows on every engine. The access value is the last clause of every query.
 */
internal object LuceneQueries {

    private const val MAX_QUERY_LENGTH = 200
    private const val MAX_TOKENS = 5
    private const val MAX_COORDINATE_PARTS = 3
    private const val MAX_CONSTRAINTS = 8
    private const val MAX_SEARCH_SIZE = 100
    private const val MAX_SEARCH_WINDOW = 10_000L
    private const val MAX_FILES_SIZE = 200
    private const val NO_CLASSIFIER = "-"
    private val WHITESPACE = Regex("\\s+")
    private val SHA1 = Regex("[0-9a-fA-F]{40}")

    /** The filter of a request: its filters that [except] does not name, and the access value. */
    fun filter(query: ArtifactSearchQuery, access: SearchAccess, except: FilterKind? = null): Query {
        val text = if (except == FilterKind.TEXT) emptyList() else textClauses(query.q, overall = true)
        return filterOf(text + others(query, except) + LuceneAccess.query(access))
    }

    /** The query of the groups of [part]. The words-only part leaves out every group the main part returns. */
    fun group(query: ArtifactSearchQuery, access: SearchAccess, part: GroupPart): Query = when (part) {
        GroupPart.MAIN -> filterOf(textClauses(query.q, overall = false) + others(query) + LuceneAccess.query(access))
        GroupPart.WORDS -> BooleanQuery.Builder()
            .add(filterOf(textClauses(query.q, overall = true) + others(query) + LuceneAccess.query(access)), BooleanClause.Occur.FILTER)
            .add(filterOf(textClauses(query.q, overall = false) + others(query)), BooleanClause.Occur.MUST_NOT)
            .build()
    }

    /** Whether the request reads the word match: one of its plain words splits into tokens the token table holds. */
    fun usesWordMatch(query: ArtifactSearchQuery): Boolean = words(query.q).any { word ->
        ':' !in word && '.' !in word && SearchTokens.partsOf(word).let { parts -> parts.isNotEmpty() && parts.size <= MAX_CONSTRAINTS }
    }

    /** The files of one version, with the origin and repository filters and the access value. */
    fun files(query: ArtifactFilesQuery, access: SearchAccess): Query {
        listOf("groupId" to query.groupId, "artifactId" to query.artifactId, "version" to query.version)
            .firstOrNull { it.second.isBlank() }
            ?.let { throw InvalidSearchQueryException(it.first, "${it.first} is required") }
        if (query.size !in 1..MAX_FILES_SIZE) throw InvalidSearchQueryException("size", "size must be between 1 and $MAX_FILES_SIZE")
        if (query.page < 0) throw InvalidSearchQueryException("page", "page must not be negative")
        val clauses = mutableListOf<Query>(
            term(LuceneDocument.GROUP_ID_LC, query.groupId.lowercase(Locale.ROOT)),
            term(LuceneDocument.ARTIFACT_ID_LC, query.artifactId.lowercase(Locale.ROOT)),
            term(LuceneDocument.VERSION_LC, query.version.lowercase(Locale.ROOT)),
        )
        when (query.origin) {
            OriginFilter.HOSTED, OriginFilter.MIRROR -> clauses += term(LuceneDocument.ORIGIN, query.origin.name)
            OriginFilter.ALL -> Unit
        }
        if (query.repositories.isNotEmpty()) clauses += terms(LuceneDocument.REPOSITORY, query.repositories)
        clauses += LuceneAccess.query(access)
        return filterOf(clauses)
    }

    /** The documents of the given GAV keys that the access value shows: the repositories and origins of a group. */
    fun presence(gavKeys: List<String>, access: SearchAccess): Query = filterOf(listOf(terms(LuceneDocument.GAV, gavKeys), LuceneAccess.query(access)))

    /** The documents of the given artifact keys that the access value shows: the versions the latest version is chosen from. */
    fun latest(artifactKeys: List<String>, access: SearchAccess): Query =
        filterOf(listOf(terms(LuceneDocument.ARTIFACT_KEY, artifactKeys), LuceneAccess.query(access)))

    /** Validates the window of a group request before any query is built. */
    fun checkWindow(page: Int, size: Int) {
        if (page < 0) throw InvalidSearchQueryException("page", "page must not be negative")
        if (size !in 1..MAX_SEARCH_SIZE) throw InvalidSearchQueryException("size", "size must be between 1 and $MAX_SEARCH_SIZE")
        if (page.toLong() * size + size > MAX_SEARCH_WINDOW) {
            throw InvalidSearchQueryException("page", "Result window is limited to $MAX_SEARCH_WINDOW entries, narrow the filters")
        }
    }

    // ---- the filters of a request ---------------------------------------------------------------------------------------

    /** The clauses of the request other than its text, in the order of [FilterKind]. A kind without a clause is absent. */
    private fun others(query: ArtifactSearchQuery, except: FilterKind? = null): List<Query> =
        parts(query).filterKeys { it != except }.values.flatten()

    private fun parts(query: ArtifactSearchQuery): Map<FilterKind, List<Query>> {
        val parts = LinkedHashMap<FilterKind, List<Query>>()
        fun add(kind: FilterKind, clauses: List<Query>) {
            if (clauses.isNotEmpty()) parts[kind] = clauses
        }
        add(FilterKind.GROUP, listOfNotNull(nonBlank(query.groupId)?.let { prefix(LuceneDocument.GROUP_ID_LC, it.lowercase(Locale.ROOT)) }))
        add(FilterKind.ARTIFACT, listOfNotNull(nonBlank(query.artifactId)?.let { prefix(LuceneDocument.ARTIFACT_ID_LC, it.lowercase(Locale.ROOT)) }))
        add(FilterKind.VERSION, listOfNotNull(versionClause(query)))
        if (query.repositories.isNotEmpty()) add(FilterKind.REPOSITORY, listOf(terms(LuceneDocument.REPOSITORY, query.repositories)))
        if (query.extensions.isNotEmpty()) {
            add(FilterKind.EXTENSION, listOf(terms(LuceneDocument.EXTENSION, query.extensions.map { it.lowercase(Locale.ROOT) })))
        }
        add(FilterKind.CLASSIFIER, listOfNotNull(classifierClause(query.classifiers)))
        add(FilterKind.SHA1, listOfNotNull(nonBlank(query.sha1)?.let { term(LuceneDocument.SHA1, checksum(it).lowercase(Locale.ROOT)) }))
        add(FilterKind.SNAPSHOT, listOfNotNull(snapshotClause(query.snapshot)))
        add(FilterKind.ORIGIN, listOfNotNull(originClause(query.origin)))
        add(FilterKind.MODIFIED, listOf(modifiedClause(query)))
        add(FilterKind.SIZE, listOf(sizeClause(query)))
        return parts
    }

    /** The words of q, lower-cased, after the length and token checks that the database engine makes. */
    private fun words(q: String?): List<String> {
        val text = q?.trim() ?: return emptyList()
        if (text.length > MAX_QUERY_LENGTH) throw InvalidSearchQueryException("q", "q is limited to $MAX_QUERY_LENGTH characters")
        val words = text.split(WHITESPACE).filter { it.isNotEmpty() }.map { it.lowercase(Locale.ROOT) }
        if (words.size > MAX_TOKENS) throw InvalidSearchQueryException("q", "q is limited to $MAX_TOKENS tokens")
        return words
    }

    /**
     * One clause per word. [overall] adds the word match of a plain word to its main match, so the clause is main's match or the word
     * match; without it the clause is main's own match, which main uses to list its groups.
     */
    private fun textClauses(q: String?, overall: Boolean): List<Query> {
        val words = words(q)
        return words.mapIndexed { index, word ->
            if (':' in word) coordinates(word) else plainWord(word, last = index == words.lastIndex, overall = overall)
        }
    }

    /** A coordinates token: one filter of its parts, each part optional. */
    private fun coordinates(token: String): Query {
        val parts = token.split(':')
        if (parts.size > MAX_COORDINATE_PARTS) {
            throw InvalidSearchQueryException("q", "a coordinates token has at most groupId:artifactId:version")
        }
        val conditions = listOfNotNull(
            parts[0].takeIf { it.isNotEmpty() }?.let { prefix(LuceneDocument.GROUP_ID_LC, it) },
            parts.getOrNull(1)?.takeIf { it.isNotEmpty() }?.let { prefix(LuceneDocument.ARTIFACT_ID_LC, it) },
            parts.getOrNull(2)?.takeIf { it.isNotEmpty() }?.let { term(LuceneDocument.VERSION_LC, it) },
        )
        if (conditions.isEmpty()) {
            throw InvalidSearchQueryException("q", "a coordinates token needs a groupId, an artifactId or a version")
        }
        return filterOf(conditions)
    }

    /**
     * A plain word: a groupId prefix, an artifactId prefix or the exact version, and, when [overall] is set and the word splits into
     * tokens, every token of the word in the token field. The last token of the query and its last part are prefixes.
     */
    private fun plainWord(word: String, last: Boolean, overall: Boolean): Query {
        val main = listOf(
            prefix(LuceneDocument.GROUP_ID_LC, word),
            prefix(LuceneDocument.ARTIFACT_ID_LC, word),
            term(LuceneDocument.VERSION_LC, word),
        )
        return should(if (overall) main + listOfNotNull(wordMatch(word, last)) else main)
    }

    private fun wordMatch(word: String, last: Boolean): Query? {
        if ('.' in word) return null
        val parts = SearchTokens.partsOf(word)
        if (parts.isEmpty() || parts.size > MAX_CONSTRAINTS) return null
        return filterOf(
            parts.mapIndexed { index, part ->
                if (last && index == parts.lastIndex) prefix(LuceneDocument.TOKENS, part) else term(LuceneDocument.TOKENS, part)
            },
        )
    }

    private fun versionClause(query: ArtifactSearchQuery): Query? {
        val exact = nonBlank(query.version)
        val prefix = nonBlank(query.versionPrefix)
        if (exact != null && prefix != null) {
            throw InvalidSearchQueryException("versionPrefix", "version and versionPrefix cannot be combined")
        }
        return exact?.let { term(LuceneDocument.VERSION_LC, it.lowercase(Locale.ROOT)) }
            ?: prefix?.let { prefix(LuceneDocument.VERSION_LC, it.lowercase(Locale.ROOT)) }
    }

    /** `-` selects files without a classifier; the other values are exact and case-sensitive. */
    private fun classifierClause(classifiers: List<String>): Query? {
        if (classifiers.isEmpty()) return null
        val none: Query? = if (NO_CLASSIFIER in classifiers) {
            BooleanQuery.Builder()
                .add(MatchAllDocsQuery(), BooleanClause.Occur.FILTER)
                .add(FieldExistsQuery(LuceneDocument.CLASSIFIER), BooleanClause.Occur.MUST_NOT)
                .build()
        } else {
            null
        }
        val named = classifiers.filter { it != NO_CLASSIFIER }.takeIf { it.isNotEmpty() }?.let { terms(LuceneDocument.CLASSIFIER, it) }
        val alternatives = listOfNotNull(none, named)
        return if (alternatives.size == 1) alternatives.single() else should(alternatives)
    }

    private fun snapshotClause(filter: SnapshotFilter): Query? = when (filter) {
        SnapshotFilter.TRUE -> term(LuceneDocument.SNAPSHOT, "true")
        SnapshotFilter.FALSE -> term(LuceneDocument.SNAPSHOT, "false")
        SnapshotFilter.ALL -> null
    }

    private fun originClause(filter: OriginFilter): Query? = when (filter) {
        OriginFilter.HOSTED, OriginFilter.MIRROR -> term(LuceneDocument.ORIGIN, filter.name)
        OriginFilter.ALL -> null
    }

    /** The lower bound is inclusive, the upper bound exclusive. Without a bound the range is open. */
    private fun modifiedClause(query: ArtifactSearchQuery): Query {
        val lower = query.modifiedAfter?.toEpochMilli() ?: Long.MIN_VALUE
        val upper = query.modifiedBefore?.toEpochMilli()?.minus(1) ?: Long.MAX_VALUE
        return if (query.modifiedAfter == null && query.modifiedBefore == null) {
            MatchAllDocsQuery()
        } else {
            LongPoint.newRangeQuery(LuceneDocument.LAST_MODIFIED, lower, upper)
        }
    }

    private fun sizeClause(query: ArtifactSearchQuery): Query {
        query.sizeMin?.takeIf { it < 0 }?.let { throw InvalidSearchQueryException("sizeMin", "sizeMin cannot be negative") }
        query.sizeMax?.takeIf { it < 0 }?.let { throw InvalidSearchQueryException("sizeMax", "sizeMax cannot be negative") }
        if (query.sizeMin == null && query.sizeMax == null) return MatchAllDocsQuery()
        return LongPoint.newRangeQuery(LuceneDocument.SIZE_BYTES, query.sizeMin ?: 0L, query.sizeMax ?: Long.MAX_VALUE)
    }

    private fun checksum(value: String): String {
        if (!SHA1.matches(value)) throw InvalidSearchQueryException("sha1", "sha1 must be 40 hexadecimal characters")
        return value
    }

    // ---- helpers --------------------------------------------------------------------------------------------------------

    private fun nonBlank(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }

    private fun term(field: String, value: String): Query = TermQuery(Term(field, value))

    private fun terms(field: String, values: List<String>): Query = TermInSetQuery(field, values.map { BytesRef(it) })

    private fun prefix(field: String, value: String): Query = PrefixQuery(Term(field, value))

    private fun filterOf(clauses: List<Query>): Query =
        BooleanQuery.Builder().apply { clauses.forEach { add(it, BooleanClause.Occur.FILTER) } }.build()

    private fun should(clauses: List<Query>): Query =
        BooleanQuery.Builder()
            .setMinimumNumberShouldMatch(1)
            .apply { clauses.forEach { add(it, BooleanClause.Occur.SHOULD) } }
            .build()

}
