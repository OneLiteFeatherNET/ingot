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

package net.onelitefeather.ingot.search.api

import com.fasterxml.jackson.databind.JsonNode
import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteSpecification
import kong.unirest.core.Unirest.get
import kong.unirest.core.Unirest.post
import net.onelitefeather.ingot.search.application.SearchFacade
import net.onelitefeather.ingot.search.application.SearchIndexAdmin
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * The search REST API over HTTP (artifact search spec, 6.1 to 6.3). The dashboard reads these shapes, so each test pins
 * one field or one status code of the contract. The index runs are driven by hand: the dispatcher is replaced, so a
 * started run stays live until the test ends it, and nothing waits for the IO pool.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class SearchApiIntegrationTest : ReposiliteSpecification() {

    private val search: SearchFacade
        get() = useFacade()

    private val admin: SearchIndexAdmin
        get() = checkNotNull(search.admin) { "search is enabled in this test" }

    private val manager: Pair<String, String>
        get() = useDefaultManagementToken()

    /** Jackson leaves out null fields (the project mapper is NON_NULL); the dashboard reads an absent field as null. */
    private fun JsonNode.isAbsentOrNull(name: String): Boolean = get(name)?.isNull ?: true

    private fun index(path: String = ""): String = "$base/api/maven/search/index$path"

    private fun startRebuild(body: String = "{}") =
        post(index("/rebuild")).basicAuth(manager.first, manager.second).header("Content-Type", "application/json").body(body).asJacksonObject(JsonNode::class)

    @Test
    fun `finds a deployed artifact once the outbox has been drained`() {
        // given: an artifact deployed to a hosted repository and the outbox drained by one tick
        useDocument("releases", "com/example/app/1.0.0", "app-1.0.0.jar", store = true)
        admin.drainOutbox()

        // when: the artifact is searched by name, anonymously
        val response = get("$base/api/maven/search?artifactId=app").asJacksonObject(JsonNode::class)

        // then: the group is returned with its repository, its origin and the index status
        assertThat(response.status).isEqualTo(200)
        val item = response.body.get("items").single()
        assertThat(item.get("groupId").asText()).isEqualTo("com.example")
        assertThat(item.get("artifactId").asText()).isEqualTo("app")
        assertThat(item.get("version").asText()).isEqualTo("1.0.0")
        assertThat(item.get("repositories").single().get("name").asText()).isEqualTo("releases")
        assertThat(item.get("repositories").single().get("origin").asText()).isEqualTo("HOSTED")
        assertThat(response.body.get("indexStatus").get("state").asText()).isEqualTo("BUILDING")
    }

    @Test
    fun `answers the facets of the search with the buckets the dashboard reads`() {
        useDocument("releases", "com/example/app/1.0.0", "app-1.0.0.jar", store = true)
        admin.drainOutbox()

        val response = get("$base/api/maven/search?artifactId=app").asJacksonObject(JsonNode::class)

        val repository = response.body.get("facets").get("repository").single()
        assertThat(repository.get("value").asText()).isEqualTo("releases")
        assertThat(repository.get("count").asLong()).isEqualTo(1)
        assertThat(response.body.get("facets").get("classifier").single().get("value").asText()).isEqualTo("-")
    }

    @Test
    fun `lists the files of one version with their browse links`() {
        useDocument("releases", "com/example/app/1.0.0", "app-1.0.0.jar", store = true)
        admin.drainOutbox()

        val response = get("$base/api/maven/search/files?groupId=com.example&artifactId=app&version=1.0.0").asJacksonObject(JsonNode::class)

        assertThat(response.status).isEqualTo(200)
        val file = response.body.get("items").single()
        assertThat(file.get("name").asText()).isEqualTo("app-1.0.0.jar")
        assertThat(file.get("repository").asText()).isEqualTo("releases")
        assertThat(file.get("origin").asText()).isEqualTo("HOSTED")
        assertThat(file.get("browseLink").asText()).isEqualTo("/browse/releases/com/example/app/1.0.0/app-1.0.0.jar")
    }

    @Test
    fun `answers 400 naming the parameter when a number does not parse`() {
        val response = get("$base/api/maven/search?page=first").asJacksonObject(JsonNode::class)

        assertThat(response.status).isEqualTo(400)
        assertThat(response.body.get("message").asText()).contains("page")
    }

    @Test
    fun `answers 400 naming the coordinate that the files request lacks`() {
        val response = get("$base/api/maven/search/files?groupId=com.example&artifactId=app").asJacksonObject(JsonNode::class)

        assertThat(response.status).isEqualTo(400)
        assertThat(response.body.get("message").asText()).contains("version")
    }

    @Test
    fun `shows the index status to a manager with the contract fields`() {
        val response = get(index()).basicAuth(manager.first, manager.second).asJacksonObject(JsonNode::class)

        assertThat(response.status).isEqualTo(200)
        val body = response.body
        assertThat(body.get("state").asText()).isEqualTo("MISSING")
        assertThat(body.get("rows").asLong()).isEqualTo(0)
        assertThat(body.get("outbox").get("pending").asLong()).isEqualTo(0)
        assertThat(body.get("outbox").get("deadLetters").asLong()).isEqualTo(0)
        assertThat(body.get("engine").asText()).isEqualTo("database")
        assertThat(body.get("repositories").map { it.asText() }).contains("releases", "snapshots")
        assertThat(body.isAbsentOrNull("activeRun")).isTrue
        assertThat(body.get("runs").isEmpty).isTrue
        assertThat(body.isAbsentOrNull("lastVerify")).isTrue
    }

    @Test
    fun `refuses the index status to a token without manager rights`() {
        assertManagerOnlyGetEndpoint("/api/maven/search/index")
    }

    @Test
    fun `starts a rebuild with 202 and lists the live run as active`() {
        admin.dispatch = { }

        val response = startRebuild()

        assertThat(response.status).isEqualTo(202)
        val runId = response.body.get("runId").asLong()
        val status = get(index()).basicAuth(manager.first, manager.second).asJacksonObject(JsonNode::class).body
        assertThat(status.get("activeRun").get("id").asLong()).isEqualTo(runId)
        assertThat(status.get("activeRun").get("status").asText()).isEqualTo("RUNNING")
        assertThat(status.get("activeRun").get("kind").asText()).isEqualTo("REBUILD")
        assertThat(status.get("activeRun").isAbsentOrNull("repositories")).describedAs("a rebuild of all repositories").isTrue
    }

    @Test
    fun `starts a rebuild of the named repositories only`() {
        admin.dispatch = { }

        startRebuild("""{"repositories":["releases"]}""")

        val status = get(index()).basicAuth(manager.first, manager.second).asJacksonObject(JsonNode::class).body
        assertThat(status.get("activeRun").get("repositories").map { it.asText() }).containsExactly("releases")
    }

    @Test
    fun `answers 400 naming an unknown repository of a rebuild`() {
        admin.dispatch = { }

        val response = startRebuild("""{"repositories":["nowhere"]}""")

        assertThat(response.status).isEqualTo(400)
        assertThat(response.body.get("message").asText()).contains("nowhere")
    }

    @Test
    fun `answers 409 to a second rebuild while a run is live`() {
        admin.dispatch = { }
        startRebuild()

        val second = startRebuild()

        assertThat(second.status).isEqualTo(409)
    }

    @Test
    fun `answers 409 to a verify run while a rebuild is live`() {
        admin.dispatch = { }
        startRebuild()

        val verify = post(index("/verify")).basicAuth(manager.first, manager.second).asJacksonObject(JsonNode::class)

        assertThat(verify.status).isEqualTo(409)
    }

    @Test
    fun `starts a verify run with 202 when no run is live`() {
        admin.dispatch = { }

        val verify = post(index("/verify")).basicAuth(manager.first, manager.second).asJacksonObject(JsonNode::class)

        assertThat(verify.status).isEqualTo(202)
        assertThat(verify.body.get("runId").asLong()).isPositive
    }

    @Test
    fun `cancels a live run with 204 and records it as cancelled`() {
        admin.dispatch = { }
        val runId = startRebuild().body.get("runId").asLong()

        val cancel = post(index("/runs/$runId/cancel")).basicAuth(manager.first, manager.second).asString()

        assertThat(cancel.status).isEqualTo(204)
        val runs = get(index()).basicAuth(manager.first, manager.second).asJacksonObject(JsonNode::class).body.get("runs")
        assertThat(runs.single { it.get("id").asLong() == runId }.get("status").asText()).isEqualTo("CANCELLED")
    }

    @Test
    fun `answers 409 when a finished run is cancelled`() {
        admin.dispatch = { }
        val runId = startRebuild().body.get("runId").asLong()
        post(index("/runs/$runId/cancel")).basicAuth(manager.first, manager.second).asString()

        val again = post(index("/runs/$runId/cancel")).basicAuth(manager.first, manager.second).asString()

        assertThat(again.status).isEqualTo(409)
    }

    @Test
    fun `answers 404 when an unknown run is cancelled`() {
        val response = post(index("/runs/987654/cancel")).basicAuth(manager.first, manager.second).asString()

        assertThat(response.status).isEqualTo(404)
    }

    @Test
    fun `records a rebuild that completes as the last full rebuild`() {
        admin.dispatch = { job -> job() }

        startRebuild()

        val status = get(index()).basicAuth(manager.first, manager.second).asJacksonObject(JsonNode::class).body
        assertThat(status.isAbsentOrNull("activeRun")).isTrue
        assertThat(status.get("runs").single().get("status").asText()).isEqualTo("COMPLETED")
        assertThat(status.get("lastFullRebuild")).describedAs("the completed rebuild").isNotNull
    }

    @Test
    fun `refuses a rebuild to a token without manager rights`() {
        val reader = useAuth("reader-token", "reader-secret")

        val response = post(index("/rebuild")).basicAuth(reader.first, reader.second).header("Content-Type", "application/json").body("{}").asString()

        assertThat(response.status).isEqualTo(403)
    }
}
