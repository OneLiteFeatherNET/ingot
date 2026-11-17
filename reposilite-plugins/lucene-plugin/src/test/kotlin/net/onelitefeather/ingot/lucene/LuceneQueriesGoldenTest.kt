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
import net.onelitefeather.ingot.search.SnapshotFilter
import net.onelitefeather.ingot.search.access.PathPredicate
import net.onelitefeather.ingot.search.access.PrecedenceRule
import net.onelitefeather.ingot.search.access.RepositoryAccess
import net.onelitefeather.ingot.search.access.SearchAccess
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

/**
 * Golden strings of the Lucene queries of [LuceneQueries] and [LuceneAccess]. Each string is the query as Lucene prints it, so a
 * change to a clause, a field or a flag changes the string and fails the test. A failure means the query changed: when the change is
 * intended, the golden string is updated in the same commit as the code.
 */
internal class LuceneQueriesGoldenTest {

    private val releases = SearchAccess(listOf(RepositoryAccess("releases", PathPredicate.All)))

    private fun filter(query: ArtifactSearchQuery, except: FilterKind? = null): String =
        LuceneQueries.filter(query, releases, except).toString()

    private fun group(query: ArtifactSearchQuery, part: GroupPart): String =
        LuceneQueries.group(query, releases, part).toString()

    // ---- the text of a request ---------------------------------------------------------------------------------------

    @Test
    fun `a single plain word is a group prefix, an artifact prefix, an exact version or a token match`() {
        assertThat(filter(ArtifactSearchQuery(q = "guava")))
            .isEqualTo("""#((group_id_lc:guava* artifact_id_lc:guava* version_lc:guava (#tokens:guava*))~1) #*:* #*:* #((repository:releases)~1)""")
    }

    @Test
    fun `the last word of a multi-word query is a token prefix and the words before it are exact tokens`() {
        assertThat(filter(ArtifactSearchQuery(q = "jackson databind")))
            .isEqualTo("""#((group_id_lc:jackson* artifact_id_lc:jackson* version_lc:jackson (#tokens:jackson))~1) #((group_id_lc:databind* artifact_id_lc:databind* version_lc:databind (#tokens:databind*))~1) #*:* #*:* #((repository:releases)~1)""")
    }

    @Test
    fun `a coordinates token is a group prefix, an artifact prefix and an exact version`() {
        assertThat(filter(ArtifactSearchQuery(q = "com.google:guava:33.0")))
            .isEqualTo("""#(#group_id_lc:com.google* #artifact_id_lc:guava* #version_lc:33.0) #*:* #*:* #((repository:releases)~1)""")
    }

    @Test
    fun `an empty part of a coordinates token leaves that part unconstrained`() {
        assertThat(filter(ArtifactSearchQuery(q = "com.google::")))
            .isEqualTo("""#(#group_id_lc:com.google*) #*:* #*:* #((repository:releases)~1)""")
    }

    @Test
    fun `a plain word with a dot has no token match`() {
        assertThat(filter(ArtifactSearchQuery(q = "guava.jar")))
            .isEqualTo("""#((group_id_lc:guava.jar* artifact_id_lc:guava.jar* version_lc:guava.jar)~1) #*:* #*:* #((repository:releases)~1)""")
    }

    @Test
    fun `a text facet leaves out the text clauses and keeps the access value`() {
        assertThat(filter(ArtifactSearchQuery(q = "guava"), except = FilterKind.TEXT))
            .isEqualTo("""#*:* #*:* #((repository:releases)~1)""")
    }

    @Test
    fun `the main groups query has the group prefix clause of the text and no token match`() {
        assertThat(group(ArtifactSearchQuery(q = "guava"), GroupPart.MAIN))
            .isEqualTo("""#((group_id_lc:guava* artifact_id_lc:guava* version_lc:guava)~1) #*:* #*:* #((repository:releases)~1)""")
    }

    @Test
    fun `the words only groups query keeps the word match and excludes the groups of the main part`() {
        assertThat(group(ArtifactSearchQuery(q = "guava"), GroupPart.WORDS))
            .isEqualTo("""#(#((group_id_lc:guava* artifact_id_lc:guava* version_lc:guava (#tokens:guava*))~1) #*:* #*:* #((repository:releases)~1)) -(#((group_id_lc:guava* artifact_id_lc:guava* version_lc:guava)~1) #*:* #*:*)""")
    }

    @Test
    fun `a query with only coordinates does not read the word match`() {
        assertThat(LuceneQueries.usesWordMatch(ArtifactSearchQuery(q = "com.google:guava")))
            .`as`("word match of a coordinates-only query")
            .isFalse()
    }

    @Test
    fun `a query with a plain word reads the word match`() {
        assertThat(LuceneQueries.usesWordMatch(ArtifactSearchQuery(q = "jackson")))
            .`as`("word match of a plain word")
            .isTrue()
    }

    @Test
    fun `a query of more than five words is rejected`() {
        assertThrows<InvalidSearchQueryException> {
            filter(ArtifactSearchQuery(q = "a b c d e f"))
        }
    }

    // ---- the filters of a request ------------------------------------------------------------------------------------

    @Test
    fun `a groupId filter is a prefix on the lower-cased groupId`() {
        assertThat(filter(ArtifactSearchQuery(groupId = "Com.Google")))
            .isEqualTo("""#group_id_lc:com.google* #*:* #*:* #((repository:releases)~1)""")
    }

    @Test
    fun `an artifactId filter is a prefix on the lower-cased artifactId`() {
        assertThat(filter(ArtifactSearchQuery(artifactId = "Guava")))
            .isEqualTo("""#artifact_id_lc:guava* #*:* #*:* #((repository:releases)~1)""")
    }

    @Test
    fun `an exact version is a term on the lower-cased version`() {
        assertThat(filter(ArtifactSearchQuery(version = "33.0-JRE")))
            .isEqualTo("""#version_lc:33.0-jre #*:* #*:* #((repository:releases)~1)""")
    }

    @Test
    fun `a version prefix is a prefix on the lower-cased version`() {
        assertThat(filter(ArtifactSearchQuery(versionPrefix = "33.")))
            .isEqualTo("""#version_lc:33.* #*:* #*:* #((repository:releases)~1)""")
    }

    @Test
    fun `a version and a version prefix together are rejected`() {
        assertThrows<InvalidSearchQueryException> {
            filter(ArtifactSearchQuery(version = "33.0", versionPrefix = "33."))
        }
    }

    @Test
    fun `repositories are an or over their exact names`() {
        assertThat(filter(ArtifactSearchQuery(repositories = listOf("releases", "snapshots"))))
            .isEqualTo("""#repository:(releases snapshots) #*:* #*:* #((repository:releases)~1)""")
    }

    @Test
    fun `extensions are an or over their lower-cased values`() {
        assertThat(filter(ArtifactSearchQuery(extensions = listOf("JAR", "pom"))))
            .isEqualTo("""#extension:(jar pom) #*:* #*:* #((repository:releases)~1)""")
    }

    @Test
    fun `the classifier dash selects the files without a classifier field`() {
        assertThat(filter(ArtifactSearchQuery(classifiers = listOf("-"))))
            .isEqualTo("""#(#*:* -FieldExistsQuery [field=classifier]) #*:* #*:* #((repository:releases)~1)""")
    }

    @Test
    fun `named classifiers are an exact, case-sensitive or over their values`() {
        assertThat(filter(ArtifactSearchQuery(classifiers = listOf("sources", "javadoc"))))
            .isEqualTo("""#classifier:(javadoc sources) #*:* #*:* #((repository:releases)~1)""")
    }

    @Test
    fun `the dash and named classifiers together are an or of the two`() {
        assertThat(filter(ArtifactSearchQuery(classifiers = listOf("-", "sources"))))
            .isEqualTo("""#(((#*:* -FieldExistsQuery [field=classifier]) classifier:(sources))~1) #*:* #*:* #((repository:releases)~1)""")
    }

    @Test
    fun `a sha1 is a term on the lower-cased checksum`() {
        assertThat(filter(ArtifactSearchQuery(sha1 = "A9993E364706816ABA3E25717850C26C9CD0D89D")))
            .isEqualTo("""#sha1:a9993e364706816aba3e25717850c26c9cd0d89d #*:* #*:* #((repository:releases)~1)""")
    }

    @Test
    fun `a sha1 that is not forty hexadecimal characters is rejected`() {
        assertThrows<InvalidSearchQueryException> {
            filter(ArtifactSearchQuery(sha1 = "not-a-checksum"))
        }
    }

    @Test
    fun `the snapshot filter true is a term on the snapshot field`() {
        assertThat(filter(ArtifactSearchQuery(snapshot = SnapshotFilter.TRUE)))
            .isEqualTo("""#snapshot:true #*:* #*:* #((repository:releases)~1)""")
    }

    @Test
    fun `the snapshot filter false is a term on the snapshot field`() {
        assertThat(filter(ArtifactSearchQuery(snapshot = SnapshotFilter.FALSE)))
            .isEqualTo("""#snapshot:false #*:* #*:* #((repository:releases)~1)""")
    }

    @Test
    fun `the origin filter hosted is a term on the origin field`() {
        assertThat(filter(ArtifactSearchQuery(origin = OriginFilter.HOSTED)))
            .isEqualTo("""#origin:HOSTED #*:* #*:* #((repository:releases)~1)""")
    }

    @Test
    fun `the origin filter mirror is a term on the origin field`() {
        assertThat(filter(ArtifactSearchQuery(origin = OriginFilter.MIRROR)))
            .isEqualTo("""#origin:MIRROR #*:* #*:* #((repository:releases)~1)""")
    }

    @Test
    fun `a modification window is a long range that includes its lower bound and excludes its upper bound`() {
        assertThat(
            filter(
                ArtifactSearchQuery(
                    modifiedAfter = Instant.parse("2026-01-01T00:00:00Z"),
                    modifiedBefore = Instant.parse("2026-02-01T00:00:00Z"),
                ),
            ),
        ).isEqualTo("""#last_modified:[1767225600000 TO 1769903999999] #*:* #((repository:releases)~1)""")
    }

    @Test
    fun `a modification window with only an upper bound has an open lower bound`() {
        assertThat(filter(ArtifactSearchQuery(modifiedBefore = Instant.parse("2026-02-01T00:00:00Z"))))
            .isEqualTo("""#last_modified:[-9223372036854775808 TO 1769903999999] #*:* #((repository:releases)~1)""")
    }

    @Test
    fun `a size window with only a minimum has an open upper bound`() {
        assertThat(filter(ArtifactSearchQuery(sizeMin = 1024L)))
            .isEqualTo("""#*:* #size_bytes:[1024 TO 9223372036854775807] #((repository:releases)~1)""")
    }

    @Test
    fun `a negative size bound is rejected`() {
        assertThrows<InvalidSearchQueryException> {
            filter(ArtifactSearchQuery(sizeMax = -1L))
        }
    }

    @Test
    fun `a facet on the artifact kind leaves out the artifactId clause and keeps the groupId clause`() {
        assertThat(filter(ArtifactSearchQuery(groupId = "com.google", artifactId = "guava"), except = FilterKind.ARTIFACT))
            .isEqualTo("""#group_id_lc:com.google* #*:* #*:* #((repository:releases)~1)""")
    }

    // ---- the window of a group search --------------------------------------------------------------------------------

    @Test
    fun `a group window that ends exactly at the result limit is accepted`() {
        LuceneQueries.checkWindow(page = 99, size = 100)
    }

    @Test
    fun `a group window that ends past the result limit is rejected`() {
        assertThrows<InvalidSearchQueryException> {
            LuceneQueries.checkWindow(page = 100, size = 100)
        }
    }

    // ---- the file search and the presence and latest reads -----------------------------------------------------------

    @Test
    fun `a file search is a term on each coordinate, the origin, the repositories and the access value`() {
        val query = ArtifactFilesQuery(
            groupId = "com.google",
            artifactId = "guava",
            version = "33.0",
            origin = OriginFilter.MIRROR,
            repositories = listOf("releases"),
        )

        assertThat(LuceneQueries.files(query, releases).toString()).isEqualTo("""#group_id_lc:com.google #artifact_id_lc:guava #version_lc:33.0 #origin:MIRROR #repository:(releases) #((repository:releases)~1)""")
    }

    @Test
    fun `a file search across all origins has no origin clause`() {
        val query = ArtifactFilesQuery(groupId = "com.google", artifactId = "guava", version = "33.0")

        assertThat(LuceneQueries.files(query, releases).toString()).isEqualTo("""#group_id_lc:com.google #artifact_id_lc:guava #version_lc:33.0 #((repository:releases)~1)""")
    }

    @Test
    fun `a file search with a page size above two hundred is rejected`() {
        assertThrows<InvalidSearchQueryException> {
            LuceneQueries.files(ArtifactFilesQuery(groupId = "g", artifactId = "a", version = "1", size = 201), releases)
        }
    }

    @Test
    fun `the presence read is an or over the GAV keys with the access value`() {
        assertThat(LuceneQueries.presence(listOf("com.google:guava:33.0"), releases).toString()).isEqualTo("""#gav:(com.google:guava:33.0) #((repository:releases)~1)""")
    }

    @Test
    fun `the latest read is an or over the artifact keys with the access value`() {
        assertThat(LuceneQueries.latest(listOf("com.google:guava"), releases).toString()).isEqualTo("""#artifact_key:(com.google:guava) #((repository:releases)~1)""")
    }

    // ---- the access value -------------------------------------------------------------------------------------------

    @Test
    fun `an access value without repositories matches no document`() {
        assertThat(LuceneAccess.query(SearchAccess.NONE).toString()).isEqualTo("""MatchNoDocsQuery("")""")
    }

    @Test
    fun `an all path access is the repository term alone`() {
        assertThat(LuceneAccess.query(releases).toString()).isEqualTo("""(repository:releases)~1""")
    }

    @Test
    fun `an exact path access is the path itself or the path below it on a segment boundary`() {
        val access = SearchAccess(listOf(RepositoryAccess("releases", PathPredicate.Exact("com/example"))))

        assertThat(LuceneAccess.query(access).toString()).isEqualTo("""((#repository:releases #((path:com/example path:com/example/*)~1)))~1""")
    }

    @Test
    fun `a subtree access without segments matches every path of the repository`() {
        val access = SearchAccess(listOf(RepositoryAccess("releases", PathPredicate.Subtree(emptyList()))))

        assertThat(LuceneAccess.query(access).toString()).isEqualTo("""((#repository:releases #*:*))~1""")
    }

    @Test
    fun `a legacy access is a prefix on the lower-cased path`() {
        val access = SearchAccess(listOf(RepositoryAccess("releases", PathPredicate.Legacy("com/Example"))))

        assertThat(LuceneAccess.query(access).toString()).isEqualTo("""((#repository:releases #path_lc:com/example*))~1""")
    }

    @Test
    fun `a never path access matches no document of its repository`() {
        val access = SearchAccess(listOf(RepositoryAccess("releases", PathPredicate.Never)))

        assertThat(LuceneAccess.query(access).toString()).isEqualTo("""((#repository:releases #MatchNoDocsQuery("")))~1""")
    }

    @Test
    fun `a conjunction access requires every part of its path condition`() {
        val access = SearchAccess(
            listOf(
                RepositoryAccess(
                    "releases",
                    PathPredicate.Conjunction(listOf(PathPredicate.Exact("com/example"), PathPredicate.Legacy("com/example/app"))),
                ),
            ),
        )

        assertThat(LuceneAccess.query(access).toString()).isEqualTo("""((#repository:releases #(#((path:com/example path:com/example/*)~1) #path_lc:com/example/app*)))~1""")
    }

    @Test
    fun `a precedence access shows a path only when no earlier denial covers it`() {
        val precedence = PathPredicate.Precedence(
            listOf(
                PrecedenceRule(PathPredicate.Subtree(listOf("net", "onelitefeather", "mycelium-bom")), allowed = false),
                PrecedenceRule(PathPredicate.Subtree(listOf("net", "onelitefeather")), allowed = true),
            ),
        )
        val access = SearchAccess(listOf(RepositoryAccess("private", precedence)))

        assertThat(LuceneAccess.query(access).toString()).isEqualTo("""((#repository:private #(((#((path:net/onelitefeather path:net/onelitefeather/*)~1) -((path:net/onelitefeather/mycelium-bom path:net/onelitefeather/mycelium-bom/*)~1)))~1)))~1""")
    }

    @Test
    fun `two repository accesses are an or of their own clauses`() {
        val access = SearchAccess(
            listOf(
                RepositoryAccess("releases", PathPredicate.All),
                RepositoryAccess("snapshots", PathPredicate.Exact("com/example")),
            ),
        )

        assertThat(LuceneAccess.query(access).toString()).isEqualTo("""(repository:releases (#repository:snapshots #((path:com/example path:com/example/*)~1)))~1""")
    }

}
