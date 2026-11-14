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

import com.reposilite.auth.application.AuthenticationSettings
import com.reposilite.auth.application.BruteForceProtectionSettings
import com.reposilite.auth.application.LdapSettings
import com.reposilite.token.AccessTokenType.TEMPORARY
import net.onelitefeather.ingot.identity.login.AuthenticationIdentityFields
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/**
 * Which fields of the legacy `authentication` domain decide who may authenticate through its LDAP directory. Each identity
 * field, changed alone, is listed by its path. The brute force protection is not identity-relevant and is not listed.
 */
internal class AuthenticationIdentityFieldsTest {

    @ParameterizedTest(name = "{0} is identity-relevant")
    @MethodSource("identityFields")
    fun `a changed LDAP field of the authentication domain is listed by its path`(expected: String, change: (LdapSettings) -> LdapSettings) {
        val changed = AuthenticationIdentityFields.changed(baseline(), baseline().copy(ldap = change(baseline().ldap)))

        assertThat(changed).describedAs("changed fields for $expected").containsExactly(expected)
    }

    @Test
    fun `a changed brute force setting is not listed`() {
        val changed = AuthenticationIdentityFields.changed(
            baseline(),
            baseline().copy(bruteForceProtection = BruteForceProtectionSettings(enabled = true, maxAttempts = 3, banDurationSeconds = 60)),
        )

        assertThat(changed).describedAs("changed fields").isEmpty()
    }

    @Test
    fun `an unchanged authentication domain lists nothing`() {
        assertThat(AuthenticationIdentityFields.changed(baseline(), baseline())).isEmpty()
    }

    private fun baseline(): AuthenticationSettings = AuthenticationSettings(
        ldap = LdapSettings(
            enabled = false,
            hostname = "ldap.example.org",
            port = 636,
            baseDn = "dc=example,dc=org",
            searchUserDn = "cn=search,dc=example,dc=org",
            searchUserPassword = "search-secret",
            typeAttribute = "person",
            userAttribute = "cn",
            userFilter = "(objectClass=person)",
            userType = TEMPORARY,
        ),
    )

    companion object {

        @JvmStatic
        fun identityFields(): Stream<Arguments> = Stream.of(
            Arguments.of("ldap.enabled", { ldap: LdapSettings -> ldap.copy(enabled = true) }),
            Arguments.of("ldap.ssl", { ldap: LdapSettings -> ldap.copy(ssl = true) }),
            Arguments.of("ldap.hostname", { ldap: LdapSettings -> ldap.copy(hostname = "ldap.evil.example") }),
            Arguments.of("ldap.port", { ldap: LdapSettings -> ldap.copy(port = 1389) }),
            Arguments.of("ldap.baseDn", { ldap: LdapSettings -> ldap.copy(baseDn = "dc=evil") }),
            Arguments.of("ldap.searchUserDn", { ldap: LdapSettings -> ldap.copy(searchUserDn = "cn=other") }),
            Arguments.of("ldap.searchUserPassword", { ldap: LdapSettings -> ldap.copy(searchUserPassword = "other") }),
            Arguments.of("ldap.typeAttribute", { ldap: LdapSettings -> ldap.copy(typeAttribute = "group") }),
            Arguments.of("ldap.userAttribute", { ldap: LdapSettings -> ldap.copy(userAttribute = "uid") }),
            Arguments.of("ldap.userFilter", { ldap: LdapSettings -> ldap.copy(userFilter = "(uid={0})") }),
            Arguments.of("ldap.userType", { ldap: LdapSettings -> ldap.copy(userType = com.reposilite.token.AccessTokenType.PERSISTENT) }),
        )
    }
}
