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

package net.onelitefeather.ingot.elasticsearch

import com.reposilite.ReposiliteObjectMapper
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.backend.IndexAction
import net.onelitefeather.ingot.search.database.pathHash
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

/** The bulk body builder: the size limits, the order of the actions and the document ids. */
internal class ElasticsearchBulkPlanTest {

    private val index = "ingot-artifacts"
    private val notAfter: Instant = Instant.parse("2026-01-01T00:00:00Z")

    private fun upsert(path: String) = IndexAction.Upsert(testRow(path = path))

    private fun bulkLines(body: String): List<String> = body.trimEnd('\n').split('\n')

    /** The document ids of the action lines, in order. An index action is followed by its document line, a delete is not. */
    private fun actionIds(body: String): List<String> {
        val lines = bulkLines(body).iterator()
        val ids = mutableListOf<String>()
        while (lines.hasNext()) {
            val action = ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER.readTree(lines.next())
            ids.add(action.elements().next().path("_id").asText())
            if (action.has("index")) lines.next()
        }
        return ids
    }

    @Test
    fun `should split bulk requests at the action limit`() {
        // given: five upserts and a limit of two actions per request
        val actions = (1..5).map { upsert("org/example/app/1.0/app-1.0-$it.jar") }

        // when: the body is planned
        val steps = ElasticsearchBulk.plan(index, actions, bulkSize = 2)

        // then: the requests carry two, two and one action
        val bulks = steps.filterIsInstance<WriteStep.Bulk>()
        assertThat(bulks.map { it.actions }).`as`("actions per bulk request").containsExactly(2, 2, 1)
    }

    @Test
    fun `should split bulk requests before the byte limit is exceeded`() {
        // given: four upserts and a byte limit that fits about two of them
        val actions = (1..4).map { upsert("org/example/app/1.0/app-1.0-$it.jar") }
        val limit = 2_000

        // when: the body is planned
        val bulks = ElasticsearchBulk.plan(index, actions, bulkSize = 500, maxBytes = limit).filterIsInstance<WriteStep.Bulk>()

        // then: no request is over the limit and the batch is split
        assertThat(bulks.size).`as`("bulk requests").isGreaterThan(1)
        assertThat(bulks.map { it.body.toByteArray(Charsets.UTF_8).size }).`as`("bytes per bulk request").allMatch { it <= limit }
    }

    @Test
    fun `should keep five megabytes as the byte limit of one request`() {
        // then: the limit is the one of the design (D4)
        assertThat(ElasticsearchBulk.MAX_BYTES).`as`("bytes per bulk request").isEqualTo(5 * 1024 * 1024)
    }

    @Test
    fun `should keep the order of the actions within one bulk request`() {
        // given: an upsert, a removal and an upsert, which all fit one request
        val first = upsert("org/example/app/1.0/app-1.0.jar")
        val removal = IndexAction.RemovePath("releases", "org/example/app/2.0/app-2.0.jar")
        val last = upsert("org/example/app/3.0/app-3.0.jar")

        // when: the body is planned
        val bulk = ElasticsearchBulk.plan(index, listOf(first, removal, last), bulkSize = 500).single() as WriteStep.Bulk

        // then: the documents are addressed in the order of the actions
        assertThat(actionIds(bulk.body)).`as`("document ids in request order").containsExactly(
            pathHash("releases", "org/example/app/1.0/app-1.0.jar"),
            pathHash("releases", "org/example/app/2.0/app-2.0.jar"),
            pathHash("releases", "org/example/app/3.0/app-3.0.jar"),
        )
    }

    @Test
    fun `should keep the order of the actions around a prefix removal`() {
        // given: an upsert, a prefix removal and an upsert
        val actions = listOf(
            upsert("org/example/app/1.0/app-1.0.jar"),
            IndexAction.RemovePrefix("releases", "org/example/old", notAfter),
            upsert("org/example/app/2.0/app-2.0.jar"),
        )

        // when: the body is planned
        val steps = ElasticsearchBulk.plan(index, actions, bulkSize = 500)

        // then: the bulk request before the removal, the removal and the bulk request after it keep their order
        assertThat(steps.map { it::class.simpleName }).`as`("steps in order").containsExactly("Bulk", "Single", "Bulk")
        assertThat(steps[1]).`as`("the removal step").isEqualTo(WriteStep.Single(actions[1]))
    }

    @Test
    fun `should address each upserted document by the database path hash`() {
        // when: one upsert is planned
        val bulk = ElasticsearchBulk.plan(index, listOf(upsert("org/example/app/1.0/app-1.0.jar")), bulkSize = 500).single() as WriteStep.Bulk

        // then: the document id is the path hash of the database
        assertThat(actionIds(bulk.body)).`as`("document id").containsExactly(
            pathHash("releases", "org/example/app/1.0/app-1.0.jar"),
        )
    }

    @Test
    fun `should address a removed path by the same document id as its upsert`() {
        // when: one removal is planned
        val path = "org/example/app/1.0/app-1.0.jar"
        val bulk = ElasticsearchBulk.plan(index, listOf(IndexAction.RemovePath("releases", path)), bulkSize = 500).single() as WriteStep.Bulk

        // then: the delete line names the id that the upsert of the same file writes
        assertThat(actionIds(bulk.body)).`as`("document id").containsExactly(pathHash("releases", path))
    }

    @Test
    fun `should write a removal as one action line without a document line`() {
        // when: one removal is planned
        val bulk = ElasticsearchBulk.plan(index, listOf(IndexAction.RemovePath("releases", "a/b.jar")), bulkSize = 500).single() as WriteStep.Bulk

        // then: the body holds the delete action only
        assertThat(bulkLines(bulk.body)).`as`("lines of the body").hasSize(1)
    }

}
