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
import net.onelitefeather.ingot.search.SnapshotFilter
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.or
import java.util.Locale

/**
 * The filters of a search request as predicates over `search_artifact` (artifact search spec, 4.1 and 4.2). Every filter
 * kind is kept apart so a facet can leave out its own kind (4.4). Validation happens here, before any SQL is built, and
 * refuses with the name of the parameter.
 */
internal class SearchFilters(query: ArtifactSearchQuery, rows: SearchArtifactTable) {

    private val parts: Map<FilterKind, Op<Boolean>> = buildMap {
        val text = freeText(query.q, rows)
        if (text != null) put(FilterKind.TEXT, text)
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
        parts.filterKeys { it != except }.values.fold<Op<Boolean>, Op<Boolean>>(Op.TRUE) { acc, part -> acc and part }

    internal enum class FilterKind { TEXT, GROUP, ARTIFACT, VERSION, REPOSITORY, EXTENSION, CLASSIFIER, SHA1, SNAPSHOT, ORIGIN, MODIFIED, SIZE }

    private companion object {
        const val MAX_QUERY_LENGTH = 200
        const val MAX_TOKENS = 5
        const val MAX_COORDINATE_PARTS = 3
        val WHITESPACE = Regex("\\s+")
        val SHA1 = Regex("[0-9a-fA-F]{40}")

        fun nonBlank(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }

        fun checksum(value: String): String {
            if (!SHA1.matches(value)) throw InvalidSearchQueryException("sha1", "sha1 must be 40 hexadecimal characters")
            return value
        }

        /** Free text: every token must match. A token with a colon is a coordinates token (4.2). */
        fun freeText(q: String?, rows: SearchArtifactTable): Op<Boolean>? {
            val text = q?.trim() ?: return null
            if (text.length > MAX_QUERY_LENGTH) {
                throw InvalidSearchQueryException("q", "q is limited to $MAX_QUERY_LENGTH characters")
            }
            val tokens = text.split(WHITESPACE).filter { it.isNotEmpty() }.map { it.lowercase(Locale.ROOT) }
            if (tokens.isEmpty()) return null
            if (tokens.size > MAX_TOKENS) throw InvalidSearchQueryException("q", "q is limited to $MAX_TOKENS tokens")
            return tokens.map { token -> if (':' in token) coordinates(token, rows) else plainToken(token, rows) }
                .reduce { acc, op -> acc and op }
        }

        /** A token matches the groupId prefix, the artifactId prefix or the exact version. */
        fun plainToken(token: String, rows: SearchArtifactTable): Op<Boolean> =
            prefixRange(rows.groupIdLc, token) or prefixRange(rows.artifactIdLc, token) or (rows.versionLc eq token)

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
