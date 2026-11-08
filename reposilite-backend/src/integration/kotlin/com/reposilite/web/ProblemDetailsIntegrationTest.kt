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

package com.reposilite.web

import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteSpecification
import com.fasterxml.jackson.databind.ObjectMapper
import io.javalin.http.HttpStatus.NOT_FOUND
import kong.unirest.core.Unirest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class ProblemDetailsIntegrationTest : ReposiliteSpecification() {

    private val mapper = ObjectMapper()

    @Test
    fun `should answer an unmatched api path with a json 404`() {
        val response = Unirest.get("$base/api/does-not-exist").asString()

        assertThat(response.status).isEqualTo(NOT_FOUND.code)
        assertThat(response.headers.getFirst("Content-Type")).startsWith("application/json")
        val body = mapper.readTree(response.body)
        assertThat(body["status"].asInt()).isEqualTo(404)
        assertThat(body["title"].asText()).isEqualTo("Not Found")
        assertThat(body["instance"].asText()).isEqualTo("/api/does-not-exist")
        assertThat(body["type"].asText()).isEqualTo("about:blank")
        assertThat(body.has("message")).isTrue
    }

    @Test
    fun `should answer an unmatched api path with problem json when the client prefers it`() {
        val response = Unirest.get("$base/api/does-not-exist").header("Accept", "application/problem+json").asString()

        assertThat(response.status).isEqualTo(NOT_FOUND.code)
        assertThat(response.headers.getFirst("Content-Type")).startsWith("application/problem+json")
    }

    @Test
    fun `should keep the html page for an unmatched path outside the api`() {
        val response = Unirest.get("$base/does-not-exist/at/all").asString()

        assertThat(response.status).isEqualTo(NOT_FOUND.code)
        assertThat(response.headers.getFirst("Content-Type")).startsWith("text/html")
    }

}
