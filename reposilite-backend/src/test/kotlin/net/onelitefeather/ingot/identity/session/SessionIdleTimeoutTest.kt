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

internal class SessionIdleTimeoutTest {

    private val loginAt = Instant.parse("2026-10-15T10:00:00Z")
    private val clock = MutableClock(loginAt)

    @TempDir
    lateinit var workingDirectory: Path

    private fun withSessions(settings: LoginSettings, block: (SessionService, PrincipalRecord) -> Unit) {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            val identities = IdentityStore(connection.database, clock)
            val sessions = SessionService(
                store = LoginStore(connection.database, clock),
                identities = identities,
                signer = SessionSigner(ByteArray(32) { 3 }, clock),
                settings = Reference.reference(settings),
                clock = clock,
                audit = {},
            )
            val alice = identities.createPrincipal(PrincipalKind.USER, "alice", "alice", disabled = false, createdAt = clock.instant())!!
            block(sessions, alice)
        }
    }

    @Test
    fun `without a timeout the refresh token keeps the refresh lifetime of 168 hours`() {
        withSessions(LoginSettings()) { sessions, alice ->
            val session = sessions.start(alice, ip = "10.0.0.1", userAgent = null)

            assertThat(session.refreshExpiresAt)
                .describedAs("the default idle limit is the refresh lifetime, not a timeout")
                .isEqualTo(loginAt.plus(Duration.ofHours(168)))

            clock.advance(Duration.ofMinutes(31))

            assertThat(sessions.refresh(session.refreshToken, ip = "10.0.0.1", userAgent = null))
                .describedAs("a session idle for 31 minutes must still be renewed by default")
                .isNotNull()
        }
    }

    @Test
    fun `a timeout of 30 minutes refuses a refresh at minute 31`() {
        withSessions(LoginSettings(idleTimeoutMinutes = 30)) { sessions, alice ->
            val session = sessions.start(alice, ip = "10.0.0.1", userAgent = null)

            clock.advance(Duration.ofMinutes(31))

            assertThat(sessions.refresh(session.refreshToken, ip = "10.0.0.1", userAgent = null))
                .describedAs("the browser was idle for longer than the timeout and must sign in again")
                .isNull()
        }
    }

    @Test
    fun `a refresh inside the timeout restarts the idle time`() {
        withSessions(LoginSettings(idleTimeoutMinutes = 30)) { sessions, alice ->
            val session = sessions.start(alice, ip = "10.0.0.1", userAgent = null)

            clock.advance(Duration.ofMinutes(29))
            val renewed = sessions.refresh(session.refreshToken, ip = "10.0.0.1", userAgent = null)
            clock.advance(Duration.ofMinutes(29))

            assertThat(sessions.refresh(renewed!!.refreshToken, ip = "10.0.0.1", userAgent = null))
                .describedAs("a browser that renews every 29 minutes stays signed in")
                .isNotNull()
        }
    }

    @Test
    fun `the refresh token expires the timeout after its last renewal, not after the login`() {
        withSessions(LoginSettings(idleTimeoutMinutes = 30)) { sessions, alice ->
            val session = sessions.start(alice, ip = "10.0.0.1", userAgent = null)
            assertThat(session.refreshExpiresAt).describedAs("the first refresh token ends after the timeout").isEqualTo(loginAt.plus(Duration.ofMinutes(30)))

            clock.advance(Duration.ofMinutes(10))
            val renewed = sessions.refresh(session.refreshToken, ip = "10.0.0.1", userAgent = null)!!

            assertThat(renewed.refreshExpiresAt)
                .describedAs("the renewal at minute 10 is valid until minute 40")
                .isEqualTo(loginAt.plus(Duration.ofMinutes(40)))
            assertThat(renewed.refreshMaxAgeSeconds).describedAs("the cookie ends with the token").isEqualTo(Duration.ofMinutes(30).seconds)
        }
    }

    @Test
    fun `the absolute end of the login still wins over the timeout`() {
        withSessions(LoginSettings(refreshAbsoluteDays = 1, idleTimeoutMinutes = 30)) { sessions, alice ->
            var refresh = sessions.start(alice, ip = "10.0.0.1", userAgent = null).refreshToken
            repeat(71) {
                clock.advance(Duration.ofMinutes(20))
                refresh = sessions.refresh(refresh, ip = "10.0.0.1", userAgent = null)!!.refreshToken
            }
            clock.advance(Duration.ofMinutes(10))

            val lastRenewal = sessions.refresh(refresh, ip = "10.0.0.1", userAgent = null)!!

            assertThat(lastRenewal.refreshExpiresAt)
                .describedAs("a renewal inside the last 30 minutes of the day may not outlive the one-day login")
                .isEqualTo(loginAt.plus(Duration.ofDays(1)))

            clock.advance(Duration.ofMinutes(20))

            assertThat(sessions.refresh(lastRenewal.refreshToken, ip = "10.0.0.1", userAgent = null))
                .describedAs("after the absolute end the login is over, even though the timeout has not passed")
                .isNull()
        }
    }

}
