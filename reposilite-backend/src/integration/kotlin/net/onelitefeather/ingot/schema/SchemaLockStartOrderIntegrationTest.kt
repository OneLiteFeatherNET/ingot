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

import com.reposilite.ReposiliteFactory
import com.reposilite.ReposiliteParameters
import com.reposilite.journalist.Channel
import com.reposilite.journalist.backend.PrintStreamLogger
import com.reposilite.plugin.api.ReposiliteInitializeEvent
import com.reposilite.plugin.api.ReposilitePostInitializeEvent
import com.reposilite.token.AccessTokenSecurityProvider
import com.reposilite.web.api.HttpServerInitializationEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.absolutePathString

/**
 * Order of the schema lock against the start of an instance, with a recording lock in place of the database one. The lock
 * is acquired while the instance is created, held while the plugins create their tables, released after the post-initialise
 * event, and only then does the HTTP server bind. The server's initialisation event fires before the port is bound, so the
 * recorded order shows that the lock is gone before anything listens.
 */
internal class SchemaLockStartOrderIntegrationTest {

    @TempDir
    lateinit var workingDirectory: Path

    private val events = Collections.synchronizedList(mutableListOf<String>())

    private val recordingLocks = SchemaLockFactory {
        events += "acquired"
        RecordingLock()
    }

    @Test
    fun `holds the schema lock until the post-initialise event and releases it before the server binds`() {
        // given: an instance created with a recording schema lock on an embedded database
        val reposilite = ReposiliteFactory.createReposilite(parameters(), logger(), recordingLocks)
        reposilite.extensions.registerEvent<ReposiliteInitializeEvent> { events += "initialize" }
        reposilite.extensions.registerEvent<ReposilitePostInitializeEvent> { events += "post-initialize" }
        reposilite.extensions.registerEvent<HttpServerInitializationEvent> { events += "server-initialization" }

        try {
            // when: the instance starts
            val result = reposilite.launch()

            // then: the lock was taken first, held through initialisation, and released before the server was set up
            assertThat(result.isOk).`as`("start result").isTrue
            assertThat(events)
                .`as`("order of lock and start events")
                .containsExactly("acquired", "initialize", "post-initialize", "released", "server-initialization")
        } finally {
            reposilite.shutdown()
        }
    }

    @Test
    fun `releases the schema lock when a store fails while its tables are created`() {
        // given: an instance whose initialisation fails after the lock was taken
        val reposilite = ReposiliteFactory.createReposilite(parameters(), logger(), recordingLocks)
        reposilite.extensions.registerEvent<ReposiliteInitializeEvent> { throw IllegalStateException("store failed") }
        reposilite.extensions.registerEvent<HttpServerInitializationEvent> { events += "server-initialization" }

        // when: the instance starts
        val result = reposilite.launch()

        // then: the start fails, the lock is released, and the server is never set up
        assertThat(result.isErr).`as`("start result").isTrue
        assertThat(events)
            .`as`("order of lock and start events")
            .containsExactly("acquired", "released")
    }

    private fun parameters(): ReposiliteParameters {
        val parameters = ReposiliteParameters()
        parameters.tokenEntries = arrayOf("manager:manager-secret")
        parameters.workingDirectoryName = workingDirectory.absolutePathString()
        parameters.testEnv = true
        // Port 0: the OS picks a free port, so nothing has to be retried.
        parameters.port = 0
        parameters.database = "sqlite --temporary"
        parameters.tokenHashStrength = AccessTokenSecurityProvider.MIN_BCRYPT_STRENGTH
        parameters.run()
        return parameters
    }

    private fun logger() = PrintStreamLogger(System.out, System.err, Channel.ALL, false)

    private inner class RecordingLock : SchemaLock {

        private val released = AtomicBoolean(false)

        override fun release() {
            if (released.compareAndSet(false, true)) {
                events += "released"
            }
        }

    }

}
