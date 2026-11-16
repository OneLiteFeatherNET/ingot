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

import net.onelitefeather.ingot.search.access.PathPredicate
import net.onelitefeather.ingot.search.access.PrecedenceRule
import net.onelitefeather.ingot.search.access.RepositoryAccess
import net.onelitefeather.ingot.search.access.SearchAccess
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import org.apache.lucene.index.Term
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.MatchAllDocsQuery
import org.apache.lucene.search.MatchNoDocsQuery
import org.apache.lucene.search.PrefixQuery
import org.apache.lucene.search.Query
import org.apache.lucene.search.TermQuery
import java.util.Locale

/**
 * The access value of a caller as a Lucene query (design D7). The translation is the one of the database engine and of the other
 * search plugins, so the same rows are visible on every engine. The query is the last clause of every search, facet and file read:
 * no row outside the access value can be returned, whatever the other clauses say.
 */
internal object LuceneAccess {

    /** The query of the access value: the repositories it names, each with its path condition. No repository means no row. */
    fun query(access: SearchAccess): Query {
        if (access.repositories.isEmpty()) return MatchNoDocsQuery()
        return should(access.repositories.map(::repositoryQuery))
    }

    private fun repositoryQuery(access: RepositoryAccess): Query {
        val repository = TermQuery(Term(LuceneDocument.REPOSITORY, access.repository))
        if (access.path == PathPredicate.All) return repository
        return filter(listOf(repository, pathQuery(access.path)))
    }

    private fun pathQuery(path: PathPredicate): Query = when (path) {
        PathPredicate.All -> MatchAllDocsQuery()
        PathPredicate.Never -> MatchNoDocsQuery()
        is PathPredicate.Exact -> segmentQuery(path.prefix)
        is PathPredicate.Subtree -> if (path.segments.isEmpty()) MatchAllDocsQuery() else segmentQuery(path.segments.joinToString("/"))
        is PathPredicate.Legacy -> PrefixQuery(Term(LuceneDocument.PATH_LC, path.prefix.lowercase(Locale.ROOT)))
        is PathPredicate.Conjunction -> if (path.parts.isEmpty()) MatchAllDocsQuery() else filter(path.parts.map(::pathQuery))
        is PathPredicate.Precedence -> precedence(path.rules)
    }

    /** The path equals [base] or lies below it, on a segment boundary. */
    private fun segmentQuery(base: String): Query =
        should(listOf(TermQuery(Term(LuceneDocument.PATH, base)), PrefixQuery(Term(LuceneDocument.PATH, "$base/"))))

    /**
     * The first rule whose condition holds decides. A path is visible when, for some allowed rule, its condition holds and no earlier
     * rule's condition holds. A list without an allowed rule shows nothing.
     */
    private fun precedence(rules: List<PrecedenceRule>): Query {
        val earlier = mutableListOf<Query>()
        val visible = mutableListOf<Query>()
        for (rule in rules) {
            val condition = pathQuery(rule.condition)
            if (rule.allowed) {
                visible += if (earlier.isEmpty()) {
                    filter(listOf(condition))
                } else {
                    BooleanQuery.Builder()
                        .add(condition, BooleanClause.Occur.FILTER)
                        .apply { earlier.forEach { add(it, BooleanClause.Occur.MUST_NOT) } }
                        .build()
                }
            }
            earlier += condition
        }
        return if (visible.isEmpty()) MatchNoDocsQuery() else should(visible)
    }

    private fun filter(clauses: List<Query>): Query =
        BooleanQuery.Builder().apply { clauses.forEach { add(it, BooleanClause.Occur.FILTER) } }.build()

    private fun should(clauses: List<Query>): Query =
        BooleanQuery.Builder()
            .setMinimumNumberShouldMatch(1)
            .apply { clauses.forEach { add(it, BooleanClause.Occur.SHOULD) } }
            .build()

}
