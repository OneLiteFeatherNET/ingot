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
import net.onelitefeather.ingot.identity.model.Role
import net.onelitefeather.ingot.identity.model.TeamMemberSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

internal class PolicyRevisionTest {

    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)
    private val createdAt: Instant = Instant.parse("2026-10-14T08:00:00Z")

    @TempDir
    lateinit var workingDirectory: Path

    @Test
    fun `creating a principal advances the policy revision by exactly one`() {
        withStore { store ->
            // given: the revision before the write
            val before = store.policyRevision()

            // when: a principal is created
            store.createPrincipal(PrincipalKind.USER, "alice", "Alice", disabled = false, createdAt = createdAt)

            // then: the revision increased by exactly one
            assertThat(store.policyRevision()).describedAs("a principal write advances the revision once").isEqualTo(before + 1)
        }
    }

    @Test
    fun `a write refused by a unique key leaves the policy revision unchanged`() {
        withStore { store ->
            // given: an existing principal and the revision after it was created
            store.createPrincipal(PrincipalKind.USER, "alice", "Alice", disabled = false, createdAt = createdAt)
            val before = store.policyRevision()

            // when: a principal with the same name is created again
            val duplicate = store.createPrincipal(PrincipalKind.USER, "ALICE", "Alice", disabled = false, createdAt = createdAt)

            // then: the write is refused and the revision did not move
            assertThat(duplicate).describedAs("the duplicate is refused").isNull()
            assertThat(store.policyRevision()).describedAs("a rolled-back write leaves the revision unchanged").isEqualTo(before)
        }
    }

    @Test
    fun `a team membership change advances the policy revision by exactly one`() {
        withStore { store ->
            // given: a team and a principal
            val team = store.createTeam("maintainers", "", externalRef = null)!!
            val principal = store.createPrincipal(PrincipalKind.USER, "alice", "Alice", disabled = false, createdAt = createdAt)!!
            val before = store.policyRevision()

            // when: the principal is added to the team
            store.addMember(team.id, principal.id, TeamMemberSource.MANUAL)

            // then: the revision increased by exactly one
            assertThat(store.policyRevision()).describedAs("a membership change advances the revision once").isEqualTo(before + 1)
        }
    }

    @Test
    fun `a grant change advances the policy revision by exactly one`() {
        withStore { store ->
            // given: a team
            val team = store.createTeam("maintainers", "", externalRef = null)!!
            val before = store.policyRevision()

            // when: a grant is added
            store.addGrant(team.id, repository = "releases", pathPrefix = null, role = Role.DEPLOY)

            // then: the revision increased by exactly one
            assertThat(store.policyRevision()).describedAs("a grant change advances the revision once").isEqualTo(before + 1)
        }
    }

    @Test
    fun `reading identity does not advance the policy revision`() {
        withStore { store ->
            // given: a principal and a team
            store.createPrincipal(PrincipalKind.USER, "alice", "Alice", disabled = false, createdAt = createdAt)
            store.createTeam("maintainers", "", externalRef = null)
            val before = store.policyRevision()

            // when: the identity is read
            store.listPrincipals()
            store.findTeam("maintainers")
            store.listTeams()

            // then: the revision did not move
            assertThat(store.policyRevision()).describedAs("reads leave the revision unchanged").isEqualTo(before)
        }
    }

    @Test
    fun `recording the last seen time does not advance the policy revision`() {
        withStore { store ->
            // given: a principal that was never seen
            val principal = store.createPrincipal(PrincipalKind.USER, "alice", "Alice", disabled = false, createdAt = createdAt)!!
            val before = store.policyRevision()

            // when: the last seen time is recorded
            store.touchPrincipal(principal.id, at = createdAt.plusSeconds(60), notBefore = createdAt)

            // then: the usage record does not change any right, so the revision stays
            assertThat(store.policyRevision()).describedAs("usage records do not advance the revision").isEqualTo(before)
        }
    }

    @Test
    fun `refreshing on a second store reads the revision written by the first store`() {
        withSharedStores { writer, reader ->
            // given: a revision watcher on the reader and a write through the writer
            val watcher = AuthorizationRevision(reader)
            writer.createPrincipal(PrincipalKind.USER, "alice", "Alice", disabled = false, createdAt = createdAt)

            // when: the reader refreshes once
            val seen = watcher.refreshOnce()

            // then: the reader sees the revision of the writer
            assertThat(seen).describedAs("the reader sees the revision written by the other store").isEqualTo(writer.policyRevision())
            assertThat(watcher.lastSeen).describedAs("the last value seen is kept").isEqualTo(seen)
        }
    }

    private fun withStore(block: (IdentityStore) -> Unit) {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            block(IdentityStore(connection.database, clock))
        }
    }

    private fun withSharedStores(block: (IdentityStore, IdentityStore) -> Unit) {
        val file = workingDirectory.resolve("identity.db").toAbsolutePath()
        DatabaseConnectionFactory.createConnection(workingDirectory, "jdbc:sqlite:$file", 1).use { writerConnection ->
            DatabaseConnectionFactory.createConnection(workingDirectory, "jdbc:sqlite:$file", 1).use { readerConnection ->
                block(IdentityStore(writerConnection.database, clock), IdentityStore(readerConnection.database, clock))
            }
        }
    }
}
