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

internal class SessionServiceTest {

    private val clock = MutableClock(Instant.parse("2026-10-15T10:00:00Z"))

    @TempDir
    lateinit var workingDirectory: Path

    private class Fixture(val identities: IdentityStore, val sessions: SessionService)

    private fun withSessions(settings: LoginSettings = LoginSettings(), block: (Fixture) -> Unit) {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            val identities = IdentityStore(connection.database, clock)
            val sessions = SessionService(
                store = LoginStore(connection.database, clock),
                identities = identities,
                signer = SessionSigner(ByteArray(32) { 3 }, clock),
                settings = Reference.reference(settings),
                clock = clock,
            )
            block(Fixture(identities, sessions))
        }
    }

    private fun Fixture.user(name: String = "alice"): PrincipalRecord =
        identities.createPrincipal(PrincipalKind.USER, name, name, disabled = false, createdAt = clock.instant())!!

    @Test
    fun `a refresh family ends at the absolute lifetime, however often it is renewed`() {
        withSessions { fixture ->
            var refresh = fixture.sessions.start(fixture.user(), ip = "10.0.0.1", userAgent = null).refreshToken
            repeat(4) {
                clock.advance(Duration.ofDays(6))
                refresh = fixture.sessions.refresh(refresh, ip = "10.0.0.1", userAgent = null)!!.refreshToken
            }

            clock.advance(Duration.ofDays(6))

            assertThat(fixture.sessions.refresh(refresh, ip = "10.0.0.1", userAgent = null))
                .describedAs("renewals within the idle lifetime must not extend the family beyond thirty days from its login")
                .isNull()
        }
    }

    @Test
    fun `caps the expiry of a renewed refresh token at the absolute lifetime`() {
        withSessions { fixture ->
            val loggedIn = Instant.parse("2026-10-15T10:00:00Z")
            var refresh = fixture.sessions.start(fixture.user(), ip = "10.0.0.1", userAgent = null).refreshToken
            repeat(3) {
                clock.advance(Duration.ofDays(6))
                refresh = fixture.sessions.refresh(refresh, ip = "10.0.0.1", userAgent = null)!!.refreshToken
            }
            clock.advance(Duration.ofDays(6))

            val renewed = fixture.sessions.refresh(refresh, ip = "10.0.0.1", userAgent = null)!!

            assertThat(renewed.refreshExpiresAt)
                .describedAs("a renewal on day 24 may live until day 30, not for the full idle lifetime of seven days")
                .isEqualTo(loggedIn.plus(Duration.ofDays(30)))
            assertThat(renewed.refreshMaxAgeSeconds)
                .describedAs("the cookie must expire with the family")
                .isEqualTo(Duration.ofDays(6).seconds)
        }
    }

    @Test
    fun `a new login after the absolute lifetime starts a new family`() {
        withSessions { fixture ->
            val alice = fixture.user()
            val old = fixture.sessions.start(alice, ip = "10.0.0.1", userAgent = null)
            clock.advance(Duration.ofDays(30))
            assertThat(fixture.sessions.refresh(old.refreshToken, ip = "10.0.0.1", userAgent = null)).isNull()

            val fresh = fixture.sessions.start(alice, ip = "10.0.0.1", userAgent = null)

            assertThat(fixture.sessions.refresh(fresh.refreshToken, ip = "10.0.0.1", userAgent = null))
                .describedAs("the absolute lifetime belongs to one login, not to the user")
                .isNotNull()
        }
    }

    @Test
    fun `the absolute lifetime can be configured`() {
        withSessions(LoginSettings(refreshAbsoluteDays = 2)) { fixture ->
            val session = fixture.sessions.start(fixture.user(), ip = "10.0.0.1", userAgent = null)
            clock.advance(Duration.ofDays(1))
            val renewed = fixture.sessions.refresh(session.refreshToken, ip = "10.0.0.1", userAgent = null)!!

            clock.advance(Duration.ofDays(1))

            assertThat(fixture.sessions.refresh(renewed.refreshToken, ip = "10.0.0.1", userAgent = null))
                .describedAs("with an absolute lifetime of two days the family must end on day two")
                .isNull()
        }
    }

    @Test
    fun `a new session authenticates as its principal`() {
        withSessions { fixture ->
            val alice = fixture.user()

            val session = fixture.sessions.start(alice, ip = "10.0.0.1", userAgent = "test")

            assertThat(fixture.sessions.authenticate(session.sessionJwt)?.principalId)
                .describedAs("the session must identify the principal it was issued to")
                .isEqualTo(alice.id)
        }
    }

    @Test
    fun `a session stops working once its lifetime is over`() {
        withSessions { fixture ->
            val session = fixture.sessions.start(fixture.user(), ip = "10.0.0.1", userAgent = null)

            clock.advance(Duration.ofMinutes(15))

            assertThat(fixture.sessions.authenticate(session.sessionJwt)).describedAs("the default session lifetime is 15 minutes").isNull()
        }
    }

    @Test
    fun `a session of a disabled principal is refused`() {
        withSessions { fixture ->
            val alice = fixture.user()
            val session = fixture.sessions.start(alice, ip = "10.0.0.1", userAgent = null)

            fixture.identities.updatePrincipal(alice.id, displayName = null, disabled = true)

            assertThat(fixture.sessions.authenticate(session.sessionJwt)).describedAs("disabling must take effect on the next request").isNull()
        }
    }

    @Test
    fun `a refresh issues a new session and a new refresh token for the same family`() {
        withSessions { fixture ->
            val alice = fixture.user()
            val first = fixture.sessions.start(alice, ip = "10.0.0.1", userAgent = null)

            val refreshed = fixture.sessions.refresh(first.refreshToken, ip = "10.0.0.1", userAgent = null)

            assertThat(refreshed).describedAs("a valid refresh token must yield a new session").isNotNull
            assertThat(refreshed?.refreshToken).describedAs("the refresh token must rotate").isNotEqualTo(first.refreshToken)
            assertThat(fixture.sessions.authenticate(refreshed!!.sessionJwt)?.familyId)
                .describedAs("rotation keeps the family, so that a logout can revoke the whole chain")
                .isEqualTo(fixture.sessions.authenticate(first.sessionJwt)?.familyId)
        }
    }

    @Test
    fun `a spent refresh token cannot be used a second time`() {
        withSessions { fixture ->
            val first = fixture.sessions.start(fixture.user(), ip = "10.0.0.1", userAgent = null)
            fixture.sessions.refresh(first.refreshToken, ip = "10.0.0.1", userAgent = null)

            assertThat(fixture.sessions.refresh(first.refreshToken, ip = "10.0.0.1", userAgent = null))
                .describedAs("a refresh token is single use")
                .isNull()
        }
    }

    @Test
    fun `reuse of a spent refresh token revokes the whole family`() {
        withSessions { fixture ->
            val first = fixture.sessions.start(fixture.user(), ip = "10.0.0.1", userAgent = null)
            val second = fixture.sessions.refresh(first.refreshToken, ip = "10.0.0.1", userAgent = null)!!

            // An attacker replays the first token, which the legitimate client has already rotated.
            fixture.sessions.refresh(first.refreshToken, ip = "10.0.0.66", userAgent = null)

            assertThat(fixture.sessions.refresh(second.refreshToken, ip = "10.0.0.1", userAgent = null))
                .describedAs("the token the legitimate client holds must be revoked too")
                .isNull()
        }
    }

    @Test
    fun `a refresh token stops working once its lifetime is over`() {
        withSessions { fixture ->
            val session = fixture.sessions.start(fixture.user(), ip = "10.0.0.1", userAgent = null)

            clock.advance(Duration.ofHours(168))

            assertThat(fixture.sessions.refresh(session.refreshToken, ip = "10.0.0.1", userAgent = null))
                .describedAs("the default refresh lifetime is seven days")
                .isNull()
        }
    }

    @Test
    fun `logout revokes the refresh token`() {
        withSessions { fixture ->
            val session = fixture.sessions.start(fixture.user(), ip = "10.0.0.1", userAgent = null)

            fixture.sessions.logout(session.refreshToken)

            assertThat(fixture.sessions.refresh(session.refreshToken, ip = "10.0.0.1", userAgent = null))
                .describedAs("a logged out refresh token must not renew the session")
                .isNull()
        }
    }

    @Test
    fun `logout with an unknown refresh token does nothing and does not fail`() {
        withSessions { fixture ->
            fixture.sessions.logout("unknown-token")
            fixture.sessions.logout(null)

            assertThat(fixture.sessions.refresh("unknown-token", ip = "10.0.0.1", userAgent = null)).isNull()
        }
    }

    @Test
    fun `a refresh for a disabled principal is refused`() {
        withSessions { fixture ->
            val alice = fixture.user()
            val session = fixture.sessions.start(alice, ip = "10.0.0.1", userAgent = null)
            fixture.identities.updatePrincipal(alice.id, displayName = null, disabled = true)

            assertThat(fixture.sessions.refresh(session.refreshToken, ip = "10.0.0.1", userAgent = null))
                .describedAs("a disabled principal must not get a new session through refresh")
                .isNull()
        }
    }

    @Test
    fun `ends every session of a user at once`() {
        withSessions { fixture ->
            val alice = fixture.user()
            val onPhone = fixture.sessions.start(alice, ip = "10.0.0.1", userAgent = "phone")
            val onLaptop = fixture.sessions.start(alice, ip = "10.0.0.2", userAgent = "laptop")

            fixture.sessions.revokeAllSessions(alice.id)

            assertThat(fixture.sessions.refresh(onPhone.refreshToken, ip = "10.0.0.1", userAgent = null))
                .describedAs("a password change must end the session on the phone")
                .isNull()
            assertThat(fixture.sessions.refresh(onLaptop.refreshToken, ip = "10.0.0.2", userAgent = null))
                .describedAs("and the session on the laptop")
                .isNull()
        }
    }

    @Test
    fun `reports the lifetimes of the session and the refresh token for the cookies`() {
        withSessions { fixture ->
            val session = fixture.sessions.start(fixture.user(), ip = "10.0.0.1", userAgent = null)

            assertThat(session.sessionMaxAgeSeconds).describedAs("session cookie lifetime in seconds").isEqualTo(900)
            assertThat(session.refreshMaxAgeSeconds).describedAs("refresh cookie lifetime in seconds").isEqualTo(604_800)
            assertThat(session.csrfToken).describedAs("every session carries a csrf token").isNotBlank()
        }
    }

}
