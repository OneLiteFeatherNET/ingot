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

internal class ListBlockValidationTest {

    private val plugin = "example"

    private fun problemsOf(block: PluginUiBlock.ListBlock): String =
        PluginUiValidator.pageProblems(
            plugin,
            PluginUiPage(id = "catalogue", title = "Catalogue", area = "maintenance", blocks = listOf(block)),
        ).joinToString(separator = "\n")

    @Test
    fun `should accept a list of the plugin with the default page size`() {
        // when: a list reads its own route with the default page size
        val problems = problemsOf(PluginUiBlock.ListBlock("/api/plugins/example/items"))

        // then: no problem is reported
        assertThat(problems).describedAs("problems of a valid list").isEmpty()
    }

    @Test
    fun `should accept a list with the largest page size of 100`() {
        // when: a list asks for 100 items per response
        val problems = problemsOf(PluginUiBlock.ListBlock("/api/plugins/example/items", pageSize = 100))

        // then: no problem is reported
        assertThat(problems).describedAs("problems").isEmpty()
    }

    @Test
    fun `should refuse a list whose source is a route outside the prefix of the plugin`() {
        // when: a list reads a data route of the server
        val problems = problemsOf(PluginUiBlock.ListBlock("/api/tokens"))

        // then: the problem names the path and the prefix it must have
        assertThat(problems).describedAs("problems").contains("/api/tokens").contains("/api/plugins/example/")
    }

    @Test
    fun `should refuse a list with a page size of 0`() {
        // when: the page size is below the floor of one item
        val problems = problemsOf(PluginUiBlock.ListBlock("/api/plugins/example/items", pageSize = 0))

        // then: the problem names the page size
        assertThat(problems).describedAs("problems").contains("pageSize 0")
    }

    @Test
    fun `should refuse a list with a page size of 101`() {
        // when: the page size is one item over the limit of 100
        val problems = problemsOf(PluginUiBlock.ListBlock("/api/plugins/example/items", pageSize = 101))

        // then: the problem names the page size
        assertThat(problems).describedAs("problems").contains("pageSize 101")
    }
}
