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

import net.onelitefeather.ingot.search.access.FixtureRow
import net.onelitefeather.ingot.search.access.PathPredicate
import net.onelitefeather.ingot.search.access.PrecedenceRule
import net.onelitefeather.ingot.search.access.RepositoryAccess
import net.onelitefeather.ingot.search.access.SearchAccess
import net.onelitefeather.ingot.search.access.SearchAccessCompiler
import net.onelitefeather.ingot.search.access.SearchAccessFixtures
import net.onelitefeather.ingot.search.access.SubjectCase
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.StringField
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.store.ByteBuffersDirectory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.Locale

/**
 * The access query returns the rows that [SearchAccess.matches] returns, which the database engine is pinned to (artifact search
 * spec, 5.7). Each test writes the fixture rows into an in-memory Lucene index, runs the access query on it and compares the result.
 */
internal class LuceneAccessTranslationTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("net.onelitefeather.ingot.search.access.SearchAccessFixtures#subjects")
    fun `the translated access returns the rows the search access returns for each parity caller`(case: SubjectCase) {
        val access = SearchAccessCompiler.compile(case.subject, SearchAccessFixtures.repositories)

        val shown = visible(access)

        val disagreements = SearchAccessFixtures.rows.filter { row ->
            shown.contains(row) != access.matches(row.repository, row.path)
        }

        assertThat(disagreements)
            .`as`("rows where the translated access and SearchAccess.matches disagree for caller '${case.name}'")
            .isEmpty()
    }

    @Test
    fun `an all path shows every row of its repository and none of another`() {
        val access = SearchAccess(listOf(RepositoryAccess("releases", PathPredicate.All)))

        assertThat(visiblePaths(access, "releases"))
            .`as`("paths of the repository the access names")
            .containsExactlyInAnyOrderElementsOf(SearchAccessFixtures.paths)
        assertThat(visiblePaths(access, "private")).`as`("paths of another repository").isEmpty()
    }

    @Test
    fun `an exact path shows the path itself and the paths below it, not a sibling with the same text`() {
        val access = SearchAccess(listOf(RepositoryAccess("releases", PathPredicate.Exact("com/example"))))

        assertThat(visiblePaths(access, "releases"))
            .`as`("paths under an exact path")
            .contains("com/example", "com/example/app/1.0/app-1.0.jar")
            .doesNotContain("com/examples/app/1.0/app-1.0.jar")
    }

    @Test
    fun `a legacy path matches its text prefix ignoring case and without a segment boundary`() {
        val access = SearchAccess(listOf(RepositoryAccess("releases", PathPredicate.Legacy("com/example"))))

        assertThat(visiblePaths(access, "releases"))
            .`as`("paths under a legacy prefix")
            .contains("com/examples/app/1.0/app-1.0.jar", "com/Example/app/1.0/app-1.0.jar")
            .doesNotContain("org/other/lib/1.0/lib-1.0.jar")
    }

    @Test
    fun `a precedence list hides the path that an earlier denial covers even when a later allow covers it too`() {
        val precedence = PathPredicate.Precedence(
            listOf(
                PrecedenceRule(PathPredicate.Subtree(listOf("net", "onelitefeather", "mycelium-bom")), allowed = false),
                PrecedenceRule(PathPredicate.Subtree(listOf("net", "onelitefeather")), allowed = true),
            ),
        )
        val access = SearchAccess(listOf(RepositoryAccess("private", precedence)))

        assertThat(visiblePaths(access, "private"))
            .`as`("paths under a precedence list with a denial first")
            .doesNotContain("net/onelitefeather/mycelium-bom/1.0/mycelium-bom-1.0.jar")
            .contains("net/onelitefeather/mycelium/2.0/mycelium-2.0.jar")
    }

    @Test
    fun `an empty access value finds no row`() {
        assertThat(visiblePaths(SearchAccess.NONE, "releases")).`as`("paths for a caller that sees nothing").isEmpty()
    }

    /** The rows of the fixtures that [access] lets the query return. */
    private fun visible(access: SearchAccess): Set<FixtureRow> = withIndex { searcher ->
        val hits = searcher.search(LuceneAccess.query(access), maxOf(1, searcher.indexReader.maxDoc())).scoreDocs
        hits.map { hit ->
            val document = searcher.storedFields().document(hit.doc)
            FixtureRow(document.get(LuceneDocument.REPOSITORY), document.get(LuceneDocument.PATH))
        }.toSet()
    }

    private fun visiblePaths(access: SearchAccess, repository: String): List<String> =
        visible(access).filter { it.repository == repository }.map { it.path }.sorted()

    /** Writes the fixture rows into a fresh in-memory index and runs [block] on a searcher of it. */
    private fun <T> withIndex(block: (IndexSearcher) -> T): T {
        val directory = ByteBuffersDirectory()
        IndexWriter(directory, IndexWriterConfig(StandardAnalyzer())).use { writer ->
            SearchAccessFixtures.rows.forEach { row -> writer.addDocument(document(row)) }
            writer.commit()
        }
        return DirectoryReader.open(directory).use { reader -> block(IndexSearcher(reader)) }
    }

    private fun document(row: FixtureRow): Document = Document().apply {
        add(StringField(LuceneDocument.REPOSITORY, row.repository, Field.Store.YES))
        add(StringField(LuceneDocument.PATH, row.path, Field.Store.YES))
        add(StringField(LuceneDocument.PATH_LC, row.path.lowercase(Locale.ROOT), Field.Store.NO))
    }

}
