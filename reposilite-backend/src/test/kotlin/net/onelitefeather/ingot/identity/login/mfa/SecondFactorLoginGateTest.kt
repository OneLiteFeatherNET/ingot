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
 * The password form is the sign-in route the second factor guards. A user with an authenticator gets a challenge instead of
 * a session, and a user without one gets the login they had before.
 */
internal class SecondFactorLoginGateTest {

    private val clock = MutableClock(Instant.parse("2026-11-14T10:00:00Z"))
    private val password = "correct horse battery"

    @TempDir
    lateinit var workingDirectory: Path

    private class Gate(
        val facade: LoginFacade,
        val factors: SecondFactorService,
        val audit: MutableList<AuditEvent>,
    )

    private fun withGate(block: (Gate) -> Unit) {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            val database = connection.database
            val identities = IdentityStore(database, clock)
            val audit = mutableListOf<AuditEvent>()
            val settings = Reference.reference(LoginSettings())
            val login = LoginService(
                identities = identities,
                credentials = LoginStore(database, clock),
                hasher = PasswordHasher(Argon2Cost(memoryKib = 8, iterations = 1, parallelism = 1)),
                settings = settings,
                clock = clock,
                audit = audit::add,
            )
            val codec = SettingsEnvelopeCodec(key = ByteArray(SettingsEnvelopeCodec.KEY_LENGTH) { 3 })
            val factors = SecondFactorService(
                store = SecondFactorStore(database, clock),
                identities = identities,
                limiter = LoginAttemptLimiter(LoginStore(database, clock), settings, clock),
                seeds = SettingsSeedCipher(codecs = { listOf(codec) }),
                clock = clock,
                audit = audit::add,
                random = SecureRandom(),
            )
            val facade = LoginFacade(login, sessions = null, ownTokens = null, secondFactor = factors)
            block(Gate(facade, factors, audit))
        }
    }

    private fun Gate.userWithPassword(name: String = "alice"): Int =
        checkNotNull(facade.createUser(name, name, password)) { "the user $name could not be created" }.id

    private fun Gate.enrolAuthenticator(principalId: Int) {
        val started = factors.enrolmentStart(principalId, "alice") as TotpEnrolmentStart.Started
        val code = TotpAlgorithm.code(started.seed, clock.instant().epochSecond / TotpAlgorithm.STEP_SECONDS)
        factors.confirmEnrolment(principalId, "alice", code, IP)
    }

    @Test
    fun `a password login of a user without a factor is not challenged`() {
        withGate { gate ->
            gate.userWithPassword()

            val outcome = gate.facade.login(LocalLoginProvider.ID, "alice", password, IP)

            assertThat(outcome).`as`("login without a factor").isInstanceOf(LoginOutcome.Succeeded::class.java)
        }
    }

    @Test
    fun `a password login of a user with an authenticator is challenged instead of signed in`() {
        withGate { gate ->
            val principalId = gate.userWithPassword()
            gate.enrolAuthenticator(principalId)

            val outcome = gate.facade.login(LocalLoginProvider.ID, "alice", password, IP)

            assertThat(outcome).`as`("login with an authenticator").isInstanceOf(LoginOutcome.SecondFactorRequired::class.java)
            assertThat((outcome as LoginOutcome.SecondFactorRequired).challenge).`as`("challenge id").isNotBlank()
        }
    }

    @Test
    fun `a wrong password is refused without issuing a challenge`() {
        withGate { gate ->
            val principalId = gate.userWithPassword()
            gate.enrolAuthenticator(principalId)

            val outcome = gate.facade.login(LocalLoginProvider.ID, "alice", "wrong password entirely", IP)

            assertThat(outcome).`as`("wrong password").isEqualTo(LoginOutcome.Rejected)
        }
    }

    private companion object {
        const val IP = "10.0.0.1"
    }

}
