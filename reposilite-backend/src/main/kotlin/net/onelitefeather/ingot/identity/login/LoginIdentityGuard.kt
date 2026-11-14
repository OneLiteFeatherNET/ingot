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

import com.reposilite.audit.api.AuditAction.IDP_SETTINGS_UNGUARDED
import com.reposilite.audit.api.AuditActor
import com.reposilite.audit.api.AuditEvent
import com.reposilite.configuration.shared.api.SharedSettings
import com.reposilite.configuration.shared.infrastructure.SettingsWriteGuard
import com.reposilite.shared.ErrorResponse
import com.reposilite.token.AccessTokenIdentifier
import io.javalin.http.HttpStatus.FORBIDDEN
import net.onelitefeather.ingot.identity.login.ldap.LdapLoginSettings
import net.onelitefeather.ingot.identity.login.oidc.OidcProviderSettings
import net.onelitefeather.ingot.identity.service.IdpGuardMode

/**
 * The guard of the `login` domain. A write that changes an identity-relevant field needs the admin area `ingot.admin.idp`,
 * because such a field decides who logs in and which groups become instance administrators. The mode is the same switch as
 * the one of team links. Enforce refuses with a generic 403, warn allows and records, off checks nothing. A write that changes
 * no identity field is not guarded, and neither is any other domain.
 */
internal class LoginIdentityGuard(
    private val mode: IdpGuardMode,
    private val holdsIdp: (AccessTokenIdentifier) -> Boolean,
    private val audit: (AuditEvent) -> Unit,
    private val warnings: (String) -> Unit,
) : SettingsWriteGuard {

    override fun refusal(
        domain: String,
        before: SharedSettings,
        after: SharedSettings,
        caller: AccessTokenIdentifier,
        actor: AuditActor,
    ): ErrorResponse? {
        if (domain != DOMAIN || mode == IdpGuardMode.OFF || before !is LoginSettings || after !is LoginSettings) return null
        val fields = LoginIdentityFields.changed(before, after)
        if (fields.isEmpty() || holdsIdp(caller)) return null

        val refused = mode == IdpGuardMode.ENFORCE
        audit(
            AuditEvent(
                action = IDP_SETTINGS_UNGUARDED,
                actor = actor,
                details = mapOf(
                    "domain" to domain,
                    "fields" to fields,
                    "mode" to mode.name.lowercase(),
                    "outcome" to if (refused) "refused" else "allowed",
                ),
            ),
        )
        if (refused) {
            return ErrorResponse(FORBIDDEN.code, "Changing the identity provider settings needs the admin area ingot.admin.idp.")
        }
        warnings("The login settings $fields are changed without ingot.admin.idp")
        return null
    }

    companion object {
        const val DOMAIN = "login"
    }
}

/**
 * The fields of the login domain that decide who logs in, which provider a link belongs to and which groups grant instance
 * administration. Each changed field is named by its path: `publicUrl`, `oidcProviders.<id>.<field>`, `ldap.<field>`, or
 * `oidcProviders.<id>` for a provider that is added or removed. Values are never named. Other fields, such as the lifetimes of
 * a session or the look of a login button, are not listed.
 */
internal object LoginIdentityFields {

    private val OIDC_FIELDS: List<Pair<String, (OidcProviderSettings) -> Any?>> = listOf(
        "enabled" to { p: OidcProviderSettings -> p.enabled },
        "issuerUrl" to { p: OidcProviderSettings -> p.issuerUrl },
        "clientId" to { p: OidcProviderSettings -> p.clientId },
        "clientSecret" to { p: OidcProviderSettings -> p.clientSecret },
        "groupsClaim" to { p: OidcProviderSettings -> p.groupsClaim },
        "usernameClaim" to { p: OidcProviderSettings -> p.usernameClaim },
        "adminGroups" to { p: OidcProviderSettings -> p.adminGroups },
    )

    private val LDAP_FIELDS: List<Pair<String, (LdapLoginSettings) -> Any?>> = listOf(
        "enabled" to { p: LdapLoginSettings -> p.enabled },
        "url" to { p: LdapLoginSettings -> p.url },
        "startTls" to { p: LdapLoginSettings -> p.startTls },
        "bindDn" to { p: LdapLoginSettings -> p.bindDn },
        "bindPassword" to { p: LdapLoginSettings -> p.bindPassword },
        "userBaseDn" to { p: LdapLoginSettings -> p.userBaseDn },
        "userFilter" to { p: LdapLoginSettings -> p.userFilter },
        "usernameAttribute" to { p: LdapLoginSettings -> p.usernameAttribute },
        "groupBaseDn" to { p: LdapLoginSettings -> p.groupBaseDn },
        "groupFilter" to { p: LdapLoginSettings -> p.groupFilter },
        "groupNameAttribute" to { p: LdapLoginSettings -> p.groupNameAttribute },
        "adminGroups" to { p: LdapLoginSettings -> p.adminGroups },
    )

    /** The paths of the identity fields that differ between [before] and [after]. Providers are matched by their id. */
    fun changed(before: LoginSettings, after: LoginSettings): List<String> {
        val changed = mutableListOf<String>()
        if (before.publicUrl != after.publicUrl) changed += "publicUrl"

        val oldProviders = before.oidcProviders.distinctBy { it.id }.associateBy { it.id }
        val newProviders = after.oidcProviders.distinctBy { it.id }.associateBy { it.id }
        (oldProviders.keys + newProviders.keys).forEach { id ->
            val old = oldProviders[id]
            val new = newProviders[id]
            when {
                old == null || new == null -> changed += "oidcProviders.$id"
                else -> OIDC_FIELDS.forEach { (name, read) ->
                    if (read(old) != read(new)) changed += "oidcProviders.$id.$name"
                }
            }
        }

        LDAP_FIELDS.forEach { (name, read) ->
            if (read(before.ldap) != read(after.ldap)) changed += "ldap.$name"
        }
        return changed
    }
}
