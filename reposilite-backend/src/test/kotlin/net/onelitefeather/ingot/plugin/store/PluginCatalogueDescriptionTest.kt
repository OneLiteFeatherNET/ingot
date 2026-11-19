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

/** The optional description of a catalogue entry. Only bytes are parsed, so no source and no store are involved. */
internal class PluginCatalogueDescriptionTest {

    private fun catalogue(description: String): ByteArray = """
        {
          "schema": 1,
          "plugins": [ {
            "id": "checksum",
            "name": "Checksum",
            "publisher": "example-publisher",
            "coordinates": "net.onelitefeather.ingot:checksum-plugin:1.0.0",
            "sha256": "${"a".repeat(64)}",
            "compatibility": { "ingot": { "min": "1.2.0", "below": "2.0.0" }, "pluginApi": 1 },
            $description
            "yanked": false
          } ]
        }
    """.trimIndent().toByteArray()

    private fun parsedEntry(description: String = ""): PluginCatalogueEntry {
        val result = PluginCatalogue.parse(catalogue(description))
        assertThat(result.isOk).describedAs("a schema 1 catalogue must be accepted").isTrue()
        return result.get().entries.single()
    }

    @Test
    fun `should read the description of an entry that carries one`() {
        // when: an entry with a description is parsed
        val entry = parsedEntry(""""description": "Verifies checksums of uploaded artifacts",""")

        // then: the entry carries the text
        assertThat(entry.description).describedAs("description of the entry").isEqualTo("Verifies checksums of uploaded artifacts")
    }

    @Test
    fun `should read the empty text for an entry without a description`() {
        // when: an entry without the field is parsed
        val entry = parsedEntry()

        // then: the entry is accepted and has no description
        assertThat(entry.description).describedAs("description of an entry without the field").isEmpty()
    }

    @Test
    fun `should normalise the description the same way as the plugin annotation`() {
        // when: an entry with surrounding whitespace, a line break and an over-long text is parsed
        val entry = parsedEntry(""""description": "  First line\nsecond line ${"x".repeat(300)}  ",""")

        // then: the line break and the surrounding whitespace are removed and the text is cut to 280 code points
        assertThat(entry.description).describedAs("normalised description").startsWith("First linesecond line x")
        assertThat(entry.description.codePointCount(0, entry.description.length)).describedAs("code points of the description").isEqualTo(280)
    }

    @Test
    fun `should read the empty text for a description that is not text`() {
        // when: an entry whose description is a number is parsed
        val entry = parsedEntry(""""description": 7,""")

        // then: the entry is still accepted, without a description
        assertThat(entry.description).describedAs("description that is not text").isEmpty()
    }

}
