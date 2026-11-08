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
import com.reposilite.plugin.api.SessionFamilyRevokedEvent
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
import java.time.Instant

internal class SessionFamilyRevokedEventTest {

    private val clock = MutableClock(Instant.parse("2026-10-15T10:00:00Z"))

    @TempDir
    lateinit var workingDirectory: Path

    private fun withSessions(block: (IdentityStore, SessionService, SessionSigner, MutableList<SessionFamilyRevokedEvent>) -> Unit) {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            val identities = IdentityStore(connection.database, clock)
            val signer = SessionSigner(ByteArray(32) { 3 }, clock)
            val events = mutableListOf<SessionFamilyRevokedEvent>()
            val sessions = SessionService(
                store = LoginStore(connection.database, clock),
                identities = identities,
                signer = signer,
                settings = Reference.reference(LoginSettings()),
                clock = clock,
                audit = {},
                publish = { event -> (event as? SessionFamilyRevokedEvent)?.let { events.add(it) } },
            )
            block(identities, sessions, signer, events)
        }
    }

    private fun IdentityStore.alice(): PrincipalRecord =
        createPrincipal(PrincipalKind.USER, "alice", "alice", disabled = false, createdAt = clock.instant())!!

    @Test
    fun `logout emits exactly one family revocation for the family of the session`() {
        withSessions { identities, sessions, signer, events ->
            // given: a logged in user
            val tokens = sessions.start(identities.alice(), ip = "10.0.0.1", userAgent = null)
            val familyId = signer.verify(tokens.sessionJwt)!!.familyId

            // when: the user logs out
            sessions.logout(tokens.refreshToken)

            // then: one event names the revoked family
            assertThat(events).containsExactly(SessionFamilyRevokedEvent(familyId))
        }
    }

    @Test
    fun `revoking all sessions of a principal emits one family revocation per family`() {
        withSessions { identities, sessions, signer, events ->
            // given: two logins of the same user, each a family of its own
            val alice = identities.alice()
            val first = sessions.start(alice, ip = "10.0.0.1", userAgent = null)
            val second = sessions.start(alice, ip = "10.0.0.2", userAgent = null)
            val families = listOf(first, second).map { signer.verify(it.sessionJwt)!!.familyId }

            // when: all sessions of the user are revoked
            sessions.revokeAllSessions(alice.id)

            // then: each family is announced once
            assertThat(events.map { it.familyId }).containsExactlyInAnyOrderElementsOf(families)
        }
    }
}
