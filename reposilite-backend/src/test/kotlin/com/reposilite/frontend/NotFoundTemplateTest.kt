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
}
