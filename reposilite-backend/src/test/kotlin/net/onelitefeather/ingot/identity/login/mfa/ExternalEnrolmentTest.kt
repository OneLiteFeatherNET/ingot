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

import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import net.onelitefeather.ingot.identity.login.LoginSettings
import net.onelitefeather.ingot.identity.login.MfaSettings
import net.onelitefeather.ingot.identity.login.MutableClock
import net.onelitefeather.ingot.identity.login.api.EnrolmentRoutes
import net.onelitefeather.ingot.identity.model.PrincipalRecord
import net.onelitefeather.ingot.identity.session.SessionScope
import net.onelitefeather.ingot.identity.session.VerifiedSession
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Directory and external accounts under the second factor policy (design D12): they enrol a factor with a sign-in that is recent
 * (`ath` within the step-up window), a GitHub user is never locked out during the grace period, and the restriction after the grace
 * period applies to them as it does to local users.
 */
internal class ExternalEnrolmentTest {

    private val start = Instant.parse("2026-11-14T10:00:00Z")
    private val clock = MutableClock(start)

    @TempDir
    lateinit var workingDirectory: Path

    private fun MfaFixture.covered(graceDays: Long = 14) {
        settings = LoginSettings(mfa = MfaSettings(requiredForAdmins = true, graceDays = graceDays))
    }

    /** An instance administrator without a local password, such as one who signs in through a provider. */
    private fun MfaFixture.externalAdmin(name: String): PrincipalRecord =
        user(name).also { identities.grantInstanceRole(it.id, null) }

    /** The browser session that a sign-in through a provider starts, as the routes see it after verification. */
    private fun MfaFixture.signIn(user: PrincipalRecord): VerifiedSession {
        val session = checkNotNull(facade.startSession(user, IP, null)) { "the session of ${user.name} could not be started" }
        return checkNotNull(facade.verifySession(session.sessionJwt)) { "the session of ${user.name} does not verify" }
    }

    /** The code of [seed] for the current step of the clock. */
    private fun MfaFixture.codeNow(seed: ByteArray): String =
        TotpAlgorithm.code(seed, clock.instant().epochSecond / TotpAlgorithm.STEP_SECONDS)

    /** Enrols an authenticator for [user] with the session that is current, and returns its seed. */
    private fun MfaFixture.enrolAuthenticator(user: PrincipalRecord): ByteArray {
        val started = facade.startAuthenticatorEnrolment(user.id, user.name, code = null, ip = IP)
        assertThat(started).describedAs("start of the enrolment of ${user.name}").isInstanceOf(TotpEnrolmentStart.Started::class.java)
        val seed = (started as TotpEnrolmentStart.Started).seed
        val confirmation = facade.confirmAuthenticatorEnrolment(user.id, user.name, codeNow(seed), IP)
        assertThat(confirmation).describedAs("confirmation of the enrolment of ${user.name}").isInstanceOf(EnrolmentConfirmation.Confirmed::class.java)
        clock.advance(Duration.ofSeconds(TotpAlgorithm.STEP_SECONDS))
        return seed
    }

    @Test
    fun `an ldap account with a fresh sign-in starts and confirms an authenticator enrolment`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            val account = fixture.user("carol")
            val session = fixture.signIn(account)

            assertThat(fixture.facade.stepUpAllowed(session, enrolment = true))
                .describedAs("first factor enrolment with the sign-in of this moment")
                .isTrue()
            fixture.enrolAuthenticator(account)

            assertThat(fixture.factors.factorsOf(account.id)).describedAs("factors of the account after the enrolment").hasSize(1)
        }
    }

    @Test
    fun `an ldap account with a fresh sign-in starts a passkey registration`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            val account = fixture.user("carol")
            val session = fixture.signIn(account)

            assertThat(fixture.facade.stepUpAllowed(session, enrolment = true))
                .describedAs("passkey enrolment with the sign-in of this moment")
                .isTrue()
            assertThat(fixture.facade.startPasskeyRegistration(account.id))
                .describedAs("passkey registration of the account")
                .isNotNull()
        }
    }

    @Test
    fun `a first factor enrolment is allowed 299 seconds after the sign-in`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            val account = fixture.user("carol")
            val session = fixture.signIn(account)
            clock.advance(Duration.ofSeconds(299))

            assertThat(fixture.facade.stepUpAllowed(session, enrolment = true))
                .describedAs("first factor enrolment 299 seconds after the sign-in")
                .isTrue()
        }
    }

    @Test
    fun `a first factor enrolment is refused 301 seconds after the sign-in of an ldap account`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            val account = fixture.user("carol")
            val session = fixture.signIn(account)
            clock.advance(Duration.ofSeconds(301))

            assertThat(fixture.facade.stepUpAllowed(session, enrolment = true))
                .describedAs("first factor enrolment 301 seconds after the sign-in, which the route answers with step_up_required")
                .isFalse()
        }
    }

    @Test
    fun `a covered github user without a factor signs in with a full session during the grace period`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            fixture.covered(graceDays = 14)
            val octo = fixture.externalAdmin("octo")

            assertThat(fixture.facade.externalSecondFactor(octo, providerMfa = false, github = true))
                .describedAs("gate of a covered github sign-in without a factor during the grace period")
                .isEqualTo(ExternalSecondFactor.Pass)
            assertThat(fixture.signIn(octo).scope)
                .describedAs("scope of the github sign-in during the grace period")
                .isEqualTo(SessionScope.FULL)
        }
    }

    @Test
    fun `a covered github user enrols a factor with the sign-in of the grace period`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            fixture.covered(graceDays = 14)
            val octo = fixture.externalAdmin("octo")
            fixture.facade.externalSecondFactor(octo, providerMfa = false, github = true)
            val session = fixture.signIn(octo)

            assertThat(fixture.facade.stepUpAllowed(session, enrolment = true))
                .describedAs("first factor enrolment with the github sign-in of this moment")
                .isTrue()
            fixture.enrolAuthenticator(octo)
        }
    }

    @Test
    fun `the next github sign-in of an enrolled user is challenged and passes with the code`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            fixture.covered(graceDays = 14)
            val octo = fixture.externalAdmin("octo")
            fixture.facade.externalSecondFactor(octo, providerMfa = false, github = true)
            fixture.signIn(octo)
            val seed = fixture.enrolAuthenticator(octo)

            val gate = fixture.facade.externalSecondFactor(octo, providerMfa = false, github = true)

            assertThat(gate).describedAs("gate of the next github sign-in of a user with a factor").isInstanceOf(ExternalSecondFactor.Challenge::class.java)
            val challenge = (gate as ExternalSecondFactor.Challenge).challenge
            assertThat(fixture.facade.completeSecondFactor(challenge, fixture.codeNow(seed), IP))
                .describedAs("completion of the github sign-in challenge with the code")
                .isInstanceOf(ChallengeCompletion.Verified::class.java)
        }
    }

    @Test
    fun `a covered github user without a factor gets the enrol scope at the sign-in after the grace period`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            fixture.covered(graceDays = 14)
            val octo = fixture.externalAdmin("octo")
            fixture.facade.externalSecondFactor(octo, providerMfa = false, github = true)
            clock.advance(Duration.ofDays(14))

            assertThat(fixture.facade.externalSecondFactor(octo, providerMfa = false, github = true))
                .describedAs("gate of a covered github sign-in after the grace period")
                .isEqualTo(ExternalSecondFactor.Pass)
            val session = fixture.signIn(octo)

            assertThat(session.scope).describedAs("scope of the github sign-in after the grace period").isEqualTo(SessionScope.ENROL)
            assertThat(session.authenticatedAt).describedAs("ath of the new github sign-in").isEqualTo(clock.instant())
        }
    }

    @Test
    fun `a covered oidc user without a factor gets the enrol scope at login after the grace period`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            fixture.covered(graceDays = 14)
            val user = fixture.externalAdmin("olive")
            fixture.facade.externalSecondFactor(user, providerMfa = false, github = false)
            clock.advance(Duration.ofDays(14))

            assertThat(fixture.signIn(user).scope)
                .describedAs("scope of a covered provider login after the grace period")
                .isEqualTo(SessionScope.ENROL)
        }
    }

    @Test
    fun `a restricted external session answers a route outside the factor set with mfa_enrolment_required`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            fixture.covered(graceDays = 14)
            val user = fixture.externalAdmin("olive")
            fixture.facade.externalSecondFactor(user, providerMfa = false, github = false)
            clock.advance(Duration.ofDays(14))
            val session = fixture.signIn(user)

            assertThat(session.scope).describedAs("scope before the route check").isEqualTo(SessionScope.ENROL)
            assertThat(EnrolmentRoutes.refused(session.scope, "GET", "/api/identity/me/tokens"))
                .describedAs("a token list in a restricted session")
                .isTrue()
        }
    }

    @Test
    fun `a restricted session refreshed with an old sign-in cannot start a factor enrolment`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            fixture.covered(graceDays = 1)
            val user = fixture.externalAdmin("olive")
            fixture.facade.externalSecondFactor(user, providerMfa = false, github = false)
            val login = checkNotNull(fixture.facade.startSession(user, IP, null))
            clock.advance(Duration.ofDays(1))

            val renewed = checkNotNull(fixture.facade.refreshSession(login.refreshToken, IP, null)) { "the refresh is refused" }
            val verified = checkNotNull(fixture.facade.verifySession(renewed.sessionJwt))

            assertThat(verified.scope).describedAs("scope after the grace period at the refresh").isEqualTo(SessionScope.ENROL)
            assertThat(verified.authenticatedAt).describedAs("ath after the refresh, which does not move it").isEqualTo(start)
            assertThat(fixture.facade.stepUpAllowed(verified, enrolment = true))
                .describedAs("first factor enrolment with a sign-in older than the window, which the route answers with step_up_required")
                .isFalse()
        }
    }

    @Test
    fun `a local password user starts a first factor enrolment and gets the enrol scope after the grace period`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            fixture.covered(graceDays = 14)
            val erin = fixture.userWithPassword("erin")
            fixture.identities.grantInstanceRole(erin.id, null)
            fixture.signIn(erin)

            assertThat(fixture.facade.startAuthenticatorEnrolment(erin.id, "erin", code = null, ip = IP))
                .describedAs("enrolment of a local password user")
                .isInstanceOf(TotpEnrolmentStart.Started::class.java)
            clock.advance(Duration.ofDays(14))

            assertThat(fixture.signIn(erin).scope)
                .describedAs("scope of a local password user after the grace period")
                .isEqualTo(SessionScope.ENROL)
        }
    }

    private companion object {
        const val IP = "10.0.0.1"
    }

}
