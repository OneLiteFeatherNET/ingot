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

package net.onelitefeather.ingot.opensearch

import com.fasterxml.jackson.databind.JsonNode
import net.onelitefeather.ingot.search.Origin
import net.onelitefeather.ingot.search.Provenance
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.backend.IndexAction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

/** The bodies of the prefix removal and the reorigin, which run as query requests and not as bulk actions. */
internal class OpenSearchRequestsTest {

    private val notAfter: Instant = Instant.parse("2026-01-01T00:00:00Z")

    private fun removal(prefix: String = "org/example/old") =
        OpenSearchRequests.deleteByPrefix(IndexAction.RemovePrefix("releases", prefix, notAfter))

    private fun filters(body: JsonNode): List<JsonNode> = body.at("/query/bool/filter").toList()

    private fun JsonNode.shouldClauses(): List<JsonNode> = path("bool").path("should").toList()

    @Test
    fun `should remove the prefix path itself and the paths below it`() {
        // when: the removal of a prefix is built
        val body = removal("org/example/old")

        // then: the path condition holds the prefix itself or the prefix followed by a slash
        val pathClause = filters(body).single { it.has("bool") }
        assertThat(pathClause.shouldClauses().map { it.toString() })
            .`as`("path conditions")
            .containsExactlyInAnyOrder(
                """{"term":{"path":"org/example/old"}}""",
                """{"prefix":{"path":"org/example/old/"}}""",
            )
    }

    @Test
    fun `should restrict the removal to the repository`() {
        // when: the removal of a prefix is built
        val body = removal()

        // then: the repository is a filter of the query
        assertThat(filters(body).map { it.toString() })
            .`as`("filters")
            .contains("""{"term":{"repository":"releases"}}""")
    }

    @Test
    fun `should remove only rows indexed at or before the cut-off`() {
        // when: the removal of a prefix is built
        val body = removal()

        // then: rows indexed later survive, as in the database engine
        val indexedAt = filters(body).single { it.has("range") && it.path("range").has("indexed_at") }
        assertThat(indexedAt.at("/range/indexed_at/lte").asLong())
            .`as`("cut-off in epoch milliseconds")
            .isEqualTo(notAfter.toEpochMilli())
    }

    @Test
    fun `should reorigin the directory with the stored script by its id`() {
        // when: a reorigin is built
        val body = OpenSearchRequests.reorigin(IndexAction.Reorigin("releases", "org/example", Provenance(Origin.MIRROR, "mirror.example.test")))

        // then: the script is the stored one, and the query covers the subtree of the directory
        assertThat(body.at("/script/id").asText()).`as`("stored script id").isEqualTo(OpenSearchRequests.REORIGIN_SCRIPT_ID)
        val subtree = filters(body).single { it.has("range") }
        assertThat(subtree.at("/range/path/gte").asText()).`as`("lower bound").isEqualTo("org/example/")
        assertThat(subtree.at("/range/path/lt").asText()).`as`("upper bound").isEqualTo("org/example0")
    }

    @Test
    fun `should pass only the origin and the source host as script parameters`() {
        // when: a reorigin is built
        val body = OpenSearchRequests.reorigin(IndexAction.Reorigin("releases", "org/example", Provenance(Origin.MIRROR, "mirror.example.test")))

        // then: the parameters are exactly the origin and the source host
        val parameters = body.at("/script/params").fieldNames().asSequence().toSet()
        assertThat(parameters).`as`("script parameter names").containsExactlyInAnyOrder("origin", "source_host")
        assertThat(body.at("/script/params/origin").asText()).`as`("origin").isEqualTo("MIRROR")
        assertThat(body.at("/script/params/source_host").asText()).`as`("source host").isEqualTo("mirror.example.test")
    }

    @Test
    fun `should send no script source so that no request value enters the script text`() {
        // when: a reorigin is built
        val body = OpenSearchRequests.reorigin(IndexAction.Reorigin("releases", "org/example", Provenance.HOSTED))

        // then: the body names the stored script and carries no source text
        assertThat(body.at("/script").has("source")).`as`("inline script source present").isFalse()
        assertThat(OpenSearchRequests.REORIGIN_SCRIPT_SOURCE).`as`("stored script source").doesNotContain("org/example")
    }

}
