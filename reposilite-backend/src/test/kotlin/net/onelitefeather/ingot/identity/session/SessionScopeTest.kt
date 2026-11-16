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

package net.onelitefeather.ingot.identity.session

import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import net.onelitefeather.ingot.identity.login.LoginSettings
import net.onelitefeather.ingot.identity.login.MfaSettings
import net.onelitefeather.ingot.identity.login.MutableClock
import net.onelitefeather.ingot.identity.login.mfa.MfaFixture
import net.onelitefeather.ingot.identity.login.mfa.withMfaFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * The scope claim of a browser session under the grace period: full while the grace lasts, enrol afterwards. The scope is
 * applied when a session is issued, so a refresh after the grace ends the live session, and no session is revoked.
 */
internal class SessionScopeTest {

    private val start = Instant.parse("2026-11-14T10:00:00Z")
    private val clock = MutableClock(start)

    @TempDir
    lateinit var workingDirectory: Path

    private fun MfaFixture.adminsCoveredWithGrace(graceDays: Long) {
        settings = LoginSettings(mfa = MfaSettings(requiredForAdmins = true, graceDays = graceDays))
    }

    @Test
    fun `a refresh inside the grace period keeps the full scope`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            fixture.adminsCoveredWithGrace(graceDays = 14)
            val admin = fixture.userWithPassword()
            fixture.identities.grantInstanceRole(admin.id, null)
            val session = fixture.sessions.start(admin, ip = IP, userAgent = null)
            clock.advance(Duration.ofDays(1))

            val renewed = fixture.sessions.refresh(session.refreshToken, ip = IP, userAgent = null)!!

            assertThat(fixture.signer.verify(renewed.sessionJwt)?.scope).describedAs("scope inside the grace").isEqualTo(SessionScope.FULL)
        }
    }

    @Test
    fun `a refresh after the grace period issues the enrol scope and keeps the session`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            fixture.adminsCoveredWithGrace(graceDays = 14)
            val admin = fixture.userWithPassword()
            fixture.identities.grantInstanceRole(admin.id, null)
            var session = fixture.sessions.start(admin, ip = IP, userAgent = null)
            repeat(14) {
                // A refresh token lives a week, so the browser renews it once a day for the two weeks of the grace.
                clock.advance(Duration.ofDays(1))
                session = fixture.sessions.refresh(session.refreshToken, ip = IP, userAgent = null)!!
            }

            assertThat(fixture.signer.verify(session.sessionJwt)?.scope).describedAs("scope after the grace").isEqualTo(SessionScope.ENROL)
            assertThat(fixture.sessions.authenticate(session.sessionJwt)).describedAs("the restricted session is still a session").isNotNull
        }
    }

    @Test
    fun `a login after the grace period issues the enrol scope at once`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            fixture.adminsCoveredWithGrace(graceDays = 0)
            val admin = fixture.userWithPassword()
            fixture.identities.grantInstanceRole(admin.id, null)

            val session = fixture.sessions.start(admin, ip = IP, userAgent = null)

            assertThat(fixture.signer.verify(session.sessionJwt)?.scope).describedAs("scope of a login after the grace").isEqualTo(SessionScope.ENROL)
        }
    }

    @Test
    fun `the scope claim of a session that carries enrol is read as enrol`() {
        withMfaFixture(workingDirectory, clock) { fixture ->
            val token = fixture.signer.issue(
                principalId = 1,
                name = "alice",
                kind = "USER",
                familyId = "family",
                expiresAt = start.plusSeconds(900),
                authenticatedAt = start,
                scope = SessionScope.ENROL,
            )

            assertThat(fixture.signer.verify(token)?.scope).describedAs("scope read from the claim").isEqualTo(SessionScope.ENROL)
        }
    }

    private companion object {
        const val IP = "10.0.0.1"
    }
}
