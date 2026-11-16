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
package net.onelitefeather.ingot.identity.service

import com.reposilite.audit.api.AuditEvent
import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.plugin.Extensions
import com.reposilite.shared.EntityTagSigner
import com.reposilite.token.application.AccessTokenComponents
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.storage.AuthorizationRevision
import net.onelitefeather.ingot.identity.storage.IdentityStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.jetbrains.exposed.v1.jdbc.Database
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Two replicas, two facades, one database file. Replica A grants the instance role. While A's guarded write is still open,
 * an audit event is emitted inside its transaction, and the listener makes replica B write. B is refused, so A's decision
 * and A's write stay one unit. Once A has committed, B writes on the state A committed.
 */
internal class GuardedFacadeWriteTest {

    private val journalist = InMemoryLogger()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)
    private val createdAt: Instant = Instant.parse("2026-10-14T08:00:00Z")

    @TempDir
    lateinit var workingDirectory: Path

    @Test
    fun `a write of another replica is refused while a guarded facade write is still open`() {
        val url = "jdbc:sqlite:${workingDirectory.resolve("identity.db").toAbsolutePath()}?busy_timeout=0"
        DatabaseConnectionFactory.createConnection(workingDirectory, url, 1).use { connectionA ->
            DatabaseConnectionFactory.createConnection(workingDirectory, url, 1).use { connectionB ->
                // given: two principals, written by replica A
                val storeA = IdentityStore(connectionA.database, clock)
                val storeB = IdentityStore(connectionB.database, clock)
                storeA.createPrincipal(PrincipalKind.USER, "alice", "Alice", disabled = false, createdAt = createdAt)
                val bob = storeA.createPrincipal(PrincipalKind.USER, "bob", "Bob", disabled = false, createdAt = createdAt)!!

                // and: replica B's write is attempted from inside A's transaction, when A emits its audit event
                val refusals = mutableListOf<Throwable>()
                val extensionsA = Extensions(journalist)
                extensionsA.registerEvent<AuditEvent> {
                    runCatching { storeB.updatePrincipal(bob.id, "Changed by B", null) }.exceptionOrNull()?.let { refusals += it }
                }
                val facadeA = facade(connectionA.database, storeA, extensionsA)

                // when: replica A grants the instance role, which emits the audit event inside its transaction
                facadeA.grantPrincipalInstanceAdmin("alice", ifMatch = null)

                // then: replica B was refused while A's guarded write was open
                assertThat(refusals).describedAs("replica B must be refused while A holds the revision").hasSize(1)

                // when: A has committed, replica B writes on the committed state
                storeB.updatePrincipal(bob.id, "Changed by B", null)

                // then: the write is visible and the instance role of A's write is committed
                assertThat(storeB.findPrincipal("bob")?.displayName).isEqualTo("Changed by B")
                assertThat(storeB.findPrincipal("alice")?.let { storeB.hasDirectInstanceRole(it.id) }).isTrue()
                assertThat(AuthorizationRevision(storeB).refreshOnce()).isEqualTo(storeB.policyRevision())
            }
        }
    }

    private fun facade(database: Database, store: IdentityStore, extensions: Extensions): IdentityFacade =
        IdentityFacade(
            store = store,
            accessTokens = AccessTokenComponents(journalist, database).accessTokenFacade(),
            entityTags = EntityTagSigner.ephemeral(),
            extensions = extensions,
            visibilityOf = { null },
            clock = clock,
        )
}
