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

package net.onelitefeather.ingot.identity.login

import com.reposilite.IsolatedResources
import com.reposilite.SharedContainers
import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import com.reposilite.schemaToken
import net.onelitefeather.ingot.identity.login.storage.LoginStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.reactive.Reference
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Two replicas on one PostgreSQL database share the account delay: the throttle state lives in the database, not in the
 * memory of an instance. Each test gets a schema of its own, and both replicas are built on that one database.
 */
internal class LoginThrottleReplicasIntegrationTest {

    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)
    private val settings = LoginSettings(accountThrottle = AccountThrottleSettings(threshold = 3))

    @TempDir
    lateinit var workingDirectory: Path

    /** Two throttles on one schema, as two replicas of Ingot would be on a shared database. */
    private class Replicas(val replicaA: AccountThrottle, val replicaB: AccountThrottle)

    private fun withReplicas(block: (Replicas) -> Unit) {
        val schema = IsolatedResources.createPostgresSchema(SharedContainers.postgres)
        try {
            DatabaseConnectionFactory.createConnection(workingDirectory, SharedContainers.postgres.schemaToken(schema), 2).use { connection ->
                val reference = Reference.reference(settings)
                block(
                    Replicas(
                        replicaA = AccountThrottle(LoginStore(connection.database, clock), reference, clock, enabled = true),
                        replicaB = AccountThrottle(LoginStore(connection.database, clock), reference, clock, enabled = true),
                    )
                )
            }
        } finally {
            IsolatedResources.dropPostgresSchema(SharedContainers.postgres, schema)
        }
    }

    @Test
    fun `a delay started on one replica applies on the other replica`() {
        withReplicas { replicas ->
            // given: three failures of alice on replica A
            repeat(3) { replicas.replicaA.recordFailure("alice") }

            // when: replica B checks alice from an address she has never signed in from
            val retryAfter = replicas.replicaB.retryAfter("alice", "10.0.0.9")

            // then: replica B sees the delay that replica A started
            assertThat(retryAfter)
                .describedAs("delay of alice from a new address on the replica that did not count the failures")
                .isNotNull()
        }
    }

    @Test
    fun `a success on one replica clears the delay seen by the other`() {
        withReplicas { replicas ->
            // given: three failures of alice on replica A, so that the delay applies
            repeat(3) { replicas.replicaA.recordFailure("alice") }

            // when: alice signs in successfully on replica B
            replicas.replicaB.recordSuccess("alice", "10.0.0.1")

            // then: replica A no longer delays alice from a new address
            assertThat(replicas.replicaA.retryAfter("alice", "10.0.0.9"))
                .describedAs("delay of alice on the replica that started it, after a success on the other replica")
                .isNull()
        }
    }

}
