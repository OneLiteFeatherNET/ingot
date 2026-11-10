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

import com.fasterxml.jackson.databind.JsonNode
import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteSpecification
import kong.unirest.core.Unirest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * The served OpenAPI document declares the two credential forms the API accepts, and a route that needs a login
 * refers to one of them, so that a generated client knows to send it.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class OpenApiSecuritySchemesIntegrationTest : ReposiliteSpecification() {

    private fun openApiDocument(): JsonNode =
        Unirest.get("$base/openapi").asJacksonObject(JsonNode::class).body

    @Test
    fun `declares HTTP Basic with a token and the session bearer as security schemes`() {
        val schemes = openApiDocument().path("components").path("securitySchemes")

        assertThat(schemes.path("tokenBasic").path("type").asText())
            .describedAs("type of the tokenBasic security scheme")
            .isEqualTo("http")
        assertThat(schemes.path("tokenBasic").path("scheme").asText())
            .describedAs("scheme of the tokenBasic security scheme")
            .isEqualTo("basic")
        assertThat(schemes.path("sessionBearer").path("type").asText())
            .describedAs("type of the sessionBearer security scheme")
            .isEqualTo("http")
        assertThat(schemes.path("sessionBearer").path("scheme").asText())
            .describedAs("scheme of the sessionBearer security scheme")
            .isEqualTo("bearer")
    }

    @Test
    fun `a route that needs a login refers to a declared security scheme`() {
        // GET /api/identity/me/tokens answers only an authenticated caller
        val document = openApiDocument()
        val requirements = document.path("paths").path("/api/identity/me/tokens").path("get").path("security")

        assertThat(requirements.isArray && requirements.size() > 0)
            .describedAs("the security requirements of GET /api/identity/me/tokens")
            .isTrue()
        val referenced = requirements.flatMap { requirement -> requirement.fieldNames().asSequence().toList() }
        assertThat(referenced)
            .describedAs("security schemes referenced by GET /api/identity/me/tokens")
            .containsAnyOf("tokenBasic", "sessionBearer")
    }

}
