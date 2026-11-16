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
import net.onelitefeather.ingot.identity.login.LocalLoginProvider
import net.onelitefeather.ingot.identity.login.LoginOutcome
import net.onelitefeather.ingot.identity.login.LoginSettings
import net.onelitefeather.ingot.identity.login.MfaSettings
import net.onelitefeather.ingot.identity.login.MutableClock
import net.onelitefeather.ingot.identity.session.SessionScope
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * The login gate under the policy: a covered user with a factor is challenged, a covered user without one keeps the grace, the
 * external sign-ins follow the trust of the provider, and the step-up window reads its setting.
 */
internal class MfaLoginGateTest {

    private val start = Instant.parse("2026-11-14T10:00:00Z")
    private val clock = MutableClock(start)

    @TempDir
    lateinit var workingDirectory: Path

    @Test
    fun `an administrator with a second factor is challenged at sign-in`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            fixture.settings = LoginSettings(mfa = MfaSettings(requiredForAdmins = true))
            val admin = fixture.userWithPassword()
            fixture.identities.grantInstanceRole(admin.id, null)
            fixture.addPasskey(admin.id)

            val outcome = fixture.facade.login(LocalLoginProvider.ID, "alice", MFA_TEST_PASSWORD, IP)

            assertThat(outcome).describedAs("sign-in of an administrator with a factor").isInstanceOf(LoginOutcome.SecondFactorRequired::class.java)
        }
    }

    @Test
    fun `an administrator without a factor signs in during the grace period with a full session`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            fixture.settings = LoginSettings(mfa = MfaSettings(requiredForAdmins = true, graceDays = 14))
            val admin = fixture.userWithPassword()
            fixture.identities.grantInstanceRole(admin.id, null)

            val outcome = fixture.facade.login(LocalLoginProvider.ID, "alice", MFA_TEST_PASSWORD, IP)
            val session = fixture.facade.startSession(admin, IP, null)!!

            assertThat(outcome).describedAs("sign-in of an administrator without a factor").isInstanceOf(LoginOutcome.Succeeded::class.java)
            assertThat(fixture.facade.verifySession(session.sessionJwt)?.scope).describedAs("scope during the grace").isEqualTo(SessionScope.FULL)
        }
    }

    @Test
    fun `a login after the grace period gets the enrol scope at once`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            fixture.settings = LoginSettings(mfa = MfaSettings(requiredForAdmins = true, graceDays = 14))
            val admin = fixture.userWithPassword()
            fixture.identities.grantInstanceRole(admin.id, null)
            fixture.policy.covers(admin)
            clock.advance(Duration.ofDays(14))

            val session = fixture.facade.startSession(admin, IP, null)!!

            assertThat(fixture.facade.verifySession(session.sessionJwt)?.scope).describedAs("scope of a login after the grace").isEqualTo(SessionScope.ENROL)
        }
    }

    @Test
    fun `a trusted provider login of a covered user passes the gate without a challenge`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            fixture.settings = LoginSettings(mfa = MfaSettings(requiredForAdmins = true))
            val admin = fixture.user()
            fixture.identities.grantInstanceRole(admin.id, null)
            fixture.addPasskey(admin.id)

            val gate = fixture.facade.externalSecondFactor(admin, providerMfa = true, github = false)

            assertThat(gate).describedAs("gate of a provider login the provider verified").isEqualTo(ExternalSecondFactor.Pass)
        }
    }

    @Test
    fun `an untrusted provider login of a covered user with a factor is challenged`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            fixture.settings = LoginSettings(mfa = MfaSettings(requiredForAdmins = true))
            val admin = fixture.user()
            fixture.identities.grantInstanceRole(admin.id, null)
            fixture.addPasskey(admin.id)

            val gate = fixture.facade.externalSecondFactor(admin, providerMfa = false, github = false)

            assertThat(gate).describedAs("gate of an untrusted provider login").isInstanceOf(ExternalSecondFactor.Challenge::class.java)
        }
    }

    @Test
    fun `an untrusted provider login of a covered user without a factor passes and the grace rule applies to the session`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            fixture.settings = LoginSettings(mfa = MfaSettings(requiredForAdmins = true))
            val admin = fixture.user()
            fixture.identities.grantInstanceRole(admin.id, null)

            val gate = fixture.facade.externalSecondFactor(admin, providerMfa = false, github = false)

            assertThat(gate).describedAs("gate of a provider login without a factor").isEqualTo(ExternalSecondFactor.Pass)
        }
    }

    @Test
    fun `a covered github user without a factor passes the gate during the grace period`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            fixture.settings = LoginSettings(mfa = MfaSettings(requiredForAdmins = true))
            val admin = fixture.user()
            fixture.identities.grantInstanceRole(admin.id, null)

            val gate = fixture.facade.externalSecondFactor(admin, providerMfa = false, github = true)

            assertThat(gate).describedAs("gate of a covered github login without a factor").isEqualTo(ExternalSecondFactor.Pass)
        }
    }

    @Test
    fun `an uncovered external user with a factor is refused as before`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            val user = fixture.user()
            fixture.addPasskey(user.id)

            val gate = fixture.facade.externalSecondFactor(user, providerMfa = false, github = false)

            assertThat(gate).describedAs("gate of an uncovered external user with a factor").isEqualTo(ExternalSecondFactor.Refused)
        }
    }

    @Test
    fun `an uncovered external user without a factor passes`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            val user = fixture.user()

            val gate = fixture.facade.externalSecondFactor(user, providerMfa = false, github = false)

            assertThat(gate).describedAs("gate of an uncovered external user").isEqualTo(ExternalSecondFactor.Pass)
        }
    }

    @Test
    fun `the step-up window of a session follows stepUpMinutes`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            fixture.settings = LoginSettings(mfa = MfaSettings(stepUpMinutes = 1))
            val user = fixture.userWithPassword()
            fixture.addPasskey(user.id)
            val session = fixture.facade.startSession(user, IP, null, mfa = true)!!
            clock.advance(Duration.ofMinutes(2))
            val verified = checkNotNull(fixture.facade.verifySession(session.sessionJwt))

            val withOneMinute = fixture.facade.stepUpAllowed(verified, enrolment = false)
            fixture.settings = LoginSettings(mfa = MfaSettings(stepUpMinutes = 5))
            val withFiveMinutes = fixture.facade.stepUpAllowed(verified, enrolment = false)

            assertThat(withOneMinute).describedAs("step-up after two minutes with a one minute window").isFalse()
            assertThat(withFiveMinutes).describedAs("step-up after two minutes with a five minute window").isTrue()
        }
    }

    private companion object {
        const val IP = "10.0.0.1"
    }
}
