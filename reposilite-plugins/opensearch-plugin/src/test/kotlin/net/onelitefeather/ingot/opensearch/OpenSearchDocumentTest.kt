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

package net.onelitefeather.ingot.opensearch

import com.fasterxml.jackson.databind.JsonNode
import net.onelitefeather.ingot.search.Origin
import net.onelitefeather.ingot.search.Provenance
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.database.pathHash
import net.onelitefeather.ingot.search.gavKeyOf
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.HexFormat

/** The JSON document that one row becomes in the index (design D3). */
internal class OpenSearchDocumentTest {

    @Test
    fun `should write exactly the fields of the mapping`() {
        // when: a row is written as a document
        val document = OpenSearchDocument.of(testRow())

        // then: the document carries the fields of the strict mapping and no others
        assertThat(document.fieldNames().asSequence().toSet())
            .`as`("document fields")
            .isEqualTo(OpenSearchIndexDefinition.fieldNames())
    }

    @Test
    fun `should write the lower case copies of the coordinates beside the original values`() {
        // when: a row with mixed case paths is written
        val document = OpenSearchDocument.of(testRow(path = "Org/Example/App/1.0/App-1.0.JAR"))

        // then: the lower case fields hold the folded values and the display fields keep their case
        assertThat(document.text("path_lc")).`as`("path_lc").isEqualTo("org/example/app/1.0/app-1.0.jar")
        assertThat(document.text("path")).`as`("path").isEqualTo("Org/Example/App/1.0/App-1.0.JAR")
        assertThat(document.text("group_id_lc")).`as`("group_id_lc").isEqualTo("org.example")
        assertThat(document.text("artifact_key")).`as`("artifact_key").isEqualTo("org.example:app")
    }

    @Test
    fun `should write the GAV key as the hex of the key of the database`() {
        // when: a row is written as a document
        val document = OpenSearchDocument.of(testRow())

        // then: the gav field is the hex of the database key of the coordinates
        val expected = HexFormat.of().formatHex(gavKeyOf("org.example", "app", "1.0"))
        assertThat(document.text("gav")).`as`("gav").isEqualTo(expected)
    }

    @Test
    fun `should write the word tokens of the coordinates`() {
        // when: a row is written as a document
        val document = OpenSearchDocument.of(testRow())

        // then: the tokens are the words of group, artifact and version
        val tokens = document.get("tokens").map { it.asText() }
        assertThat(tokens).`as`("tokens").containsExactly("org", "example", "app", "1", "0")
    }

    @Test
    fun `should write a missing classifier as null`() {
        // when: a main artifact without classifier is written
        val document = OpenSearchDocument.of(testRow())

        // then: the classifier field is present and null
        assertThat(document.get("classifier").isNull).`as`("classifier is null").isTrue()
    }

    @Test
    fun `should write the origin and the source host of a mirror copy`() {
        // when: a mirror copy is written
        val document = OpenSearchDocument.of(testRow(origin = Provenance(Origin.MIRROR, "mirror.example.test")))

        // then: the origin is the enum name and the source host is kept
        assertThat(document.text("origin")).`as`("origin").isEqualTo("MIRROR")
        assertThat(document.text("source_host")).`as`("source host").isEqualTo("mirror.example.test")
    }

    @Test
    fun `should use the database path hash as the document id`() {
        // when: the id of a row is taken
        val id = OpenSearchDocument.id(testRow())

        // then: it is the path hash of the database
        assertThat(id).`as`("document id").isEqualTo(pathHash("releases", "org/example/app/1.0/app-1.0.jar"))
    }

    private fun JsonNode.text(field: String): String = get(field).asText()

}
