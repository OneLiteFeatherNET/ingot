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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ObjectNode
import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteSpecification
import java.nio.file.Files
import java.nio.file.Path
import kong.unirest.core.Unirest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Keeps the committed snapshot in `docs/openapi/ingot-openapi.json` equal to the OpenAPI document the server serves at
 * `GET /openapi`. The pull request check compares two snapshots and never builds the base branch, so the snapshot has to
 * be exactly what the server serves.
 *
 * Keys are sorted and `info.version` is replaced by a placeholder, so a release does not change the file and an API change
 * shows up as a plain line diff. After an intended API change, refresh the snapshot with the Gradle property named in the
 * failure message. Refreshing writes the file and passes; commit the result with the change that caused it.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class OpenApiSnapshotIntegrationTest : ReposiliteSpecification() {

    @Test
    fun `served OpenAPI document equals the committed snapshot`() {
        val served = canonical(Unirest.get("$base/openapi").asString().body)

        if (refreshRequested()) {
            Files.writeString(SNAPSHOT, served + "\n")
            return
        }

        assertThat(Files.exists(SNAPSHOT))
            .describedAs("$SNAPSHOT must exist; generate it with: $REFRESH_COMMAND")
            .isTrue()

        val committed = Files.readString(SNAPSHOT).removeSuffix("\n")
        assertThat(served)
            .describedAs(
                "$SNAPSHOT differs from the document GET /openapi serves. If the API change is intended, refresh the " +
                    "snapshot with: $REFRESH_COMMAND"
            )
            .isEqualTo(committed)
    }

    @Test
    fun `console execute keeps the plain answer schema and describes the structured answer`() {
        // given: the OpenAPI document the server serves
        val document = MAPPER.readTree(Unirest.get("$base/openapi").asString().body)

        // when: the 200 response of the execute route is read
        val ok = document.path("paths").path("/api/console/execute").path("post").path("responses").path("200")

        // then: the plain schema is still the one of the 200 response, and the description names the structured answer
        assertThat(ok.path("content").path("application/json").path("schema").path("\$ref").asText())
            .describedAs("schema of the 200 response of POST /api/console/execute")
            .isEqualTo("#/components/schemas/ExecutionResponse")
        assertThat(ok.path("description").asText())
            .describedAs("description of the 200 response of POST /api/console/execute")
            .contains("structured=true")
            .contains("exit code")
    }

    @Test
    fun `console socket describes both protocols and the frames of version 1`() {
        // given: the OpenAPI document the server serves
        val document = MAPPER.readTree(Unirest.get("$base/openapi").asString().body)

        // when: the description of the socket route is read
        val description = document.path("paths").path("/api/console/sock").path("patch").path("description").asText()

        // then: it names the legacy protocol, the hello that opens version 1 and the frames of the session
        assertThat(description)
            .describedAs("description of /api/console/sock")
            .contains("Authorization:")
            .contains("hello")
            .contains("welcome")
            .contains("execute")
            .contains("result")
            .contains("confirm")
            .contains("complete")
            .contains("log")
            .contains("error")
    }

    @Test
    fun `own security status describes its 200 answer`() {
        // given: the OpenAPI document the server serves
        val document = MAPPER.readTree(Unirest.get("$base/openapi").asString().body)

        // when: the 200 response of the own security status is read
        val security = document.path("paths").path("/api/identity/me/security").path("get").path("responses").path("200")

        // then: it names the security status schema
        assertThat(security.path("content").path("application/json").path("schema").path("\$ref").asText())
            .describedAs("schema of the 200 response of GET /api/identity/me/security")
            .isEqualTo("#/components/schemas/SecurityStatusDto")
    }

    private fun refreshRequested(): Boolean = System.getProperty(REFRESH_PROPERTY) == "true"

    private fun canonical(document: String): String {
        val tree = MAPPER.readTree(document)
        (tree.path("info") as? ObjectNode)?.put("version", VERSION_PLACEHOLDER)
        @Suppress("UNCHECKED_CAST")
        val plain = MAPPER.convertValue(tree, Map::class.java) as Map<*, *>
        return MAPPER.writeValueAsString(plain)
    }

    private companion object {
        /** Gradle runs the tests of this module with the backend directory as the working directory. */
        val SNAPSHOT: Path = Path.of("../docs/openapi/ingot-openapi.json")
        const val REFRESH_PROPERTY = "ingot.openapi.refresh"
        const val VERSION_PLACEHOLDER = "snapshot"
        const val REFRESH_COMMAND =
            "./gradlew :reposilite-backend:integration --tests 'com.reposilite.web.OpenApiSnapshotIntegrationTest' -PrefreshOpenApiSnapshot"

        val MAPPER: ObjectMapper = ObjectMapper()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(SerializationFeature.INDENT_OUTPUT)
    }
}
