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
import net.onelitefeather.ingot.identity.login.LoginOutcome
import net.onelitefeather.ingot.identity.login.LoginSettings
import net.onelitefeather.ingot.identity.login.MfaSettings
import net.onelitefeather.ingot.identity.login.MutableClock
import net.onelitefeather.ingot.identity.login.ldap.InMemoryDirectory
import net.onelitefeather.ingot.identity.login.ldap.LdapLoginSettings
import net.onelitefeather.ingot.identity.model.PrincipalRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * The second factor at the sign-ins of this design (D12). An OpenID Connect or GitHub callback asks an uncovered user for nothing,
 * even with a factor, because the provider authenticated the user. A covered user with a factor is challenged, with the same
 * challenge as a password login. LDAP is a password login, so an LDAP user with a factor is challenged whether covered or not.
 */
internal class ExternalSecondFactorGateTest {

    private val start = Instant.parse("2026-11-14T10:00:00Z")
    private val clock = MutableClock(start)

    @TempDir
    lateinit var workingDirectory: Path

    private fun MfaFixture.covered() {
        settings = LoginSettings(mfa = MfaSettings(requiredForAdmins = true))
    }

    /** An instance administrator without a local password, such as one who signs in through a provider. */
    private fun MfaFixture.externalAdmin(name: String): PrincipalRecord =
        user(name).also { identities.grantInstanceRole(it.id, null) }

    /** The code of [seed] for the current step of the clock. */
    private fun MfaFixture.codeNow(seed: ByteArray): String =
        TotpAlgorithm.code(seed, clock.instant().epochSecond / TotpAlgorithm.STEP_SECONDS)

    /** Enrols an authenticator for [user] and moves the clock to the next step, so that the next code is a new one. */
    private fun MfaFixture.enrolAuthenticator(user: PrincipalRecord): ByteArray {
        val started = facade.startAuthenticatorEnrolment(user.id, user.name, code = null, ip = IP)
        assertThat(started).describedAs("start of the enrolment of ${user.name}").isInstanceOf(TotpEnrolmentStart.Started::class.java)
        val seed = (started as TotpEnrolmentStart.Started).seed
        val confirmation = facade.confirmAuthenticatorEnrolment(user.id, user.name, codeNow(seed), IP)
        assertThat(confirmation).describedAs("confirmation of the enrolment of ${user.name}").isInstanceOf(EnrolmentConfirmation.Confirmed::class.java)
        clock.advance(Duration.ofSeconds(TotpAlgorithm.STEP_SECONDS))
        return seed
    }

    private fun InMemoryDirectory.ldapSettings() = LdapLoginSettings(
        enabled = true,
        url = url,
        bindDn = InMemoryDirectory.SERVICE_DN,
        bindPassword = InMemoryDirectory.SERVICE_PASSWORD,
        userBaseDn = "ou=people,${InMemoryDirectory.BASE_DN}",
        autoCreateUsers = true,
    )

    @Test
    fun `an uncovered oidc user with an authenticator passes the external gate and starts a session`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            val olive = fixture.user("olive")
            fixture.enrolAuthenticator(olive)

            assertThat(fixture.facade.externalSecondFactor(olive, providerMfa = false, github = false))
                .describedAs("external gate of an uncovered oidc user with an authenticator")
                .isEqualTo(ExternalSecondFactor.Pass)
            assertThat(fixture.facade.startSession(olive, IP, null))
                .describedAs("session of the uncovered oidc user")
                .isNotNull()
        }
    }

    @Test
    fun `an uncovered github user with a passkey passes the external gate without a challenge`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            val octo = fixture.user("octo")
            fixture.addPasskey(octo.id)

            assertThat(fixture.facade.externalSecondFactor(octo, providerMfa = false, github = true))
                .describedAs("external gate of an uncovered github user with a passkey")
                .isEqualTo(ExternalSecondFactor.Pass)
        }
    }

    @Test
    fun `a covered oidc user with an authenticator is challenged at the external gate and passes with the code`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            fixture.covered()
            val olive = fixture.externalAdmin("olive")
            val seed = fixture.enrolAuthenticator(olive)

            val gate = fixture.facade.externalSecondFactor(olive, providerMfa = false, github = false)

            assertThat(gate).describedAs("external gate of a covered oidc user with an authenticator").isInstanceOf(ExternalSecondFactor.Challenge::class.java)
            val challenge = (gate as ExternalSecondFactor.Challenge).challenge
            assertThat(fixture.facade.completeSecondFactor(challenge, fixture.codeNow(seed), IP))
                .describedAs("completion of the challenge of the oidc callback with the code")
                .isInstanceOf(ChallengeCompletion.Verified::class.java)
        }
    }

    @Test
    fun `an uncovered ldap user with an authenticator is challenged at the password login`() {
        InMemoryDirectory().use { directory ->
            withMfaFixture(workingDirectory, clock, ldap = directory.ldapSettings()) { fixture ->
                fixture.user("admin")
                val alice = (fixture.facade.login("ldap", "alice", "alice-secret", IP) as LoginOutcome.Succeeded).principal
                fixture.enrolAuthenticator(alice)

                assertThat(fixture.facade.login("ldap", "alice", "alice-secret", IP))
                    .describedAs("password login of an uncovered ldap user with an authenticator")
                    .isInstanceOf(LoginOutcome.SecondFactorRequired::class.java)
            }
        }
    }

    @Test
    fun `a covered ldap user with an authenticator is challenged at the password login and passes with the code`() {
        InMemoryDirectory().use { directory ->
            withMfaFixture(workingDirectory, clock, ldap = directory.ldapSettings()) { fixture ->
                fixture.covered()
                fixture.user("admin")
                val alice = (fixture.facade.login("ldap", "alice", "alice-secret", IP) as LoginOutcome.Succeeded).principal
                fixture.identities.grantInstanceRole(alice.id, null)
                val seed = fixture.enrolAuthenticator(alice)

                val outcome = fixture.facade.login("ldap", "alice", "alice-secret", IP)

                assertThat(outcome).describedAs("password login of a covered ldap user with an authenticator").isInstanceOf(LoginOutcome.SecondFactorRequired::class.java)
                val challenge = (outcome as LoginOutcome.SecondFactorRequired).challenge
                assertThat(fixture.facade.completeSecondFactor(challenge, fixture.codeNow(seed), IP))
                    .describedAs("completion of the ldap challenge with the code")
                    .isInstanceOf(ChallengeCompletion.Verified::class.java)
            }
        }
    }

    private companion object {
        const val IP = "10.0.0.1"
    }
}
