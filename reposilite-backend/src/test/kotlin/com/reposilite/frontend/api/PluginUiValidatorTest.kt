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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class PluginUiValidatorTest {

    private val plugin = "example"

    private fun page(
        id: String = "status",
        title: String = "Status",
        area: String = "maintenance",
        group: String = "System",
        blocks: List<PluginUiBlock> = listOf(PluginUiBlock.Text("Hello")),
    ) = PluginUiPage(id = id, title = title, area = area, group = group, blocks = blocks)

    private fun problemsOf(page: PluginUiPage): String =
        PluginUiValidator.pageProblems(plugin, page).joinToString(separator = "\n")

    @Test
    fun `should accept a valid page of the plugin`() {
        // when: a page with one plain text block is validated
        val problems = PluginUiValidator.pageProblems(plugin, page())

        // then: no problem is reported
        assertThat(problems).describedAs("problems of a valid page").isEmpty()
    }

    @Test
    fun `should refuse an id with capital letters`() {
        // when: a page id contains a capital letter
        val problems = problemsOf(page(id = "Status"))

        // then: the problem names the id
        assertThat(problems).describedAs("problems").contains("id 'Status'")
    }

    @Test
    fun `should refuse a title of 61 characters`() {
        // when: the title is one character over the limit
        val problems = problemsOf(page(title = "t".repeat(61)))

        // then: the problem names the title
        assertThat(problems).describedAs("problems").contains("title")
    }

    @Test
    fun `should refuse a page with 31 blocks`() {
        // given: one block more than a page may hold
        val blocks = List(31) { PluginUiBlock.Text("line $it") }

        // when: the page is validated
        val problems = problemsOf(page(blocks = blocks))

        // then: the problem names the block count
        assertThat(problems).describedAs("problems").contains("30 blocks")
    }

    @Test
    fun `should refuse a page without blocks`() {
        // when: the page has no block
        val problems = problemsOf(page(blocks = emptyList()))

        // then: the problem names the block count
        assertThat(problems).describedAs("problems").contains("blocks")
    }

    @Test
    fun `should refuse an unknown admin area`() {
        // when: the page names an area that is not an admin area
        val problems = problemsOf(page(area = "billing"))

        // then: the problem lists the valid areas
        assertThat(problems).describedAs("problems").contains("billing").contains("maintenance")
    }

    @Test
    fun `should accept the instance admin area as the area of a page`() {
        // when: the page names the full instance administrator
        val problems = PluginUiValidator.pageProblems(plugin, page(area = "*"))

        // then: the page is accepted
        assertThat(problems).describedAs("problems").isEmpty()
    }

    @Test
    fun `should refuse an unknown menu group`() {
        // when: the page names a group that the navigation does not have
        val problems = problemsOf(page(group = "Finance"))

        // then: the problem names the group
        assertThat(problems).describedAs("problems").contains("Finance")
    }

    @Test
    fun `should refuse a data path outside the prefix of the plugin`() {
        // when: a key value block reads a route of the server instead of its own plugin
        val problems = problemsOf(page(blocks = listOf(PluginUiBlock.KeyValue("/api/tokens"))))

        // then: the problem names the path and the prefix it must have
        assertThat(problems).describedAs("problems").contains("/api/tokens").contains("/api/plugins/example/")
    }

    @Test
    fun `should refuse a path that climbs out of the prefix`() {
        // when: a path starts with the prefix but leaves it through parent segments
        val problems = problemsOf(page(blocks = listOf(PluginUiBlock.KeyValue("/api/plugins/example/../../tokens"))))

        // then: the path is refused
        assertThat(problems).describedAs("problems").contains("/api/plugins/example/../../tokens")
    }

    @Test
    fun `should derive the prefix from the plugin name and not from the page`() {
        // given: a path below the prefix of the plugin named example
        val block = PluginUiBlock.KeyValue("/api/plugins/example/status")

        // when: the same page is validated for two plugins
        val forExample = PluginUiValidator.pageProblems("example", page(blocks = listOf(block)))
        val forOther = PluginUiValidator.pageProblems("other", page(blocks = listOf(block)))

        // then: the path belongs to example only
        assertThat(forExample).describedAs("problems for example").isEmpty()
        assertThat(forOther).describedAs("problems for other").isNotEmpty()
    }

    @Test
    fun `should refuse an action with the method GET`() {
        // when: an action names a read method, which is not a write
        val problems = problemsOf(page(blocks = listOf(PluginUiBlock.Action("Start", "GET", "/api/plugins/example/start"))))

        // then: the problem names the method
        assertThat(problems).describedAs("problems").contains("GET")
    }

    @Test
    fun `should refuse a status block that refreshes every 4 seconds`() {
        // when: the refresh interval is below the floor of five seconds
        val problems = problemsOf(page(blocks = listOf(PluginUiBlock.Status("/api/plugins/example/status", refreshSeconds = 4))))

        // then: the problem names the interval
        assertThat(problems).describedAs("problems").contains("refreshSeconds")
    }

    @Test
    fun `should refuse a form schema that references a document outside the page`() {
        // given: a schema that loads another document when the form is drawn
        val schema = mapOf("properties" to mapOf("host" to mapOf("\$ref" to "https://example.com/host.json")))

        // when: the form is validated
        val problems = problemsOf(page(blocks = listOf(PluginUiBlock.Form("/api/plugins/example/config", "/api/plugins/example/config", "PUT", schema))))

        // then: the external reference is refused
        assertThat(problems).describedAs("problems").contains("https://example.com/host.json")
    }

    @Test
    fun `should accept a form schema that references its own document`() {
        // given: a schema that refers to a definition inside itself
        val schema = mapOf(
            "properties" to mapOf("host" to mapOf("\$ref" to "#/definitions/host")),
            "definitions" to mapOf("host" to mapOf("type" to "string")),
        )

        // when: the form is validated
        val problems = PluginUiValidator.pageProblems(
            plugin,
            page(blocks = listOf(PluginUiBlock.Form("/api/plugins/example/config", "/api/plugins/example/config", "PUT", schema)))
        )

        // then: the page is accepted
        assertThat(problems).describedAs("problems").isEmpty()
    }

    @Test
    fun `should refuse a manifest of one plugin above 64 KiB`() {
        // given: two pages of thirty text blocks of 2000 characters each, about 120 KiB in all
        val text = "a".repeat(2000)
        val big = page(blocks = List(30) { PluginUiBlock.Text(text) })

        // when: the manifest of the plugin is validated
        val problem = PluginUiValidator.manifestProblem(plugin, listOf(big, big.copy(id = "other")))

        // then: the problem names the plugin and the size limit
        assertThat(problem).describedAs("problem").isNotNull().contains("example").contains("64 KiB")
    }

    @Test
    fun `should refuse a settings section whose label is empty`() {
        // when: a section has no label
        val problems = PluginUiValidator.sectionProblems(plugin, PluginUiSection(domain = "example", label = ""))

        // then: the problem names the label
        assertThat(problems).describedAs("problems").anyMatch { it.contains("label") }
    }

}
