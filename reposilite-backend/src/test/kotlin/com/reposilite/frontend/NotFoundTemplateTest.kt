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
    fun `should link the dashboard to the path based browse route under the root`() {
        // when: the page is built for a server mounted at the root
        val page = NotFoundTemplate.createNotFoundPage("/", "/releases/net/missing", "")

        // then: the link is a path URL and no hash route is left
        assertThat(page)
            .contains("href=\"/browse/releases/net/missing\"")
            .doesNotContain("#/")
    }

    @Test
    fun `should link to the browse route when no base path is configured`() {
        // when: the page is built with an empty base path
        val page = NotFoundTemplate.createNotFoundPage("", "/releases/missing", "")

        // then: the link still starts at the host root
        assertThat(page).contains("href=\"/browse/releases/missing\"")
    }

    @Test
    fun `should keep the mount point in front of the browse route`() {
        // when: the server is mounted below a prefix
        val page = NotFoundTemplate.createNotFoundPage("/ingot/", "/releases/net/missing", "")

        // then: the browse route is reached below that prefix
        assertThat(page).contains("href=\"/ingot/browse/releases/net/missing\"")
    }

    @Test
    fun `should not let a request path break out of the href attribute`() {
        // when: the requested path carries a markup injection
        val page = NotFoundTemplate.createNotFoundPage("/", "/releases/\"><script>alert(1)</script>", "")

        // then: the quote and the tags are percent-encoded inside the attribute
        assertThat(page)
            .`as`("href must keep the injected quote and tags encoded")
            .contains("href=\"/browse/releases/%22%3E%3Cscript%3Ealert(1)%3C/script%3E\"")
            .`as`("the raw injection must not reach the markup")
            .doesNotContain("\"><script>")
            .doesNotContain("<script>alert(1)")
            .doesNotContain("</script>")
    }

    @Test
    fun `should escape the displayed request path as text`() {
        // when: the requested path carries a quote, an ampersand and a tag
        val page = NotFoundTemplate.createNotFoundPage("/", "/releases/o'brien&co<x>", "")

        // then: the visible link text shows the escaped path, not raw characters
        assertThat(page)
            .`as`("text node must show the escaped path")
            .contains(">/browse/releases/o&#39;brien&amp;co%3Cx%3E</a>")
            .`as`("href must carry the same escaped value")
            .contains("href=\"/browse/releases/o&#39;brien&amp;co%3Cx%3E\"")
    }

    @Test
    fun `should keep normal maven coordinates readable in the link`() {
        // when: the requested path is a regular repository path with dots and digits
        val page = NotFoundTemplate.createNotFoundPage("/", "/releases/com/example/1.0", "")

        // then: no characters are escaped in the link
        assertThat(page).contains("href=\"/browse/releases/com/example/1.0\"")
    }

    @Test
    fun `should not double encode a request path that is already percent encoded`() {
        // when: the servlet container hands over the raw, percent-encoded request URI
        val page = NotFoundTemplate.createNotFoundPage("/", "/releases/my%20lib/1.0", "")

        // then: the link keeps the single encoding of the space
        assertThat(page)
            .`as`("space must stay encoded exactly once")
            .contains("href=\"/browse/releases/my%20lib/1.0\"")
            .doesNotContain("%2520")
    }

    @Test
    fun `should escape the base path in the href`() {
        // when: the configured base path contains a quote
        val page = NotFoundTemplate.createNotFoundPage("/ingot\"x", "/releases", "")

        // then: the base path cannot end the attribute
        assertThat(page)
            .`as`("base path must be percent-encoded in the href")
            .contains("href=\"/ingot%22x/browse/releases\"")
            .doesNotContain("href=\"/ingot\"")
    }

    @Test
    fun `should escape tags and apostrophes of a path shown as link text`() {
        // when: the requested path carries an apostrophe and an image tag with an event handler
        val page = NotFoundTemplate.createNotFoundPage("/", "/releases/o'brien/<img onerror=alert(1)>", "")

        // then: the visible text and the href show the escaped value, no raw tag reaches the markup
        assertThat(page)
            .`as`("the raw tag must not reach the markup")
            .doesNotContain("<img")
            .`as`("apostrophe must be escaped in the href")
            .contains("href=\"/browse/releases/o&#39;brien/%3Cimg%20onerror=alert(1)%3E\"")
            .`as`("link text must carry the same escaped value")
            .contains(">/browse/releases/o&#39;brien/%3Cimg%20onerror=alert(1)%3E</a>")
    }

    @Test
    fun `should keep a percent encoded request path encoded exactly once below a mount point`() {
        // when: the servlet container hands over the raw, percent-encoded request URI
        val page = NotFoundTemplate.createNotFoundPage("/ingot", "/releases/my%20lib/1.0", "")

        // then: the link keeps the single encoding of the space under the base path
        assertThat(page)
            .`as`("space must stay encoded exactly once")
            .contains("href=\"/ingot/browse/releases/my%20lib/1.0\"")
            .doesNotContain("%2520")
    }
}
