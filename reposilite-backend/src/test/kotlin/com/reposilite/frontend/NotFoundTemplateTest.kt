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

package com.reposilite.frontend

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class NotFoundTemplateTest {

    @Test
    fun `should not let a request path break out of the href attribute`() {
        // when: the requested path carries a markup injection
        val page = NotFoundTemplate.createNotFoundPage("/", "/\"><script>alert(1)</script>", "")

        // then: the quote and the tags are percent-encoded inside the attribute and the text
        assertThat(page)
            .`as`("href must keep the injected quote and tags encoded")
            .contains("href=\"/#/%22%3E%3Cscript%3Ealert(1)%3C/script%3E\"")
            .`as`("the raw injection must not reach the markup")
            .doesNotContain("\"><script>")
            .doesNotContain("<script>")
    }

    @Test
    fun `should escape quotes and tags in a path that is shown as link text`() {
        // when: the requested path carries an apostrophe and an image tag with an event handler
        val page = NotFoundTemplate.createNotFoundPage("/", "/releases/o'brien/<img onerror=alert(1)>", "")

        // then: the visible text and the href show the escaped value, no raw tag reaches the markup
        assertThat(page)
            .`as`("the raw tag must not reach the markup")
            .doesNotContain("<img")
            .`as`("apostrophe must be escaped in the href")
            .contains("href=\"/#/releases/o&#39;brien/%3Cimg%20onerror=alert(1)%3E\"")
            .`as`("link text must carry the same escaped value")
            .contains(">/#/releases/o&#39;brien/%3Cimg%20onerror=alert(1)%3E</a>")
    }

    @Test
    fun `should escape the base path in the href`() {
        // when: the configured base path contains a quote
        val page = NotFoundTemplate.createNotFoundPage("/ingot\"x", "/releases", "")

        // then: the base path cannot end the attribute
        assertThat(page)
            .`as`("base path must be percent-encoded in the href")
            .contains("href=\"/ingot%22x/#/releases\"")
            .doesNotContain("href=\"/ingot\"")
    }

    @Test
    fun `should keep a normal maven path as the same dashboard link`() {
        // when: the requested path is a regular repository path with dots and digits
        val page = NotFoundTemplate.createNotFoundPage("/", "/releases/com/example", "")

        // then: the link keeps the fragment route without any escaping
        assertThat(page)
            .`as`("href must stay the fragment route of the path")
            .contains("href=\"/#/releases/com/example\"")
            .`as`("visible text must show the same link")
            .contains(">/#/releases/com/example</a>")
    }

    @Test
    fun `should keep a percent encoded request path encoded exactly once`() {
        // when: the servlet container hands over the raw, percent-encoded request URI
        val page = NotFoundTemplate.createNotFoundPage("/ingot", "/releases/my%20lib/1.0", "")

        // then: the link keeps the single encoding of the space under the base path
        assertThat(page)
            .`as`("space must stay encoded exactly once")
            .contains("href=\"/ingot/#/releases/my%20lib/1.0\"")
            .doesNotContain("%2520")
    }
}
