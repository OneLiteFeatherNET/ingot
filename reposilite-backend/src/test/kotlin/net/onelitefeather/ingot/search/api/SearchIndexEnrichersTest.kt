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

package net.onelitefeather.ingot.search.api

import com.fasterxml.jackson.databind.JsonNode
import com.reposilite.ReposiliteObjectMapper
import net.onelitefeather.ingot.search.IndexState
import net.onelitefeather.ingot.search.backend.SearchBackendState
import net.onelitefeather.ingot.search.enrichment.EnrichmentTally
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The additive `enrichers` block of the index status response (artifact enrichment, D7 and 6.1). */
internal class SearchIndexEnrichersTest {

    private fun response(enrichers: Map<String, EnricherStatusResponse>) = SearchIndexResponse(
        state = IndexState.MISSING,
        lagSeconds = 0,
        lastFullRebuild = null,
        rows = 0,
        outbox = OutboxCounts(pending = 0, deadLetters = 0, droppedEnqueues = 0),
        repositories = listOf("releases"),
        engine = "database",
        activeRun = null,
        runs = emptyList(),
        lastVerify = null,
        backend = SearchBackendResponse("database", SearchBackendState.AVAILABLE, "", "UP", "", 0),
        enrichers = enrichers,
    )

    @Test
    fun `the block is empty when no enricher is registered`() {
        assertThat(enricherStatusOf(emptyMap())).`as`("enrichers block without a registered enricher").isEmpty()
    }

    @Test
    fun `the block carries the counters of every enricher by id`() {
        val counts = mapOf(
            "zip-id" to EnrichmentTally(completed = 0, skipped = 0, failed = 0),
            "app-id" to EnrichmentTally(completed = 1, skipped = 2, failed = 2, busy = 1, failures = mapOf("timeout" to 2L)),
        )

        val block = enricherStatusOf(counts)

        assertThat(block.keys).`as`("enricher ids, sorted").containsExactly("app-id", "zip-id")
        assertThat(block.getValue("app-id")).`as`("counters of app-id").isEqualTo(
            EnricherStatusResponse(calls = 5, completed = 1, skipped = 2, failed = 2, busy = 1, failures = mapOf("timeout" to 2L)),
        )
        assertThat(block.getValue("zip-id").calls).`as`("calls of an enricher not yet called").isEqualTo(0L)
    }

    @Test
    fun `the response keeps every existing field and adds an empty enrichers object`() {
        val json = ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER.valueToTree<JsonNode>(response(emptyMap()))

        assertThat(json.fieldNames().asSequence().toList()).`as`("fields of the index status response").containsExactlyInAnyOrder(
            "state", "lagSeconds", "rows", "outbox", "repositories", "engine", "runs", "backend", "enrichers",
        )
        assertThat(json.get("enrichers").isObject && json.get("enrichers").isEmpty).`as`("enrichers block is an empty object").isTrue()
    }

    @Test
    fun `the response names the counters of an enricher as the contract does`() {
        val block = mapOf("app-id" to EnricherStatusResponse(3, 1, 0, 2, 0, mapOf("timeout" to 2L)))

        val json = ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER.valueToTree<JsonNode>(response(block))

        val entry = json.get("enrichers").get("app-id")
        assertThat(entry.fieldNames().asSequence().toList()).`as`("counter names of an enricher")
            .containsExactlyInAnyOrder("calls", "completed", "skipped", "failed", "busy", "failures")
        assertThat(entry.get("failures").get("timeout").asLong()).`as`("failures with the reason timeout").isEqualTo(2L)
    }

}
