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
package net.onelitefeather.ingot.schema

import com.reposilite.IsolatedResources
import com.reposilite.ReposiliteFactory
import com.reposilite.ReposiliteParameters
import com.reposilite.SharedContainers
import com.reposilite.databaseToken
import com.reposilite.schemaToken
import com.reposilite.token.AccessTokenSecurityProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Several replicas start at the same moment against one shared database, as a rolling update or a first install with
 * several pods does. Every replica must complete its start, with its schema initialised without a race between the
 * stores that create tables.
 *
 * Each replica has its own working directory. The start gate is a latch, so all replicas leave it together, and every
 * wait is bounded by a timeout on that latch or on the futures.
 */
internal class ConcurrentSchemaInitialisationIntegrationTest {

    private val replicas = 4
    private val timeoutSeconds = 120L

    @TempDir
    lateinit var root: Path

    enum class Remote { MARIADB, POSTGRESQL }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Remote::class)
    fun `every replica that starts together completes its start against one database`(remote: Remote) {
        withSharedDatabase(remote) { token ->
            val gate = CountDownLatch(1)
            val pool = Executors.newFixedThreadPool(replicas)
            try {
                val starts = (1..replicas).map { index ->
                    pool.submit(Callable { startReplica(index, token, gate) })
                }
                gate.countDown()

                val failures = starts
                    .mapNotNull { it.get(timeoutSeconds, TimeUnit.SECONDS) }

                assertThat(failures)
                    .`as`("replicas that failed to start together on $remote")
                    .isEmpty()
            } finally {
                pool.shutdownNow()
            }
        }
    }

    /** Returns the failure message of this replica, or null when its start completed. */
    private fun startReplica(index: Int, token: String, gate: CountDownLatch): String? {
        val workingDirectory = root.resolve("replica-$index").toFile().also { it.mkdirs() }
        val parameters = ReposiliteParameters()
        parameters.tokenEntries = arrayOf("manager:manager-secret")
        parameters.workingDirectoryName = workingDirectory.absolutePath
        parameters.testEnv = true
        parameters.port = 0
        parameters.database = token
        parameters.tokenHashStrength = AccessTokenSecurityProvider.MIN_BCRYPT_STRENGTH
        parameters.run()

        check(gate.await(timeoutSeconds, TimeUnit.SECONDS)) { "start gate was not opened" }

        val reposilite = ReposiliteFactory.createReposilite(parameters)
        return try {
            reposilite.launch().fold({ null }, { error -> "replica $index: ${error.message}" })
        } finally {
            reposilite.shutdown()
        }
    }

    private fun withSharedDatabase(remote: Remote, block: (String) -> Unit) {
        when (remote) {
            Remote.MARIADB -> {
                val name = IsolatedResources.createMariaDbDatabase(SharedContainers.mariaDb)
                try {
                    block(SharedContainers.mariaDb.databaseToken(name))
                } finally {
                    IsolatedResources.dropMariaDbDatabase(SharedContainers.mariaDb, name)
                }
            }
            Remote.POSTGRESQL -> {
                val schema = IsolatedResources.createPostgresSchema(SharedContainers.postgres)
                try {
                    block(SharedContainers.postgres.schemaToken(schema))
                } finally {
                    IsolatedResources.dropPostgresSchema(SharedContainers.postgres, schema)
                }
            }
        }
    }

}
