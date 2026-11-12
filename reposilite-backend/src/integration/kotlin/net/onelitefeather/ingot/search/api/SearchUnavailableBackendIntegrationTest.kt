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

import com.fasterxml.jackson.databind.ObjectMapper
import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteSpecification
import com.reposilite.configuration.local.LocalConfiguration
import kong.unirest.core.Unirest.get
import kong.unirest.core.Unirest.post
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import panda.std.reactive.ReferenceUtils

/**
 * A `searchEngine` that no factory answers for: the server starts, search and the index administration answer 503 with a
 * problem body and `Retry-After: 30`, and the index status answers 200 with the `backend` block (search backend
 * registration, D4 and D8). The detail never shows the user information of the configured value.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class SearchUnavailableBackendIntegrationTest : ReposiliteSpecification() {

    private val mapper = ObjectMapper()

    override fun overrideLocalConfiguration(localConfiguration: LocalConfiguration) {
        ReferenceUtils.setValue(localConfiguration.searchEngine, UNKNOWN_ENGINE)
    }

    private val manager: Pair<String, String>
        get() = useDefaultManagementToken()

    @Test
    fun `answers 503 with a problem body and retry after thirty seconds to the search`() {
        val response = get("$base/api/maven/search").header("Accept", "application/problem+json").asString()

        assertThat(response.status).`as`("status of the search").isEqualTo(503)
        assertThat(response.headers.getFirst("Retry-After")).`as`("Retry-After of the search").isEqualTo("30")
        assertThat(response.headers.getFirst("Content-Type")).`as`("content type of the search").startsWith("application/problem+json")
        val body = mapper.readTree(response.body)
        assertThat(body["title"].asText()).`as`("title of the problem").isEqualTo("Search backend not available")
        assertThat(body["detail"].asText()).`as`("detail of the problem").contains("search.example")
        assertThat(body["status"].asInt()).`as`("status in the body").isEqualTo(503)
    }

    @Test
    fun `answers 503 to the file lookup`() {
        val response = get("$base/api/maven/search/files?groupId=a&artifactId=b&version=1").asString()

        assertThat(response.status).`as`("status of the file lookup").isEqualTo(503)
        assertThat(response.headers.getFirst("Retry-After")).`as`("Retry-After of the file lookup").isEqualTo("30")
    }

    @Test
    fun `answers 503 to a rebuild and to a verify run of a manager`() {
        val (name, secret) = manager

        val rebuild = post("$base/api/maven/search/index/rebuild").basicAuth(name, secret).body("{}").asString()
        val verify = post("$base/api/maven/search/index/verify").basicAuth(name, secret).asString()

        assertThat(rebuild.status).`as`("status of the rebuild").isEqualTo(503)
        assertThat(verify.status).`as`("status of the verify run").isEqualTo(503)
    }

    @Test
    fun `answers 200 to the index status of a manager with the backend block and a redacted detail`() {
        val (name, secret) = manager

        val response = get("$base/api/maven/search/index").basicAuth(name, secret).asString()

        assertThat(response.status).`as`("status of the index status").isEqualTo(200)
        val backend = mapper.readTree(response.body)["backend"]
        assertThat(backend["id"].asText()).`as`("backend id").isEqualTo(UNKNOWN_ENGINE)
        assertThat(backend["state"].asText()).`as`("backend state").isEqualTo("NOT_INSTALLED")
        assertThat(backend["detail"].asText()).`as`("backend detail").contains("search.example").doesNotContain("hunter2")
        assertThat(backend["accessMismatches"].asLong()).`as`("access mismatches").isZero()
    }

    private companion object {
        /** An id that no factory answers for, with user information that must never reach an answer. */
        const val UNKNOWN_ENGINE = "opensearch://admin:hunter2@search.example"
    }

}
