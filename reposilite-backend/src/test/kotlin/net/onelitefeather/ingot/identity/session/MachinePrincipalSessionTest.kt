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
import net.onelitefeather.ingot.identity.login.LoginOutcome
import net.onelitefeather.ingot.identity.login.LoginSettings
import net.onelitefeather.ingot.identity.login.MutableClock
import net.onelitefeather.ingot.identity.login.ldap.InMemoryDirectory
import net.onelitefeather.ingot.identity.login.ldap.LegacyLdapEnvironment
import net.onelitefeather.ingot.identity.login.storage.LoginStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.reactive.Reference
import java.nio.file.Path
import java.time.Instant

/** The only producer of a machine principal login is the legacy LDAP provider; its session must work like any other. */
internal class MachinePrincipalSessionTest {

    private val clock = MutableClock(Instant.parse("2026-10-15T10:00:00Z"))

    @TempDir
    lateinit var workingDirectory: Path

    private lateinit var directory: InMemoryDirectory

    @BeforeEach
    fun startDirectory() {
        directory = InMemoryDirectory()
    }

    @AfterEach
    fun stopDirectory() {
        directory.close()
    }

    private fun withSessions(block: (LegacyLdapEnvironment, SessionService) -> Unit) {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            val environment = LegacyLdapEnvironment(directory, connection.database, clock)
            val sessions = SessionService(
                store = LoginStore(connection.database, clock),
                identities = environment.identities,
                signer = SessionSigner(ByteArray(32) { 3 }, clock),
                settings = Reference.reference(LoginSettings()),
                clock = clock,
                audit = {},
            )
            block(environment, sessions)
        }
    }

    private fun LegacyLdapEnvironment.legacyLogin() =
        (loginService().login("ldap", "alice", "alice-secret", ip = "10.0.0.1") as LoginOutcome.Succeeded).principal

    @Test
    fun `a machine principal that logged in through ldap gets a session that authenticates`() {
        withSessions { environment, sessions ->
            val principal = environment.legacyLogin()

            val started = sessions.start(principal, ip = "10.0.0.1", userAgent = null)

            assertThat(sessions.authenticate(started.sessionJwt)?.principalId)
                .describedAs("the session must resolve to the principal of the ldap token").isEqualTo(principal.id)
        }
    }

    @Test
    fun `the session of a machine principal can be refreshed`() {
        withSessions { environment, sessions ->
            val started = sessions.start(environment.legacyLogin(), ip = "10.0.0.1", userAgent = null)

            assertThat(sessions.refresh(started.refreshToken, ip = "10.0.0.1", userAgent = null)).describedAs("refresh of a machine session").isNotNull()
        }
    }

    @Test
    fun `a disabled machine principal loses its session`() {
        withSessions { environment, sessions ->
            val principal = environment.legacyLogin()
            val started = sessions.start(principal, ip = "10.0.0.1", userAgent = null)
            environment.identities.updatePrincipal(principal.id, displayName = null, disabled = true)

            assertThat(sessions.authenticate(started.sessionJwt)).describedAs("disabled principal").isNull()
            assertThat(sessions.refresh(started.refreshToken, ip = "10.0.0.1", userAgent = null)).describedAs("refresh of a disabled principal").isNull()
        }
    }

}
