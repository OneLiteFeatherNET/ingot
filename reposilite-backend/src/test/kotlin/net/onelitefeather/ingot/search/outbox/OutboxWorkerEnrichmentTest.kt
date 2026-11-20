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

@file:OptIn(net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi::class)

package net.onelitefeather.ingot.search.outbox

import com.reposilite.audit.MutableClock
import net.onelitefeather.ingot.search.Origin
import net.onelitefeather.ingot.search.Provenance
import net.onelitefeather.ingot.search.backend.FakeArtifactSearchBackend
import net.onelitefeather.ingot.search.backend.IndexAction
import net.onelitefeather.ingot.search.backend.SearchCapability
import net.onelitefeather.ingot.search.database.SearchSchema
import net.onelitefeather.ingot.search.database.SearchStateStore
import net.onelitefeather.ingot.search.database.SqliteFileDatabase
import net.onelitefeather.ingot.search.enrichment.ARTIFACT_ENRICHER_API_VERSION
import net.onelitefeather.ingot.search.enrichment.ArtifactContent
import net.onelitefeather.ingot.search.enrichment.ArtifactEnricher
import net.onelitefeather.ingot.search.enrichment.ArtifactEnrichmentExecutor
import net.onelitefeather.ingot.search.enrichment.ArtifactEnrichmentPipeline
import net.onelitefeather.ingot.search.enrichment.ArtifactEnrichmentRequest
import net.onelitefeather.ingot.search.enrichment.EnrichmentAttributes
import net.onelitefeather.ingot.search.enrichment.EnrichmentCompleted
import net.onelitefeather.ingot.search.enrichment.EnrichmentFailed
import net.onelitefeather.ingot.search.enrichment.EnrichmentResult
import net.onelitefeather.ingot.search.enrichment.EnrichmentTrigger
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

/**
 * The outbox worker with enrichers registered (artifact enrichment, 4.1). Storage, the origin ledger and the index are fakes
 * the test sets; the enrichers run through the real executor on a pool that runs each task on the calling thread, so no test
 * waits on a deadline.
 */
internal class OutboxWorkerEnrichmentTest {

    private val start = Instant.parse("2026-10-16T10:00:00Z")
    private val clock = MutableClock(start)
    private val jar = "com/example/app/1.0/app-1.0.jar"
    private val sha1Value = "da39a3ee5e6b4b0d3255bfef95601890afd80709"
    private val sha256Value = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

    @TempDir
    lateinit var directory: Path

    private class FakeStorage : IndexStorage {
        val files = mutableMapOf<String, StoredFile>()
        val heads = mutableMapOf<String, String>()
        var contentRequests = 0

        override fun file(repository: String, path: String): StoredFile? = files[path]

        override fun exists(repository: String, path: String): Boolean = files.containsKey(path)

        override fun head(repository: String, path: String, maxBytes: Int): String? = heads[path]?.take(maxBytes)

        override fun content(repository: String, path: String, sizeBytes: Long): ArtifactContent? {
            contentRequests++
            return EmptyContent(sizeBytes)
        }
    }

    private class EmptyContent(override val sizeBytes: Long) : ArtifactContent {
        override fun range(offset: Long, length: Long): InputStream = ByteArrayInputStream(ByteArray(0))
    }

    private class RecordingEnricher(
        override val id: String,
        override val extensions: Set<String>,
        private val outcome: (ArtifactEnrichmentRequest) -> EnrichmentResult = { EnrichmentCompleted },
    ) : ArtifactEnricher {
        override val apiVersion: Int = ARTIFACT_ENRICHER_API_VERSION
        override val version: String = "1"
        val requests = mutableListOf<ArtifactEnrichmentRequest>()

        override fun enrich(artifact: ArtifactEnrichmentRequest): EnrichmentResult {
            requests += artifact
            return outcome(artifact)
        }
    }

    /** Runs every submitted task on the calling thread and completes it before returning. */
    private class InlineExecutorService : AbstractExecutorService() {
        private var shut = false

        override fun execute(command: Runnable) = command.run()

        override fun shutdown() {
            shut = true
        }

        override fun shutdownNow(): MutableList<Runnable> {
            shut = true
            return mutableListOf()
        }

        override fun isShutdown(): Boolean = shut

        override fun isTerminated(): Boolean = shut

        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = true
    }

    private inner class Harness(database: Database, enrichers: List<ArtifactEnricher>, capabilities: Set<SearchCapability>) {
        val backend = FakeArtifactSearchBackend(capabilities = capabilities)
        val outbox = OutboxStore(database, clock)
        val storage = FakeStorage()
        val warnings = mutableListOf<String>()
        var provenance: Provenance = Provenance.HOSTED
        val pipeline = ArtifactEnrichmentPipeline(
            enrichers = enrichers,
            call = ArtifactEnrichmentExecutor(InlineExecutorService(), clock)::call,
            warn = { warnings += it },
        )
        val worker = OutboxWorker(
            owner = "instance-a",
            outbox = outbox,
            index = backend,
            storage = storage,
            origins = { _, _ -> provenance },
            state = SearchStateStore(database, clock),
            clock = clock,
            enrichment = pipeline,
        )
    }

    private fun withWorker(
        enrichers: List<ArtifactEnricher>,
        capabilities: Set<SearchCapability> = emptySet(),
        block: Harness.() -> Unit,
    ) {
        SqliteFileDatabase(directory.resolve("outbox.db")).use { sqlite ->
            SearchSchema.ensure(sqlite.database, clock)
            Harness(sqlite.database, enrichers, capabilities).block()
        }
    }

    @Test
    fun `a path no enricher matches is upserted without reading its content`() {
        val enricher = RecordingEnricher("pom-only", setOf("pom"))
        withWorker(listOf(enricher)) {
            storage.files[jar] = StoredFile(sizeBytes = 2048, lastModified = start)
            outbox.enqueue("releases", jar, OutboxOp.CHANGED_PATH)

            worker.tick()

            assertThat(enricher.requests).`as`("calls of an enricher that does not match the extension").isEmpty()
            assertThat(storage.contentRequests).`as`("content reads for a path no enricher matches").isZero()
            assertThat(backend.batches.single().single()).`as`("the row is still upserted").isInstanceOf(IndexAction.Upsert::class.java)
        }
    }

    @Test
    fun `a matching extension reaches the enricher once with the changed trigger and the stored checksums`() {
        val enricher = RecordingEnricher("app-id", setOf("jar"))
        withWorker(listOf(enricher)) {
            storage.files[jar] = StoredFile(sizeBytes = 2048, lastModified = start)
            storage.heads["$jar.sha1"] = "$sha1Value  app-1.0.jar\n"
            storage.heads["$jar.sha256"] = "$sha256Value\n"
            outbox.enqueue("releases", jar, OutboxOp.CHANGED_PATH)

            worker.tick()

            val request = enricher.requests.single()
            assertThat(request.trigger).`as`("trigger of a changed path").isEqualTo(EnrichmentTrigger.CHANGED)
            assertThat(request.repository).`as`("repository of the call").isEqualTo("releases")
            assertThat(request.path).`as`("path of the call").isEqualTo(jar)
            assertThat(request.sizeBytes).`as`("size of the stored file").isEqualTo(2048L)
            assertThat(request.sha1).`as`("sha1 from the sidecar").isEqualTo(sha1Value)
            assertThat(request.sha256).`as`("sha256 from the sidecar").isEqualTo(sha256Value)
            assertThat(request.origin).`as`("origin of a hosted file").isEqualTo(Origin.HOSTED)
        }
    }

    @Test
    fun `a mirror file reaches the enricher with the mirror provenance`() {
        val enricher = RecordingEnricher("app-id", setOf("jar"))
        withWorker(listOf(enricher)) {
            provenance = Provenance(Origin.MIRROR, "maven.example.org")
            storage.files[jar] = StoredFile(sizeBytes = 2048, lastModified = start)
            outbox.enqueue("releases", jar, OutboxOp.CHANGED_PATH)

            worker.tick()

            val request = enricher.requests.single()
            assertThat(request.origin).`as`("origin of a mirror copy").isEqualTo(Origin.MIRROR)
            assertThat(request.sourceHost).`as`("source host of a mirror copy").isEqualTo("maven.example.org")
        }
    }

    @Test
    fun `an enricher that throws leaves the upsert in place and is counted as failed with one warning`() {
        val enricher = RecordingEnricher("app-id", setOf("jar")) { throw IllegalStateException("/var/secret/path") }
        withWorker(listOf(enricher)) {
            storage.files[jar] = StoredFile(sizeBytes = 2048, lastModified = start)
            outbox.enqueue("releases", jar, OutboxOp.CHANGED_PATH)

            worker.tick()

            assertThat(backend.batches.single().single()).`as`("the row is still upserted").isInstanceOf(IndexAction.Upsert::class.java)
            assertThat(pipeline.counts().getValue("app-id").failed).`as`("failed calls of the enricher").isEqualTo(1L)
            assertThat(warnings).`as`("warnings of the failed call").hasSize(1)
            assertThat(warnings.single())
                .`as`("warning names the enricher and the class of the failure")
                .contains("app-id", "exception: IllegalStateException")
                .`as`("warning carries no message of the failure")
                .doesNotContain("/var/secret/path")
        }
    }

    private fun Harness.upserted(): IndexAction.Upsert = backend.batches.single().single() as IndexAction.Upsert

    @Test
    fun `a changed file at a backend with attributes is written with the union of the results and the enricher versions`() {
        val loader = RecordingEnricher("loader", setOf("jar")) { EnrichmentAttributes(mapOf("loader.name" to listOf("paper"))) }
        val marker = RecordingEnricher("marker", setOf("jar"))
        withWorker(listOf(loader, marker), setOf(SearchCapability.ATTRIBUTES)) {
            storage.files[jar] = StoredFile(sizeBytes = 2048, lastModified = start)
            outbox.enqueue("releases", jar, OutboxOp.CHANGED_PATH)

            worker.tick()

            assertThat(upserted().attributes).`as`("attributes of the changed file").isEqualTo(
                mapOf(
                    "loader.name" to listOf("paper"),
                    "core.enricher.loader" to listOf("1"),
                    "core.enricher.marker" to listOf("1"),
                ),
            )
        }
    }

    @Test
    fun `a failed enricher leaves neither keys nor its version on the changed file`() {
        val loader = RecordingEnricher("loader", setOf("jar")) { EnrichmentAttributes(mapOf("loader.name" to listOf("paper"))) }
        val broken = RecordingEnricher("broken", setOf("jar")) { EnrichmentFailed("no manifest") }
        withWorker(listOf(loader, broken), setOf(SearchCapability.ATTRIBUTES)) {
            storage.files[jar] = StoredFile(sizeBytes = 2048, lastModified = start)
            outbox.enqueue("releases", jar, OutboxOp.CHANGED_PATH)

            worker.tick()

            assertThat(upserted().attributes).`as`("attributes after one enricher failed").isEqualTo(
                mapOf("loader.name" to listOf("paper"), "core.enricher.loader" to listOf("1")),
            )
        }
    }

    @Test
    fun `a backend without attributes gets the row without attributes and the set counts as skipped`() {
        val loader = RecordingEnricher("loader", setOf("jar")) { EnrichmentAttributes(mapOf("loader.name" to listOf("paper"))) }
        withWorker(listOf(loader)) {
            storage.files[jar] = StoredFile(sizeBytes = 2048, lastModified = start)
            outbox.enqueue("releases", jar, OutboxOp.CHANGED_PATH)

            worker.tick()

            assertThat(upserted().attributes).`as`("attributes sent to a backend that declares none").isNull()
            assertThat(pipeline.counts().getValue("loader").skipped).`as`("skipped calls of the enricher").isEqualTo(1L)
        }
    }

}
