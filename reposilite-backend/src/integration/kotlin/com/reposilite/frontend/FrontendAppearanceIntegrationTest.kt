@file:Suppress("FunctionName")

package com.reposilite.frontend

import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteSpecification
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.frontend.application.AppearanceSettings
import com.reposilite.frontend.application.FrontendSettings
import io.javalin.http.HttpStatus.OK
import kong.unirest.core.Unirest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * The dashboard reads its appearance from the public settings endpoint, so a copy served from a
 * separate container gets the same values as the bundled one. These tests pin down both channels.
 */
internal abstract class FrontendAppearanceSpecification : ReposiliteSpecification() {

    protected fun settingsResponse() = Unirest.get("$base/api/frontend/settings")
        .asString()
        .also { assertThat(it.status).describedAs("status of the settings endpoint").isEqualTo(OK.code) }
        .body
        .let { DEFAULT_OBJECT_MAPPER.readTree(it) }

}

@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class DefaultFrontendAppearanceIntegrationTest : FrontendAppearanceSpecification() {

    @Test
    fun `should answer the settings endpoint without credentials with the default appearance`() {
        // when: the settings are requested without any token
        val settings = settingsResponse()

        // then: the appearance is present with its defaults, next to the identity the frontend needs
        assertThat(settings.at("/appearance/seedColor").asText()).isEmpty()
        assertThat(settings.at("/appearance/schemeVariant").asText()).isEqualTo("VIBRANT")
        assertThat(settings.at("/appearance/favicon").asText()).isEmpty()
        assertThat(settings.at("/title").asText()).isEqualTo("Ingot Repository")
        assertThat(settings.at("/placeholders/{{REPOSILITE.TITLE}}").asText()).isEqualTo("Ingot Repository")
    }

    @Test
    fun `should link the bundled favicon in the dashboard when none is configured`() {
        // when: the dashboard is requested
        val body = Unirest.get("$base/").asString().body

        // then: the favicon is the bundled one, and no token is left in the page
        assertThat(body).describedAs("dashboard favicon link").contains("favicon.png")
        assertThat(body).describedAs("placeholders left unresolved").doesNotContain("{{REPOSILITE.")
    }

}

@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class ConfiguredFrontendAppearanceIntegrationTest : FrontendAppearanceSpecification() {

    override fun overrideSharedConfiguration(sharedConfigurationFacade: SharedConfigurationFacade) {
        sharedConfigurationFacade.getDomainSettings<FrontendSettings>().update { old ->
            old.copy(
                appearance = AppearanceSettings(
                    seedColor = "#1A2B3C",
                    logoDark = "https://example.com/logo-dark.png",
                    favicon = "https://example.com/icon.png",
                    footerText = "Hosted by Team & Friends",
                )
            )
        }
    }

    @Test
    fun `should link the configured favicon in the dashboard`() {
        // when: the dashboard is requested
        val body = Unirest.get("$base/").asString().body

        // then: the operator's favicon replaces the bundled one
        assertThat(body).describedAs("dashboard favicon link").contains("https://example.com/icon.png")
        assertThat(body).describedAs("placeholders left unresolved").doesNotContain("{{REPOSILITE.")
    }

    @Test
    fun `should answer the settings endpoint with the configured appearance`() {
        // when: the settings are requested
        val settings = settingsResponse()

        // then: every appearance field is delivered under the name the frontend reads
        assertThat(settings.at("/appearance/seedColor").asText()).isEqualTo("#1A2B3C")
        assertThat(settings.at("/appearance/logoDark").asText()).isEqualTo("https://example.com/logo-dark.png")
        assertThat(settings.at("/appearance/favicon").asText()).isEqualTo("https://example.com/icon.png")
        assertThat(settings.at("/appearance/footerText").asText()).isEqualTo("Hosted by Team & Friends")
    }

}
