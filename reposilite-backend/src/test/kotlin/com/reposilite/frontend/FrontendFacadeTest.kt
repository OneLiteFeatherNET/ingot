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

import com.reposilite.frontend.application.FrontendSettings
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.reactive.Reference.reference

internal class FrontendFacadeTest {

    private fun facade(settings: FrontendSettings = FrontendSettings()): FrontendFacade =
        FrontendFacade(
            basePath = reference("/"),
            frontendSettings = reference(settings),
            forwardedPrefixHeader = reference("")
        )

    @Test
    fun `should expose the values the dashboard renders itself from`() {
        // given: an instance configured with a title and an organisation
        val facade = facade(FrontendSettings(title = "Team Repository", organizationWebsite = "https://example.com"))

        // when: the placeholders are collected
        val placeholders = facade.createPlaceholders(escapeForJs = false)

        // then: the dashboard can find what it needs under the placeholder tokens
        assertThat(placeholders["{{REPOSILITE.TITLE}}"]).isEqualTo("Team Repository")
        assertThat(placeholders["{{REPOSILITE.ORGANIZATION_WEBSITE}}"]).isEqualTo("https://example.com")
        assertThat(placeholders["{{REPOSILITE.BASE_PATH}}"]).isEqualTo("/")
    }

    @Test
    fun `should escape for javascript only when substituting into an asset`() {
        // given: a title containing a quote, which would terminate a JavaScript string early
        val facade = facade(FrontendSettings(title = "Bob's Repository"))

        // when: the placeholders are collected for both consumers
        val forAssets = facade.createPlaceholders(escapeForJs = true)
        val forApi = facade.createPlaceholders(escapeForJs = false)

        // then: the asset copy is escaped, and the API copy is not, because JSON encodes itself
        assertThat(forAssets["{{REPOSILITE.TITLE}}"]).isEqualTo("Bob\\'s Repository")
        assertThat(forApi["{{REPOSILITE.TITLE}}"]).isEqualTo("Bob's Repository")
    }

    @Test
    fun `should carry placeholders registered by plugins`() {
        // given: a plugin that contributes its own placeholder, as the javadoc plugin does
        val facade = facade()
        facade.registerPlaceholder("{{REPOSILITE.JAVADOC_SUFFIXES}}", reference("-javadoc.jar"))

        // when: the placeholders are collected
        val placeholders = facade.createPlaceholders(escapeForJs = false)

        // then: the contribution reaches a dashboard that is not served by this instance
        assertThat(placeholders["{{REPOSILITE.JAVADOC_SUFFIXES}}"]).isEqualTo("-javadoc.jar")
    }

    @Test
    fun `should not expose the url encoded spellings through the api`() {
        // given: a default instance
        val facade = facade()

        // when: the placeholders are collected
        val placeholders = facade.createPlaceholders(escapeForJs = false)

        // then: only the plain tokens are present. The encoded ones exist for substitution
        // inside query strings and would be noise for a consumer reading JSON.
        assertThat(placeholders.keys).allMatch { it.startsWith("{{") && it.endsWith("}}") }
    }

}
