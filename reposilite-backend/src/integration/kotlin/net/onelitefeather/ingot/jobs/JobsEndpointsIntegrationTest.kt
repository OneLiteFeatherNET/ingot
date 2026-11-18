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

@file:Suppress("FunctionName")

package net.onelitefeather.ingot.jobs

import com.fasterxml.jackson.databind.JsonNode
import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteSpecification
import kong.unirest.core.Unirest
import net.onelitefeather.ingot.search.application.SearchFacade
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * The Jobs endpoints over HTTP (background jobs spec, paging, filters and detail). The index runs are started and cancelled by
 * hand, because the dispatcher is replaced in this test; the cleanup run is made by a manual run of a policy.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class JobsEndpointsIntegrationTest : ReposiliteSpecification() {

    private val keepNewestOnly = """
        {"policies":[{"id":"keep-newest","enabled":false,"repositories":["releases"],"scope":"RELEASES","keepLatest":1}]}
    """.trimIndent()

    private fun jobs(query: String = ""): JsonNode {
        val (token, secret) = useDefaultManagementToken()
        val response = Unirest.get("$base/api/jobs$query").basicAuth(token, secret).asJacksonObject(JsonNode::class)
        assertThat(response.status).describedAs("GET /api/jobs$query").isEqualTo(200)
        return response.body
    }

    /**
     * Starts one index rebuild with the dispatcher replaced, so that it stays live, and cancels it on the search endpoint. The
     * run is then listed as cancelled on the Jobs page.
     */
    private fun startCancelledRebuild() {
        val (token, secret) = useDefaultManagementToken()
        checkNotNull(useFacade<SearchFacade>().admin).dispatch = { }
        val start = Unirest.post("$base/api/maven/search/index/rebuild")
            .basicAuth(token, secret)
            .header("Content-Type", "application/json")
            .body("{}")
            .asJacksonObject(JsonNode::class)
        assertThat(start.status).describedAs("start of a rebuild").isEqualTo(202)
        val runId = start.body.get("runId").asLong()
        val cancel = Unirest.post("$base/api/maven/search/index/runs/$runId/cancel").basicAuth(token, secret).asEmpty()
        assertThat(cancel.status).describedAs("cancel of the rebuild").isEqualTo(204)
    }

    /** Configures a policy and runs it once by hand, which records one cleanup run. */
    private fun runCleanupPolicy() {
        val (token, secret) = useDefaultManagementToken()
        Unirest.put("$base/api/settings/domain/cleanup").basicAuth(token, secret).body(keepNewestOnly).asEmpty()
        Unirest.post("$base/api/cleanup/policies/keep-newest/run").basicAuth(token, secret).asEmpty()
    }

    @Test
    fun `lists the index and cleanup runs in one page with the newest cleanup run first`() {
        // given: two cancelled index rebuilds and one cleanup run, made in that order
        startCancelledRebuild()
        startCancelledRebuild()
        runCleanupPolicy()

        // when: the first page of the Jobs page is read
        val items = jobs().get("items").toList()

        // then: all three runs are listed, and the cleanup run comes first (it started last)
        assertThat(items).describedAs("every run of both sources").hasSize(3)
        assertThat(items.first().get("source").asText()).describedAs("newest run first").isEqualTo("cleanup")
    }

    @Test
    fun `pages through every run with the cursor and repeats none`() {
        // given: three runs of two sources
        startCancelledRebuild()
        startCancelledRebuild()
        runCleanupPolicy()

        // when: the list is read one run per page until no cursor is left
        val seen = mutableListOf<String>()
        var cursor: String? = null
        do {
            val page = jobs("?size=1" + (cursor?.let { "&cursor=$it" } ?: ""))
            seen += page.get("items").map { it.get("id").asText() }
            cursor = page.get("nextCursor")?.takeUnless { it.isNull }?.asText()
        } while (cursor != null && seen.size < 10)

        // then: every run is seen exactly once
        assertThat(seen).describedAs("every run once, with no repeat").hasSize(3).doesNotHaveDuplicates()
    }

    @Test
    fun `filters the list to the index runs with kind index`() {
        // given: an index rebuild and a cleanup run
        startCancelledRebuild()
        runCleanupPolicy()

        // when: the list is filtered by the source index
        val items = jobs("?kind=index").get("items").toList()

        // then: only the index run is listed
        assertThat(items.map { it.get("source").asText() }).describedAs("sources of the filtered page").containsOnly("index")
        assertThat(items).hasSize(1)
    }

    @Test
    fun `filters by state FAILED and lists no cancelled run`() {
        // given: a cancelled index rebuild and a cleanup run, neither of them failed
        startCancelledRebuild()
        runCleanupPolicy()

        // when: the list is filtered by the state FAILED and by CANCELLED
        val failed = jobs("?state=FAILED").get("items").toList()
        val cancelled = jobs("?state=CANCELLED").get("items").toList()

        // then: the failed filter is empty, and the cancelled filter holds the rebuild only
        assertThat(failed).describedAs("no run of this instance failed").isEmpty()
        assertThat(cancelled.map { it.get("source").asText() }).describedAs("cancelled runs").containsExactly("index")
    }

    @Test
    fun `answers the detail of an index run by its encoded id`() {
        // given: one cancelled index rebuild
        startCancelledRebuild()
        val id = jobs("?kind=index").get("items").single().get("id").asText()
        val (token, secret) = useDefaultManagementToken()

        // when: the run is read by its id, with the colon encoded
        val detail = Unirest.get("$base/api/jobs/${URLEncoder.encode(id, StandardCharsets.UTF_8)}")
            .basicAuth(token, secret)
            .asJacksonObject(JsonNode::class)

        // then: the detail names the kind and the normalised state
        assertThat(detail.status).isEqualTo(200)
        assertThat(detail.body.get("kind").asText()).isEqualTo("index.rebuild")
        assertThat(detail.body.get("state").asText()).isEqualTo("CANCELLED")
    }

    @Test
    fun `answers 404 for a run id that does not exist`() {
        val (token, secret) = useDefaultManagementToken()

        val response = Unirest.get("$base/api/jobs/index%3A999999").basicAuth(token, secret).asEmpty()

        assertThat(response.status).describedAs("unknown run").isEqualTo(404)
    }

}
