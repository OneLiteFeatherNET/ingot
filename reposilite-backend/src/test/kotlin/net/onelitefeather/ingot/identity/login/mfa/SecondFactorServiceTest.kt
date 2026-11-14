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
import com.reposilite.audit.api.AuditActor
import com.reposilite.audit.api.AuditEvent
import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import java.nio.file.Path
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import net.onelitefeather.ingot.identity.login.LoginAttemptLimiter
import net.onelitefeather.ingot.identity.login.LoginSettings
import net.onelitefeather.ingot.identity.login.MutableClock
import net.onelitefeather.ingot.identity.login.storage.LoginStore
import net.onelitefeather.ingot.identity.login.storage.SecondFactorStore
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.storage.IdentityStore
import net.onelitefeather.ingot.settings.encryption.SettingsEnvelopeCodec
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.reactive.Reference

internal class SecondFactorServiceTest {

    private val clock = MutableClock(Instant.parse("2026-11-14T10:00:00Z"))
    private val key = ByteArray(SettingsEnvelopeCodec.KEY_LENGTH) { (it + 1).toByte() }

    @TempDir
    lateinit var workingDirectory: Path

    private class Fixture(
        val identities: IdentityStore,
        val service: SecondFactorService,
        val audit: MutableList<AuditEvent>,
        val principalId: Int,
    ) {
        /** The seed of the authenticator enrolled by [enrolAuthenticator], once one exists. */
        var seed: ByteArray? = null
    }

    private fun withFixture(
        keyed: Boolean = true,
        settings: LoginSettings = LoginSettings(maxFailedAttempts = 3, lockoutMinutes = 5),
        block: (Fixture) -> Unit,
    ) {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            val identities = IdentityStore(connection.database, clock)
            val audit = mutableListOf<AuditEvent>()
            val principal = identities.createPrincipal(PrincipalKind.USER, "alice", "Alice", false, clock.instant())!!
            val service = service(connection.database, identities, keyed, settings, audit)
            block(Fixture(identities, service, audit, principal.id))
        }
    }

    private fun service(
        database: org.jetbrains.exposed.v1.jdbc.Database,
        identities: IdentityStore,
        keyed: Boolean,
        settings: LoginSettings,
        audit: MutableList<AuditEvent>,
    ): SecondFactorService {
        val codec = SettingsEnvelopeCodec(key = key)
        val seeds = SettingsSeedCipher(codecs = { if (keyed) listOf(codec) else emptyList() })
        return SecondFactorService(
            store = SecondFactorStore(database, clock),
            identities = identities,
            limiter = LoginAttemptLimiter(LoginStore(database, clock), Reference.reference(settings), clock),
            seeds = seeds,
            clock = clock,
            audit = audit::add,
            random = SecureRandom(),
        )
    }

    /** Enrols an authenticator for [fixture]'s user and returns its seed. Confirmation is done with the current code. */
    private fun Fixture.enrolAuthenticator(): ByteArray {
        val started = service.enrolmentStart(principalId, "alice") as TotpEnrolmentStart.Started
        val confirmation = service.confirmEnrolment(principalId, "alice", currentCode(started.seed), ip = IP)
        assertThat(confirmation).`as`("enrolment confirmed with the current code").isInstanceOf(EnrolmentConfirmation.Confirmed::class.java)
        seed = started.seed
        return started.seed
    }

    /** A wrong six digit code for the current step of [seed], which is never the right one. */
    private fun wrongCodeFor(seed: ByteArray): String =
        currentCode(seed).let { right -> if (right == "000001") "000002" else "000001" }

    private fun currentCode(seed: ByteArray, step: Long = clock.instant().epochSecond / TotpAlgorithm.STEP_SECONDS): String =
        TotpAlgorithm.code(seed, step)

    private fun Fixture.signInChallenge(): String =
        service.startChallenge(principalId)

    @Test
    fun `refuses to start an authenticator enrolment while no settings key is active`() {
        withFixture(keyed = false) { fixture ->
            val outcome = fixture.service.enrolmentStart(fixture.principalId, "alice")

            assertThat(outcome).`as`("enrolment without a settings key").isInstanceOf(TotpEnrolmentStart.Unavailable::class.java)
        }
    }

    @Test
    fun `refuses an authenticator for an account without a local password`() {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            val identities = IdentityStore(connection.database, clock)
            val principal = identities.createPrincipal(PrincipalKind.USER, "dana", "Dana", false, clock.instant())!!
            val service = SecondFactorService(
                store = SecondFactorStore(connection.database, clock),
                identities = identities,
                limiter = LoginAttemptLimiter(LoginStore(connection.database, clock), Reference.reference(LoginSettings()), clock),
                seeds = SettingsSeedCipher(codecs = { listOf(SettingsEnvelopeCodec(key = key)) }),
                clock = clock,
                audit = {},
                hasLocalPassword = { false },
            )

            val outcome = service.enrolmentStart(principal.id, "dana")

            assertThat(outcome).`as`("enrolment of a directory account").isInstanceOf(TotpEnrolmentStart.Unavailable::class.java)
        }
    }

    @Test
    fun `a pending enrolment is not a factor until it is confirmed`() {
        withFixture { fixture ->
            fixture.service.enrolmentStart(fixture.principalId, "alice")

            assertThat(fixture.service.factorsOf(fixture.principalId)).`as`("factors of a user with a pending enrolment").isEmpty()
            assertThat(fixture.service.requiresSecondFactor(fixture.principalId)).`as`("sign-in needs a factor").isFalse()
        }
    }

    @Test
    fun `confirming the enrolment with the current code creates the factor and ten recovery codes`() {
        withFixture { fixture ->
            val started = fixture.service.enrolmentStart(fixture.principalId, "alice") as TotpEnrolmentStart.Started

            val outcome = fixture.service.confirmEnrolment(fixture.principalId, "alice", currentCode(started.seed), IP)

            assertThat(outcome).isInstanceOf(EnrolmentConfirmation.Confirmed::class.java)
            assertThat((outcome as EnrolmentConfirmation.Confirmed).recoveryCodes).`as`("recovery codes of the first factor").hasSize(10)
            assertThat(fixture.service.factorsOf(fixture.principalId)).`as`("factors after confirmation").hasSize(1)
        }
    }

    @Test
    fun `a wrong code at confirmation keeps the enrolment pending`() {
        withFixture { fixture ->
            val started = fixture.service.enrolmentStart(fixture.principalId, "alice") as TotpEnrolmentStart.Started
            val wrong = (currentCode(started.seed).toInt() + 1).mod(1_000_000).toString().padStart(6, '0')

            val outcome = fixture.service.confirmEnrolment(fixture.principalId, "alice", wrong, IP)

            assertThat(outcome).`as`("wrong code at confirmation").isEqualTo(EnrolmentConfirmation.WrongCode)
            assertThat(fixture.service.factorsOf(fixture.principalId)).`as`("factors after a wrong code").isEmpty()
        }
    }

    @Test
    fun `confirming without a pending enrolment is refused`() {
        withFixture { fixture ->
            val outcome = fixture.service.confirmEnrolment(fixture.principalId, "alice", "123456", IP)

            assertThat(outcome).`as`("confirmation without enrolment").isEqualTo(EnrolmentConfirmation.NoPendingEnrolment)
        }
    }

    @Test
    fun `a second authenticator does not issue recovery codes again`() {
        withFixture { fixture ->
            val firstSeed = fixture.enrolAuthenticator()
            clock.advance(Duration.ofSeconds(TotpAlgorithm.STEP_SECONDS))
            val started = fixture.service.enrolmentStart(fixture.principalId, "alice", code = currentCode(firstSeed), ip = IP) as TotpEnrolmentStart.Started

            val outcome = fixture.service.confirmEnrolment(fixture.principalId, "alice", currentCode(started.seed), IP)

            assertThat((outcome as EnrolmentConfirmation.Confirmed).recoveryCodes).`as`("recovery codes of a second factor").isNull()
        }
    }

    @Test
    fun `the code that confirmed an enrolment cannot be used again at sign-in`() {
        withFixture { fixture ->
            val seed = fixture.enrolAuthenticator()
            val challenge = fixture.signInChallenge()

            val outcome = fixture.service.completeChallenge(challenge, currentCode(seed), IP)

            assertThat(outcome).`as`("replayed confirmation code").isEqualTo(ChallengeCompletion.WrongCode)
        }
    }

    @Test
    fun `a user without a confirmed authenticator needs no second factor`() {
        withFixture { fixture ->
            assertThat(fixture.service.requiresSecondFactor(fixture.principalId)).`as`("user without factors").isFalse()
        }
    }

    @Test
    fun `a user with a confirmed authenticator needs a second factor`() {
        withFixture { fixture ->
            fixture.enrolAuthenticator()

            assertThat(fixture.service.requiresSecondFactor(fixture.principalId)).`as`("user with an authenticator").isTrue()
        }
    }

    @Test
    fun `completes a sign-in with the current authenticator code`() {
        withFixture { fixture ->
            val seed = fixture.enrolAuthenticator()
            clock.advance(Duration.ofSeconds(TotpAlgorithm.STEP_SECONDS))
            val challenge = fixture.signInChallenge()

            val outcome = fixture.service.completeChallenge(challenge, currentCode(seed), IP)

            assertThat(outcome).`as`("sign-in with the current code").isInstanceOf(ChallengeCompletion.Verified::class.java)
            assertThat((outcome as ChallengeCompletion.Verified).principal.name).isEqualTo("alice")
        }
    }

    @Test
    fun `accepts the code of the next step, which the clock reaches a step later`() {
        withFixture { fixture ->
            val seed = fixture.enrolAuthenticator()
            clock.advance(Duration.ofSeconds(TotpAlgorithm.STEP_SECONDS))
            val nextStep = clock.instant().epochSecond / TotpAlgorithm.STEP_SECONDS + 1
            val challenge = fixture.signInChallenge()

            val outcome = fixture.service.completeChallenge(challenge, currentCode(seed, nextStep), IP)

            assertThat(outcome).`as`("code of the next step").isInstanceOf(ChallengeCompletion.Verified::class.java)
        }
    }

    @Test
    fun `refuses a code once the clock has moved out of the window`() {
        withFixture { fixture ->
            val seed = fixture.enrolAuthenticator()
            clock.advance(Duration.ofSeconds(TotpAlgorithm.STEP_SECONDS))
            val staleCode = currentCode(seed, clock.instant().epochSecond / TotpAlgorithm.STEP_SECONDS - 2)
            val challenge = fixture.signInChallenge()

            val outcome = fixture.service.completeChallenge(challenge, staleCode, IP)

            assertThat(outcome).`as`("code two steps old").isEqualTo(ChallengeCompletion.WrongCode)
        }
    }

    @Test
    fun `a code is accepted once in its step, a second sign-in with it is refused`() {
        withFixture { fixture ->
            val seed = fixture.enrolAuthenticator()
            clock.advance(Duration.ofSeconds(TotpAlgorithm.STEP_SECONDS))
            val code = currentCode(seed)
            fixture.service.completeChallenge(fixture.signInChallenge(), code, IP)

            val second = fixture.service.completeChallenge(fixture.signInChallenge(), code, IP)

            assertThat(second).`as`("replayed code in the same step").isEqualTo(ChallengeCompletion.WrongCode)
        }
    }

    @Test
    fun `completes a sign-in with a recovery code, typed in lower case with dashes`() {
        withFixture { fixture ->
            fixture.enrolAuthenticator()
            val recoveryCode = regenerate(fixture)

            val typed = recoveryCode.lowercase().replace("-", " ")
            val outcome = fixture.service.completeChallenge(fixture.signInChallenge(), typed, IP)

            assertThat(outcome).`as`("sign-in with a typed recovery code").isInstanceOf(ChallengeCompletion.Verified::class.java)
        }
    }

    @Test
    fun `a recovery code works once`() {
        withFixture { fixture ->
            fixture.enrolAuthenticator()
            val recoveryCode = regenerate(fixture)
            fixture.service.completeChallenge(fixture.signInChallenge(), recoveryCode, IP)

            val second = fixture.service.completeChallenge(fixture.signInChallenge(), recoveryCode, IP)

            assertThat(second).`as`("spent recovery code").isEqualTo(ChallengeCompletion.WrongCode)
        }
    }

    @Test
    fun `a challenge is spent by the first successful completion`() {
        withFixture { fixture ->
            val seed = fixture.enrolAuthenticator()
            clock.advance(Duration.ofSeconds(TotpAlgorithm.STEP_SECONDS))
            val challenge = fixture.signInChallenge()
            fixture.service.completeChallenge(challenge, currentCode(seed), IP)

            val again = fixture.service.completeChallenge(challenge, currentCode(seed), IP)

            assertThat(again).`as`("spent challenge").isEqualTo(ChallengeCompletion.Gone)
        }
    }

    @Test
    fun `a wrong code keeps the challenge open for the right one`() {
        withFixture { fixture ->
            val seed = fixture.enrolAuthenticator()
            clock.advance(Duration.ofSeconds(TotpAlgorithm.STEP_SECONDS))
            val challenge = fixture.signInChallenge()
            fixture.service.completeChallenge(challenge, wrongCodeFor(seed), IP)

            val outcome = fixture.service.completeChallenge(challenge, currentCode(seed), IP)

            assertThat(outcome).`as`("right code after a wrong one").isInstanceOf(ChallengeCompletion.Verified::class.java)
        }
    }

    @Test
    fun `refuses a challenge after its five minute lifetime`() {
        withFixture { fixture ->
            val seed = fixture.enrolAuthenticator()
            val challenge = fixture.signInChallenge()
            clock.advance(Duration.ofMinutes(5).plusSeconds(1))

            val outcome = fixture.service.completeChallenge(challenge, currentCode(seed), IP)

            assertThat(outcome).`as`("expired challenge").isEqualTo(ChallengeCompletion.Gone)
        }
    }

    @Test
    fun `a challenge is spent once across two services sharing one database`() {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            val identities = IdentityStore(connection.database, clock)
            val principal = identities.createPrincipal(PrincipalKind.USER, "alice", "Alice", false, clock.instant())!!
            val audit = mutableListOf<AuditEvent>()
            val first = service(connection.database, identities, true, LoginSettings(), audit)
            val second = service(connection.database, identities, true, LoginSettings(), audit)
            val started = first.enrolmentStart(principal.id, "alice") as TotpEnrolmentStart.Started
            first.confirmEnrolment(principal.id, "alice", currentCode(started.seed), IP)
            clock.advance(Duration.ofSeconds(TotpAlgorithm.STEP_SECONDS))
            val challenge = first.startChallenge(principal.id)

            val winner = first.completeChallenge(challenge, currentCode(started.seed), IP)
            val loser = second.completeChallenge(challenge, currentCode(started.seed), IP)

            assertThat(winner).`as`("first instance").isInstanceOf(ChallengeCompletion.Verified::class.java)
            assertThat(loser).`as`("second instance on the same challenge").isEqualTo(ChallengeCompletion.Gone)
        }
    }

    @Test
    fun `blocks further codes after too many wrong ones, with the time to wait`() {
        withFixture { fixture ->
            val seed = fixture.enrolAuthenticator()
            clock.advance(Duration.ofSeconds(TotpAlgorithm.STEP_SECONDS))
            val challenge = fixture.signInChallenge()
            repeat(3) { fixture.service.completeChallenge(challenge, "000001", IP) }

            val outcome = fixture.service.completeChallenge(challenge, currentCode(seed), IP)

            assertThat(outcome).`as`("code after the limit").isInstanceOf(ChallengeCompletion.Blocked::class.java)
            assertThat((outcome as ChallengeCompletion.Blocked).retryAfterSeconds).`as`("retry after").isPositive()
        }
    }

    @Test
    fun `records a failed code in the audit trail without the code itself`() {
        withFixture { fixture ->
            fixture.enrolAuthenticator()
            clock.advance(Duration.ofSeconds(TotpAlgorithm.STEP_SECONDS))
            fixture.audit.clear()

            fixture.service.completeChallenge(fixture.signInChallenge(), "424242", IP)

            val failure = fixture.audit.single { it.action == AuditAction.MFA_CHALLENGE_FAILED }
            assertThat(failure.details.values.map { it.toString() }).`as`("audit details").noneMatch { it.contains("424242") }
        }
    }

    @Test
    fun `removes a factor when the current code is given`() {
        withFixture { fixture ->
            val seed = fixture.enrolAuthenticator()
            val factorId = fixture.service.factorsOf(fixture.principalId).single().id
            clock.advance(Duration.ofSeconds(TotpAlgorithm.STEP_SECONDS))

            val outcome = fixture.service.removeFactor(fixture.principalId, "alice", factorId, currentCode(seed), IP, AuditActor.SYSTEM_ACTOR)

            assertThat(outcome).isEqualTo(FactorRemoval.Removed)
            assertThat(fixture.service.requiresSecondFactor(fixture.principalId)).`as`("sign-in after removal").isFalse()
        }
    }

    @Test
    fun `refuses to remove a factor without a valid code`() {
        withFixture { fixture ->
            fixture.enrolAuthenticator()
            val factorId = fixture.service.factorsOf(fixture.principalId).single().id
            clock.advance(Duration.ofSeconds(TotpAlgorithm.STEP_SECONDS))

            val outcome = fixture.service.removeFactor(fixture.principalId, "alice", factorId, "000002", IP, AuditActor.SYSTEM_ACTOR)

            assertThat(outcome).`as`("removal without a code").isEqualTo(FactorRemoval.WrongCode)
            assertThat(fixture.service.factorsOf(fixture.principalId)).`as`("factors after the refused removal").hasSize(1)
        }
    }

    @Test
    fun `removing the last factor also removes the recovery codes`() {
        withFixture { fixture ->
            val seed = fixture.enrolAuthenticator()
            val factorId = fixture.service.factorsOf(fixture.principalId).single().id
            clock.advance(Duration.ofSeconds(TotpAlgorithm.STEP_SECONDS))
            fixture.service.removeFactor(fixture.principalId, "alice", factorId, currentCode(seed), IP, AuditActor.SYSTEM_ACTOR)

            val outcome = fixture.service.regenerateRecoveryCodes(fixture.principalId, "alice", "000003", IP, AuditActor.SYSTEM_ACTOR)

            assertThat(outcome).`as`("regeneration without a factor").isEqualTo(RecoveryCodesOutcome.NoFactor)
        }
    }

    @Test
    fun `regenerating recovery codes makes the old ones useless`() {
        withFixture { fixture ->
            val seed = fixture.enrolAuthenticator()
            val oldCode = regenerate(fixture)
            clock.advance(Duration.ofSeconds(TotpAlgorithm.STEP_SECONDS))
            fixture.service.regenerateRecoveryCodes(fixture.principalId, "alice", currentCode(seed), IP, AuditActor.SYSTEM_ACTOR)

            val outcome = fixture.service.completeChallenge(fixture.signInChallenge(), oldCode, IP)

            assertThat(outcome).`as`("old recovery code after regeneration").isEqualTo(ChallengeCompletion.WrongCode)
        }
    }

    @Test
    fun `an administrator reset removes every factor and writes one audit event`() {
        withFixture { fixture ->
            fixture.enrolAuthenticator()
            fixture.audit.clear()

            val removed = fixture.service.resetFactors(fixture.principalId, "alice", factorId = null, actor = AuditActor.SYSTEM_ACTOR)

            assertThat(removed).`as`("factors removed by the reset").isEqualTo(1)
            assertThat(fixture.service.requiresSecondFactor(fixture.principalId)).`as`("sign-in after the reset").isFalse()
            assertThat(fixture.audit.count { it.action == AuditAction.MFA_FACTORS_RESET }).`as`("reset audit events").isEqualTo(1)
        }
    }

    /** Regenerates the recovery codes with a fresh authenticator code and returns the first of the new set. */
    private fun regenerate(fixture: Fixture): String {
        clock.advance(Duration.ofSeconds(TotpAlgorithm.STEP_SECONDS))
        val seed = checkNotNull(fixture.seed) { "enrol an authenticator before regenerating recovery codes" }
        val outcome = fixture.service.regenerateRecoveryCodes(fixture.principalId, "alice", currentCode(seed), IP, AuditActor.SYSTEM_ACTOR)
        return (outcome as RecoveryCodesOutcome.Regenerated).codes.first()
    }

    /** Enrols a second authenticator. The proof for the enrolment is a current code of [proofSeed]. Returns the new seed. */
    private fun Fixture.enrolSecondAuthenticator(proofSeed: ByteArray): ByteArray {
        // The proof is a code of a new step: the code that confirmed the first factor is already used.
        clock.advance(Duration.ofSeconds(TotpAlgorithm.STEP_SECONDS))
        val started = service.enrolmentStart(principalId, "alice", code = currentCode(proofSeed), ip = IP) as TotpEnrolmentStart.Started
        val confirmation = service.confirmEnrolment(principalId, "alice", currentCode(started.seed), ip = IP)
        assertThat(confirmation).`as`("second enrolment confirmed").isInstanceOf(EnrolmentConfirmation.Confirmed::class.java)
        return started.seed
    }

    @Test
    fun `a second enrolment without a current code is refused`() {
        withFixture { fixture ->
            fixture.enrolAuthenticator()
            clock.advance(Duration.ofSeconds(TotpAlgorithm.STEP_SECONDS))

            val outcome = fixture.service.enrolmentStart(fixture.principalId, "alice", code = null, ip = IP)

            assertThat(outcome).`as`("second enrolment without a proof").isEqualTo(TotpEnrolmentStart.ProofRequired)
        }
    }

    @Test
    fun `a second enrolment with a current code of an existing factor is accepted`() {
        withFixture { fixture ->
            val seed = fixture.enrolAuthenticator()
            clock.advance(Duration.ofSeconds(TotpAlgorithm.STEP_SECONDS))

            val outcome = fixture.service.enrolmentStart(fixture.principalId, "alice", code = currentCode(seed), ip = IP)

            assertThat(outcome).`as`("second enrolment with a proof").isInstanceOf(TotpEnrolmentStart.Started::class.java)
        }
    }

    @Test
    fun `removing a factor with its own code is refused while another factor exists`() {
        withFixture { fixture ->
            val firstSeed = fixture.enrolAuthenticator()
            fixture.enrolSecondAuthenticator(firstSeed)
            clock.advance(Duration.ofSeconds(TotpAlgorithm.STEP_SECONDS))
            val first = fixture.service.factorsOf(fixture.principalId).first().id

            val outcome = fixture.service.removeFactor(fixture.principalId, "alice", first, currentCode(firstSeed), IP, AuditActor.SYSTEM_ACTOR)

            assertThat(outcome).`as`("removal with the factor's own code").isEqualTo(FactorRemoval.WrongCode)
            assertThat(fixture.service.factorsOf(fixture.principalId)).`as`("factors after the refusal").hasSize(2)
        }
    }

    @Test
    fun `removing a factor with the code of another factor is accepted`() {
        withFixture { fixture ->
            val firstSeed = fixture.enrolAuthenticator()
            val secondSeed = fixture.enrolSecondAuthenticator(firstSeed)
            clock.advance(Duration.ofSeconds(TotpAlgorithm.STEP_SECONDS))
            val first = fixture.service.factorsOf(fixture.principalId).first().id

            val outcome = fixture.service.removeFactor(fixture.principalId, "alice", first, currentCode(secondSeed), IP, AuditActor.SYSTEM_ACTOR)

            assertThat(outcome).`as`("removal with another factor's code").isEqualTo(FactorRemoval.Removed)
        }
    }

    @Test
    fun `a failed code locks the account for every address, not only the one that failed`() {
        withFixture(settings = LoginSettings(maxFailedAttempts = 3, lockoutMinutes = 5)) { fixture ->
            fixture.enrolAuthenticator()
            val seed = checkNotNull(fixture.seed)
            clock.advance(Duration.ofSeconds(TotpAlgorithm.STEP_SECONDS))
            val challenge = fixture.signInChallenge()
            repeat(3) { fixture.service.completeChallenge(challenge, wrongCodeFor(seed), "10.0.0.1") }

            val fromAnotherAddress = fixture.service.completeChallenge(challenge, currentCode(seed), "10.0.0.2")

            assertThat(fromAnotherAddress).`as`("right code from a rotated address").isInstanceOf(ChallengeCompletion.Blocked::class.java)
        }
    }

    private companion object {
        const val IP = "10.0.0.1"
    }

}
