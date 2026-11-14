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

import com.reposilite.audit.api.AuditAction
import com.reposilite.audit.api.AuditActor
import com.reposilite.audit.api.AuditEvent
import com.reposilite.token.AccessTokenIdentifier
import com.reposilite.token.AccessTokenType.PERSISTENT
import net.onelitefeather.ingot.identity.login.ldap.LdapLoginSettings
import net.onelitefeather.ingot.identity.login.oidc.OidcProviderSettings
import net.onelitefeather.ingot.identity.service.IdpGuardMode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The guard of the `login` domain: a write that changes an identity-relevant field needs `ingot.admin.idp`, in enforce mode.
 * Each test builds its own settings, its own holders and its own recorders.
 */
internal class LoginIdentityGuardTest {

    private val caller = AccessTokenIdentifier(PERSISTENT, 7)
    private val operator = AuditActor.named("operator", "10.0.0.7")

    @Test
    fun `an unrelated login field needs no idp right`() {
        withGuard(IdpGuardMode.ENFORCE, holders = emptySet()) { guard, events, warnings ->
            // when
            val refusal = guard.refusal("login", baseline(), baseline().copy(sessionLifetimeMinutes = 30), caller, operator)

            // then
            assertThat(refusal).describedAs("refusal").isNull()
            assertThat(events).describedAs("events").isEmpty()
            assertThat(warnings).describedAs("warnings").isEmpty()
        }
    }

    @Test
    fun `a settings domain other than login is not guarded here`() {
        withGuard(IdpGuardMode.ENFORCE, holders = emptySet()) { guard, events, _ ->
            val changed = baseline().copy(oidcProviders = listOf(provider().copy(adminGroups = listOf("everyone"))))

            assertThat(guard.refusal("maven", baseline(), changed, caller, operator)).describedAs("refusal").isNull()
            assertThat(events).describedAs("events").isEmpty()
        }
    }

    @Test
    fun `enforce refuses a change of an admin group without the idp right`() {
        withGuard(IdpGuardMode.ENFORCE, holders = emptySet()) { guard, events, warnings ->
            // when
            val refusal = guard.refusal("login", baseline(), changedAdminGroups(), caller, operator)

            // then
            assertThat(refusal?.status).describedAs("status of the refusal").isEqualTo(403)
            val event = events.single { it.action == AuditAction.IDP_SETTINGS_UNGUARDED }
            assertThat(event.details).containsEntry("domain", "login").containsEntry("outcome", "refused").containsEntry("mode", "enforce")
            assertThat(event.details["fields"]).describedAs("changed fields").isEqualTo(listOf("oidcProviders.corp.adminGroups"))
            assertThat(warnings).describedAs("warnings in enforce").isEmpty()
        }
    }

    @Test
    fun `a change of an admin group is allowed with the idp right`() {
        withGuard(IdpGuardMode.ENFORCE, holders = setOf(caller)) { guard, events, _ ->
            val refusal = guard.refusal("login", baseline(), changedAdminGroups(), caller, operator)

            assertThat(refusal).describedAs("refusal with the right").isNull()
            assertThat(events.map { it.action }).describedAs("events with the right").doesNotContain(AuditAction.IDP_SETTINGS_UNGUARDED)
        }
    }

    @Test
    fun `warn allows the change without the right, records it and logs a warning`() {
        withGuard(IdpGuardMode.WARN, holders = emptySet()) { guard, events, warnings ->
            val refusal = guard.refusal("login", baseline(), changedAdminGroups(), caller, operator)

            assertThat(refusal).describedAs("refusal in warn").isNull()
            val event = events.single { it.action == AuditAction.IDP_SETTINGS_UNGUARDED }
            assertThat(event.details).containsEntry("outcome", "allowed").containsEntry("mode", "warn")
            assertThat(warnings).describedAs("warning lines").singleElement().asString().contains("login")
        }
    }

    @Test
    fun `off checks and records nothing`() {
        withGuard(IdpGuardMode.OFF, holders = emptySet()) { guard, events, warnings ->
            assertThat(guard.refusal("login", baseline(), changedAdminGroups(), caller, operator)).describedAs("refusal in off").isNull()
            assertThat(events).describedAs("events in off").isEmpty()
            assertThat(warnings).describedAs("warnings in off").isEmpty()
        }
    }

    @Test
    fun `the refusal names neither the changed value nor the secret`() {
        withGuard(IdpGuardMode.ENFORCE, holders = emptySet()) { guard, _, _ ->
            val changed = baseline().copy(
                oidcProviders = listOf(provider().copy(issuerUrl = "https://attacker.example", clientSecret = "hijack-secret", adminGroups = listOf("evil-group"))),
            )

            val message = guard.refusal("login", baseline(), changed, caller, operator)?.message.orEmpty()

            assertThat(message).describedAs("generic refusal")
                .doesNotContain("attacker.example").doesNotContain("hijack-secret").doesNotContain("evil-group").doesNotContain("corp")
            assertThat(message).contains("ingot.admin.idp")
        }
    }

    private fun changedAdminGroups(): LoginSettings =
        baseline().copy(oidcProviders = listOf(provider().copy(adminGroups = listOf("ingot-admins", "contractors"))))

    private fun baseline(): LoginSettings = LoginSettings(
        oidcProviders = listOf(provider()),
        ldap = LdapLoginSettings(enabled = true, url = "ldaps://ldap.example.org:636", userBaseDn = "ou=people,dc=example,dc=org"),
    )

    private fun provider(): OidcProviderSettings = OidcProviderSettings(
        id = "corp",
        enabled = true,
        issuerUrl = "https://sso.example.org",
        clientId = "ingot",
        clientSecret = "client-secret",
        groupsClaim = "groups",
        adminGroups = listOf("ingot-admins"),
    )

    private fun withGuard(
        mode: IdpGuardMode,
        holders: Set<AccessTokenIdentifier>,
        block: (LoginIdentityGuard, List<AuditEvent>, List<String>) -> Unit,
    ) {
        val events = mutableListOf<AuditEvent>()
        val warnings = mutableListOf<String>()
        val guard = LoginIdentityGuard(
            mode = mode,
            holdsIdp = { holders.contains(it) },
            audit = { events += it },
            warnings = { warnings += it },
        )
        block(guard, events, warnings)
    }
}
