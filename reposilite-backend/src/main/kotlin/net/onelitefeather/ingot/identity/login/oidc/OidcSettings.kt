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

package net.onelitefeather.ingot.identity.login.oidc

import com.reposilite.configuration.shared.api.Doc
import com.reposilite.configuration.shared.api.Sensitive
import com.reposilite.configuration.shared.api.SettingsKey
import io.javalin.openapi.JsonSchema
import net.onelitefeather.ingot.identity.login.LoginSettings
import panda.std.reactive.Reference

/** The provider the login page shows the button for. GITHUB selects GitHub's own OAuth endpoints, see [isGitHub]. */
enum class OidcIcon { GENERIC, GITHUB, GITLAB, GOOGLE, MICROSOFT, KEYCLOAK, AUTHENTIK, OKTA, AUTH0, GITEA }

/**
 * One external login provider of the `login` domain. The defaults keep it disabled. Entries are identified by [id], which
 * is a slug and unique across the list; the id is part of every group reference (`oidc:<id>:<group>`), so it must not change.
 */
@JsonSchema(requireNonNulls = false)
data class OidcProviderSettings(
    @get:SettingsKey
    @get:Doc(title = "Id", description = "Slug of the provider, unique in the list. It is part of the group references, so keep it stable")
    val id: String = "",
    @get:Doc(title = "Enabled", description = "Announces the provider on the login page and serves its login routes")
    val enabled: Boolean = false,
    @get:Doc(title = "Label", description = "Text of the login button")
    val label: String = "",
    @get:Doc(title = "Icon", description = "Logo of the login button. GITHUB uses GitHub's OAuth endpoints when the issuer is empty or https://github.com")
    val icon: OidcIcon = OidcIcon.GENERIC,
    @get:Doc(title = "Issuer URL", description = "Base URL of the provider. Discovery is read from its /.well-known/openid-configuration")
    val issuerUrl: String = "",
    @get:Doc(title = "Client Id", description = "Client id registered with the provider")
    val clientId: String = "",
    @get:Sensitive
    @get:Doc(title = "Client Secret", description = "Client secret registered with the provider. Sent with HTTP Basic authentication")
    val clientSecret: String = "",
    @get:Doc(title = "Scopes", description = "Scopes requested. For GitHub the default becomes read:user user:email read:org")
    val scopes: String = "openid profile email",
    @get:Doc(title = "Username Claim", description = "Claim that gives the user name of a new user")
    val usernameClaim: String = "preferred_username",
    @get:Doc(title = "Display Name Claim", description = "Claim that gives the display name")
    val displayNameClaim: String = "name",
    @get:Doc(title = "Email Claim", description = "Claim that gives the email address, used for the domain rule")
    val emailClaim: String = "email",
    @get:Doc(title = "Groups Claim", description = "Claim that lists the groups of the user, as an array or a single string")
    val groupsClaim: String = "groups",
    @get:Doc(title = "Allowed Email Domains", description = "When not empty, the email must be in one of these domains. Checked at every login")
    val allowedEmailDomains: List<String> = emptyList(),
    @get:Doc(title = "Automatically Create Users", description = "Creates a user at the first login of a subject. When off, only linked users can log in")
    val autoCreateUsers: Boolean = true,
    @get:Doc(title = "Admin Groups", description = "Groups whose members become instance administrators through a synchronised team")
    val adminGroups: List<String> = emptyList(),
) {

    /**
     * GitHub is OAuth 2 with its own endpoints: no issuer, or the issuer GitHub publishes. A function rather than a property,
     * so that the settings serializer does not write it into the stored document.
     */
    fun usesGitHubEndpoints(): Boolean =
        icon == OidcIcon.GITHUB && (issuerUrl.isBlank() || issuerUrl.trimEnd('/') == GITHUB_ISSUER)

    /** The name of the first setting that the provider needs and does not have, or `null` when it is usable. */
    fun missingField(): String? = when {
        usesGitHubEndpoints() -> when {
            clientId.isBlank() -> "clientId"
            clientSecret.isBlank() -> "clientSecret"
            else -> null
        }
        issuerUrl.isBlank() -> "issuerUrl"
        clientId.isBlank() -> "clientId"
        else -> null
    }

    companion object {
        const val GITHUB_ISSUER = "https://github.com"
        val SLUG = Regex("[a-z0-9-]{1,64}")
        const val GITHUB_DEFAULT_SCOPES = "read:user user:email read:org"
        const val OIDC_DEFAULT_SCOPES = "openid profile email"
    }

}

/**
 * The enabled external providers of the login settings, read on every call so that a change applies at once. Entries with
 * an id that is not a slug are ignored, and so is every entry after the first one with the same id.
 */
class OidcProviderCatalog internal constructor(private val settings: Reference<LoginSettings>) {

    /** Enabled providers in the order of the settings. */
    fun enabled(): List<OidcProviderSettings> =
        settings.get().oidcProviders
            .filter { it.id.matches(OidcProviderSettings.SLUG) }
            .distinctBy { it.id }
            .filter { it.enabled }

    /** The enabled provider with [id], or `null` when there is none. */
    fun find(id: String): OidcProviderSettings? =
        enabled().firstOrNull { it.id == id }

    /** The enabled providers that are usable: the ones that are announced on the login page. */
    fun announced(): List<OidcProviderSettings> =
        enabled().filter { it.missingField() == null }

}
