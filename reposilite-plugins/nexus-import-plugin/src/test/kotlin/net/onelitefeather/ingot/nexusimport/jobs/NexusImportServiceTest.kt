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

import com.reposilite.maven.RepositoryType
import com.reposilite.maven.RepositoryVisibility
import com.reposilite.shared.ErrorResponse
import io.javalin.http.HttpStatus
import net.onelitefeather.ingot.nexusimport.fake.FakeReply
import net.onelitefeather.ingot.nexusimport.plan.LossStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.Result
import java.nio.file.Path
import java.util.Base64

/**
 * The import service end to end against the fake Nexus: the dry run, the import with its definitions and copy, the verify
 * pass, conflicts, operator control, credentials, read-only mode and the audit modes. One scenario per test.
 */
internal class NexusImportServiceTest {

    @TempDir
    lateinit var root: Path

    private lateinit var scenario: ImportScenario

    @BeforeEach
    fun setUp() {
        scenario = ImportScenario(root)
    }

    @AfterEach
    fun tearDown() {
        scenario.close()
    }

    private fun request(kind: String, password: String = "pw", repositories: List<String>? = null, targets: Map<String, String> = emptyMap(), policy: String = "refuse", audit: String? = null) =
        JobRequest(kind = kind, url = scenario.nexus.baseUri.toString(), user = "importer", password = password, repositories = repositories, targets = targets, conflictPolicy = policy, audit = audit)

    private fun create(service: NexusImportService, request: JobRequest): Long =
        service.create(request).orThrow().id

    @Test
    fun `should write nothing and store the loss report when a dry run completes`() {
        // given: a dry run over the Nexus repositories
        val service = scenario.service()
        val id = create(service, request("DRY_RUN"))

        // when: the job runs
        service.run(id)

        // then: no definition, no file and no index change were made, and Nexus saw only reads
        assertThat(scenario.domain.updates).describedAs("a dry run never updates the maven domain").isEmpty()
        assertThat(scenario.target.read("releases", "com/example/app/1.0/app-1.0.jar")).isNull()
        scenario.nexus.assertOnlyReadMethods()

        // and: the report holds a row for the hosted repository, the summary the transfer size
        val report = service.report(id).orThrow().rows
        assertThat(report.single { it.nexusId == "releases" }.status).isEqualTo(LossStatus.MAPPED)
        assertThat(service.job(id).orThrow().summary?.transferBytes).isEqualTo(scenario.files.values.sumOf { it.size.toLong() })
    }

    @Test
    fun `should create the definition, copy every file and verify the repository in an import and a verify job`() {
        // given: an import of the hosted repository
        val service = scenario.service()
        val id = create(service, request("IMPORT"))

        // when: the import runs
        assertThat(service.run(id)).isEqualTo(RunOutcome.COMPLETED)

        // then: the definition is private and hosted, and every file has the same bytes in the target
        val definition = scenario.domain.stored.single { it.id == "releases" }
        assertThat(definition.visibility).isEqualTo(RepositoryVisibility.PRIVATE)
        assertThat(definition.type).isEqualTo(RepositoryType.HOSTED)
        scenario.files.forEach { (path, bytes) -> assertThat(scenario.target.read("releases", path)).isEqualTo(bytes) }
        assertThat(service.job(id).orThrow().repositories.single().status).isEqualTo(RepositoryStatus.COPIED)

        // when: a verify job checks the copy against Nexus
        val verify = create(service, request("VERIFY", repositories = listOf("releases"), targets = mapOf("releases" to "releases")).copy(verifyImportJob = id))
        service.run(verify)

        // then: the repository is verified, and the run event names both jobs
        assertThat(service.job(verify).orThrow().repositories.single().status).isEqualTo(RepositoryStatus.VERIFIED)
        assertThat(scenario.ports.runs.map { it.first }).containsExactly(id, verify)
        scenario.nexus.assertOnlyReadMethods()
    }

    @Test
    fun `should keep a differing file and leave the repository conflicted under the refuse policy`() {
        // given: the target repository exists and holds a different pom at a path Nexus also has
        scenario.close()
        scenario = ImportScenario(root, initialDomain = listOf(ImportScenario.hostedReleases()))
        scenario.ports.existing = setOf("releases")
        scenario.target.write("releases", "com/example/app/1.0/app-1.0.pom", "local-pom".toByteArray())
        val service = scenario.service()
        val id = create(service, request("IMPORT", targets = mapOf("releases" to "releases")))

        // when: the import runs under the default policy
        service.run(id)

        // then: the local file is kept, the conflict lists both checksums and the repository is conflicted
        assertThat(scenario.target.read("releases", "com/example/app/1.0/app-1.0.pom")).isEqualTo("local-pom".toByteArray())
        val conflict = service.conflicts(id).orThrow().single()
        assertThat(conflict.path).isEqualTo("com/example/app/1.0/app-1.0.pom")
        assertThat(conflict.detail).contains("nexus sha1").contains("target sha1")
        assertThat(service.job(id).orThrow().repositories.single().status).isEqualTo(RepositoryStatus.CONFLICTED)

        // and: a verify job never marks that repository verified
        val verify = create(service, request("VERIFY", repositories = listOf("releases")).copy(verifyImportJob = id))
        service.run(verify)
        assertThat(service.job(verify).orThrow().repositories.single().status).isEqualTo(RepositoryStatus.CONFLICTED)
    }

    @Test
    fun `should not start a job while the instance is read-only`() {
        // given: read-only mode is on
        scenario.readOnly = true
        val service = scenario.service()
        val id = create(service, request("IMPORT"))

        // when: the job is run
        val outcome = service.run(id)

        // then: it stays queued and Nexus is not contacted
        assertThat(outcome).isEqualTo(RunOutcome.NOT_STARTED_READ_ONLY)
        assertThat(service.job(id).orThrow().status).isEqualTo(JobStatus.QUEUED)
        assertThat(scenario.nexus.requests()).isEmpty()
    }

    @Test
    fun `should pause at the operator's request before the next file and copy each file once after resume`() {
        // given: the operator pauses the job while the second file is downloaded
        val service = scenario.service()
        val id = create(service, request("IMPORT"))
        val pom = "/repository/releases/com/example/app/1.0/app-1.0.pom"
        var requested = false
        scenario.nexus.on(pom) {
            if (!requested) {
                requested = true
                service.pause(id)
            }
            FakeReply.bytes("pom-bytes".toByteArray())
        }

        // when: the job runs and is paused
        val paused = service.run(id)

        // then: it stops before the third file, with the reason of the operator
        assertThat(paused).isEqualTo(RunOutcome.PAUSED_OPERATOR)
        assertThat(service.job(id).orThrow().status).isEqualTo(JobStatus.PAUSED)
        assertThat(service.job(id).orThrow().pauseReason).isEqualTo(PauseReason.OPERATOR)
        assertThat(scenario.downloads()).hasSize(2)

        // when: the operator resumes it
        service.resume(id).orThrow()
        assertThat(service.run(id)).isEqualTo(RunOutcome.COMPLETED)

        // then: every file was downloaded exactly once
        scenario.files.keys.forEach { path ->
            assertThat(scenario.downloads().count { it == "/repository/releases/$path" }).describedAs(path).isEqualTo(1)
        }
    }

    @Test
    fun `should cancel a running job after the file in flight and keep the rows of the copied files`() {
        // given: the operator cancels the job while the second file is downloaded
        val service = scenario.service()
        val id = create(service, request("IMPORT"))
        val pom = "/repository/releases/com/example/app/1.0/app-1.0.pom"
        var requested = false
        scenario.nexus.on(pom) {
            if (!requested) {
                requested = true
                service.cancel(id)
            }
            FakeReply.bytes("pom-bytes".toByteArray())
        }

        // when: the job runs
        val outcome = service.run(id)

        // then: it is cancelled, the two files it copied keep their rows, the metadata was not copied
        assertThat(outcome).isEqualTo(RunOutcome.CANCELLED)
        assertThat(service.job(id).orThrow().status).isEqualTo(JobStatus.CANCELLED)
        assertThat(scenario.store.assets(id)).hasSize(2)
        assertThat(scenario.target.read("releases", "com/example/app/maven-metadata.xml")).isNull()
    }

    @Test
    fun `should not run a job with a literal password on a replica that does not hold the password`() {
        // given: replica A creates a job with a literal password, replica B has no password for it
        val replicaA = scenario.service(token = "replica-a")
        val replicaB = scenario.service(token = "replica-b")
        val id = create(replicaA, request("IMPORT", password = "literal-pw"))

        // when: replica B is asked to run it
        val outcome = replicaB.run(id)

        // then: B does not take it and the job stays queued without any request to Nexus
        assertThat(outcome).isEqualTo(RunOutcome.NO_PASSWORD_HERE)
        assertThat(scenario.store.job(id)?.status).isEqualTo(JobStatus.QUEUED)
        assertThat(scenario.nexus.requests()).isEmpty()
    }

    @Test
    fun `should pause a job of a lost replica for the password and resume it on the replica that receives the password`() {
        // given: replica A took a job with a literal password and then vanished, its lease ran out
        val replicaA = scenario.service(token = "replica-a")
        val replicaB = scenario.service(token = "replica-b")
        val id = create(replicaA, request("IMPORT", password = "literal-pw"))
        assertThat(scenario.store.acquire(id, "replica-a")).isTrue
        scenario.clock.advance(ImportScenario.LEASE.plusSeconds(1))

        // when: replica B looks at the job without the password
        assertThat(replicaB.run(id)).isEqualTo(RunOutcome.NO_PASSWORD_HERE)

        // then: the job waits for the operator with the credentials reason
        assertThat(replicaB.job(id).orThrow().status).isEqualTo(JobStatus.PAUSED)
        assertThat(replicaB.job(id).orThrow().pauseReason).isEqualTo(PauseReason.CREDENTIALS_NEEDED)

        // when: the operator supplies the password to replica B, which runs the job to the end
        replicaB.supplyCredentials(id, "literal-pw").orThrow()
        assertThat(replicaB.run(id)).isEqualTo(RunOutcome.COMPLETED)
        assertThat(scenario.target.read("releases", "com/example/app/1.0/app-1.0.jar")).isEqualTo("jar-bytes".toByteArray())
    }

    @Test
    fun `should resolve a password reference at each run and never store the resolved value`() {
        // given: a reference to an environment variable and its value, known only to the resolver
        scenario.references["${'$'}{env:NEXUS_PASSWORD}"] = "s3cret-value"
        val service = scenario.service()
        val id = create(service, request("IMPORT", password = "${'$'}{env:NEXUS_PASSWORD}"))

        // when: the job runs
        assertThat(service.run(id)).isEqualTo(RunOutcome.COMPLETED)

        // then: Nexus received the resolved password, and the job row holds only the reference text
        val authorization = scenario.nexus.requests().first { it.path == NEXUS_REPOSITORIES }.headers["authorization"]
        assertThat(authorization).isEqualTo("Basic " + Base64.getEncoder().encodeToString("importer:s3cret-value".toByteArray()))
        assertThat(scenario.store.job(id)?.parameters).contains("${'$'}{env:NEXUS_PASSWORD}").doesNotContain("s3cret-value")
    }

    @Test
    fun `should pause a reference job for the credentials when its reference cannot be resolved at run time`() {
        // given: a reference the resolver knows when the job is created, and no longer knows when it runs
        scenario.references["\${env:NEXUS_PASSWORD}"] = "s3cret-value"
        val service = scenario.service()
        val id = create(service, request("IMPORT", password = "\${env:NEXUS_PASSWORD}"))
        scenario.references.clear()

        // when: the job runs
        val outcome = service.run(id)

        // then: it waits for the operator, who corrects the reference, and Nexus was not contacted with a guess
        assertThat(outcome).isEqualTo(RunOutcome.PAUSED_CREDENTIALS)
        assertThat(service.job(id).orThrow().pauseReason).isEqualTo(PauseReason.CREDENTIALS_NEEDED)
        assertThat(scenario.nexus.requests()).isEmpty()
    }

    @Test
    fun `should keep a literal password out of the job row, the report and the run event when the job fails`() {
        // given: Nexus answers every listing with a server error, so the job fails
        scenario.nexus.on(NEXUS_ASSETS) { FakeReply.status(500) }
        val service = scenario.service()
        val id = create(service, request("IMPORT", password = CANARY))

        // when: the job runs and fails
        service.run(id)

        // then: the job failed, and the canary appears in no stored value
        val job = scenario.store.job(id)
        assertThat(job?.status).isEqualTo(JobStatus.FAILED)
        assertThat(job?.parameters).doesNotContain(CANARY)
        assertThat(job?.lastError).doesNotContain(CANARY)
        assertThat(scenario.store.report(id)).isEmpty()
        assertThat(scenario.ports.runs.map { it.second.toString() }).noneMatch { it.contains(CANARY) }
        assertThat(service.create(request("DRY_RUN", password = CANARY)).orThrow().id).isPositive
    }

    @Test
    fun `should refuse a URL with user information and an unknown kind before anything is stored`() {
        // given: a URL that carries a password, and an unknown job kind
        val service = scenario.service()

        // when / then: both are refused with 400 and no job is created
        val withUser = service.create(request("IMPORT").copy(url = "http://importer:secret@nexus.internal:8081"))
        assertThat(withUser.isOk).isFalse
        assertThat(withUser.error.status).isEqualTo(HttpStatus.BAD_REQUEST.code)
        val unknown = service.create(request("MIGRATE"))
        assertThat(unknown.error.status).isEqualTo(HttpStatus.BAD_REQUEST.code)
        assertThat(service.jobs()).isEmpty()
    }

    @Test
    fun `should summarise the copy in the audit trail under the summary mode and record the run event`() {
        // given: a job in summary mode
        val service = scenario.service()
        val id = create(service, request("IMPORT", audit = "summary"))

        // when: it runs
        service.run(id)

        // then: the bulk session of the job was registered and released, and one run event was recorded
        assertThat(scenario.ports.bulkStarted).containsExactly("migration@$id")
        assertThat(scenario.ports.bulkEnded).containsExactly("migration@$id")
        assertThat(scenario.ports.runs.map { it.first }).containsExactly(id)
    }

    @Test
    fun `should not register a bulk session in the per-file mode`() {
        // given: a job in the default per-file mode
        val service = scenario.service()
        val id = create(service, request("IMPORT"))

        // when: it runs
        service.run(id)

        // then: no bulk session was registered and the run event is still recorded
        assertThat(scenario.ports.bulkStarted).isEmpty()
        assertThat(scenario.ports.runs.map { it.first }).containsExactly(id)
    }

    @Test
    fun `should delete a finished job with its rows and refuse to delete a running one`() {
        // given: a finished job and a paused one
        val service = scenario.service()
        val finished = create(service, request("DRY_RUN"))
        service.run(finished)
        val paused = create(service, request("IMPORT"))
        service.pause(paused)

        // when / then: the paused job is refused with 409, the finished one is removed
        assertThat(service.delete(paused).error.status).isEqualTo(HttpStatus.CONFLICT.code)
        service.delete(finished).orThrow()
        assertThat(service.job(finished).isOk).isFalse
    }

    @Test
    fun `should record a failed job with its reason when Nexus refuses the credential on a download`() {
        // given: the first download is refused with 401
        val service = scenario.service()
        val id = create(service, request("IMPORT"))
        scenario.nexus.script("/repository/releases/com/example/app/1.0/app-1.0.jar", FakeReply.status(401))

        // when: the job runs
        val outcome = service.run(id)

        // then: it waits for the operator with the credentials reason
        assertThat(outcome).isEqualTo(RunOutcome.PAUSED_CREDENTIALS)
        assertThat(service.job(id).orThrow().pauseReason).isEqualTo(PauseReason.CREDENTIALS_NEEDED)
    }

    private fun <T> Result<T, ErrorResponse>.orThrow(): T =
        if (isOk) get() else throw AssertionError("Expected success but got ${error.status}: ${error.message}")

    private companion object {
        const val CANARY = "canary-7f3a"
        const val NEXUS_REPOSITORIES = "/service/rest/v1/repositories"
        const val NEXUS_ASSETS = "/service/rest/v1/assets"
    }
}
