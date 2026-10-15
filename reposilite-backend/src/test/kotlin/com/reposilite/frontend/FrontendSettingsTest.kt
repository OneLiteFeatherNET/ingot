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

import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.frontend.application.AppearanceSettings
import com.reposilite.frontend.application.FrontendSettings
import com.reposilite.frontend.application.SchemeVariant.FIDELITY
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

internal class FrontendSettingsTest {

    @Test
    fun `should load a frontend block written before appearance existed with default appearance`() {
        // given: a frontend block as it was written before the appearance object was introduced
        val json = """
            {
              "id": "ingot-repository",
              "title": "Ingot Repository",
              "description": "Public Maven repository hosted through Ingot",
              "organizationWebsite": "https://github.com/OneLiteFeatherNET",
              "organizationLogo": "https://avatars.githubusercontent.com/u/103827826",
              "icpLicense": ""
            }
        """.trimIndent()

        // when: the block is read
        val settings = DEFAULT_OBJECT_MAPPER.readValue(json, FrontendSettings::class.java)

        // then: the appearance takes its defaults and the existing values are kept
        assertThat(settings.appearance).isEqualTo(AppearanceSettings())
        assertThat(settings.title).isEqualTo("Ingot Repository")
    }

    @Test
    fun `should load an empty frontend block with every default`() {
        // when: an empty block is read
        val settings = DEFAULT_OBJECT_MAPPER.readValue("{}", FrontendSettings::class.java)

        // then: the result is the default configuration
        assertThat(settings).isEqualTo(FrontendSettings())
    }

    @Test
    fun `should round trip every appearance field`() {
        // given: an appearance with every field set
        val settings = FrontendSettings(
            appearance = AppearanceSettings(
                seedColor = "#1A2B3C",
                schemeVariant = FIDELITY,
                primaryColor = "#112233",
                secondaryColor = "#223344",
                tertiaryColor = "#334455",
                errorColor = "#B00020",
                logoDark = "https://example.com/logo-dark.png",
                favicon = "https://example.com/favicon.ico",
                footerText = "Hosted by Team & Friends",
            )
        )

        // when: the settings are written and read back
        val json = DEFAULT_OBJECT_MAPPER.writeValueAsString(settings)
        val restored = DEFAULT_OBJECT_MAPPER.readValue(json, FrontendSettings::class.java)

        // then: nothing is lost on the way
        assertThat(restored).isEqualTo(settings)
    }

    @Test
    fun `should write the appearance under the field names of the contract`() {
        // when: the default settings are written
        val node = DEFAULT_OBJECT_MAPPER.readTree(DEFAULT_OBJECT_MAPPER.writeValueAsString(FrontendSettings()))

        // then: the appearance object carries every field under the name the frontend reads
        assertThat(node.at("/appearance").fieldNames().asSequence().toList()).containsExactlyInAnyOrder(
            "seedColor", "schemeVariant", "primaryColor", "secondaryColor", "tertiaryColor",
            "errorColor", "logoDark", "favicon", "footerText"
        )
        assertThat(node.at("/appearance/schemeVariant").asText()).isEqualTo("VIBRANT")
    }

    @Test
    fun `should accept default appearance without violations`() {
        assertThat(FrontendSettings().violations()).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(strings = ["#1A2B3C", "#abcdef", "#000000"])
    fun `should accept six digit hex colours`(colour: String) {
        // when: the colour is used as the seed colour
        val violations = FrontendSettings(appearance = AppearanceSettings(seedColor = colour)).violations()

        // then: it is accepted
        assertThat(violations).describedAs("violations for '$colour'").isEmpty()
    }

    @ParameterizedTest
    @ValueSource(strings = ["red", "#FFF", "1A2B3C", "#1A2B3C4", "#GG0000", " #1A2B3C"])
    fun `should reject a seed colour that is not six digit hex`(colour: String) {
        // when: the colour is used as the seed colour
        val violations = FrontendSettings(appearance = AppearanceSettings(seedColor = colour)).violations()

        // then: exactly one violation names the field and the expected format
        assertThat(violations).describedAs("violations for '$colour'").singleElement().asString()
            .contains("appearance.seedColor")
            .contains("#RRGGBB")
    }

    @Test
    fun `should report every malformed colour field`() {
        // when: two colour overrides are malformed
        val violations = FrontendSettings(
            appearance = AppearanceSettings(primaryColor = "blue", errorColor = "#12")
        ).violations()

        // then: each malformed field is reported, and the well-formed ones are not
        assertThat(violations).hasSize(2)
        assertThat(violations.joinToString("\n")).contains("appearance.primaryColor").contains("appearance.errorColor")
    }

    @Test
    fun `should not validate free text and urls as colours`() {
        // when: text and links that are not colours are set
        val violations = FrontendSettings(
            appearance = AppearanceSettings(footerText = "Plain text", logoDark = "https://example.com/logo.svg", favicon = "/favicon.ico")
        ).violations()

        // then: they are accepted as they are
        assertThat(violations).isEmpty()
    }

}
