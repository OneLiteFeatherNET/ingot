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

import com.fasterxml.jackson.databind.JsonNode
import com.reposilite.ReposiliteObjectMapper
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.backend.IndexAction
import net.onelitefeather.ingot.search.backend.SearchCapability
import net.onelitefeather.ingot.search.database.pathHash
import net.onelitefeather.ingot.search.http.HttpAnswer
import net.onelitefeather.ingot.search.http.HttpCall
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** The plugin attributes of a row in the index (search document attributes, D4 and D6), answered by a fake transport. */
internal class ElasticsearchAttributesTest {

    private val mapper = ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER

    private val clock: Clock = Clock.fixed(Instant.parse("2026-11-21T10:00:00Z"), ZoneOffset.UTC)

    private val environment = mapOf(
        "INGOT_ELASTICSEARCH_URLS" to "https://search.test:9200",
        "INGOT_ELASTICSEARCH_USERNAME" to "ingot",
        "INGOT_ELASTICSEARCH_PASSWORD" to "not-in-requests",
    )

    private val attributes = mapOf(
        "portal.platforms" to listOf("Paper", "Folia"),
        "core.enricher.portal" to listOf("1.2.0"),
    )

    @Test
    fun `should map every attribute as a keyword through a dynamic template`() {
        // when: the index definition is created
        val definition = ElasticsearchIndexDefinition.create(replicas = 1)

        // then: the attributes object takes new keys, and each one is a keyword
        assertThat(definition.at("/mappings/properties/attributes/dynamic").asText())
            .`as`("dynamic setting of attributes")
            .isEqualTo("true")
        val template = definition.at("/mappings/dynamic_templates/0/ingot_attributes")
        assertThat(template.path("path_match").asText()).`as`("path of the attribute template").isEqualTo("attributes.*")
        assertThat(template.path("match_mapping_type").asText())
            .`as`("value type of the attribute template, so that the objects of a dotted key stay objects")
            .isEqualTo("string")
        assertThat(template.at("/mapping/type").asText()).`as`("type of an attribute").isEqualTo("keyword")
    }

    @Test
    fun `should write the attributes of a row with their order and case`() {
        // when: a row is written with attributes
        val document = ElasticsearchDocument.of(testRow(), attributes)

        // then: the attributes object holds every key with its values as given
        val written = document.path("attributes")
        assertThat(written.path("portal.platforms").map { it.asText() })
            .`as`("values of portal.platforms")
            .containsExactly("Paper", "Folia")
        assertThat(written.path("core.enricher.portal").map { it.asText() }).`as`("enricher version").containsExactly("1.2.0")
    }

    @Test
    fun `should replace the document when an upsert carries attributes`() {
        // when: an upsert with attributes is planned
        val lines = bulkLines(IndexAction.Upsert(testRow(), attributes))

        // then: the document is indexed whole, attributes included, so no older attribute survives
        assertThat(lines[0].has("index")).`as`("operation of an upsert with attributes").isTrue()
        assertThat(lines[1].path("attributes").path("portal.platforms").size()).`as`("values written").isEqualTo(2)
    }

    @Test
    fun `should write an empty attributes object when an upsert carries an empty set`() {
        // when: an upsert with an empty attribute set is planned
        val lines = bulkLines(IndexAction.Upsert(testRow(), emptyMap()))

        // then: the document is indexed whole with no attribute, which removes the stored ones
        assertThat(lines[0].has("index")).`as`("operation of an upsert with an empty set").isTrue()
        assertThat(lines[1].path("attributes").isObject).`as`("attributes object written").isTrue()
        assertThat(lines[1].path("attributes").isEmpty).`as`("attributes written").isTrue()
    }

    @Test
    fun `should update the fields and keep the stored attributes when an upsert carries none`() {
        // when: an upsert without attributes is planned
        val lines = bulkLines(IndexAction.Upsert(testRow()))

        // then: the fixed fields are merged into the document, or create it, and the attributes are not part of the request
        assertThat(lines[0].path("update").path("_id").asText()).`as`("id of the update").isEqualTo(ElasticsearchDocument.id(testRow()))
        assertThat(lines[1].path("doc_as_upsert").asBoolean()).`as`("doc_as_upsert").isTrue()
        assertThat(lines[1].path("doc").path("path").asText()).`as`("path in the merged document").isEqualTo(testRow().file.path)
        assertThat(lines[1].path("doc").has("attributes")).`as`("attributes in the merged document").isFalse()
    }

    @Test
    fun `should declare that it stores attributes`() {
        // when: the backend is created
        val backend = ElasticsearchSearchBackend(ElasticsearchSettings.read(environment), clock, sslContext = null, exchange = FakeExchange())

        // then: it declares the attribute capability, so the core hands it the attributes
        assertThat(backend.capabilities).`as`("capabilities").contains(SearchCapability.ATTRIBUTES)
    }

    @Test
    fun `should report the stored enricher versions of a row`() {
        // given: a cluster that stores a row with an enricher version and another attribute
        val path = "org/example/app/1.0/app-1.0.jar"
        val exchange = FakeExchange { storedRow(pathHash("releases", path)) }
        val backend = ElasticsearchSearchBackend(ElasticsearchSettings.read(environment), clock, sslContext = null, exchange = exchange)

        // when: the stored state of the row is read
        val stored = backend.existing("releases", listOf(path)).getValue(path)

        // then: the version of each enricher comes from its core.enricher key, and the lookup asks for the attributes
        assertThat(stored.enricherVersions).`as`("enricher versions").isEqualTo(mapOf("portal" to "1.2.0"))
        assertThat(exchange.calls.single().uri.query).`as`("source fields of the lookup").contains("attributes")
    }

    @Test
    fun `should add the attribute mapping to an index that exists already`() {
        // given: a cluster whose alias points to an index of this mapping version
        val exchange = FakeExchange(::answerExistingIndex)
        val backend = ElasticsearchSearchBackend(ElasticsearchSettings.read(environment), clock, sslContext = null, exchange = exchange)

        // when: the index is opened
        backend.open()

        // then: the attribute mapping is put on the physical index, an addition that needs no reindex
        val put = exchange.calls.filter { it.method == "PUT" && it.uri.path == "/ingot-artifacts-v1/_mapping" }
        assertThat(put).`as`("mapping requests of the open").hasSize(1)
        val body = mapper.readTree(put.single().body)
        assertThat(body.at("/properties/attributes/dynamic").asText()).`as`("dynamic setting of attributes").isEqualTo("true")
        assertThat(body.at("/dynamic_templates/0/ingot_attributes/path_match").asText())
            .`as`("path of the attribute template")
            .isEqualTo("attributes.*")
    }

    private fun bulkLines(action: IndexAction): List<JsonNode> {
        val bulk = ElasticsearchBulk.plan("ingot-artifacts", listOf(action), bulkSize = 500).single() as WriteStep.Bulk
        return bulk.body.trimEnd('\n').split('\n').map { mapper.readTree(it) }
    }

    private fun storedRow(id: String): HttpAnswer = HttpAnswer(
        200,
        emptyMap(),
        """
        {"docs":[{"_id":"$id","found":true,"_source":{"size_bytes":10,"last_modified":1,"sha1":null,"sha256":null,
        "attributes":{"portal.platforms":["Paper"],"core.enricher.portal":["1.2.0"]}}}]}
        """.trimIndent(),
    )

    private fun answerExistingIndex(call: HttpCall): HttpAnswer {
        val body = when (call.uri.path) {
            "/" -> """{"version":{"number":"8.19.0"}}"""
            "/_cluster/health" -> """{"status":"green"}"""
            "/_alias/ingot-artifacts" -> """{"ingot-artifacts-v1":{}}"""
            "/ingot-artifacts-v1/_settings/index.uuid" ->
                """{"ingot-artifacts-v1":{"settings":{"index":{"uuid":"uuid-of-the-existing-index"}}}}"""
            "/ingot-artifacts-v1/_mapping" ->
                """{"ingot-artifacts-v1":{"mappings":{"_meta":{"ingot_mapping_version":${ElasticsearchIndexDefinition.MAPPING_VERSION}}}}}"""
            else -> "{}"
        }
        return HttpAnswer(200, emptyMap(), body)
    }

}
