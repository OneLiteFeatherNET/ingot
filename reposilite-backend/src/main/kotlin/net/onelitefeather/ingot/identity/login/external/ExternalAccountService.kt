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

package net.onelitefeather.ingot.identity.login.external

import com.reposilite.audit.api.AuditAction
import com.reposilite.audit.api.AuditActor
import com.reposilite.audit.api.AuditEvent
import net.onelitefeather.ingot.identity.login.LoginNames
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.model.PrincipalRecord
import net.onelitefeather.ingot.identity.storage.IdentityStore

/** A subject as an external provider has described it, already checked for its signature or bind. */
data class ExternalIdentity(
    /** `oidc:<id>` or `ldap:<url-host>`. */
    val provider: String,
    val subject: String,
    val username: String,
    val displayName: String?,
    val email: String?,
    val autoCreateUsers: Boolean,
    val allowedEmailDomains: List<String>,
    /** The groups of the subject, as the provider names them. Teams are matched by `<groupPrefix><group>`. */
    val groups: List<String> = emptyList(),
    /** The groups whose members become instance administrators through a synchronised team. */
    val adminGroups: List<String> = emptyList(),
    /** The prefix of the team references of this provider: `oidc:<id>:` for OpenID Connect and GitHub, `ldap:` for LDAP. */
    val groupPrefix: String = "$provider:",
)

/** Why an external login was refused. [code] is the stable word the login page and the audit trail use. */
enum class ExternalRefusal(val code: String) {
    SETUP_REQUIRED("setup_required"),
    USER_NOT_PROVISIONED("user_not_provisioned"),
    USERNAME_TAKEN("username_taken"),
    INVALID_USERNAME("invalid_username"),
    USER_DISABLED("user_disabled"),
    EMAIL_DOMAIN_NOT_ALLOWED("email_domain_not_allowed"),
}

sealed interface ExternalOutcome {

    data class Succeeded(val principal: PrincipalRecord) : ExternalOutcome

    data class Refused(val reason: ExternalRefusal) : ExternalOutcome

}

/**
 * Maps an external subject to a local user principal. A linked subject logs in as its principal. An unlinked one creates a
 * user, but only when automatic creation is on, a user already exists (the first administrator comes from the setup code),
 * and the user name is free. A name that belongs to another principal is refused, never linked: an external login must not
 * take over a local account.
 */
class ExternalAccountService internal constructor(
    private val identities: IdentityStore,
    private val links: ExternalIdentityStore,
    private val audit: (AuditEvent) -> Unit = {},
    log: (String) -> Unit = {},
) {

    private val groupSync = ExternalGroupSync(identities, log, audit)

    /**
     * Decides the login and, when it succeeds, synchronises the teams with the groups of the login. The caller audits the
     * outcome, because only the caller knows the address and the protocol.
     */
    fun resolve(identity: ExternalIdentity): ExternalOutcome {
        val outcome = decide(identity)
        if (outcome is ExternalOutcome.Succeeded) {
            groupSync.apply(outcome.principal, identity.provider, identity.groupPrefix, identity.groups.toSet(), identity.adminGroups.toSet())
        }
        return outcome
    }

    private fun decide(identity: ExternalIdentity): ExternalOutcome {
        emailRefusal(identity)?.let { return ExternalOutcome.Refused(it) }

        val linkedId = links.principalIdOf(identity.provider, identity.subject)
        val linked = linkedId?.let { identities.findPrincipalById(it) }
        if (linked != null) {
            if (linked.disabled) return ExternalOutcome.Refused(ExternalRefusal.USER_DISABLED)
            identity.displayName?.takeIf { it.isNotBlank() && it != linked.displayName }?.let { name ->
                identities.updatePrincipal(linked.id, displayName = name, disabled = null)
            }
            return ExternalOutcome.Succeeded(linked)
        }

        if (!identity.autoCreateUsers) return ExternalOutcome.Refused(ExternalRefusal.USER_NOT_PROVISIONED)
        if (identities.listPrincipals(PrincipalKind.USER).isEmpty()) return ExternalOutcome.Refused(ExternalRefusal.SETUP_REQUIRED)
        LoginNames.usernameProblem(identity.username)?.let { return ExternalOutcome.Refused(ExternalRefusal.INVALID_USERNAME) }

        val principal = identities.createPrincipal(
            PrincipalKind.USER,
            identity.username,
            identity.displayName?.takeIf { it.isNotBlank() } ?: identity.username,
            disabled = false,
            createdAt = identities.now(),
        ) ?: return ExternalOutcome.Refused(ExternalRefusal.USERNAME_TAKEN)
        audit(AuditEvent(action = AuditAction.PRINCIPAL_CREATED, actor = AuditActor.named(identity.provider, null), details = mapOf("principal" to principal.name)))

        if (!links.link(identity.provider, identity.subject, principal.id)) {
            // Another request linked the subject meanwhile. Its principal is the one this login is about.
            val winner = links.principalIdOf(identity.provider, identity.subject)?.let { identities.findPrincipalById(it) }
            return if (winner != null && !winner.disabled) ExternalOutcome.Succeeded(winner) else ExternalOutcome.Refused(ExternalRefusal.USER_DISABLED)
        }
        return ExternalOutcome.Succeeded(principal)
    }

    /** The domain rule, checked on every login. Letter case of the domain does not matter. */
    private fun emailRefusal(identity: ExternalIdentity): ExternalRefusal? {
        if (identity.allowedEmailDomains.isEmpty()) return null
        val domain = identity.email?.substringAfterLast('@', missingDelimiterValue = "")?.lowercase()
        val allowed = identity.allowedEmailDomains.map { it.trim().lowercase() }
        return if (domain.isNullOrEmpty() || domain !in allowed) ExternalRefusal.EMAIL_DOMAIN_NOT_ALLOWED else null
    }

}
