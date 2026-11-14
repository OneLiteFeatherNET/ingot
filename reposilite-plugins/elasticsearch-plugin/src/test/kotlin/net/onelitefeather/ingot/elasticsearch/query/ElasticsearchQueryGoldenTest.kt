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
package net.onelitefeather.ingot.elasticsearch.query

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import net.onelitefeather.ingot.search.ArtifactSearchQuery
import net.onelitefeather.ingot.search.OriginFilter
import net.onelitefeather.ingot.search.SnapshotFilter
import net.onelitefeather.ingot.search.access.PathPredicate
import net.onelitefeather.ingot.search.access.RepositoryAccess
import net.onelitefeather.ingot.search.access.SearchAccess
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * One golden JSON file per query shape (design D5). Each test builds the body from fixed inputs and compares its tree with
 * the file under `golden/elasticsearch`. The bodies are compared as trees, so key order and whitespace do not matter, but
 * array order does.
 */
internal class ElasticsearchQueryGoldenTest {

    private val mapper = ObjectMapper()

    private val access = SearchAccess(listOf(RepositoryAccess("releases", PathPredicate.All)))

    @Test
    fun `should translate free text into one clause per word with the token match`() {
        assertGolden("word", ElasticsearchQueries.queryFilter(ArtifactSearchQuery(q = "mycelium bom"), access))
    }

    @Test
    fun `should translate coordinates into one filter of the prefixes and the exact version`() {
        assertGolden("coordinates", ElasticsearchQueries.queryFilter(ArtifactSearchQuery(q = "net.onelitefeather:mycelium:2.0"), access))
    }

    @Test
    fun `should match the lower-cased group as a prefix`() {
        assertGolden("filter-group", ElasticsearchQueries.queryFilter(ArtifactSearchQuery(groupId = "Net.OneLiteFeather"), access))
    }

    @Test
    fun `should match the lower-cased artifact as a prefix`() {
        assertGolden("filter-artifact", ElasticsearchQueries.queryFilter(ArtifactSearchQuery(artifactId = "Mycelium"), access))
    }

    @Test
    fun `should match the lower-cased version exactly`() {
        assertGolden("filter-version", ElasticsearchQueries.queryFilter(ArtifactSearchQuery(version = "1.0-RC1"), access))
    }

    @Test
    fun `should match the lower-cased version as a prefix`() {
        assertGolden("filter-version-prefix", ElasticsearchQueries.queryFilter(ArtifactSearchQuery(versionPrefix = "1.0"), access))
    }

    @Test
    fun `should match any of the repositories`() {
        assertGolden(
            "filter-repositories",
            ElasticsearchQueries.queryFilter(ArtifactSearchQuery(repositories = listOf("releases", "private")), access),
        )
    }

    @Test
    fun `should match any of the extensions in lower case`() {
        assertGolden("filter-extensions", ElasticsearchQueries.queryFilter(ArtifactSearchQuery(extensions = listOf("JAR", "pom")), access))
    }

    @Test
    fun `should match files without a classifier for the dash and the named classifiers together`() {
        assertGolden(
            "filter-classifiers",
            ElasticsearchQueries.queryFilter(ArtifactSearchQuery(classifiers = listOf("-", "sources")), access),
        )
    }

    @Test
    fun `should match the checksum in lower case`() {
        assertGolden(
            "filter-sha1",
            ElasticsearchQueries.queryFilter(ArtifactSearchQuery(sha1 = "ABCDEF0123456789ABCDEF0123456789ABCDEF01"), access),
        )
    }

    @Test
    fun `should match snapshots as a boolean`() {
        assertGolden("filter-snapshot", ElasticsearchQueries.queryFilter(ArtifactSearchQuery(snapshot = SnapshotFilter.TRUE), access))
    }

    @Test
    fun `should match the origin by its stored name`() {
        assertGolden("filter-origin", ElasticsearchQueries.queryFilter(ArtifactSearchQuery(origin = OriginFilter.MIRROR), access))
    }

    @Test
    fun `should bound the last modification with a lower bound inclusive and an upper bound exclusive`() {
        val query = ArtifactSearchQuery(
            modifiedAfter = Instant.parse("2026-01-01T00:00:00Z"),
            modifiedBefore = Instant.parse("2026-02-01T00:00:00Z"),
        )
        assertGolden("filter-modified", ElasticsearchQueries.queryFilter(query, access))
    }

    @Test
    fun `should bound the size in bytes with both bounds inclusive`() {
        assertGolden("filter-size", ElasticsearchQueries.queryFilter(ArtifactSearchQuery(sizeMin = 1024, sizeMax = 4096), access))
    }

    @Test
    fun `should leave the repository filter out of the repository facet`() {
        val query = ArtifactSearchQuery(repositories = listOf("releases"), extensions = listOf("jar"), origin = OriginFilter.HOSTED)
        assertGolden("facet-repository", ElasticsearchQueries.facet(FilterKind.REPOSITORY, query, access))
    }

    @Test
    fun `should count files without a classifier in the dash bucket of the classifier facet`() {
        assertGolden(
            "facet-classifier",
            ElasticsearchQueries.facet(FilterKind.CLASSIFIER, ArtifactSearchQuery(extensions = listOf("jar")), access),
        )
    }

    private fun assertGolden(name: String, body: JsonNode) {
        val expected = mapper.readTree(resource("$name.json"))
        // Round trip, so the numbers have the same node types as the parsed golden file.
        val actual = mapper.readTree(mapper.writeValueAsString(body))
        assertThat(actual)
            .describedAs("the body differs from golden/elasticsearch/$name.json; actual body: $actual")
            .isEqualTo(expected)
    }

    private fun resource(file: String): String {
        val url = requireNotNull(javaClass.classLoader.getResource("golden/elasticsearch/$file")) { "golden file $file is missing" }
        return url.readText()
    }

}
