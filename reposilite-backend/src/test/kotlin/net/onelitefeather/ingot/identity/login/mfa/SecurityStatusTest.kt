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
import net.onelitefeather.ingot.identity.session.SessionScope
import net.onelitefeather.ingot.identity.session.VerifiedSession
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** The fields of the security status of a user (design D14), read against the injected clock on a fresh database per test. */
internal class SecurityStatusTest {

    private val start = Instant.parse("2026-11-14T10:00:00Z")
    private val clock = MutableClock(start)

    @TempDir
    lateinit var workingDirectory: Path

    /** A confirmed authenticator with the given recovery code hashes, stored the way the enrolment stores them. */
    private fun MfaFixture.confirmedAuthenticator(principalId: Int, recoveryHashes: List<String>? = null) {
        factorStore.insertPendingTotp(principalId, "key-$principalId", "Authenticator app", "sealed", clock.instant())
        val pending = checkNotNull(factorStore.findPendingTotp(principalId)) { "the pending enrolment" }
        factorStore.confirmWithRecoveryCodes(pending.id, clock.instant(), step = 1, recoveryHashes = recoveryHashes)
    }

    private fun MfaFixture.adminsRequired(graceDays: Long = 14) {
        settings = LoginSettings(mfa = MfaSettings(requiredForAdmins = true, graceDays = graceDays))
    }

    private fun sessionOf(principalId: Int, authenticatedAt: Instant, scope: SessionScope) = VerifiedSession(
        principalId = principalId,
        name = "alice",
        kind = "USER",
        familyId = "family-1",
        expiresAt = authenticatedAt.plus(Duration.ofHours(1)),
        authenticatedAt = authenticatedAt,
        mfa = true,
        scope = scope,
    )

    @Test
    fun `the factor counts split authenticators from passkeys`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            val alice = fixture.user()
            fixture.confirmedAuthenticator(alice.id)
            fixture.addPasskey(alice.id)
            fixture.addPasskey(alice.id)

            val status = fixture.facade.securityStatusOf(alice.id, session = null)

            assertThat(status.authenticators).describedAs("authenticator count").isEqualTo(1)
            assertThat(status.passkeys).describedAs("passkey count").isEqualTo(2)
        }
    }

    @Test
    fun `the recovery codes left count only the codes that were not used`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            val alice = fixture.user()
            fixture.confirmedAuthenticator(alice.id, recoveryHashes = listOf("code-a", "code-b", "code-c"))
            fixture.factorStore.claimRecoveryCode(alice.id, "code-a", clock.instant())

            val status = fixture.facade.securityStatusOf(alice.id, session = null)

            assertThat(status.recoveryCodesLeft).describedAs("recovery codes left after one use").isEqualTo(2)
        }
    }

    @Test
    fun `a covered user in grace has the grace end at the required since plus the grace days`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            fixture.adminsRequired(graceDays = 14)
            val admin = fixture.user()
            fixture.identities.grantInstanceRole(admin.id, null)
            fixture.policy.covers(admin)
            clock.advance(Duration.ofDays(3))

            val status = fixture.facade.securityStatusOf(admin.id, session = null)

            assertThat(status.covered).describedAs("coverage of the administrator").isTrue()
            assertThat(status.graceUntil).describedAs("grace end").isEqualTo(start.plus(Duration.ofDays(14)))
        }
    }

    @Test
    fun `a covered user with a factor has no grace end`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            fixture.adminsRequired()
            val admin = fixture.user()
            fixture.identities.grantInstanceRole(admin.id, null)
            fixture.policy.covers(admin)
            fixture.confirmedAuthenticator(admin.id)

            val status = fixture.facade.securityStatusOf(admin.id, session = null)

            assertThat(status.graceUntil).describedAs("grace end of a user with a factor").isNull()
        }
    }

    @Test
    fun `a plain user is not covered and has no grace end`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            fixture.adminsRequired()
            val alice = fixture.user()

            val status = fixture.facade.securityStatusOf(alice.id, session = null)

            assertThat(status.covered).describedAs("coverage of a plain user").isFalse()
            assertThat(status.graceUntil).describedAs("grace end of a plain user").isNull()
        }
    }

    @Test
    fun `the policy is reported as configured`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            fixture.settings = LoginSettings(mfa = MfaSettings(requiredForAdmins = true, requiredForTeams = listOf("ops")))
            val alice = fixture.user()

            val status = fixture.facade.securityStatusOf(alice.id, session = null)

            assertThat(status.requiredForAdmins).describedAs("requiredForAdmins").isTrue()
            assertThat(status.requiredForTeams).describedAs("requiredForTeams").containsExactly("ops")
        }
    }

    @Test
    fun `a session reports its scope and the end of its step-up window`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            val alice = fixture.user()
            val authenticatedAt = start.plus(Duration.ofMinutes(1))

            val status = fixture.facade.securityStatusOf(alice.id, sessionOf(alice.id, authenticatedAt, SessionScope.ENROL))

            assertThat(status.scope).describedAs("scope of the session").isEqualTo(SessionScope.ENROL)
            assertThat(status.stepUpUntil).describedAs("step-up end of the session").isEqualTo(authenticatedAt.plus(Duration.ofMinutes(5)))
        }
    }

    @Test
    fun `a caller without a session has no step-up end and the full scope`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            val alice = fixture.user()

            val status = fixture.facade.securityStatusOf(alice.id, session = null)

            assertThat(status.stepUpUntil).describedAs("step-up end of a Basic or Bearer caller").isNull()
            assertThat(status.scope).describedAs("scope of a Basic or Bearer caller").isEqualTo(SessionScope.FULL)
        }
    }
}
