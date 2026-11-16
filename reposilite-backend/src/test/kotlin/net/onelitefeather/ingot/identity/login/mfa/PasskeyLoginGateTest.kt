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
import java.security.SecureRandom
import java.time.Instant
import net.onelitefeather.ingot.identity.login.Argon2Cost
import net.onelitefeather.ingot.identity.login.LocalLoginProvider
import net.onelitefeather.ingot.identity.login.LoginAttemptLimiter
import net.onelitefeather.ingot.identity.login.LoginFacade
import net.onelitefeather.ingot.identity.login.LoginOutcome
import net.onelitefeather.ingot.identity.login.LoginService
import net.onelitefeather.ingot.identity.login.LoginSettings
import net.onelitefeather.ingot.identity.login.MutableClock
import net.onelitefeather.ingot.identity.login.PasswordHasher
import net.onelitefeather.ingot.identity.login.storage.LoginStore
import net.onelitefeather.ingot.identity.login.storage.SecondFactorStore
import net.onelitefeather.ingot.identity.storage.IdentityStore
import net.onelitefeather.ingot.settings.encryption.SettingsEnvelopeCodec
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.reactive.Reference

/**
 * The login gate of a passkey. A passkey is a second factor, so a user whose only factor is a passkey is challenged after the
 * password, like a user with an authenticator. Enrolling an authenticator still asks no proof of a passkey-only user.
 */
internal class PasskeyLoginGateTest {

    private val clock = MutableClock(Instant.parse("2026-11-14T10:00:00Z"))
    private val password = "correct horse battery"
    private val ip = "10.0.0.2"

    @TempDir
    lateinit var workingDirectory: Path

    private class Gate(
        val facade: LoginFacade,
        val factors: SecondFactorService,
        val passkeys: PasskeyService,
    )

    private fun withGate(block: (Gate) -> Unit) {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            val database = connection.database
            val identities = IdentityStore(database, clock)
            val audit = mutableListOf<AuditEvent>()
            val settings = Reference.reference(LoginSettings())
            val credentials = LoginStore(database, clock)
            val store = SecondFactorStore(database, clock)
            lateinit var factors: SecondFactorService
            val login = LoginService(
                identities = identities,
                credentials = credentials,
                hasher = PasswordHasher(Argon2Cost(memoryKib = 8, iterations = 1, parallelism = 1)),
                settings = settings,
                clock = clock,
                audit = audit::add,
                secondFactorRequired = { principal -> factors.requiresSecondFactor(principal.id) },
            )
            val codec = SettingsEnvelopeCodec(key = ByteArray(SettingsEnvelopeCodec.KEY_LENGTH) { 3 })
            factors = SecondFactorService(
                store = store,
                identities = identities,
                limiter = LoginAttemptLimiter(credentials, settings, clock),
                seeds = SettingsSeedCipher(codecs = { listOf(codec) }),
                clock = clock,
                audit = audit::add,
                random = SecureRandom(),
            )
            val facade = LoginFacade(login, sessions = null, ownTokens = null, secondFactor = factors)
            val passkeys = PasskeyService(store = store, identities = identities, clock = clock, audit = audit::add)
            block(Gate(facade, factors, passkeys))
        }
    }

    /** Creates the user with a password and registers one passkey for it, through the ceremonies of the core. */
    private fun Gate.userWithPasskey(name: String): Int {
        val principalId = checkNotNull(facade.createUser(name, name, password)) { "user $name" }.id
        val started = passkeys.startRegistration(principalId)
        val attestation = SoftwarePasskey().registration(challenge = started.challenge)
        val result = passkeys.finishRegistration(principalId, started.challengeId, attestation.clientDataJson, attestation.attestationObject, RP_ID, ORIGIN)
        assertThat(result).`as`("registration of the passkey").isInstanceOf(PasskeyRegistration.Registered::class.java)
        return principalId
    }

    @Test
    fun `a password login of a user whose only factor is a passkey is challenged instead of signed in`() {
        withGate { gate ->
            gate.userWithPasskey("paula")

            val outcome = gate.facade.login(LocalLoginProvider.ID, "paula", password, ip)

            assertThat(outcome).`as`("login of a user with a passkey only").isInstanceOf(LoginOutcome.SecondFactorRequired::class.java)
        }
    }

    @Test
    fun `enrolling an authenticator asks no code of a user whose only factor is a passkey`() {
        withGate { gate ->
            val principalId = gate.userWithPasskey("pedro")

            val started = gate.factors.enrolmentStart(principalId, "pedro")

            assertThat(started).`as`("enrolment start with a passkey only").isInstanceOf(TotpEnrolmentStart.Started::class.java)
        }
    }

}
