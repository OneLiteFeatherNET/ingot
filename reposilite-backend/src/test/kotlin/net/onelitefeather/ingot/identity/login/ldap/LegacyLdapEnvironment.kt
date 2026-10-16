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
import com.reposilite.auth.application.LdapSettings
import com.reposilite.audit.api.AuditEvent
import com.reposilite.journalist.backend.AggregatedLogger
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.journalist.backend.PrintStreamLogger
import com.reposilite.status.FailureFacade
import com.reposilite.token.AccessTokenFacade
import com.reposilite.token.AccessTokenSecurityProvider
import com.reposilite.token.AccessTokenType.PERSISTENT
import com.reposilite.token.ExportService
import com.reposilite.token.infrastructure.InMemoryAccessTokenRepository
import net.onelitefeather.ingot.identity.login.LoginService
import net.onelitefeather.ingot.identity.login.LoginSettings
import net.onelitefeather.ingot.identity.login.MutableClock
import net.onelitefeather.ingot.identity.login.PasswordHasher
import net.onelitefeather.ingot.identity.login.external.ExternalAccountService
import net.onelitefeather.ingot.identity.login.external.ExternalIdentityStore
import net.onelitefeather.ingot.identity.login.storage.LoginStore
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.storage.IdentityAccessTokenRepository
import net.onelitefeather.ingot.identity.storage.IdentityStore
import org.jetbrains.exposed.v1.jdbc.Database
import panda.std.reactive.Reference

/**
 * The legacy `authentication.ldap` settings pointed at an [InMemoryDirectory], with a real [LdapAuthenticator] on top of
 * the identity tables: the same pieces the Maven path uses. One instance per test, on a database of its own.
 */
internal class LegacyLdapEnvironment(
    directory: InMemoryDirectory,
    val database: Database,
    val clock: MutableClock,
    legacyEnabled: Boolean = true,
) {

    val identities = IdentityStore(database, clock)

    val accounts = ExternalAccountService(identities, ExternalIdentityStore(database))

    private val journalist = AggregatedLogger(InMemoryLogger(), PrintStreamLogger(System.out, System.out))

    private val accessTokens = AccessTokenFacade(
        journalist,
        InMemoryAccessTokenRepository(),
        IdentityAccessTokenRepository(identities),
        ExportService(),
        bcryptStrength = AccessTokenSecurityProvider.MIN_BCRYPT_STRENGTH,
    )

    val authenticator = LdapAuthenticator(
        journalist = journalist,
        ldapSettings = Reference.reference(
            LdapSettings(
                enabled = legacyEnabled,
                ssl = false,
                hostname = "127.0.0.1",
                port = directory.port,
                baseDn = "ou=people,${InMemoryDirectory.BASE_DN}",
                searchUserDn = InMemoryDirectory.SERVICE_DN,
                searchUserPassword = InMemoryDirectory.SERVICE_PASSWORD,
                typeAttribute = "inetOrgPerson",
                userAttribute = "uid",
                userFilter = "",
                userType = PERSISTENT,
            ),
        ),
        accessTokenFacade = accessTokens,
        failureFacade = FailureFacade(journalist),
        disableUserPasswordAuthentication = false,
    )

    fun directories(login: LdapLoginSettings = LdapLoginSettings()): LdapDirectories =
        LdapDirectories(login = { login }, legacy = authenticator, accounts = accounts, identities = identities)

    /** A login service whose LDAP providers are the ones [directories] selects, as the plugin wires them. */
    fun loginService(
        login: LdapLoginSettings = LdapLoginSettings(),
        audit: MutableList<AuditEvent> = mutableListOf(),
        maxFailedAttempts: Int = 5,
    ): LoginService {
        val directories = directories(login)
        return LoginService(
            identities = identities,
            credentials = LoginStore(database, clock),
            hasher = PasswordHasher(),
            settings = Reference.reference(LoginSettings(maxFailedAttempts = maxFailedAttempts, lockoutMinutes = 5, ldap = login)),
            clock = clock,
            audit = audit::add,
            externalPasswordProviders = { directories.provider()?.let { listOf(it) }.orEmpty() },
            externalAnnouncements = { listOfNotNull(directories.announcement()) },
        )
    }

    fun createHuman(name: String) =
        identities.createPrincipal(PrincipalKind.USER, name, name, disabled = false, createdAt = clock.instant())!!

}
