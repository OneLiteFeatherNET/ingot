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

package net.onelitefeather.ingot.plugin.openapi

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// Every test builds its own documents and fragments in memory, so the order of the tests does not matter.
class PluginOpenApiFragmentsTest {

    private val mapper = ObjectMapper()
    private val warnings = mutableListOf<String>()

    private fun document(json: String): ObjectNode = mapper.readTree(json) as ObjectNode

    private fun fragment(pluginId: String, json: String) = PluginOpenApiFragment(pluginId, json.toByteArray())

    private fun merge(document: ObjectNode, vararg fragments: PluginOpenApiFragment): ObjectNode =
        PluginOpenApiFragments.merge(document, fragments.toList()) { warnings += it }

    private val serverDocument = """
        {
          "openapi": "3.0.3",
          "paths": {
            "/api/status/instance": { "get": { "summary": "server" } }
          },
          "components": {
            "schemas": {
              "ServerItem": { "type": "object" }
            }
          }
        }
    """.trimIndent()

    @Test
    fun `merge returns the same document object when no fragment is given`() {
        val document = document(serverDocument)

        val merged = PluginOpenApiFragments.merge(document, emptyList()) { warnings += it }

        assertThat(merged).`as`("the merge with no fragment must return the document it was given").isSameAs(document)
        assertThat(warnings).`as`("no fragment must log nothing").isEmpty()
    }

    @Test
    fun `merge adds the paths and schemas of a fragment`() {
        val merged = merge(
            document(serverDocument),
            fragment(
                "fixture",
                """{"paths":{"/api/fixture/items":{"get":{"summary":"fixture"}}},"components":{"schemas":{"FixtureItem":{"type":"object"}}}}"""
            )
        )

        assertThat(merged.path("paths").has("/api/fixture/items"))
            .`as`("the path of the fragment must be added to the document")
            .isTrue()
        assertThat(merged.path("components").path("schemas").has("FixtureItem"))
            .`as`("the schema of the fragment must be added to the document")
            .isTrue()
        assertThat(warnings).`as`("a fragment without conflicts must log nothing").isEmpty()
    }

    @Test
    fun `merge keeps a server path and logs one line naming the plugin and the path`() {
        val merged = merge(
            document(serverDocument),
            fragment("fixture", """{"paths":{"/api/status/instance":{"get":{"summary":"plugin"}}}}""")
        )

        assertThat(merged.path("paths").path("/api/status/instance").path("get").path("summary").asText())
            .`as`("the server path must win over the plugin path")
            .isEqualTo("server")
        assertThat(warnings)
            .`as`("one WARN line must name the plugin and the kept path")
            .singleElement()
            .asString()
            .contains("fixture", "/api/status/instance")
    }

    @Test
    fun `merge keeps a server schema and logs one line naming the plugin and the schema`() {
        val merged = merge(
            document(serverDocument),
            fragment("fixture", """{"components":{"schemas":{"ServerItem":{"type":"string"}}}}""")
        )

        assertThat(merged.path("components").path("schemas").path("ServerItem").path("type").asText())
            .`as`("the server schema must win over the plugin schema")
            .isEqualTo("object")
        assertThat(warnings)
            .`as`("one WARN line must name the plugin and the kept schema")
            .singleElement()
            .asString()
            .contains("fixture", "ServerItem")
    }

    @Test
    fun `merge keeps the schema of the lower plugin id and logs one line naming the other plugin`() {
        val merged = merge(
            document(serverDocument),
            fragment("beta", """{"components":{"schemas":{"Shared":{"description":"beta"}}}}"""),
            fragment("alpha", """{"components":{"schemas":{"Shared":{"description":"alpha"}}}}""")
        )

        assertThat(merged.path("components").path("schemas").path("Shared").path("description").asText())
            .`as`("the schema of the lower plugin id, alpha, must be served")
            .isEqualTo("alpha")
        assertThat(warnings)
            .`as`("one WARN line must name beta and the schema Shared")
            .singleElement()
            .asString()
            .contains("beta", "Shared")
    }

    @Test
    fun `merge gives an equal document for both registration orders`() {
        val alpha = fragment("alpha", """{"paths":{"/api/a":{}},"components":{"schemas":{"Shared":{"description":"alpha"}}}}""")
        val beta = fragment("beta", """{"paths":{"/api/b":{}},"components":{"schemas":{"Shared":{"description":"beta"}}}}""")

        val forward = PluginOpenApiFragments.merge(document(serverDocument), listOf(alpha, beta)) { }
        val backward = PluginOpenApiFragments.merge(document(serverDocument), listOf(beta, alpha)) { }

        assertThat(backward)
            .`as`("the merged document must not depend on the order in which the plugins were registered")
            .isEqualTo(forward)
    }

    @Test
    fun `merge skips a fragment that is not JSON and logs one line naming the plugin`() {
        val document = document(serverDocument)
        val before = document.deepCopy()

        val merged = merge(document, fragment("broken", "not json"))

        assertThat(merged)
            .`as`("a fragment that is not JSON must add nothing to the document")
            .isEqualTo(before)
        assertThat(warnings)
            .`as`("one WARN line must name the broken plugin")
            .singleElement()
            .asString()
            .contains("broken")
    }

    @Test
    fun `merge skips a fragment whose paths is an array, including its schemas`() {
        val merged = merge(
            document(serverDocument),
            fragment("fixture", """{"paths":[],"components":{"schemas":{"FixtureItem":{"type":"object"}}}}""")
        )

        assertThat(merged.path("components").path("schemas").has("FixtureItem"))
            .`as`("no schema of a fragment with an array as paths may be added")
            .isFalse()
        assertThat(warnings)
            .`as`("one WARN line must name the plugin of the array fragment")
            .singleElement()
            .asString()
            .contains("fixture")
    }

    @Test
    fun `merge adds the paths of a fragment without components and creates no components`() {
        val document = document("""{"openapi":"3.0.3","paths":{}}""")

        val merged = merge(document, fragment("fixture", """{"paths":{"/api/fixture/items":{}}}"""))

        assertThat(merged.path("paths").has("/api/fixture/items"))
            .`as`("the path of the fragment must be added")
            .isTrue()
        assertThat(merged.has("components"))
            .`as`("a fragment without schemas must not create components")
            .isFalse()
    }

    @Test
    fun `merge creates components schemas only when a fragment adds a schema`() {
        val document = document("""{"openapi":"3.0.3","paths":{}}""")

        val merged = merge(document, fragment("fixture", """{"components":{"schemas":{"FixtureItem":{"type":"object"}}}}"""))

        assertThat(merged.path("components").path("schemas").has("FixtureItem"))
            .`as`("components.schemas must be created to hold the schema of the fragment")
            .isTrue()
    }

    @Test
    fun `merge logs nothing for a fragment that holds neither paths nor schemas`() {
        val merged = merge(document(serverDocument), fragment("fixture", """{"info":{"title":"ignored"}}"""))

        assertThat(merged.path("paths").size())
            .`as`("the server paths must stay as they were")
            .isEqualTo(1)
        assertThat(warnings).`as`("a fragment with nothing to merge must log nothing").isEmpty()
    }
}
