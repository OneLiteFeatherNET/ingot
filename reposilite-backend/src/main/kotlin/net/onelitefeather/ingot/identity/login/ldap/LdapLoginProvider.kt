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

import net.onelitefeather.ingot.identity.login.LoginOutcome
import net.onelitefeather.ingot.identity.login.LoginProvider
import net.onelitefeather.ingot.identity.login.LoginProviderType
import net.onelitefeather.ingot.identity.login.external.ExternalAccountService
import net.onelitefeather.ingot.identity.login.external.ExternalIdentity
import net.onelitefeather.ingot.identity.login.external.ExternalOutcome
import net.onelitefeather.ingot.identity.login.external.ExternalRefusal
import java.net.URI
import javax.naming.AuthenticationException
import javax.naming.directory.DirContext

/**
 * A password login against a directory. The service bind finds the user with an escaped filter; exactly one entry must match.
 * The user then binds with the password, so the directory itself checks it. The result goes through the same
 * [ExternalAccountService] as the redirect providers, and the failure limiter of [LoginService] wraps it.
 *
 * A snapshot of the settings is taken per request, so changes apply at once and a disabled provider does not exist.
 */
internal class LdapLoginProvider(
    private val settings: LdapLoginSettings,
    private val accounts: ExternalAccountService,
) : LoginProvider {

    override val id: String = ID
    override val type: LoginProviderType = LoginProviderType.LDAP
    override val label: String = settings.label.ifBlank { "LDAP" }

    private val directory = LdapDirectory(settings.url, settings.startTls)

    /** The directory identifies the provider in external references, so it is the host of the URL. */
    private val providerReference: String =
        "ldap:" + (runCatching { URI.create(settings.url.trim()).host }.getOrNull() ?: "directory")

    override fun authenticate(username: String, password: String): LoginOutcome {
        // An empty password is an unauthenticated bind in LDAP, which many servers accept. It is never a login, and it is
        // refused before the directory is asked anything.
        if (username.isBlank() || password.isEmpty()) {
            return LoginOutcome.Rejected
        }
        return try {
            authenticateAt(username.trim(), password)
        } catch (_: DirectoryUnavailableException) {
            LoginOutcome.Unavailable
        } catch (_: AuthenticationException) {
            // The service bind itself was refused: the configured bind account is wrong, which is not the user's failure.
            LoginOutcome.Unavailable
        }
    }

    private fun authenticateAt(username: String, password: String): LoginOutcome {
        val service = openService()
        try {
            val matches = directory.search(
                context = service,
                base = settings.userBaseDn,
                filter = LdapFilters.fill(settings.userFilter, mapOf("username" to username)),
                attributes = listOf(settings.usernameAttribute, settings.displayNameAttribute, settings.emailAttribute),
            )
            if (matches.size != 1) {
                return LoginOutcome.Rejected
            }
            val entry = matches.single()
            if (!directory.bindSucceeds(entry.dn, password)) {
                return LoginOutcome.Rejected
            }
            val identity = ExternalIdentity(
                provider = providerReference,
                subject = entry.dn,
                username = entry.first(settings.usernameAttribute) ?: username,
                displayName = entry.first(settings.displayNameAttribute),
                email = entry.first(settings.emailAttribute),
                autoCreateUsers = settings.autoCreateUsers,
                allowedEmailDomains = emptyList(),
            )
            return when (val outcome = accounts.resolve(identity)) {
                is ExternalOutcome.Succeeded -> LoginOutcome.Succeeded(outcome.principal, mustChangePassword = false)
                is ExternalOutcome.Refused -> when (outcome.reason) {
                    ExternalRefusal.USER_DISABLED -> LoginOutcome.Disabled
                    else -> LoginOutcome.Refused(outcome.reason)
                }
            }
        } finally {
            service.close()
        }
    }

    /** The service connection: the configured bind account, or anonymous when no bind DN is set. */
    private fun openService(): DirContext {
        val bindDn = settings.bindDn.trim().takeIf { it.isNotEmpty() }
        return try {
            directory.open(bindDn, settings.bindPassword.takeIf { bindDn != null })
        } catch (exception: AuthenticationException) {
            throw DirectoryUnavailableException("the service bind was refused", exception)
        }
    }

    companion object {
        const val ID = "ldap"
    }

}
