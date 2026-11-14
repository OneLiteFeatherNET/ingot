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

import net.onelitefeather.ingot.identity.login.ldap.LdapLoginSettings
import net.onelitefeather.ingot.identity.login.oidc.OidcIcon
import net.onelitefeather.ingot.identity.login.oidc.OidcProviderSettings
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/**
 * Which fields of the login domain are identity-relevant. Each identity field, changed alone, is listed by its name. A
 * field that only changes how the login looks, or how long a session lasts, is not listed. Every case builds its own settings.
 */
internal class LoginIdentityFieldsTest {

    @ParameterizedTest(name = "{0} is identity-relevant")
    @MethodSource("identityFields")
    fun `a changed identity field is listed by its name`(expected: String, change: (LoginSettings) -> LoginSettings) {
        val changed = LoginIdentityFields.changed(baseline(), change(baseline()))

        assertThat(changed).describedAs("changed fields for $expected").contains(expected)
    }

    @ParameterizedTest(name = "{0} is not identity-relevant")
    @MethodSource("unrelatedFields")
    fun `a changed presentation or session field is not listed`(change: (LoginSettings) -> LoginSettings) {
        assertThat(LoginIdentityFields.changed(baseline(), change(baseline()))).describedAs("changed fields").isEmpty()
    }

    @Test
    fun `an unchanged login domain lists nothing`() {
        assertThat(LoginIdentityFields.changed(baseline(), baseline())).isEmpty()
    }

    @Test
    fun `a provider that is added or removed is listed by its id`() {
        val added = baseline().copy(oidcProviders = baseline().oidcProviders + provider().copy(id = "second"))

        assertThat(LoginIdentityFields.changed(baseline(), added)).containsExactly("oidcProviders.second")
    }

    @Test
    fun `a renamed provider id is listed as removed and added`() {
        val renamed = baseline().copy(oidcProviders = listOf(provider().copy(id = "renamed")))

        assertThat(LoginIdentityFields.changed(baseline(), renamed)).containsExactlyInAnyOrder("oidcProviders.corp", "oidcProviders.renamed")
    }

    private fun baseline(): LoginSettings = LoginSettings(
        oidcProviders = listOf(provider()),
        ldap = LdapLoginSettings(
            enabled = true,
            url = "ldaps://ldap.example.org:636",
            bindDn = "cn=ingot,ou=services,dc=example,dc=org",
            bindPassword = "bind-secret",
            userBaseDn = "ou=people,dc=example,dc=org",
            groupBaseDn = "ou=groups,dc=example,dc=org",
            adminGroups = listOf("admins"),
        ),
    )

    private fun provider(): OidcProviderSettings = OidcProviderSettings(
        id = "corp",
        enabled = true,
        label = "Corporate",
        icon = OidcIcon.GENERIC,
        issuerUrl = "https://sso.example.org",
        clientId = "ingot",
        clientSecret = "client-secret",
        groupsClaim = "groups",
        usernameClaim = "preferred_username",
        adminGroups = listOf("ingot-admins"),
    )

    companion object {

        @JvmStatic
        fun identityFields(): Stream<Arguments> = Stream.of(
            Arguments.of("oidcProviders.corp.enabled", LoginIdentityFieldsTest.onProviderOf { copy(enabled = false) }),
            Arguments.of("oidcProviders.corp.issuerUrl", LoginIdentityFieldsTest.onProviderOf { copy(issuerUrl = "https://evil.example") }),
            Arguments.of("oidcProviders.corp.clientId", LoginIdentityFieldsTest.onProviderOf { copy(clientId = "other") }),
            Arguments.of("oidcProviders.corp.clientSecret", LoginIdentityFieldsTest.onProviderOf { copy(clientSecret = "other") }),
            Arguments.of("oidcProviders.corp.groupsClaim", LoginIdentityFieldsTest.onProviderOf { copy(groupsClaim = "roles") }),
            Arguments.of("oidcProviders.corp.usernameClaim", LoginIdentityFieldsTest.onProviderOf { copy(usernameClaim = "sub") }),
            Arguments.of("oidcProviders.corp.adminGroups", LoginIdentityFieldsTest.onProviderOf { copy(adminGroups = listOf("everyone")) }),
            Arguments.of("ldap.enabled", LoginIdentityFieldsTest.onLdapOf { copy(enabled = false) }),
            Arguments.of("ldap.url", LoginIdentityFieldsTest.onLdapOf { copy(url = "ldap://elsewhere.example:389") }),
            Arguments.of("ldap.startTls", LoginIdentityFieldsTest.onLdapOf { copy(startTls = true) }),
            Arguments.of("ldap.bindDn", LoginIdentityFieldsTest.onLdapOf { copy(bindDn = "cn=other") }),
            Arguments.of("ldap.bindPassword", LoginIdentityFieldsTest.onLdapOf { copy(bindPassword = "other") }),
            Arguments.of("ldap.userBaseDn", LoginIdentityFieldsTest.onLdapOf { copy(userBaseDn = "ou=other") }),
            Arguments.of("ldap.userFilter", LoginIdentityFieldsTest.onLdapOf { copy(userFilter = "(cn={username})") }),
            Arguments.of("ldap.usernameAttribute", LoginIdentityFieldsTest.onLdapOf { copy(usernameAttribute = "cn") }),
            Arguments.of("ldap.groupBaseDn", LoginIdentityFieldsTest.onLdapOf { copy(groupBaseDn = "ou=other") }),
            Arguments.of("ldap.groupFilter", LoginIdentityFieldsTest.onLdapOf { copy(groupFilter = "(uniqueMember={dn})") }),
            Arguments.of("ldap.groupNameAttribute", LoginIdentityFieldsTest.onLdapOf { copy(groupNameAttribute = "name") }),
            Arguments.of("ldap.adminGroups", LoginIdentityFieldsTest.onLdapOf { copy(adminGroups = listOf("everyone")) }),
            Arguments.of("publicUrl", LoginIdentityFieldsTest.onSettings { copy(publicUrl = "https://evil.example/") }),
        )

        @JvmStatic
        fun unrelatedFields(): Stream<Arguments> = Stream.of(
            Arguments.of(LoginIdentityFieldsTest.onSettings { copy(sessionLifetimeMinutes = 60) }),
            Arguments.of(LoginIdentityFieldsTest.onSettings { copy(maxFailedAttempts = 9) }),
            Arguments.of(LoginIdentityFieldsTest.onProviderOf { copy(label = "Renamed") }),
            Arguments.of(LoginIdentityFieldsTest.onProviderOf { copy(icon = OidcIcon.KEYCLOAK) }),
            Arguments.of(LoginIdentityFieldsTest.onProviderOf { copy(scopes = "openid email") }),
            Arguments.of(LoginIdentityFieldsTest.onProviderOf { copy(displayNameClaim = "nickname") }),
            Arguments.of(LoginIdentityFieldsTest.onLdapOf { copy(label = "Staff") }),
            Arguments.of(LoginIdentityFieldsTest.onLdapOf { copy(displayNameAttribute = "displayName") }),
        )

        private fun onSettings(change: LoginSettings.() -> LoginSettings): (LoginSettings) -> LoginSettings =
            { settings -> settings.change() }

        private fun onProviderOf(change: OidcProviderSettings.() -> OidcProviderSettings): (LoginSettings) -> LoginSettings =
            { settings -> settings.copy(oidcProviders = settings.oidcProviders.map { it.change() }) }

        private fun onLdapOf(change: LdapLoginSettings.() -> LdapLoginSettings): (LoginSettings) -> LoginSettings =
            { settings -> settings.copy(ldap = settings.ldap.change()) }
    }
}
