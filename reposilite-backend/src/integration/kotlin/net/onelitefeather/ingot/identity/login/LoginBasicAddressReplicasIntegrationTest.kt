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
import com.reposilite.auth.FailedAddressRules
import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import com.reposilite.schemaToken
import net.onelitefeather.ingot.identity.login.storage.LoginStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * Two replicas on one PostgreSQL database share the per-address counter of Basic authentication: a failure counted on one
 * replica is the count of the other, and a ban started there is the ban there. Each test gets a schema of its own.
 */
internal class LoginBasicAddressReplicasIntegrationTest {

    private val now: Instant = Instant.parse("2026-10-15T10:00:00Z")
    private val rules = FailedAddressRules(maxAttempts = 3, banDuration = Duration.ofMinutes(5), lifetime = Duration.ofHours(1))

    @TempDir
    lateinit var workingDirectory: Path

    /** Two counters on one schema, as two replicas of Ingot would be on a shared database. */
    private class Replicas(val replicaA: SharedFailedAddressStore, val replicaB: SharedFailedAddressStore)

    private fun withReplicas(block: (Replicas) -> Unit) {
        val schema = IsolatedResources.createPostgresSchema(SharedContainers.postgres)
        try {
            DatabaseConnectionFactory.createConnection(workingDirectory, SharedContainers.postgres.schemaToken(schema), 2).use { connection ->
                val clock = Clock.fixed(now, ZoneOffset.UTC)
                block(
                    Replicas(
                        replicaA = SharedFailedAddressStore(LoginStore(connection.database, clock)),
                        replicaB = SharedFailedAddressStore(LoginStore(connection.database, clock)),
                    )
                )
            }
        } finally {
            IsolatedResources.dropPostgresSchema(SharedContainers.postgres, schema)
        }
    }

    @Test
    fun `a ban started by failures on one replica is read as the same ban on the other replica`() {
        withReplicas { replicas ->
            // given: three failed attempts of one address, all counted on replica A
            repeat(3) { replicas.replicaA.countFailure(ADDRESS, now, rules) }

            // when: replica B reads the address a second later
            val attempt = replicas.replicaB.read(ADDRESS, now.plusSeconds(1), rules)

            // then: replica B sees the count and the end of the ban that replica A wrote
            assertThat(attempt?.count)
                .describedAs("failed attempts of the address as read by replica B")
                .isEqualTo(3)
            assertThat(attempt?.bannedUntil)
                .describedAs("end of the ban of the address as read by replica B")
                .isEqualTo(now.plus(rules.banDuration))
        }
    }

    @Test
    fun `a forgotten address on one replica starts again from zero on the other replica`() {
        withReplicas { replicas ->
            // given: two failed attempts of one address, counted on replica A
            repeat(2) { replicas.replicaA.countFailure(ADDRESS, now, rules) }

            // when: replica B forgets the address after a valid login
            replicas.replicaB.forget(ADDRESS)

            // then: replica A reads no record, so the next failure counts as the first one
            assertThat(replicas.replicaA.read(ADDRESS, now, rules))
                .describedAs("record of the address on replica A after a valid login on replica B")
                .isNull()
        }
    }

    private companion object {
        const val ADDRESS = "10.0.0.7"
    }

}
