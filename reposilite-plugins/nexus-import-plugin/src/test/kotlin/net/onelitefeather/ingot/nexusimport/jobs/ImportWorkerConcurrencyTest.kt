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
package net.onelitefeather.ingot.nexusimport.jobs

import com.fasterxml.jackson.databind.ObjectMapper
import net.onelitefeather.ingot.nexusimport.copy.ConflictPolicy
import net.onelitefeather.ingot.nexusimport.copy.MemoryTarget
import net.onelitefeather.ingot.nexusimport.fake.AdvancingClock
import net.onelitefeather.ingot.nexusimport.fake.FakeNexus
import net.onelitefeather.ingot.nexusimport.fake.FakeReply
import net.onelitefeather.ingot.nexusimport.nexus.JdkNexusTransport
import net.onelitefeather.ingot.nexusimport.nexus.NexusClient
import net.onelitefeather.ingot.nexusimport.nexus.NexusConnection
import net.onelitefeather.ingot.nexusimport.nexus.Secret
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The worker with several copies in flight. Latches decide when two downloads have met, so no test waits on the clock. Every
 * test has one database file, one fake clock and one fake Nexus.
 */
internal class ImportWorkerConcurrencyTest {

    @TempDir
    lateinit var root: Path

    private lateinit var nexus: FakeNexus
    private lateinit var store: JobStore
    private lateinit var clock: AdvancingClock
    private val target = MemoryTarget()
    private val json = ObjectMapper()
    private val artifacts = (1..4).associate { "com/example/app/$it.0/app-$it.0.jar" to "jar-$it".toByteArray() }
    private val metadata = mapOf("com/example/app/maven-metadata.xml" to "meta".toByteArray())
    private val files = artifacts + metadata

    @BeforeEach
    fun setUp() {
        nexus = FakeNexus()
        clock = AdvancingClock()
        store = JobStore(Database.connect("jdbc:sqlite:${root.resolve("jobs.db")}", driver = "org.sqlite.JDBC"), clock, lease = LEASE)
        val items = files.map { (path, bytes) ->
            mapOf(
                "path" to path,
                "downloadUrl" to "${nexus.baseUri}/repository/releases/$path",
                "fileSize" to bytes.size,
                "checksum" to mapOf("sha1" to sha("SHA-1", bytes)),
            )
        }
        nexus.on(NexusClient.ASSETS_PATH) { FakeReply.json(json.writeValueAsString(mapOf("items" to items, "continuationToken" to null))) }
        files.forEach { (path, bytes) -> nexus.on("/repository/releases/$path") { FakeReply.bytes(bytes) } }
    }

    @AfterEach
    fun tearDown() {
        nexus.close()
    }

    private fun sha(algorithm: String, bytes: ByteArray) =
        java.security.MessageDigest.getInstance(algorithm).digest(bytes).joinToString("") { "%02x".format(it) }

    private fun worker(token: String, concurrency: Int) = ImportWorker(
        store = store,
        clock = clock,
        lease = LEASE,
        nexus = NexusClient(NexusConnection(nexus.baseUri, "importer", Secret("pw")), JdkNexusTransport(), clock, sleeper = { clock.advance(it) }, requestsPerSecond = 100),
        target = target,
        tempDir = root,
        readOnly = { false },
        token = token,
        concurrency = concurrency,
    )

    private fun targets() = listOf(CopyTarget(nexusName = "releases", targetName = "releases"))

    private fun downloads() = nexus.requests().filter { it.path.startsWith("/repository/releases/") }.map { it.path.removePrefix("/repository/releases/") }

    @Test
    fun `should keep two copies in flight together and never a third when the concurrency is two`() {
        // given: every download waits until two of them have started, so the first two must overlap
        val id = store.createJob("IMPORT", "{}")
        val inFlight = AtomicInteger(0)
        val peak = AtomicInteger(0)
        val bothStarted = CountDownLatch(2)
        artifacts.forEach { (path, bytes) ->
            nexus.on("/repository/releases/$path") {
                peak.accumulateAndGet(inFlight.incrementAndGet(), ::maxOf)
                bothStarted.countDown()
                bothStarted.await(10, TimeUnit.SECONDS)
                inFlight.decrementAndGet()
                FakeReply.bytes(bytes)
            }
        }

        // when: the worker copies with a concurrency of two
        val outcome = worker("worker-a", concurrency = 2).copy(id, targets(), ConflictPolicy.REFUSE)

        // then: the job completes, two copies were in flight together and never three
        assertThat(outcome).isEqualTo(RunOutcome.COMPLETED)
        assertThat(peak.get()).describedAs("copies in flight at the same time, at most the concurrency").isEqualTo(2)
    }

    @Test
    fun `should copy one file after the other in the sorted order with a concurrency of one`() {
        // given: a job over the repository
        val id = store.createJob("IMPORT", "{}")

        // when: the worker copies with a concurrency of one
        worker("worker-a", concurrency = 1).copy(id, targets(), ConflictPolicy.REFUSE)

        // then: the artifacts follow their path order and the metadata comes last
        assertThat(downloads()).containsExactly(*(artifacts.keys.sorted() + metadata.keys).toTypedArray())
    }

    @Test
    fun `should keep the results of the other files when one file fails with the server error`() {
        // given: the second artifact keeps failing on the Nexus side
        val id = store.createJob("IMPORT", "{}")
        val failing = artifacts.keys.sorted()[1]
        nexus.on("/repository/releases/$failing") { FakeReply.status(500) }

        // when: the worker copies with a concurrency of two
        val outcome = worker("worker-a", concurrency = 2).copy(id, targets(), ConflictPolicy.REFUSE)

        // then: the job completes, the failing file is recorded as failed and every other file is copied
        assertThat(outcome).isEqualTo(RunOutcome.COMPLETED)
        val results = store.assets(id).associate { it.path to it.result }
        assertThat(results.getValue(failing)).isEqualTo(AssetResult.FAILED)
        assertThat(results.filterKeys { it != failing }.values).containsOnly(AssetResult.COPIED)
        assertThat(results).hasSize(files.size)
    }

    @Test
    fun `should stop with the lease lost and commit nothing with the stale token when a copy outlives the lease`() {
        // given: a download of the first artifact runs past the lease of the worker
        val id = store.createJob("IMPORT", "{}")
        val slow = artifacts.keys.sorted().first()
        nexus.on("/repository/releases/$slow") {
            clock.advance(LEASE.plusSeconds(1))
            FakeReply.bytes(artifacts.getValue(slow))
        }

        // when: worker A copies with a concurrency of two
        val first = worker("worker-a", concurrency = 2).copy(id, targets(), ConflictPolicy.REFUSE)

        // then: A reports the lost lease, and the slow file has no row written with its stale token
        assertThat(first).isEqualTo(RunOutcome.LEASE_LOST)
        assertThat(store.assets(id).map { it.path }).doesNotContain(slow)

        // when: worker B takes the expired job over and finishes it
        nexus.on("/repository/releases/$slow") { FakeReply.bytes(artifacts.getValue(slow)) }
        val second = worker("worker-b", concurrency = 2).copy(id, targets(), ConflictPolicy.REFUSE)

        // then: B completes with one final row per file
        assertThat(second).isEqualTo(RunOutcome.COMPLETED)
        assertThat(store.assets(id)).hasSize(files.size)
    }

    private companion object {
        val LEASE: Duration = Duration.ofSeconds(30)
    }
}
