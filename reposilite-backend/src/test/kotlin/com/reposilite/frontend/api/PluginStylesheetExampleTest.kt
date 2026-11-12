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
package com.reposilite.frontend.api

import com.reposilite.ReposiliteObjectMapper
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The example sheet of the dashboard guide is the one a plugin author copies, so it must pass the same validator as any
 * plugin sheet, and it must name only stable names that the anchor registry lists. The files are read from the repository
 * relative to the module, as the dashboard path tests do.
 */
internal class PluginStylesheetExampleTest {

    private val guide: Path = Path.of("..", "docs", "gitbook", "how-to", "extend-the-dashboard-from-a-plugin.md")
        .toAbsolutePath().normalize()

    private val registry: Path = Path.of("..", "reposilite-frontend", "src", "anchors", "registry.json")
        .toAbsolutePath().normalize()

    private fun exampleSheet(): String {
        val text = Files.readString(guide, UTF_8)
        val block = Regex("```css\\n(.*?)```", RegexOption.DOT_MATCHES_ALL).find(text)
            ?: error("the guide has no css block")
        return block.groupValues[1]
    }

    @Test
    fun `should pass the validator with the example sheet of the guide`() {
        // when: the example is validated as the sheet of a plugin
        val problems = PluginStylesheetValidator.problems("example", exampleSheet().toByteArray(UTF_8))

        // then: the example follows every rule that it documents
        assertThat(problems).describedAs("problems of the guide example").isEmpty()
    }

    @Test
    fun `should start every rule of the example with a stable component name`() {
        // when: the selectors of the example are read, one per line that opens a block
        val selectors = exampleSheet().lines().map { it.trim() }.filter { it.endsWith("{") }

        // then: each one starts with the component attribute, so it reaches only the component it names
        assertThat(selectors).describedAs("selectors").isNotEmpty()
        assertThat(selectors.filterNot { it.startsWith("[data-ingot-component=\"") })
            .describedAs("selectors without a component name").isEmpty()
    }

    @Test
    fun `should name only components and parts that the anchor registry lists`() {
        // given: the components and their parts in the registry
        val components = ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER.readTree(Files.readString(registry, UTF_8))["components"]
        val partsByComponent = components.associate { component ->
            component["name"].asText() to component["parts"].map { it.asText() }.toSet()
        }

        // when: every rule of the example is read with the component and part names it uses
        val rules = exampleSheet().lines().filter { it.trim().endsWith("{") }
        val unknown = rules.flatMap { rule ->
            val component = COMPONENT.find(rule)?.groupValues?.get(1)
            val parts = PART.findAll(rule).map { it.groupValues[1] }.toList()
            when {
                component == null -> listOf("no component in '$rule'")
                component !in partsByComponent -> listOf("component '$component' is not in the registry")
                else -> parts.filter { it !in partsByComponent.getValue(component) }
                    .map { "part '$it' is not a part of '$component'" }
            }
        }

        // then: nothing in the example is unknown to the registry
        assertThat(unknown).describedAs("names the registry does not list").isEmpty()
    }

    private companion object {
        val COMPONENT = Regex("data-ingot-component=\"([^\"]+)\"")
        val PART = Regex("data-ingot-part=\"([^\"]+)\"")
    }
}
