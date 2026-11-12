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
