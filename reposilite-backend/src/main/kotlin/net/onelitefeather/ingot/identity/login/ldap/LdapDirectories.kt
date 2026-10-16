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

package net.onelitefeather.ingot.identity.login.ldap

import com.reposilite.auth.LdapAuthenticator
import net.onelitefeather.ingot.identity.login.LoginProvider
import net.onelitefeather.ingot.identity.login.LoginProviderType
import net.onelitefeather.ingot.identity.login.ProviderAnnouncement
import net.onelitefeather.ingot.identity.login.external.ExternalAccountService
import net.onelitefeather.ingot.identity.storage.IdentityStore

/**
 * Chooses the directory that serves the dashboard login, on every call, so that a change of the settings applies at once.
 * `login.ldap` is used when it is enabled and complete. An enabled but incomplete `login.ldap` serves nothing: it is the
 * newer setting, so it never hands the login to the legacy one. Only while `login.ldap` is off do the legacy
 * `authentication.ldap` settings serve it, so an upgrade from 1.1 needs no change.
 */
internal class LdapDirectories(
    private val login: () -> LdapLoginSettings,
    private val legacy: LdapAuthenticator,
    private val accounts: ExternalAccountService?,
    private val identities: IdentityStore?,
) {

    /** The password provider for the dashboard login, or `null` when no directory serves it. */
    fun provider(): LoginProvider? {
        val settings = login()
        return when {
            settings.enabled -> if (accounts != null && settings.missingField() == null) LdapLoginProvider(settings, accounts) else null
            legacyActive() && identities != null -> LegacyLdapLoginProvider(legacy, identities)
            else -> null
        }
    }

    /** What the login page shows for [provider]; `null` when it is none. */
    fun announcement(): ProviderAnnouncement? {
        val provider = provider() ?: return null
        return ProviderAnnouncement(id = provider.id, type = LoginProviderType.LDAP, label = provider.label, icon = "LDAP")
    }

    /** Whether the legacy settings serve the dashboard login: `login.ldap` is off and `authentication.ldap` is on. */
    fun legacyActive(): Boolean =
        identities != null && !login().enabled && legacy.enabled()

}
