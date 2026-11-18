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

import net.onelitefeather.ingot.plugin.ExperimentalIngotPluginApi
import net.onelitefeather.ingot.plugin.ui.PluginUiReaderPage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@OptIn(ExperimentalIngotPluginApi::class)
internal class PluginUiReaderPageValidatorTest {

    private val plugin = "example"

    private fun page(
        id: String = "releases",
        title: String = "Releases",
        blocks: List<PluginUiBlock> = listOf(PluginUiBlock.Text("Hello")),
    ) = PluginUiReaderPage(id = id, title = title, blocks = blocks)

    private fun problemsOf(page: PluginUiReaderPage): String =
        PluginUiValidator.readerPageProblems(plugin, page).joinToString(separator = "\n")

    @Test
    fun `should accept a valid reader page of the plugin`() {
        // when: a reader page with one plain text block is validated
        val problems = PluginUiValidator.readerPageProblems(plugin, page())

        // then: no problem is reported
        assertThat(problems).describedAs("problems of a valid reader page").isEmpty()
    }

    @Test
    fun `should refuse a form block in a reader page`() {
        // given: a reader page that asks for a form
        val form = PluginUiBlock.Form(
            source = "/api/plugins/example/settings",
            target = "/api/plugins/example/settings",
            method = "PUT",
            schema = mapOf("type" to "object"),
        )

        // when: the page is validated
        val problems = problemsOf(page(blocks = listOf(form)))

        // then: the problem names the block and the page
        assertThat(problems).describedAs("problems").contains("block 0 of page 'releases'").contains("form")
    }

    @Test
    fun `should refuse an action block in a reader page`() {
        // given: a reader page with a button that sends a request
        val action = PluginUiBlock.Action(label = "Delete", method = "DELETE", path = "/api/plugins/example/releases/1")

        // when: the page is validated
        val problems = problemsOf(page(blocks = listOf(action)))

        // then: the problem names the block and the kind of block
        assertThat(problems).describedAs("problems").contains("block 0 of page 'releases'").contains("action")
    }

    @Test
    fun `should refuse a status block in a reader page`() {
        // given: a reader page with a block that polls a source
        val status = PluginUiBlock.Status(source = "/api/plugins/example/health")

        // when: the page is validated
        val problems = problemsOf(page(blocks = listOf(status)))

        // then: the problem names the block and the kind of block
        assertThat(problems).describedAs("problems").contains("block 0 of page 'releases'").contains("status")
    }

    @Test
    fun `should refuse a reader source outside the prefix of the plugin`() {
        // given: a reader page whose table reads a route of the admin api
        val table = PluginUiBlock.Table(source = "/api/tokens", columns = listOf(PluginUiColumn("name", "Name")))

        // when: the page is validated
        val problems = problemsOf(page(blocks = listOf(table)))

        // then: the problem names the path
        assertThat(problems).describedAs("problems").contains("'/api/tokens'").contains("/api/plugins/example/")
    }

    @Test
    fun `should refuse a reader page with 31 blocks`() {
        // given: one block more than a page may hold
        val blocks = List(31) { PluginUiBlock.Text("line $it") }

        // when: the page is validated
        val problems = problemsOf(page(blocks = blocks))

        // then: the problem names the block count
        assertThat(problems).describedAs("problems").contains("30 blocks")
    }

    @Test
    fun `should refuse a reader page id with capital letters`() {
        // when: the page id contains a capital letter
        val problems = problemsOf(page(id = "Releases"))

        // then: the problem names the id
        assertThat(problems).describedAs("problems").contains("id 'Releases'")
    }
}
