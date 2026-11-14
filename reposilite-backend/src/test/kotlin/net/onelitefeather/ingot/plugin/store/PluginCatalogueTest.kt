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

package net.onelitefeather.ingot.plugin.store

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class PluginCatalogueTest {

    private val digest = "a".repeat(64)

    private fun entry(extra: String = "", compatibility: String = """"compatibility": { "ingot": { "min": "1.2.0", "below": "2.0.0" }, "pluginApi": 1 },"""): String = """
        {
          "id": "opensearch-plugin",
          "name": "OpenSearch search backend",
          "publisher": "example-publisher",
          "coordinates": "net.onelitefeather.ingot:opensearch-plugin:1.0.0",
          "sha256": "$digest",
          $compatibility
          $extra
          "yanked": false
        }
    """.trimIndent()

    private fun catalogue(vararg entries: String, schema: Int = 1, extraTop: String = ""): ByteArray = """
        {
          "schema": $schema,
          "generated": "2026-11-01T00:00:00Z",
          $extraTop
          "plugins": [ ${entries.joinToString(",")} ]
        }
    """.trimIndent().toByteArray()

    @Test
    fun `should parse the entries of a schema 1 catalogue`() {
        // given: a schema 1 catalogue with one entry
        val bytes = catalogue(entry())

        // when: the catalogue is parsed
        val parsed = PluginCatalogue.parse(bytes).get()

        // then: the entry carries its id, coordinate and digest
        val first = parsed.entries.single()
        assertThat(first.id).describedAs("the id must be the plugin id").isEqualTo("opensearch-plugin")
        assertThat(first.coordinate.toString())
            .describedAs("the coordinate must be the Maven coordinate of the jar")
            .isEqualTo("net.onelitefeather.ingot:opensearch-plugin:1.0.0")
        assertThat(first.sha256).describedAs("the digest must be kept as written").isEqualTo(digest)
    }

    @Test
    fun `should refuse a catalogue with another schema and name both numbers`() {
        // given: a catalogue that declares schema 2
        val bytes = catalogue(entry(), schema = 2)

        // when: the catalogue is parsed
        val result = PluginCatalogue.parse(bytes)

        // then: the whole catalogue is refused and the reason names both numbers
        assertThat(result.isError).describedAs("schema 2 must be refused").isTrue()
        assertThat(result.error)
            .describedAs("the reason must name the schema found and the one read")
            .contains("2", "1")
    }

    @Test
    fun `should ignore fields that schema 1 does not know`() {
        // given: unknown fields at the top level and in the entry
        val bytes = catalogue(entry(extra = """"futureField": { "anything": true },"""), extraTop = """"futureTop": 7,""")

        // when: the catalogue is parsed
        val parsed = PluginCatalogue.parse(bytes).get()

        // then: the entry is read as usual
        assertThat(parsed.entries.single().id).describedAs("unknown fields must not refuse the catalogue").isEqualTo("opensearch-plugin")
    }

    @Test
    fun `should leave the compatibility empty when the block is missing`() {
        // given: an entry without a compatibility block
        val bytes = catalogue(entry(compatibility = ""))

        // when: the catalogue is parsed
        val parsed = PluginCatalogue.parse(bytes).get()

        // then: the entry has no compatibility, so the store refuses it
        assertThat(parsed.entries.single().compatibility)
            .describedAs("a missing block must stay missing, the compatibility rule refuses it")
            .isNull()
    }

    @Test
    fun `should keep a yanked entry visible but not selectable`() {
        // given: an entry marked as yanked
        val bytes = catalogue(entry().replace("\"yanked\": false", "\"yanked\": true"))

        // when: the catalogue is parsed
        val first = PluginCatalogue.parse(bytes).get().entries.single()

        // then: the entry is listed, and a new selection of it is refused
        assertThat(first.yanked).describedAs("the yank must be read").isTrue()
        assertThat(first.selectable).describedAs("a yanked entry must not be selectable").isFalse()
    }

    @Test
    fun `should refuse a catalogue that is not valid JSON`() {
        // when: the bytes are not JSON
        val result = PluginCatalogue.parse("<html>not a catalogue</html>".toByteArray())

        // then: the catalogue is refused with a reason
        assertThat(result.isError).describedAs("a page that is not JSON must be refused").isTrue()
        assertThat(result.error).describedAs("the reason must say the catalogue is unreadable").contains("catalogue")
    }

}
