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
import java.security.MessageDigest
import java.time.Duration

/**
 * The worker, the verify pass and their interplay with the fenced lease, against the fake Nexus and an in-memory target. One
 * database file and one fake clock per test.
 */
internal class ImportWorkerTest {

    @TempDir
    lateinit var root: Path

    private lateinit var nexus: FakeNexus
    private lateinit var database: Database
    private lateinit var clock: AdvancingClock
    private lateinit var store: JobStore
    private val target = MemoryTarget()
    private val json = ObjectMapper()
    private val files = linkedMapOf(
        "com/example/app/1.0/app-1.0.jar" to "jar".toByteArray(),
        "com/example/app/1.0/app-1.0.pom" to "pom".toByteArray(),
        "com/example/app/maven-metadata.xml" to "meta".toByteArray(),
    )

    @BeforeEach
    fun setUp() {
        nexus = FakeNexus()
        database = Database.connect("jdbc:sqlite:${root.resolve("jobs.db")}", driver = "org.sqlite.JDBC")
        clock = AdvancingClock()
        store = JobStore(database, clock, lease = LEASE)
        serveRepository(files)
    }

    @AfterEach
    fun tearDown() {
        nexus.close()
    }

    private fun serveRepository(content: Map<String, ByteArray>) {
        val items = content.map { (path, bytes) ->
            mapOf(
                "path" to path,
                "downloadUrl" to "${nexus.baseUri}/repository/releases/$path",
                "fileSize" to bytes.size,
                "checksum" to mapOf("sha1" to sha("SHA-1", bytes), "md5" to sha("MD5", bytes)),
            )
        }
        nexus.on(NexusClient.ASSETS_PATH) { FakeReply.json(json.writeValueAsString(mapOf("items" to items, "continuationToken" to null))) }
        content.forEach { (path, bytes) -> nexus.on("/repository/releases/$path") { FakeReply.bytes(bytes) } }
    }

    private fun sha(algorithm: String, bytes: ByteArray) =
        MessageDigest.getInstance(algorithm).digest(bytes).joinToString("") { "%02x".format(it) }

    private fun worker(token: String, readOnly: () -> Boolean = { false }) = ImportWorker(
        store = store,
        clock = clock,
        lease = LEASE,
        nexus = NexusClient(NexusConnection(nexus.baseUri, "importer", Secret("pw")), JdkNexusTransport(), clock, sleeper = { clock.advance(it) }, requestsPerSecond = 100),
        target = target,
        tempDir = root,
        readOnly = readOnly,
        token = token,
    )

    private fun downloads() = nexus.requests().filter { it.path.startsWith("/repository/releases/") }.map { it.path }

    private fun targets() = listOf(CopyTarget(nexusName = "releases", targetName = "releases"))

    @Test
    fun `should copy every file, finish the job and verify the target`() {
        // given: a job over one Nexus repository
        val id = store.createJob("IMPORT", "{}")

        // when: the worker runs to the end
        val outcome = worker("worker-a").copy(id, targets(), ConflictPolicy.REFUSE)

        // then: the job is complete, one row per file, and the verify pass agrees
        assertThat(outcome).isEqualTo(RunOutcome.COMPLETED)
        assertThat(store.assets(id)).hasSize(files.size)
        assertThat(store.job(id)?.status).isEqualTo(JobStatus.COMPLETED)
        assertThat(Verifier(nexus.client(), target).verify(targets(), emptySet()).values.single()).isEqualTo(VerifyStatus.VERIFIED)
    }

    @Test
    fun `should copy the metadata after the artifacts of its directory`() {
        // given: a job over the repository
        store.createJob("IMPORT", "{}")

        // when: the worker copies it
        worker("worker-a").copy(store.createJob("IMPORT", "{}"), targets(), ConflictPolicy.REFUSE)

        // then: maven-metadata.xml is the last file downloaded of the directory
        assertThat(downloads().last()).endsWith("maven-metadata.xml")
    }

    @Test
    fun `should stop without another download when the lease is lost mid-run and let a second worker finish`() {
        // given: the second file's download runs past the lease of the first worker
        val id = store.createJob("IMPORT", "{}")
        val secondPath = "/repository/releases/com/example/app/1.0/app-1.0.pom"
        nexus.on(secondPath) {
            clock.advance(LEASE.plusSeconds(1))
            FakeReply.bytes("pom".toByteArray())
        }

        // when: worker A runs into the expired lease
        val first = worker("worker-a").copy(id, targets(), ConflictPolicy.REFUSE)

        // then: A stops with its lease lost, and starts no file after the one that expired
        assertThat(first).isEqualTo(RunOutcome.LEASE_LOST)
        assertThat(downloads()).describedAs("A must not start a file after its lease ran out").hasSize(2)

        // when: worker B takes the job over and finishes it
        nexus.on(secondPath) { FakeReply.bytes("pom".toByteArray()) }
        val second = worker("worker-b").copy(id, targets(), ConflictPolicy.REFUSE)

        // then: B completes, every file has exactly one row and the second file was not downloaded twice
        assertThat(second).isEqualTo(RunOutcome.COMPLETED)
        assertThat(store.assets(id)).hasSize(files.size)
        assertThat(store.assets(id).map { it.path }.toSet()).hasSize(files.size)
        assertThat(downloads().count { it == secondPath }).describedAs("the file A wrote is skipped by B").isEqualTo(1)
    }

    @Test
    fun `should pause for read-only mode and continue after it is switched off`() {
        // given: read-only turns on after the second download
        val id = store.createJob("IMPORT", "{}")
        val readOnly = { downloads().size >= 2 }

        // when: the worker meets read-only mode
        val paused = worker("worker-a", readOnly).copy(id, targets(), ConflictPolicy.REFUSE)

        // then: the job is paused for read-only mode and no further file was started
        assertThat(paused).isEqualTo(RunOutcome.PAUSED_READ_ONLY)
        assertThat(store.job(id)?.pauseReason).isEqualTo(PauseReason.READ_ONLY)
        assertThat(downloads()).hasSize(2)

        // when: the operator resumes the job and read-only is off
        assertThat(store.resume(id, PauseReason.READ_ONLY)).isTrue
        val finished = worker("worker-b").copy(id, targets(), ConflictPolicy.REFUSE)

        // then: the job completes
        assertThat(finished).isEqualTo(RunOutcome.COMPLETED)
    }

    @Test
    fun `should pause for credentials when Nexus refuses a download`() {
        // given: the download of the first file is refused with 401
        val id = store.createJob("IMPORT", "{}")
        nexus.script("/repository/releases/com/example/app/1.0/app-1.0.jar", FakeReply.status(401))

        // when: the worker copies
        val outcome = worker("worker-a").copy(id, targets(), ConflictPolicy.REFUSE)

        // then: the job waits for the operator to enter the credentials again
        assertThat(outcome).isEqualTo(RunOutcome.PAUSED_CREDENTIALS)
        assertThat(store.job(id)?.pauseReason).isEqualTo(PauseReason.CREDENTIALS_NEEDED)
    }

    @Test
    fun `should record a differing target file as a conflict, keep it, and never verify the repository`() {
        // given: the target holds a different file at a path of Nexus
        val id = store.createJob("IMPORT", "{}")
        target.write("releases", "com/example/app/1.0/app-1.0.pom", "local".toByteArray())

        // when: the default policy meets the conflict
        val outcome = worker("worker-a").copy(id, targets(), ConflictPolicy.REFUSE)

        // then: the local file is kept, the row is a conflict and the repository is conflicted, not verified
        assertThat(outcome).isEqualTo(RunOutcome.COMPLETED)
        assertThat(target.read("releases", "com/example/app/1.0/app-1.0.pom")).isEqualTo("local".toByteArray())
        assertThat(store.assets(id).single { it.path.endsWith(".pom") }.result).isEqualTo(AssetResult.CONFLICT)
        assertThat(Verifier(nexus.client(), target).verify(targets(), conflicted = setOf("releases")).values.single())
            .isEqualTo(VerifyStatus.CONFLICTED)
    }

    @Test
    fun `should stop the repository at its first conflict under the abort policy`() {
        // given: the first file in the listing conflicts
        val id = store.createJob("IMPORT", "{}")
        target.write("releases", "com/example/app/1.0/app-1.0.jar", "local".toByteArray())

        // when: the abort policy runs
        worker("worker-a").copy(id, targets(), ConflictPolicy.ABORT)

        // then: no later file of the repository was downloaded
        assertThat(downloads()).isEmpty()
    }

    @Test
    fun `should report a mismatch with the first differing paths when a file is missing from the target`() {
        // given: a complete copy, and then one file disappears from the target
        val id = store.createJob("IMPORT", "{}")
        worker("worker-a").copy(id, targets(), ConflictPolicy.REFUSE)
        target.remove("releases", "com/example/app/1.0/app-1.0.pom")

        // when: the verify pass runs
        val status = Verifier(nexus.client(), target).verify(targets(), emptySet()).values.single()

        // then: the repository is a mismatch
        assertThat(status).isEqualTo(VerifyStatus.MISMATCH)
    }

    @Test
    fun `should send only GET requests during copy and verify`() {
        // given: a job over the repository
        val id = store.createJob("IMPORT", "{}")

        // when: copy and verify run
        worker("worker-a").copy(id, targets(), ConflictPolicy.REFUSE)
        Verifier(nexus.client(), target).verify(targets(), emptySet())

        // then: Nexus saw only read methods
        nexus.assertOnlyReadMethods()
    }

    private fun FakeNexus.client() = NexusClient(NexusConnection(baseUri, "importer", Secret("pw")), JdkNexusTransport(), clock, sleeper = { clock.advance(it) }, requestsPerSecond = 100)

    private companion object {
        val LEASE: Duration = Duration.ofSeconds(30)
    }
}
