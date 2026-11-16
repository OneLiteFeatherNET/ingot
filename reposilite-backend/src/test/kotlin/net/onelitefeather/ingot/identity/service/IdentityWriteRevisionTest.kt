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

import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.plugin.Extensions
import com.reposilite.shared.EntityTagSigner
import com.reposilite.token.application.AccessTokenComponents
import net.onelitefeather.ingot.identity.api.CreatePrincipalRequest
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
 * A guarded write reads the policy revision before it decides. Two replicas share one temporary SQLite file: the writer
 * changes the instance admins, the decider holds an older view and then performs a guarded write.
 */
internal class IdentityWriteRevisionTest {

    private val journalist = InMemoryLogger()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)

    @TempDir
    lateinit var workingDirectory: Path

    @Test
    fun `a guarded write records the revision it decided on`() {
        val file = workingDirectory.resolve("identity.db").toAbsolutePath()
        DatabaseConnectionFactory.createConnection(workingDirectory, "jdbc:sqlite:$file", 1).use { writerConnection ->
            DatabaseConnectionFactory.createConnection(workingDirectory, "jdbc:sqlite:$file", 1).use { deciderConnection ->
                // given: the writer grants the instance role to a principal the decider has not seen yet
                val writerStore = IdentityStore(writerConnection.database, clock)
                val writer = facade(writerConnection.database, writerStore, AuthorizationRevision(writerStore))
                writer.createPrincipal(CreatePrincipalRequest(kind = "USER", name = "alice"))
                writer.grantPrincipalInstanceAdmin("alice", ifMatch = null)

                // and: the decider's revision watch has not been refreshed since the start
                val deciderStore = IdentityStore(deciderConnection.database, clock)
                val revision = AuthorizationRevision(deciderStore)
                val decider = facade(deciderConnection.database, deciderStore, revision)
                assertThat(revision.lastSeen).describedAs("the decider has not seen any revision yet").isZero()

                // when: the decider performs a guarded write, which reads the revision before it decides
                val decidedOn = deciderStore.policyRevision()
                decider.revokePrincipalInstanceAdmin("alice", ifMatch = null)

                // then: the decider recorded the revision it read before deciding, and the write took effect
                // The guarded write holds the revision from its start, so the decision reads the value its own advance produced.
                assertThat(revision.lastSeen).describedAs("the guarded write records the revision it holds when it decides").isEqualTo(decidedOn + 1)
                assertThat(deciderStore.findPrincipal("alice")?.let { deciderStore.hasDirectInstanceRole(it.id) })
                    .describedAs("the instance role is revoked through the decider")
                    .isFalse()
            }
        }
    }

    private fun facade(database: Database, store: IdentityStore, revision: AuthorizationRevision): IdentityFacade =
        IdentityFacade(
            store = store,
            accessTokens = AccessTokenComponents(journalist, database).accessTokenFacade(),
            entityTags = EntityTagSigner.ephemeral(),
            extensions = Extensions(journalist),
            visibilityOf = { null },
            clock = clock,
            revision = revision,
        )
}
