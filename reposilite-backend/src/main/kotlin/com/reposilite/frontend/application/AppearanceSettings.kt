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

package com.reposilite.frontend.application

import com.reposilite.configuration.shared.api.Doc
import com.reposilite.configuration.shared.api.SharedSettings

/**
 * Operator branding of the dashboard. Every field has a default that reproduces the built-in
 * appearance, so a configuration without this object renders exactly as before.
 */
@Doc(title = "Appearance", description = "Colours, logos, favicon and footer of the dashboard.")
data class AppearanceSettings(
    @get:Doc(
        title = "Seed colour",
        description = """
            Hex colour in the form #RRGGBB that the dashboard palette is derived from.
            Leave empty to keep the built-in Ingot theme.
        """,
    )
    val seedColor: String = "",
    @get:Doc(
        title = "Scheme variant",
        description = "How the palette is derived from the seed colour. Only applies when a seed colour is set.",
    )
    val schemeVariant: SchemeVariant = SchemeVariant.VIBRANT,
    @get:Doc(
        title = "Primary colour",
        description = "Hex colour in the form #RRGGBB that overrides the primary key colour. Leave empty to derive it.",
    )
    val primaryColor: String = "",
    @get:Doc(
        title = "Secondary colour",
        description = "Hex colour in the form #RRGGBB that overrides the secondary key colour. Leave empty to derive it.",
    )
    val secondaryColor: String = "",
    @get:Doc(
        title = "Tertiary colour",
        description = "Hex colour in the form #RRGGBB that overrides the tertiary key colour. Leave empty to derive it.",
    )
    val tertiaryColor: String = "",
    @get:Doc(
        title = "Error colour",
        description = "Hex colour in the form #RRGGBB that overrides the error key colour. Leave empty to derive it.",
    )
    val errorColor: String = "",
    @get:Doc(
        title = "Logo for dark theme",
        description = "Link to a logo shown in the dark theme. Leave empty to use the organisation logo.",
    )
    val logoDark: String = "",
    @get:Doc(
        title = "Favicon",
        description = "Link to the favicon of the dashboard. Leave empty to use the bundled favicon.",
    )
    val favicon: String = "",
    @get:Doc(
        title = "Footer text",
        description = "Plain text shown in the footer of the dashboard. HTML is not rendered.",
    )
    val footerText: String = "",
) : SharedSettings {

    /**
     * Describes every colour field that is set but is not a six digit hex colour.
     * Empty means unset, which is always valid.
     */
    fun violations(): List<String> =
        colourFields()
            .filter { (_, value) -> value.isNotEmpty() && !HEX_COLOUR.matches(value) }
            .map { (field, value) -> "appearance.$field must be a hex colour in the form #RRGGBB, but was '$value'" }

    private fun colourFields(): List<Pair<String, String>> = listOf(
        "seedColor" to seedColor,
        "primaryColor" to primaryColor,
        "secondaryColor" to secondaryColor,
        "tertiaryColor" to tertiaryColor,
        "errorColor" to errorColor,
    )

    private companion object {
        val HEX_COLOUR = Regex("#[0-9A-Fa-f]{6}")
    }

}
