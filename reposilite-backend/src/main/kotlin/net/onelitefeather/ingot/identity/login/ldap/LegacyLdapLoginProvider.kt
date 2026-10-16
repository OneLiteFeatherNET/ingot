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
import com.reposilite.auth.api.Credentials
import net.onelitefeather.ingot.identity.login.LoginOutcome
import net.onelitefeather.ingot.identity.login.LoginProvider
import net.onelitefeather.ingot.identity.login.LoginProviderType
import net.onelitefeather.ingot.identity.login.external.ExternalRefusal
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.storage.IdentityStore

/**
 * The dashboard login with the `authentication.ldap` settings, as 1.1.0 accepted it: the legacy [LdapAuthenticator] finds
 * the user, binds as the user to check the password and gets or creates the access token named after the directory user.
 * The login belongs to the machine principal that owns that token, which is the identity the Maven path uses, so the user
 * has one principal and the same rights whichever way they sign in. No human principal is created.
 *
 * The authenticator must be built with the password check on. The provider is only used while `login.ldap` is off, see
 * [LdapDirectories].
 */
internal class LegacyLdapLoginProvider(
    private val authenticator: LdapAuthenticator,
    private val identities: IdentityStore,
) : LoginProvider {

    override val id: String = LdapLoginProvider.ID
    override val type: LoginProviderType = LoginProviderType.LDAP
    override val label: String = "LDAP"

    override fun authenticate(username: String, password: String): LoginOutcome {
        // An empty password is an unauthenticated bind in LDAP, which many servers accept. It is never a login, and it is
        // refused before the directory is asked anything.
        if (username.isBlank() || password.isEmpty()) {
            return LoginOutcome.Rejected
        }
        val result = try {
            authenticator.authenticate(Credentials(ip = "", name = username, secret = password))
        } catch (_: IllegalStateException) {
            // The token of the directory user would need a machine principal, and the name belongs to a human already.
            return LoginOutcome.Refused(ExternalRefusal.USERNAME_TAKEN)
        }
        // The authenticator answers a wrong password and an unreachable directory alike, so both are a rejection here.
        if (result.isErr) {
            return LoginOutcome.Rejected
        }
        val name = result.get().name

        // A token that the identity tables do not know (a temporary one) has no principal and no session.
        val principal = identities.findToken(name)?.let { identities.findPrincipalById(it.principalId) }
        return when {
            principal == null || principal.kind != PrincipalKind.MACHINE -> LoginOutcome.Refused(ExternalRefusal.USERNAME_TAKEN)
            principal.disabled -> LoginOutcome.Disabled
            else -> LoginOutcome.Succeeded(principal, mustChangePassword = false)
        }
    }

}
