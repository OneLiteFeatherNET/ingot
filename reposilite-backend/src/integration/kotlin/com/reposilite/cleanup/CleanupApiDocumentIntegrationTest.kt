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

package com.reposilite.cleanup

import com.fasterxml.jackson.databind.JsonNode
import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteSpecification
import kong.unirest.core.Unirest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * The served OpenAPI document keeps the cleanup routes and the schemas they reference. The cleanup code is loaded from its own
 * plugin jar, so the document has to pick the routes up from the plugin and not only from the server. Each assertion names the
 * entry it checks, so a failure points at the missing route or schema rather than at a whole-file diff.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class CleanupApiDocumentIntegrationTest : ReposiliteSpecification() {

    private fun openApiDocument(): JsonNode =
        Unirest.get("$base/openapi").asJacksonObject(JsonNode::class).body

    @Test
    fun `served document lists the cleanup runs route with a GET operation`() {
        val paths = openApiDocument().path("paths")

        assertThat(paths.has("/api/cleanup/runs"))
            .describedAs("path /api/cleanup/runs in the served OpenAPI document")
            .isTrue()
        assertThat(paths.path("/api/cleanup/runs").has("get"))
            .describedAs("GET operation of /api/cleanup/runs in the served OpenAPI document")
            .isTrue()
    }

    @Test
    fun `served document lists the cleanup run route with a POST operation`() {
        val paths = openApiDocument().path("paths")

        assertThat(paths.has("/api/cleanup/policies/{id}/run"))
            .describedAs("path /api/cleanup/policies/{id}/run in the served OpenAPI document")
            .isTrue()
        assertThat(paths.path("/api/cleanup/policies/{id}/run").has("post"))
            .describedAs("POST operation of /api/cleanup/policies/{id}/run in the served OpenAPI document")
            .isTrue()
    }

    @Test
    fun `served document lists the cleanup dry-run route with a POST operation`() {
        val paths = openApiDocument().path("paths")

        assertThat(paths.has("/api/cleanup/policies/{id}/dry-run"))
            .describedAs("path /api/cleanup/policies/{id}/dry-run in the served OpenAPI document")
            .isTrue()
        assertThat(paths.path("/api/cleanup/policies/{id}/dry-run").has("post"))
            .describedAs("POST operation of /api/cleanup/policies/{id}/dry-run in the served OpenAPI document")
            .isTrue()
    }

    @Test
    fun `served document defines the CleanupRunResponse schema that the run answers reference`() {
        val document = openApiDocument()

        assertThat(document.path("components").path("schemas").has("CleanupRunResponse"))
            .describedAs("schema CleanupRunResponse in components of the served OpenAPI document")
            .isTrue()
    }

    @Test
    fun `served document defines the CleanupPreviewResponse schema that the dry-run answer references`() {
        val document = openApiDocument()

        assertThat(document.path("components").path("schemas").has("CleanupPreviewResponse"))
            .describedAs("schema CleanupPreviewResponse in components of the served OpenAPI document")
            .isTrue()
    }
}
