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

import net.onelitefeather.ingot.search.ArtifactSearchQuery
import net.onelitefeather.ingot.search.InvalidSearchQueryException
import net.onelitefeather.ingot.search.OriginFilter
import net.onelitefeather.ingot.search.SearchTokens
import net.onelitefeather.ingot.search.SnapshotFilter
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.notInSubQuery
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.union
import org.jetbrains.exposed.v1.jdbc.except
import org.jetbrains.exposed.v1.core.AbstractQuery
import java.util.Locale

/**
 * The filters of a search request as predicates over `search_artifact` (artifact search spec, 4.1 and 4.2). Every filter
 * kind is kept apart so a facet can leave out its own kind (4.4). Validation happens here, before any SQL is built, and
 * refuses with the name of the parameter.
 */
internal class SearchFilters(
    private val query: ArtifactSearchQuery,
    private val rows: SearchArtifactTable,
    private val tokens: SearchTokenTable,
) {

    private val artifacts: SearchArtifactTable = rows

    /**
     * One whitespace token of q (search decision): [main] is main's predicate for it, [match] its word match, or null. The union
     * of the two is built by [overall] only, because the set operation needs an open transaction to resolve its dialect.
     */
    private class TokenPredicate(val main: Op<Boolean>, val match: Op<Boolean>?, val artifacts: SearchArtifactTable) {

        val wordMatch: Boolean get() = match != null

        /** `id IN (main rows UNION word rows)` for a plain token, [main] for the others. Call inside a transaction. */
        fun overallIds(): AbstractQuery<*> {
            val mainIds = artifacts.select(artifacts.id).where { main }
            val word = match ?: return mainIds
            return mainIds.union(artifacts.select(artifacts.id).where { word })
        }

        fun overall(): Op<Boolean> {
            val word = match ?: return main
            val mainRows = artifacts.select(artifacts.id).where { main }
            val wordRows = artifacts.select(artifacts.id).where { word }
            return artifacts.id inSubQuery mainRows.union(wordRows)
        }
    }

    private val tokenPredicates: List<TokenPredicate> = parseTokens(query.q, rows, tokens)

    /** Main's text: the main predicates of the tokens, ANDed, exactly as main matches q. Null when q has no word. */
    private val text: Op<Boolean>? = tokenPredicates.map { it.main }.reduceOrNull { acc, op -> acc and op }

    /** The text with the word matches: the overall predicates of the tokens, ANDed. Null when q has no word. */
    private val tokenText: Op<Boolean>?
        get() = tokenPredicates.map { it.overall() }.reduceOrNull { acc, op -> acc and op }

    /** True when a token has a word match, so the query reads the token table (search decision). */
    val usesTokenTable: Boolean get() = tokenPredicates.any { it.wordMatch }

    private fun parts(): Map<FilterKind, Op<Boolean>> = buildMap {
        tokenText?.let { put(FilterKind.TEXT, it) }
        nonBlank(query.groupId)?.let { put(FilterKind.GROUP, prefixRange(rows.groupIdLc, it.lowercase(Locale.ROOT))) }
        nonBlank(query.artifactId)?.let { put(FilterKind.ARTIFACT, prefixRange(rows.artifactIdLc, it.lowercase(Locale.ROOT))) }
        versionFilter(query, rows)?.let { put(FilterKind.VERSION, it) }
        if (query.repositories.isNotEmpty()) put(FilterKind.REPOSITORY, rows.repository inList query.repositories)
        if (query.extensions.isNotEmpty()) {
            put(FilterKind.EXTENSION, rows.extension inList query.extensions.map { it.lowercase(Locale.ROOT) })
        }
        classifierFilter(query.classifiers, rows)?.let { put(FilterKind.CLASSIFIER, it) }
        nonBlank(query.sha1)?.let { put(FilterKind.SHA1, rows.sha1 eq checksum(it).lowercase(Locale.ROOT)) }
        snapshotFilter(query.snapshot, rows)?.let { put(FilterKind.SNAPSHOT, it) }
        originFilter(query.origin, rows)?.let { put(FilterKind.ORIGIN, it) }
        modifiedFilter(query, rows)?.let { put(FilterKind.MODIFIED, it) }
        sizeFilter(query, rows)?.let { put(FilterKind.SIZE, it) }
    }

    /** The AND of every filter, except the kind [except] when a facet asks for its own alternatives. */
    fun predicate(except: FilterKind? = null): Op<Boolean> =
        parts().filterKeys { it != except }.values.fold<Op<Boolean>, Op<Boolean>>(Op.TRUE) { acc, part -> acc and part }

    /** Main's rows and the other filters: the groups main returns, which a search lists first, in main's order. */
    fun coordinatePredicate(): Op<Boolean> = (text ?: Op.TRUE) and others()

    /**
     * The rows that only a word match returns (search decision): the overall text and the other filters, but none of the rows
     * main returns. A search lists them after main's rows. Requires [usesTokenTable].
     */
    fun wordOnlyPredicate(): Op<Boolean> {
        if (tokenPredicates.size == 1 && tokenPredicates.single().wordMatch) {
            // Single token: the word-only ids are (union of main and word ids) EXCEPT main ids, driven by the index lookups.
            val only = tokenPredicates.single()
            val wordOnlyIds = only.overallIds().except(artifacts.select(artifacts.id).where { only.main })
            return (artifacts.id inSubQuery wordOnlyIds) and others()
        }
        val mainRows = artifacts.select(artifacts.id).where { coordinatePredicate() }
        return (tokenText ?: Op.FALSE) and others() and (artifacts.id notInSubQuery mainRows)
    }

    private fun others(): Op<Boolean> = parts().filterKeys { it != FilterKind.TEXT }.values
        .fold<Op<Boolean>, Op<Boolean>>(Op.TRUE) { acc, part -> acc and part }

    internal enum class FilterKind { TEXT, GROUP, ARTIFACT, VERSION, REPOSITORY, EXTENSION, CLASSIFIER, SHA1, SNAPSHOT, ORIGIN, MODIFIED, SIZE }

    private companion object {
        const val MAX_QUERY_LENGTH = 200
        const val MAX_TOKENS = 5
        const val MAX_COORDINATE_PARTS = 3

        /** The most token constraints of one loose query. A query above it gets no word match. */
        const val MAX_CONSTRAINTS = 8

        val WHITESPACE = Regex("\\s+")
        val SHA1 = Regex("[0-9a-fA-F]{40}")

        fun nonBlank(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }

        fun checksum(value: String): String {
            if (!SHA1.matches(value)) throw InvalidSearchQueryException("sha1", "sha1 must be 40 hexadecimal characters")
            return value
        }

        /**
         * The tokens of q (search decision, per whitespace token). Lengths, the token count and the errors are main's. A token with
         * a `.` or a `:` keeps main's predicate only. A plain token adds its word match: every part equals a token of the GAV, the
         * last part of the last token by its prefix. The overall predicate is `id IN (main rows UNION word rows)`, an uncorrelated
         * IN with no OR and no correlated EXISTS.
         */
        fun parseTokens(q: String?, rows: SearchArtifactTable, tokens: SearchTokenTable): List<TokenPredicate> {
            val text = q?.trim() ?: return emptyList()
            if (text.length > MAX_QUERY_LENGTH) {
                throw InvalidSearchQueryException("q", "q is limited to $MAX_QUERY_LENGTH characters")
            }
            val words = text.split(WHITESPACE).filter { it.isNotEmpty() }.map { it.lowercase(Locale.ROOT) }
            if (words.isEmpty()) return emptyList()
            if (words.size > MAX_TOKENS) throw InvalidSearchQueryException("q", "q is limited to $MAX_TOKENS tokens")
            return words.mapIndexed { index, word ->
                val main = if (':' in word) coordinates(word, rows) else plainToken(word, rows)
                val match = if ('.' in word || ':' in word) null else wordMatch(word, index == words.lastIndex, rows, tokens)
                TokenPredicate(main, match, rows)
            }
        }

        /** A word matches the groupId prefix, the artifactId prefix or the exact version. Unchanged from main. */
        fun plainToken(word: String, rows: SearchArtifactTable): Op<Boolean> =
            prefixRange(rows.groupIdLc, word) or prefixRange(rows.artifactIdLc, word) or (rows.versionLc eq word)

        /**
         * The word match of one plain token: each part is one uncorrelated IN on the token index, ANDed. Null when the word has no
         * part or more than [MAX_CONSTRAINTS] parts.
         */
        fun wordMatch(word: String, last: Boolean, rows: SearchArtifactTable, tokens: SearchTokenTable): Op<Boolean>? {
            val parts = SearchTokens.partsOf(word)
            if (parts.isEmpty() || parts.size > MAX_CONSTRAINTS) return null
            val lookups: List<Op<Boolean>> = parts.mapIndexed { index, part ->
                val prefix = last && index == parts.lastIndex
                val match = tokens.select(tokens.gavKey).where {
                    if (prefix) prefixRange(tokens.token, part) else tokens.token eq part
                }
                rows.gavKey inSubQuery match
            }
            return lookups.reduce { acc, op -> acc and op }
        }

        /** `group:artifact:version`, each part optional: groupId prefix, artifactId prefix, exact version. */
        fun coordinates(token: String, rows: SearchArtifactTable): Op<Boolean> {
            val parts = token.split(':')
            if (parts.size > MAX_COORDINATE_PARTS) {
                throw InvalidSearchQueryException("q", "a coordinates token has at most groupId:artifactId:version")
            }
            val group = parts[0].takeIf { it.isNotEmpty() }
            val artifact = parts.getOrNull(1)?.takeIf { it.isNotEmpty() }
            val version = parts.getOrNull(2)?.takeIf { it.isNotEmpty() }
            val conditions = listOfNotNull(
                group?.let { prefixRange(rows.groupIdLc, it) },
                artifact?.let { prefixRange(rows.artifactIdLc, it) },
                version?.let { rows.versionLc eq it },
            )
            if (conditions.isEmpty()) {
                throw InvalidSearchQueryException("q", "a coordinates token needs a groupId, an artifactId or a version")
            }
            return conditions.reduce { acc, op -> acc and op }
        }

        fun versionFilter(query: ArtifactSearchQuery, rows: SearchArtifactTable): Op<Boolean>? {
            val exact = nonBlank(query.version)
            val prefix = nonBlank(query.versionPrefix)
            if (exact != null && prefix != null) {
                throw InvalidSearchQueryException("versionPrefix", "version and versionPrefix cannot be combined")
            }
            return exact?.let { rows.versionLc eq it.lowercase(Locale.ROOT) }
                ?: prefix?.let { prefixRange(rows.versionLc, it.lowercase(Locale.ROOT)) }
        }

        /** `-` selects files without a classifier; the other values are exact and case-sensitive. */
        fun classifierFilter(classifiers: List<String>, rows: SearchArtifactTable): Op<Boolean>? {
            if (classifiers.isEmpty()) return null
            val named = classifiers.filter { it != NO_CLASSIFIER }
            val none: Op<Boolean>? = if (NO_CLASSIFIER in classifiers) rows.classifier.isNull() else null
            val some: Op<Boolean>? = named.takeIf { it.isNotEmpty() }?.let { rows.classifier inList it }
            return listOfNotNull(none, some).reduce { acc, op -> acc or op }
        }

        fun snapshotFilter(filter: SnapshotFilter, rows: SearchArtifactTable): Op<Boolean>? = when (filter) {
            SnapshotFilter.TRUE -> rows.snapshot eq true
            SnapshotFilter.FALSE -> rows.snapshot eq false
            SnapshotFilter.ALL -> null
        }

        fun originFilter(filter: OriginFilter, rows: SearchArtifactTable): Op<Boolean>? = when (filter) {
            OriginFilter.HOSTED -> rows.origin eq OriginFilter.HOSTED.name
            OriginFilter.MIRROR -> rows.origin eq OriginFilter.MIRROR.name
            OriginFilter.ALL -> null
        }

        /** The lower bound is inclusive, the upper bound exclusive. */
        fun modifiedFilter(query: ArtifactSearchQuery, rows: SearchArtifactTable): Op<Boolean>? {
            val after: Op<Boolean>? = query.modifiedAfter?.let { rows.lastModified greaterEq it.toEpochMilli() }
            val before: Op<Boolean>? = query.modifiedBefore?.let { rows.lastModified less it.toEpochMilli() }
            return listOfNotNull(after, before).takeIf { it.isNotEmpty() }?.reduce { acc, op -> acc and op }
        }

        fun sizeFilter(query: ArtifactSearchQuery, rows: SearchArtifactTable): Op<Boolean>? {
            query.sizeMin?.takeIf { it < 0 }?.let { throw InvalidSearchQueryException("sizeMin", "sizeMin cannot be negative") }
            query.sizeMax?.takeIf { it < 0 }?.let { throw InvalidSearchQueryException("sizeMax", "sizeMax cannot be negative") }
            val min: Op<Boolean>? = query.sizeMin?.let { rows.sizeBytes greaterEq it }
            val max: Op<Boolean>? = query.sizeMax?.let { rows.sizeBytes lessEq it }
            return listOfNotNull(min, max).takeIf { it.isNotEmpty() }?.reduce { acc, op -> acc and op }
        }

        /**
         * The range form of a prefix match (2.10): `column >= prefix AND column < next`. No LIKE, so no escaping and no
         * dialect-specific collation. The caller passes a lower-cased prefix for the `_lc` columns.
         */
        fun prefixRange(column: Column<String>, prefix: String): Op<Boolean> {
            val lower: Op<Boolean> = column greaterEq prefix
            val upper = prefixUpperBound(prefix) ?: return lower
            return lower and (column less upper)
        }

    }

}

/**
 * The exclusive upper bound of the prefix range of [prefix] (2.10): the last code point incremented by one, after dropping
 * trailing code points at the top of Unicode. Null when no such bound exists: the empty prefix, or only top code points.
 */
internal fun prefixUpperBound(prefix: String): String? {
    val codePoints = prefix.codePoints().toArray()
    val last = codePoints.indexOfLast { it != MAX_CODE_POINT }
    if (last < 0) return null
    return String(codePoints, 0, last) + String(Character.toChars(codePoints[last] + 1))
}

private const val MAX_CODE_POINT = 0x10FFFF

/** The classifier value that selects files without a classifier (4.1). */
private const val NO_CLASSIFIER = "-"
