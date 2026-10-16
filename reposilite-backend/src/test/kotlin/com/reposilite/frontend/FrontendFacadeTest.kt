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

import com.reposilite.frontend.application.AppearanceSettings
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
    fun `should fall back to the bundled favicon when none is configured`() {
        // given: an instance without a configured favicon
        val facade = facade()

        // when: the placeholders for the bundled assets are collected
        val placeholders = facade.createAssetPlaceholders()

        // then: the favicon placeholder resolves to the file bundled next to the dashboard
        assertThat(placeholders["{{REPOSILITE.FAVICON}}"]).isEqualTo("favicon.png")
    }

    @Test
    fun `should point the favicon placeholder at the configured favicon`() {
        // given: an instance with an operator supplied favicon
        val facade = facade(FrontendSettings(appearance = AppearanceSettings(favicon = "https://example.com/icon.png")))

        // when: the placeholders for the bundled assets are collected
        val placeholders = facade.createAssetPlaceholders()

        // then: the configured favicon replaces the bundled one
        assertThat(placeholders["{{REPOSILITE.FAVICON}}"]).isEqualTo("https://example.com/icon.png")
    }

    @Test
    fun `should keep the favicon placeholder out of the api response`() {
        // given: an instance with an operator supplied favicon
        val facade = facade(FrontendSettings(appearance = AppearanceSettings(favicon = "https://example.com/icon.png")))

        // when: the placeholders for the api are collected
        val placeholders = facade.createPlaceholders(escapeForJs = false)

        // then: the placeholder map of the api is unchanged by the favicon
        assertThat(placeholders.keys).doesNotContain("{{REPOSILITE.FAVICON}}")
    }

    @Test
    fun `should answer the settings request with the identity and the appearance`() {
        // given: an instance with a title and an appearance
        val facade = facade(
            FrontendSettings(
                title = "Team Repository",
                appearance = AppearanceSettings(seedColor = "#1A2B3C", footerText = "Hosted by Team"),
            )
        )

        // when: the settings response is built
        val response = facade.createSettingsResponse()

        // then: the frontend gets the values as plain fields, and the appearance under its own name
        assertThat(response.title).isEqualTo("Team Repository")
        assertThat(response.appearance.seedColor).isEqualTo("#1A2B3C")
        assertThat(response.appearance.footerText).isEqualTo("Hosted by Team")
        assertThat(response.placeholders["{{REPOSILITE.TITLE}}"]).isEqualTo("Team Repository")
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
