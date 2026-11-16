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

import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import net.onelitefeather.ingot.identity.login.LoginSettings
import net.onelitefeather.ingot.identity.login.MutableClock
import net.onelitefeather.ingot.identity.login.storage.LoginStore
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.model.PrincipalRecord
import net.onelitefeather.ingot.identity.storage.IdentityStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.reactive.Reference
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

/** The authentication time, the second factor and the step-up of a browser session, against the injected clock. */
internal class SessionStepUpTest {

    private val loggedIn = Instant.parse("2026-11-14T10:00:00Z")
    private val clock = MutableClock(loggedIn)

    @TempDir
    lateinit var workingDirectory: Path

    private class Fixture(val identities: IdentityStore, val sessions: SessionService, val signer: SessionSigner)

    private fun withSessions(block: (Fixture) -> Unit) {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            val identities = IdentityStore(connection.database, clock)
            val signer = SessionSigner(ByteArray(32) { 3 }, clock)
            val sessions = SessionService(
                store = LoginStore(connection.database, clock),
                identities = identities,
                signer = signer,
                settings = Reference.reference(LoginSettings()),
                clock = clock,
                audit = {},
            )
            block(Fixture(identities, sessions, signer))
        }
    }

    private fun Fixture.user(): PrincipalRecord =
        identities.createPrincipal(PrincipalKind.USER, "alice", "alice", disabled = false, createdAt = clock.instant())!!

    @Test
    fun `a session issued at login carries the authentication time of the clock`() {
        withSessions { fixture ->
            val session = fixture.sessions.start(fixture.user(), ip = "10.0.0.1", userAgent = null)

            val claims = fixture.signer.verify(session.sessionJwt)

            assertThat(claims?.authenticatedAt).describedAs("ath of a login").isEqualTo(loggedIn)
        }
    }

    @Test
    fun `a session issued at login carries no second factor`() {
        withSessions { fixture ->
            val session = fixture.sessions.start(fixture.user(), ip = "10.0.0.1", userAgent = null)

            assertThat(fixture.signer.verify(session.sessionJwt)?.mfa).describedAs("mfa of a password login").isFalse()
        }
    }

    @Test
    fun `a session started after a second factor carries mfa`() {
        withSessions { fixture ->
            val session = fixture.sessions.start(fixture.user(), ip = "10.0.0.1", userAgent = null, mfa = true)

            assertThat(fixture.signer.verify(session.sessionJwt)?.mfa).describedAs("mfa after a second factor").isTrue()
        }
    }

    @Test
    fun `refresh keeps the authentication time and the second factor, and does not move the authentication time`() {
        withSessions { fixture ->
            val session = fixture.sessions.start(fixture.user(), ip = "10.0.0.1", userAgent = null, mfa = true)
            clock.advance(Duration.ofMinutes(2))

            val renewed = fixture.sessions.refresh(session.refreshToken, ip = "10.0.0.1", userAgent = null)!!
            val claims = fixture.signer.verify(renewed.sessionJwt)

            assertThat(claims?.authenticatedAt).describedAs("ath after a renewal").isEqualTo(loggedIn)
            assertThat(claims?.mfa).describedAs("mfa after a renewal").isTrue()
        }
    }

    @Test
    fun `a session issued before the claim existed is not fresh`() {
        withSessions { fixture ->
            val oldToken = fixture.signer.issue(principalId = 1, name = "alice", kind = "USER", familyId = "old-family", expiresAt = clock.instant().plusSeconds(900))

            val claims = fixture.signer.verify(oldToken)

            assertThat(claims).describedAs("an old session is still a session").isNotNull()
            assertThat(claims?.authenticatedAt).describedAs("ath of an old session").isNull()
            assertThat(isWithinStepUpWindow(claims?.authenticatedAt, clock.instant(), Duration.ofMinutes(STEP_UP_MINUTES)))
                .describedAs("an old session never passes the window")
                .isFalse()
        }
    }

    @Test
    fun `a step-up reissues the session of the same family with a new authentication time and a second factor`() {
        withSessions { fixture ->
            val session = fixture.sessions.start(fixture.user(), ip = "10.0.0.1", userAgent = null)
            val before = fixture.signer.verify(session.sessionJwt)!!
            clock.advance(Duration.ofMinutes(10))

            val stepped = fixture.sessions.stepUp(before, ip = "10.0.0.1", userAgent = null)!!
            val claims = fixture.signer.verify(stepped.sessionJwt)

            assertThat(claims?.familyId).describedAs("sid of the step-up session").isEqualTo(before.familyId)
            assertThat(claims?.authenticatedAt).describedAs("ath after the step-up").isEqualTo(clock.instant())
            assertThat(claims?.mfa).describedAs("mfa after the step-up").isTrue()
        }
    }

    @Test
    fun `the refresh token of a step-up carries the new authentication time`() {
        withSessions { fixture ->
            val session = fixture.sessions.start(fixture.user(), ip = "10.0.0.1", userAgent = null)
            clock.advance(Duration.ofMinutes(10))
            val stepped = fixture.sessions.stepUp(fixture.signer.verify(session.sessionJwt)!!, ip = "10.0.0.1", userAgent = null)!!
            clock.advance(Duration.ofMinutes(1))

            val renewed = fixture.sessions.refresh(stepped.refreshToken, ip = "10.0.0.1", userAgent = null)!!

            assertThat(fixture.signer.verify(renewed.sessionJwt)?.authenticatedAt).isEqualTo(clock.instant().minusSeconds(60))
        }
    }

    @Test
    fun `a step-up is refused for a session whose family was logged out`() {
        withSessions { fixture ->
            val session = fixture.sessions.start(fixture.user(), ip = "10.0.0.1", userAgent = null)
            val verified = fixture.signer.verify(session.sessionJwt)!!
            fixture.sessions.logout(session.refreshToken, ip = "10.0.0.1")

            assertThat(fixture.sessions.stepUp(verified, ip = "10.0.0.1", userAgent = null))
                .describedAs("a revoked family must not be revived by a step-up")
                .isNull()
        }
    }

}
