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

import com.reposilite.audit.api.AuditAction
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
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.storage.IdentityStore
import net.onelitefeather.ingot.settings.encryption.SettingsEnvelopeCodec
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.time.Duration
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
        val identities: IdentityStore,
        val audit: MutableList<AuditEvent>,
    )

    private fun withGate(block: (Gate) -> Unit) {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            val database = connection.database
            val identities = IdentityStore(database, clock)
            val audit = mutableListOf<AuditEvent>()
            val settings = Reference.reference(LoginSettings())
            val credentials = LoginStore(database, clock)
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
                store = SecondFactorStore(database, clock),
                identities = identities,
                limiter = LoginAttemptLimiter(credentials, settings, clock),
                seeds = SettingsSeedCipher(codecs = { listOf(codec) }),
                clock = clock,
                audit = audit::add,
                random = SecureRandom(),
                hasLocalPassword = { principalId -> credentials.findCredential(principalId) != null },
            )
            val facade = LoginFacade(login, sessions = null, ownTokens = null, secondFactor = factors)
            block(Gate(facade, factors, identities, audit))
        }
    }

    private fun Gate.userWithPassword(name: String = "alice"): Int =
        checkNotNull(facade.createUser(name, name, password)) { "the user $name could not be created" }.id

    /** Enrols an authenticator for [principalId] and returns its seed. The clock moves on, so the next code is a new step. */
    private fun Gate.enrolAuthenticator(principalId: Int): ByteArray {
        val started = factors.enrolmentStart(principalId, "alice") as TotpEnrolmentStart.Started
        val code = TotpAlgorithm.code(started.seed, clock.instant().epochSecond / TotpAlgorithm.STEP_SECONDS)
        factors.confirmEnrolment(principalId, "alice", code, IP)
        clock.advance(Duration.ofSeconds(TotpAlgorithm.STEP_SECONDS))
        return started.seed
    }

    private fun Gate.codeNow(seed: ByteArray): String =
        TotpAlgorithm.code(seed, clock.instant().epochSecond / TotpAlgorithm.STEP_SECONDS)

    @Test
    fun `a password login of a user without a factor is not challenged`() {
        withGate { gate ->
            gate.userWithPassword()

            val outcome = gate.facade.login(LocalLoginProvider.ID, "alice", password, IP)

            assertThat(outcome).`as`("login without a factor").isInstanceOf(LoginOutcome.Succeeded::class.java)
        }
    }

    @Test
    fun `a password login of a user without a factor records the login`() {
        withGate { gate ->
            gate.userWithPassword()

            gate.facade.login(LocalLoginProvider.ID, "alice", password, IP)

            assertThat(gate.audit.map { it.action }).`as`("audit of a login without a factor").contains(AuditAction.LOGIN_SUCCEEDED)
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
    fun `a password login of a user with an authenticator records the first factor and no login yet`() {
        withGate { gate ->
            val principalId = gate.userWithPassword()
            gate.enrolAuthenticator(principalId)
            gate.audit.clear()

            gate.facade.login(LocalLoginProvider.ID, "alice", password, IP)

            val actions = gate.audit.map { it.action }
            assertThat(actions).`as`("audit after the password of a user with a factor").contains(AuditAction.MFA_FIRST_FACTOR_ACCEPTED)
            assertThat(actions).`as`("audit after the password of a user with a factor").doesNotContain(AuditAction.LOGIN_SUCCEEDED)
        }
    }

    @Test
    fun `recording an issued session after the second factor writes the login once`() {
        withGate { gate ->
            val principalId = gate.userWithPassword()
            val seed = gate.enrolAuthenticator(principalId)
            val challenge = (gate.facade.login(LocalLoginProvider.ID, "alice", password, IP) as LoginOutcome.SecondFactorRequired).challenge
            gate.audit.clear()

            val completion = gate.facade.completeSecondFactor(challenge, gate.codeNow(seed), IP)
            gate.facade.recordSessionIssued("alice", IP)

            assertThat(completion).`as`("completion with the current code").isInstanceOf(ChallengeCompletion.Verified::class.java)
            assertThat(gate.audit.count { it.action == AuditAction.LOGIN_SUCCEEDED }).`as`("login records after the session").isEqualTo(1)
        }
    }

    @Test
    fun `a challenge that was completed once cannot complete again`() {
        withGate { gate ->
            val principalId = gate.userWithPassword()
            val seed = gate.enrolAuthenticator(principalId)
            val challenge = (gate.facade.login(LocalLoginProvider.ID, "alice", password, IP) as LoginOutcome.SecondFactorRequired).challenge
            gate.facade.completeSecondFactor(challenge, gate.codeNow(seed), IP)

            val again = gate.facade.completeSecondFactor(challenge, gate.codeNow(seed), IP)

            assertThat(again).`as`("reused challenge").isEqualTo(ChallengeCompletion.Gone)
        }
    }

    @Test
    fun `a challenge older than its lifetime cannot complete, even with the right code`() {
        withGate { gate ->
            val principalId = gate.userWithPassword()
            val seed = gate.enrolAuthenticator(principalId)
            val challenge = (gate.facade.login(LocalLoginProvider.ID, "alice", password, IP) as LoginOutcome.SecondFactorRequired).challenge
            clock.advance(Duration.ofMinutes(5).plusSeconds(1))

            val expired = gate.facade.completeSecondFactor(challenge, gate.codeNow(seed), IP)

            assertThat(expired).`as`("expired challenge").isEqualTo(ChallengeCompletion.Gone)
        }
    }

    @Test
    fun `an account without a local password cannot enrol an authenticator through the facade`() {
        withGate { gate ->
            // A directory account has a principal but no local password, so it has no credential row.
            val directoryAccount = checkNotNull(gate.identities.createPrincipal(PrincipalKind.USER, "bob", "bob", false, clock.instant())).id

            val outcome = gate.facade.startAuthenticatorEnrolment(directoryAccount, "bob", code = null, ip = IP)

            assertThat(outcome).`as`("enrolment of a directory account").isInstanceOf(TotpEnrolmentStart.Unavailable::class.java)
        }
    }

    private companion object {
        const val IP = "10.0.0.1"
    }

}
