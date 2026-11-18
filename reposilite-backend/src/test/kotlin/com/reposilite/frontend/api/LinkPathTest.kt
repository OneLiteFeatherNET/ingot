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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

internal class LinkPathTest {

    @ParameterizedTest
    @ValueSource(strings = ["/plugins/x/y", "/browse/repo/a/b.jar", "/repo/list?page=2&sort=name", "/a/b~c_d.e-f"])
    fun `should accept a same-origin path of the dashboard or a repository`(href: String) {
        // when: a link carries a same-origin path
        val problem = PluginUiValidator.linkProblem(href)

        // then: the path is accepted
        assertThat(problem).describedAs("problem of '$href'").isNull()
    }

    @ParameterizedTest
    @ValueSource(strings = ["//h", "/\\h", "https://h", "javascript:alert(1)", "data:text/html,x", "mailto:a@b"])
    fun `should refuse a path that leaves the host or starts a scheme`(href: String) {
        // when: a link names another host, a backslash start or a scheme
        val problem = PluginUiValidator.linkProblem(href)

        // then: the link is refused
        assertThat(problem).describedAs("problem of '$href'").isNotNull()
    }

    @ParameterizedTest
    @ValueSource(strings = ["/api/tokens", "/assets/x"])
    fun `should refuse a data route or an asset`(href: String) {
        // when: a link points at a route that answers data or serves assets
        val problem = PluginUiValidator.linkProblem(href)

        // then: the link is refused
        assertThat(problem).describedAs("problem of '$href'").isNotNull()
    }

    @ParameterizedTest
    @ValueSource(strings = ["/a/../b", "/a%2e%2e/b", "/a/./b", "/a//b", "/a b", "/a#frag"])
    fun `should refuse traversal, percent encoding, empty segments and characters outside the set`(href: String) {
        // when: a link climbs out, hides a segment or uses a character outside the set
        val problem = PluginUiValidator.linkProblem(href)

        // then: the link is refused
        assertThat(problem).describedAs("problem of '$href'").isNotNull()
    }

    @Test
    fun `should refuse a path of 501 characters`() {
        // given: one character over the limit of 500
        val href = "/" + "a".repeat(500)

        // when: the link is checked
        val problem = PluginUiValidator.linkProblem(href)

        // then: the link is refused
        assertThat(problem).describedAs("problem").isNotNull()
    }

    @Test
    fun `should accept a path of 500 characters`() {
        // given: exactly the limit of 500 characters
        val href = "/" + "a".repeat(499)

        // when: the link is checked
        val problem = PluginUiValidator.linkProblem(href)

        // then: the link is accepted
        assertThat(problem).describedAs("problem").isNull()
    }
}
