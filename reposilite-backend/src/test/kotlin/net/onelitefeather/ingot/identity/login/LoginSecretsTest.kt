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

package net.onelitefeather.ingot.identity.login

import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.configuration.shared.SettingsSecrets
import net.onelitefeather.ingot.identity.login.ldap.LdapLoginSettings
import net.onelitefeather.ingot.identity.login.oidc.OidcIcon
import net.onelitefeather.ingot.identity.login.oidc.OidcProviderSettings
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The client secret of every OpenID Connect provider and the LDAP bind password are masked by the settings API. */
internal class LoginSecretsTest {

    private val settings = LoginSettings(
        oidcProviders = listOf(
            OidcProviderSettings(id = "corp", enabled = true, icon = OidcIcon.KEYCLOAK, issuerUrl = "https://sso.example.org", clientId = "ingot", clientSecret = "oidc-real-secret"),
        ),
        ldap = LdapLoginSettings(enabled = true, url = "ldaps://ldap.example.org", bindPassword = "ldap-real-bind-password"),
    )

    @Test
    fun `the oidc client secret and the ldap bind password are masked`() {
        val masked = SettingsSecrets.mask(LoginSettings::class.java, DEFAULT_OBJECT_MAPPER.valueToTree(settings))
        val text = masked.toString()

        assertThat(text).doesNotContain("oidc-real-secret").doesNotContain("ldap-real-bind-password")
        assertThat(text).contains("********")
    }

    @Test
    fun `a masked list entry restores its stored secret by id`() {
        val incoming = DEFAULT_OBJECT_MAPPER.valueToTree<com.fasterxml.jackson.databind.JsonNode>(settings)
            .also { (it.get("oidcProviders").get(0) as com.fasterxml.jackson.databind.node.ObjectNode).put("clientSecret", "********") }
        val stored = DEFAULT_OBJECT_MAPPER.valueToTree<com.fasterxml.jackson.databind.JsonNode>(settings)

        val restored = SettingsSecrets.restore(LoginSettings::class.java, incoming, stored)

        assertThat(restored.isOk).isTrue
        assertThat(restored.get().get("oidcProviders").get(0).get("clientSecret").asText()).isEqualTo("oidc-real-secret")
    }
}
