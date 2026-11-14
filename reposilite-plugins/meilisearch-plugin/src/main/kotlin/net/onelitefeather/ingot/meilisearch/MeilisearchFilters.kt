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

import net.onelitefeather.ingot.search.InvalidSearchQueryException
import net.onelitefeather.ingot.search.SearchUnavailableException
import net.onelitefeather.ingot.search.access.PathPredicate
import net.onelitefeather.ingot.search.access.SearchAccess
import net.onelitefeather.ingot.search.SearchTokens
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import java.util.Locale

/**
 * The filter expressions of the Meilisearch backend (design D6). The access value of a caller becomes one filter of every
 * request, so no row outside it is ever returned. A path predicate is rendered from the directories of a row, which hold every
 * ancestor directory of its path, so no string range is needed. A `Legacy` predicate is refused: a plain string prefix cannot be
 * expressed in this filter language.
 */
internal object MeilisearchFilters {

    /** Matches every document: every document has an id. */
    const val ALL_ROWS: String = "id EXISTS"

    /** Matches no document. */
    const val NO_ROWS: String = "id NOT EXISTS"

    /**
     * The filter of an access value, or null when the caller has no repository at all, so that the answer is empty without a
     * request. Each repository is one condition on its repository and its path predicate; the conditions are joined with or.
     */
    fun access(access: SearchAccess): String? {
        val parts = access.repositories.map { entry ->
            val repository = "repository = ${quote(entry.repository)}"
            if (entry.path == PathPredicate.All) {
                "($repository)"
            } else {
                "($repository AND ${path(entry.path)})"
            }
        }
        return when (parts.size) {
            0 -> null
            1 -> parts.single()
            else -> parts.joinToString(separator = " OR ", prefix = "(", postfix = ")")
        }
    }

    /** The directories of a path: every ancestor directory from the top down, for example `a/b` for `a/b/c.jar`. */
    fun directoriesOf(path: String): List<String> {
        val segments = path.split('/')
        return (1 until segments.size).map { segments.subList(0, it).joinToString("/") }
    }

    /** The condition of a path predicate over the path and the directories of a row. */
    fun path(predicate: PathPredicate): String = when (predicate) {
        PathPredicate.All -> ALL_ROWS
        PathPredicate.Never -> NO_ROWS
        is PathPredicate.Exact -> pathOrDirectory(predicate.prefix)
        is PathPredicate.Subtree -> pathOrDirectory(predicate.segments.joinToString("/"))
        is PathPredicate.Legacy -> throw SearchUnavailableException("legacy grants are not supported by the meilisearch backend")
        is PathPredicate.Conjunction -> if (predicate.parts.isEmpty()) {
            ALL_ROWS
        } else {
            predicate.parts.joinToString(separator = " AND ", prefix = "(", postfix = ")") { path(it) }
        }
        is PathPredicate.Precedence -> precedence(predicate)
    }

    /**
     * The first match rule (design D6): a rule allows a row when the row matches its condition and no earlier rule matches it.
     * The allowed rules are joined with or; no allowed rule matches nothing.
     */
    private fun precedence(predicate: PathPredicate.Precedence): String {
        val earlier = mutableListOf<String>()
        val allowed = mutableListOf<String>()
        predicate.rules.forEach { rule ->
            val condition = path(rule.condition)
            if (rule.allowed) {
                allowed += if (earlier.isEmpty()) {
                    "($condition)"
                } else {
                    "($condition AND NOT (${earlier.joinToString(" OR ")}))"
                }
            }
            earlier += condition
        }
        return when (allowed.size) {
            0 -> NO_ROWS
            1 -> allowed.single()
            else -> allowed.joinToString(separator = " OR ", prefix = "(", postfix = ")")
        }
    }

    private fun pathOrDirectory(path: String): String =
        "(path = ${quote(path)} OR directories = ${quote(path)})"

    /** A string value of the filter language, with the backslash and the quote escaped. */
    fun quote(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    /** The most words of a query text, and the most characters, as the database backend refuses them. */
    const val MAX_QUERY_WORDS: Int = 5
    const val MAX_QUERY_LENGTH: Int = 200

    /** The most parts of a coordinate word, `groupId:artifactId:version`. */
    const val MAX_COORDINATE_PARTS: Int = 3

    /**
     * The lower-cased words of a query text. A text longer than [MAX_QUERY_LENGTH] characters, or with more than [MAX_QUERY_WORDS]
     * words, is refused with the message of the database backend.
     */
    fun wordsOf(text: String?): List<String> {
        val trimmed = text?.trim().orEmpty()
        if (trimmed.length > MAX_QUERY_LENGTH) throw InvalidSearchQueryException("q", "q is limited to $MAX_QUERY_LENGTH characters")
        val words = trimmed.lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.size > MAX_QUERY_WORDS) throw InvalidSearchQueryException("q", "q is limited to $MAX_QUERY_WORDS tokens")
        return words
    }

    /**
     * The conditions of the words of a query text, one per word, all of which must match, as on the database backend.
     */
    fun wordConditions(text: String?): List<String> {
        val words = wordsOf(text)
        return words.mapIndexed { index, word -> wordCondition(word, last = index == words.lastIndex) }
    }

    /**
     * The parts of a coordinate word, `groupId:artifactId:version`, each one optional and empty when absent. A word with more parts
     * than [MAX_COORDINATE_PARTS] is refused, as on the database backend.
     */
    fun coordinatePartsOf(word: String): List<String> {
        val parts = word.split(':')
        if (parts.size > MAX_COORDINATE_PARTS) {
            throw InvalidSearchQueryException("q", "a coordinates token has at most groupId:artifactId:version")
        }
        return parts + List(MAX_COORDINATE_PARTS - parts.size) { "" }
    }

    /**
     * The condition of a coordinate word, as the database matches it: the groupId is a prefix of the groupId, the artifactId a prefix
     * of the artifactId and the version the exact version. A word without any part is refused, and it has no token branch.
     */
    private fun coordinateCondition(word: String): String {
        val (group, artifact, version) = coordinatePartsOf(word)
        val conditions = listOfNotNull(
            group.takeIf { it.isNotEmpty() }?.let { "group_heads = ${quote(it)}" },
            artifact.takeIf { it.isNotEmpty() }?.let { "artifact_heads = ${quote(it)}" },
            version.takeIf { it.isNotEmpty() }?.let { "version_lc = ${quote(it)}" },
        )
        if (conditions.isEmpty()) {
            throw InvalidSearchQueryException("q", "a coordinates token needs a groupId, an artifactId or a version")
        }
        return if (conditions.size == 1) conditions.single() else conditions.joinToString(separator = " AND ", prefix = "(", postfix = ")")
    }

    /**
     * The condition of one word, the rule of the database backend (its word match and main predicate): the word is a prefix of the
     * groupId or the artifactId, or the exact version (`word_heads`), or its parts match the tokens. A part equals a token, and only the
     * last part of the last word is a prefix of a token. A word with a dot has no token branch, and a word of more than [MAX_WORD_PARTS]
     * parts or without parts has none either.
     */
    private fun wordCondition(word: String, last: Boolean): String {
        val head = "word_heads = ${quote(word)}"
        if (':' in word) return coordinateCondition(word)
        if ('.' in word) return head
        val parts = SearchTokens.partsOf(word)
        if (parts.isEmpty() || parts.size > MAX_WORD_PARTS) return head
        val terms = parts.mapIndexed { index, part ->
            if (last && index == parts.lastIndex) "word_prefixes = ${quote(part)}" else "tokens = ${quote(part)}"
        }
        val branch = if (terms.size == 1) terms.single() else terms.joinToString(separator = " AND ", prefix = "(", postfix = ")")
        return "($head OR $branch)"
    }

    /** The most parts of a word that get a token branch: the database rule, which allows eight constraints per query word. */
    const val MAX_WORD_PARTS: Int = 8

    /** A list of values as the `IN` list of the filter language. */
    fun inList(values: Collection<String>): String = values.joinToString(separator = ", ", prefix = "[", postfix = "]") { quote(it) }

}
