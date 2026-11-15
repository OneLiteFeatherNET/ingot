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
package net.onelitefeather.ingot.identity.storage

import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import net.onelitefeather.ingot.identity.model.PrincipalKind
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Two replicas share one temporary SQLite file. A guarded write holds the policy revision for its whole transaction, so a
 * write of the other replica cannot commit between the decision and the write. The busy timeout is zero, so the refused
 * write fails at once instead of waiting on the wall clock.
 */
internal class GuardedWriteLockTest {

    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)
    private val createdAt: Instant = Instant.parse("2026-10-14T08:00:00Z")

    @TempDir
    lateinit var workingDirectory: Path

    @Test
    fun `a write of another replica is refused while a guarded write holds the revision`() {
        val url = "jdbc:sqlite:${workingDirectory.resolve("identity.db").toAbsolutePath()}?busy_timeout=0"
        DatabaseConnectionFactory.createConnection(workingDirectory, url, 1).use { connectionA ->
            DatabaseConnectionFactory.createConnection(workingDirectory, url, 1).use { connectionB ->
                // given: a principal written by replica A, and the revision before the guarded write
                val replicaA = IdentityStore(connectionA.database, clock)
                val replicaB = IdentityStore(connectionB.database, clock)
                val alice = replicaA.createPrincipal(PrincipalKind.USER, "alice", "Alice", disabled = false, createdAt = createdAt)!!
                val before = replicaA.policyRevision()

                // when: replica A runs a guarded write, and replica B tries to write while A's transaction is open
                replicaA.guardedWrite {
                    // then: the revision was advanced before the decision, so the decision reads the revision it holds
                    assertThat(replicaA.policyRevision())
                        .describedAs("a guarded write advances the revision before it decides")
                        .isEqualTo(before + 1)

                    // and: replica B is refused, so nothing can slip in between the decision and the write
                    assertThatThrownBy { replicaB.updatePrincipal(alice.id, "Changed by B", null) }
                        .describedAs("a concurrent write of another replica must not commit while the revision is held")
                }

                // when: the guarded write has committed, replica B writes on the state A committed
                replicaB.updatePrincipal(alice.id, "Changed by B", null)

                // then: B's write advanced the revision once more and is visible
                assertThat(replicaB.policyRevision()).describedAs("B's write advances the revision after A's").isEqualTo(before + 2)
                assertThat(replicaB.findPrincipal("alice")?.displayName).isEqualTo("Changed by B")
            }
        }
    }
}
