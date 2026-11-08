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

package net.onelitefeather.ingot.cluster

import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.plugin.Extensions
import com.reposilite.plugin.api.SessionFamilyRevokedEvent
import net.onelitefeather.ingot.identity.login.LoginFacade
import net.onelitefeather.ingot.identity.login.LoginSettings
import net.onelitefeather.ingot.identity.login.MutableClock
import net.onelitefeather.ingot.identity.login.storage.LoginStore
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.session.SessionService
import net.onelitefeather.ingot.identity.session.SessionSigner
import net.onelitefeather.ingot.identity.storage.IdentityStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.reactive.Reference
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A session revoked on one instance must be refused on the others. Two instances share one database and one signing key,
 * and the revocation travels over an in-memory bus. Waits are bounded by latches, and time moves only through the clock.
 */
internal class ClusterSessionRevocationTest {

    private val now = Instant.parse("2026-10-15T10:00:00Z")
    private val clock = MutableClock(now)
    private val hub = InMemoryClusterHub()

    @TempDir
    lateinit var workingDirectory: Path

    @AfterEach
    fun closeHub() {
        hub.close()
    }

    /** One instance: its sessions, and the plugin that carries the revocations of its sessions. */
    private class Node(val extensions: Extensions, val plugin: ClusterPlugin, val sessions: SessionService)

    private fun startNode(instanceId: String, identities: IdentityStore, store: LoginStore, signer: SessionSigner): Node {
        val variables = mapOf(
            "INGOT_CLUSTER_URL" to "nats://hub.example:4222",
            "INGOT_CLUSTER_INSTANCE_ID" to instanceId,
            // no running instance here: console fan-out needs the journalist of a running instance
            "INGOT_CLUSTER_CONSOLE" to "false",
        )
        val extensions = Extensions(InMemoryLogger())
        val sessions = SessionService(
            store = store,
            identities = identities,
            signer = signer,
            settings = Reference.reference(LoginSettings()),
            clock = clock,
            audit = {},
            publish = { extensions.emitEvent(it) },
        )
        extensions.registerFacade(LoginFacade(service = null, sessions = sessions, ownTokens = null))
        val plugin = ClusterPlugin(
            sources = ClusterSources(property = { null }, variable = variables::get, hostName = { "host" }),
            transports = mapOf("nats" to hub.factory()),
            clusterIdentifier = { "cluster-1" },
            clock = clock,
        )
        extensions.registerPlugin(plugin)
        plugin.initialize()
        return Node(extensions, plugin, sessions)
    }

    private fun withCluster(block: (IdentityStore, Node, Node, SessionSigner) -> Unit) {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            val identities = IdentityStore(connection.database, clock)
            val store = LoginStore(connection.database, clock)
            val signer = SessionSigner(ByteArray(32) { 3 }, clock)
            val a = startNode("ingot-a", identities, store, signer)
            val b = startNode("ingot-b", identities, store, signer)
            block(identities, a, b, signer)
        }
    }

    private fun IdentityStore.alice() =
        createPrincipal(PrincipalKind.USER, "alice", "alice", disabled = false, createdAt = clock.instant())!!

    @Test
    fun `a session logged out on another instance is refused on this instance`() {
        withCluster { identities, a, b, _ ->
            // given: a session started on instance A, which instance B accepts
            val session = a.sessions.start(identities.alice(), ip = "10.0.0.1", userAgent = null)
            assertThat(b.sessions.authenticate(session.sessionJwt)).describedAs("session accepted before the logout").isNotNull()
            val received = CountDownLatch(1)
            b.plugin.onReceived(ClusterEventType.SESSION_REVOKED) { received.countDown() }

            // when: the user logs out on instance A and the revocation is received on instance B
            a.sessions.logout(session.refreshToken)
            assertThat(received.await(5, TimeUnit.SECONDS)).describedAs("revocation applied within five seconds").isTrue()

            // then: the session token of that family is refused on instance B before its expiry
            assertThat(b.sessions.authenticate(session.sessionJwt)).describedAs("session refused after the revocation").isNull()
        }
    }

    @Test
    fun `a logout publishes exactly one session revocation carrying the family`() {
        withCluster { identities, a, b, signer ->
            // given: a session on instance A and a listener for its revocation on instance B
            val session = a.sessions.start(identities.alice(), ip = "10.0.0.1", userAgent = null)
            val familyId = signer.verify(session.sessionJwt)!!.familyId
            val families = CopyOnWriteArrayList<String>()
            val received = CountDownLatch(1)
            b.plugin.onReceived(ClusterEventType.SESSION_REVOKED) { envelope ->
                families.add(envelope.payload.get("family").asText())
                received.countDown()
            }

            // when: the user logs out on instance A
            a.sessions.logout(session.refreshToken)
            assertThat(received.await(5, TimeUnit.SECONDS)).describedAs("revocation applied within five seconds").isTrue()

            // then: instance A published the family once
            assertThat(families).containsExactly(familyId)
            assertThat(hub.publishedBy("ingot-a")).isEqualTo(1)
        }
    }

    @Test
    fun `applying a received session revocation publishes nothing on the receiving instance`() {
        withCluster { identities, a, b, _ ->
            // given: a session on instance A and a listener on instance B
            val session = a.sessions.start(identities.alice(), ip = "10.0.0.1", userAgent = null)
            val received = CountDownLatch(1)
            b.plugin.onReceived(ClusterEventType.SESSION_REVOKED) { received.countDown() }

            // when: the logout on A is received on B
            a.sessions.logout(session.refreshToken)
            assertThat(received.await(5, TimeUnit.SECONDS)).describedAs("revocation applied within five seconds").isTrue()

            // then: applying it caused no event on instance B
            assertThat(hub.publishedBy("ingot-b")).isZero()
        }
    }

    @Test
    fun `without a received revocation a session token is accepted until its expiry`() {
        withCluster { identities, _, b, _ ->
            // given: a session that instance B issued, and no revocation event for it
            val session = b.sessions.start(identities.alice(), ip = "10.0.0.1", userAgent = null)

            // when: the clock moves to one second before the expiry of the session token
            clock.advance(Duration.between(clock.instant(), session.sessionExpiresAt).minusSeconds(1))

            // then: the token is still accepted, because nothing announced its revocation
            assertThat(b.sessions.authenticate(session.sessionJwt)).describedAs("session accepted before its expiry").isNotNull()

            // when: the expiry is reached
            clock.advance(Duration.ofSeconds(1))

            // then: the token is refused
            assertThat(b.sessions.authenticate(session.sessionJwt)).describedAs("session refused at its expiry").isNull()
        }
    }

    @Test
    fun `the revocation event of a logout is emitted on the instance that logged out`() {
        withCluster { identities, a, _, _ ->
            // given: a listener for the local event of instance A
            val local = CopyOnWriteArrayList<SessionFamilyRevokedEvent>()
            a.extensions.registerEvent { event: SessionFamilyRevokedEvent -> local.add(event) }
            val session = a.sessions.start(identities.alice(), ip = "10.0.0.1", userAgent = null)

            // when: the user logs out
            a.sessions.logout(session.refreshToken)

            // then: the local event is emitted once, as before the cluster bus existed
            assertThat(local).hasSize(1)
        }
    }
}
