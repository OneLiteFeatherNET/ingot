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
import net.onelitefeather.ingot.identity.login.MfaSettings
import net.onelitefeather.ingot.identity.login.LoginSettings
import net.onelitefeather.ingot.identity.login.MutableClock
import net.onelitefeather.ingot.identity.model.TeamMemberSource
import net.onelitefeather.ingot.identity.session.SessionScope
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Coverage of the second factor policy, the requiredSince bookkeeping and the grace period, against the injected clock. */
internal class SecondFactorPolicyTest {

    private val start = Instant.parse("2026-11-14T10:00:00Z")
    private val clock = MutableClock(start)

    @TempDir
    lateinit var workingDirectory: Path

    private fun MfaFixture.adminsRequired() {
        settings = LoginSettings(mfa = MfaSettings(requiredForAdmins = true))
    }

    private fun MfaFixture.grace(days: Long = 14) {
        settings = LoginSettings(mfa = MfaSettings(requiredForAdmins = true, graceDays = days))
    }

    @Test
    fun `a policy that covers nobody records no required since`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            val admin = fixture.user()
            fixture.identities.grantInstanceRole(admin.id, null)

            assertThat(fixture.policy.covers(admin)).describedAs("coverage with the policy off").isFalse()
            assertThat(fixture.identities.requiredSinceOf(admin.id)).describedAs("requiredSince with the policy off").isNull()
        }
    }

    @Test
    fun `an instance administrator is covered when administrators are required`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            fixture.adminsRequired()
            val admin = fixture.user()
            fixture.identities.grantInstanceRole(admin.id, null)

            assertThat(fixture.policy.covers(admin)).isTrue()
        }
    }

    @Test
    fun `a user who is not an administrator is not covered by the administrator rule`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            fixture.adminsRequired()
            val user = fixture.user()

            assertThat(fixture.policy.covers(user)).isFalse()
        }
    }

    @Test
    fun `a member of a team named in the policy is covered`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            fixture.settings = LoginSettings(mfa = MfaSettings(requiredForTeams = listOf("ops")))
            val member = fixture.user()
            val ops = checkNotNull(fixture.identities.createTeam("ops", "", null))
            fixture.identities.addMember(ops.id, member.id, TeamMemberSource.MANUAL)

            assertThat(fixture.policy.covers(member)).isTrue()
        }
    }

    @Test
    fun `a member of a team that the policy does not name is not covered`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            fixture.settings = LoginSettings(mfa = MfaSettings(requiredForTeams = listOf("ops")))
            val member = fixture.user()
            val devs = checkNotNull(fixture.identities.createTeam("devs", "", null))
            fixture.identities.addMember(devs.id, member.id, TeamMemberSource.MANUAL)

            assertThat(fixture.policy.covers(member)).isFalse()
        }
    }

    @Test
    fun `the required since is written once and a later sign-in does not move it`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            fixture.adminsRequired()
            val admin = fixture.user()
            fixture.identities.grantInstanceRole(admin.id, null)
            fixture.policy.covers(admin)
            clock.advance(Duration.ofDays(3))

            fixture.policy.covers(admin)

            assertThat(fixture.identities.requiredSinceOf(admin.id)).describedAs("requiredSince after a second sign-in").isEqualTo(start)
        }
    }

    @Test
    fun `a change of the policy does not clear the required since`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            fixture.adminsRequired()
            val admin = fixture.user()
            fixture.identities.grantInstanceRole(admin.id, null)
            fixture.policy.covers(admin)

            fixture.settings = LoginSettings()

            assertThat(fixture.policy.covers(admin)).describedAs("coverage after the policy is switched off").isFalse()
            assertThat(fixture.identities.requiredSinceOf(admin.id)).describedAs("requiredSince after the policy is switched off").isEqualTo(start)
        }
    }

    @Test
    fun `the grace period still holds one second before its end`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            fixture.grace(days = 14)
            val admin = fixture.userWithPassword()
            fixture.identities.grantInstanceRole(admin.id, null)
            fixture.policy.covers(admin)

            val justBeforeEnd = start.plus(Duration.ofDays(14)).minusSeconds(1)

            assertThat(fixture.policy.scopeOf(admin, justBeforeEnd)).describedAs("scope one second before the grace ends").isEqualTo(SessionScope.FULL)
        }
    }

    @Test
    fun `the grace period ends exactly after graceDays and the scope becomes enrol`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            fixture.grace(days = 14)
            val admin = fixture.userWithPassword()
            fixture.identities.grantInstanceRole(admin.id, null)
            fixture.policy.covers(admin)

            val end = start.plus(Duration.ofDays(14))

            assertThat(fixture.policy.scopeOf(admin, end)).describedAs("scope at the end of the grace").isEqualTo(SessionScope.ENROL)
        }
    }

    @Test
    fun `an administrator with a second factor keeps the full scope after the grace period`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            fixture.grace(days = 14)
            val admin = fixture.userWithPassword()
            fixture.identities.grantInstanceRole(admin.id, null)
            fixture.addPasskey(admin.id)
            fixture.policy.covers(admin)

            assertThat(fixture.policy.scopeOf(admin, start.plus(Duration.ofDays(30))))
                .describedAs("scope of an administrator who has a factor")
                .isEqualTo(SessionScope.FULL)
        }
    }

    @Test
    fun `a covered user without a local password keeps the full scope after the grace period`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            fixture.grace(days = 14)
            val admin = fixture.user()
            fixture.identities.grantInstanceRole(admin.id, null)
            fixture.policy.covers(admin)

            assertThat(fixture.policy.scopeOf(admin, start.plus(Duration.ofDays(30))))
                .describedAs("scope of a provider account without a local password (open question)")
                .isEqualTo(SessionScope.FULL)
        }
    }

    @Test
    fun `a covered user without a factor and with a local password gets the enrol scope after the grace period`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            fixture.grace(days = 14)
            val admin = fixture.userWithPassword()
            fixture.identities.grantInstanceRole(admin.id, null)
            fixture.policy.covers(admin)

            assertThat(fixture.policy.scopeOf(admin, start.plus(Duration.ofDays(14))))
                .describedAs("scope after the grace of a covered user without a factor")
                .isEqualTo(SessionScope.ENROL)
        }
    }

    @Test
    fun `a covered user without a factor keeps the full scope during the grace period`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            fixture.grace(days = 14)
            val admin = fixture.userWithPassword()
            fixture.identities.grantInstanceRole(admin.id, null)
            fixture.policy.covers(admin)

            assertThat(fixture.policy.scopeOf(admin, start.plus(Duration.ofDays(1))))
                .describedAs("scope during the grace")
                .isEqualTo(SessionScope.FULL)
        }
    }
}
