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

package net.onelitefeather.ingot.identity.login.mfa

import com.reposilite.audit.api.AuditEvent
import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import java.nio.file.Path
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import net.onelitefeather.ingot.identity.login.Argon2Cost
import net.onelitefeather.ingot.identity.login.LoginAttemptLimiter
import net.onelitefeather.ingot.identity.login.LoginFacade
import net.onelitefeather.ingot.identity.login.LoginService
import net.onelitefeather.ingot.identity.login.LoginSettings
import net.onelitefeather.ingot.identity.login.MutableClock
import net.onelitefeather.ingot.identity.login.PasswordHasher
import net.onelitefeather.ingot.identity.login.external.ExternalAccountService
import net.onelitefeather.ingot.identity.login.external.ExternalIdentityStore
import net.onelitefeather.ingot.identity.login.ldap.LdapLoginProvider
import net.onelitefeather.ingot.identity.login.ldap.LdapLoginSettings
import net.onelitefeather.ingot.identity.login.storage.AuthChallengeTable
import net.onelitefeather.ingot.identity.login.storage.LoginStore
import net.onelitefeather.ingot.identity.login.storage.SecondFactorStore
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.model.PrincipalRecord
import net.onelitefeather.ingot.identity.session.SessionService
import net.onelitefeather.ingot.identity.session.SessionSigner
import net.onelitefeather.ingot.identity.storage.IdentityStore
import net.onelitefeather.ingot.settings.encryption.SettingsEnvelopeCodec
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import panda.std.reactive.Reference

/** The password of every user that [MfaFixture.userWithPassword] creates. */
internal const val MFA_TEST_PASSWORD = "correct horse battery"

/**
 * The login stack of the second factor policy on one fresh database: identities, factors, passkeys, the password login, the
 * sessions and the facade. [settings] is the policy and the step-up settings of the test, and can be changed between two steps
 * of a test, as an administrator would change them. Every other service reads the default settings.
 */
internal class MfaFixture(val clock: MutableClock, private val database: Database, private val ldap: LdapLoginSettings? = null) {

    var settings: LoginSettings = LoginSettings()

    private val defaults = Reference.reference(LoginSettings())
    val identities = IdentityStore(database, clock)
    val credentials = LoginStore(database, clock)
    val factorStore = SecondFactorStore(database, clock)
    val audit = mutableListOf<AuditEvent>()

    private val codec = SettingsEnvelopeCodec(key = ByteArray(SettingsEnvelopeCodec.KEY_LENGTH) { 3 })

    val factors = SecondFactorService(
        store = factorStore,
        identities = identities,
        limiter = LoginAttemptLimiter(credentials, defaults, clock),
        seeds = SettingsSeedCipher(codecs = { listOf(codec) }),
        clock = clock,
        audit = audit::add,
        random = SecureRandom(),
        passkeys = PasskeyService(store = factorStore, identities = identities, clock = clock, audit = audit::add),
    )

    val policy = SecondFactorPolicy(
        identities = identities,
        settings = { settings },
        clock = clock,
        secondFactorRequired = { principalId -> factors.requiresSecondFactor(principalId) },
    )

    private val accounts = ExternalAccountService(identities, ExternalIdentityStore(database))

    private val login = LoginService(
        identities = identities,
        credentials = credentials,
        hasher = PasswordHasher(Argon2Cost(memoryKib = 8, iterations = 1, parallelism = 1)),
        settings = defaults,
        clock = clock,
        audit = audit::add,
        secondFactorRequired = { principal -> factors.requiresSecondFactor(principal.id) },
        externalPasswordProviders = { ldap?.takeIf { it.enabled }?.let { listOf(LdapLoginProvider(it, accounts)) }.orEmpty() },
    )

    val signer = SessionSigner(ByteArray(32) { 5 }, clock)

    val sessions = SessionService(
        store = LoginStore(database, clock),
        identities = identities,
        signer = signer,
        settings = defaults,
        clock = clock,
        audit = audit::add,
        policy = policy,
    )

    val facade = LoginFacade(
        service = login,
        sessions = sessions,
        ownTokens = null,
        secondFactor = factors,
        clock = clock,
        policy = policy,
        settings = { settings },
    )

    /** A user without a local password, such as one who signs in through a provider. */
    fun user(name: String = "alice"): PrincipalRecord =
        checkNotNull(identities.createPrincipal(PrincipalKind.USER, name, name, disabled = false, createdAt = clock.instant()))

    /** A user with a local password, created through the login service. */
    fun userWithPassword(name: String = "alice"): PrincipalRecord =
        checkNotNull(facade.createUser(name, name, MFA_TEST_PASSWORD)) { "the user $name could not be created" }

    /** Gives the user a passkey, which is a second factor. The key bytes are never checked by these tests. */
    fun addPasskey(principalId: Int) {
        factorStore.insertPasskey(
            principalId = principalId,
            factorKey = UUID.randomUUID().toString(),
            name = "Passkey",
            credentialId = "credential-$principalId-${UUID.randomUUID()}",
            publicKey = ByteArray(4),
            signCount = 0,
            at = clock.instant(),
        )
    }

    /** The number of sign-in and ceremony challenges stored. A refused request must leave it unchanged. */
    fun challengeCount(): Int =
        transaction(database) { AuthChallengeTable.selectAll().count().toInt() }

    /** The stored row of a challenge id as the browser holds it, `null` when no row has that id. */
    fun challengeKindOf(challengeId: String) =
        factorStore.findChallenge(sha256Hex(challengeId))?.kind

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { "%02x".format(it) }
}

/** Runs [block] on a fresh temporary database, so that no test sees the state of another. */
internal fun withMfaFixture(
    workingDirectory: Path,
    clock: MutableClock,
    ldap: LdapLoginSettings? = null,
    block: (MfaFixture) -> Unit,
) {
    DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
        block(MfaFixture(clock, connection.database, ldap))
    }
}
