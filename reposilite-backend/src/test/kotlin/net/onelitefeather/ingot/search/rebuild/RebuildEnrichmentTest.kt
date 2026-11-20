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

package net.onelitefeather.ingot.search.rebuild

import net.onelitefeather.ingot.search.ArtifactPathParser
import net.onelitefeather.ingot.search.ArtifactPathResult
import net.onelitefeather.ingot.search.IndexRow
import net.onelitefeather.ingot.search.Provenance
import net.onelitefeather.ingot.search.backend.ArtifactSearchBackend
import net.onelitefeather.ingot.search.backend.FakeArtifactSearchBackend
import net.onelitefeather.ingot.search.backend.IndexAction
import net.onelitefeather.ingot.search.backend.SearchCapability
import net.onelitefeather.ingot.search.database.DatabaseSearchBackend
import net.onelitefeather.ingot.search.enrichment.ARTIFACT_ENRICHER_API_VERSION
import net.onelitefeather.ingot.search.enrichment.ArtifactContent
import net.onelitefeather.ingot.search.enrichment.ArtifactEnricher
import net.onelitefeather.ingot.search.enrichment.ArtifactEnrichmentExecutor
import net.onelitefeather.ingot.search.enrichment.ArtifactEnrichmentPipeline
import net.onelitefeather.ingot.search.enrichment.ArtifactEnrichmentRequest
import net.onelitefeather.ingot.search.enrichment.EnrichmentAttributes
import net.onelitefeather.ingot.search.enrichment.EnrichmentCompleted
import net.onelitefeather.ingot.search.enrichment.EnrichmentResult
import net.onelitefeather.ingot.search.enrichment.EnrichmentTrigger
import net.onelitefeather.ingot.search.outbox.OutboxOp
import net.onelitefeather.ingot.search.rebuild.RebuildHarness.Companion.jar
import net.onelitefeather.ingot.search.rebuild.RebuildHarness.Companion.withHarness
import net.onelitefeather.ingot.search.runs.RunStatus
import net.onelitefeather.ingot.search.runs.RunTrigger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

/**
 * The rebuild with enrichers registered (artifact enrichment, 5.1). The enrichers run through the real executor on a pool
 * that runs each task on the calling thread, so no test waits on a deadline.
 */
internal class RebuildEnrichmentTest {

    @TempDir
    lateinit var directory: Path

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

    private fun RebuildHarness.enrichingService(
        enricher: ArtifactEnricher,
        warnings: MutableList<String> = mutableListOf(),
        backend: ArtifactSearchBackend = DatabaseSearchBackend(database, clock),
    ): RebuildService {
        val pipeline = ArtifactEnrichmentPipeline(
            enrichers = listOf(enricher),
            call = ArtifactEnrichmentExecutor(InlineExecutorService(), clock)::call,
            warn = { warnings += it },
        )
        return service(backend = backend, enrichment = pipeline, content = { _, _, size -> EmptyContent(size) })
    }

    @Test
    fun `a new row reaches the enricher with the rebuild trigger`() {
        withHarness(directory, "enrich-new") {
            val enricher = RecordingEnricher("app-id", setOf("jar"))
            entries.put("releases", jar(1), size = 2048)

            enrichingService(enricher).rebuild(RunTrigger.MANUAL)

            val request = enricher.requests.single()
            assertThat(request.trigger).`as`("trigger of a rebuild call").isEqualTo(EnrichmentTrigger.REBUILD)
            assertThat(request.repository).`as`("repository of the call").isEqualTo("releases")
            assertThat(request.path).`as`("path of the call").isEqualTo(jar(1))
            assertThat(request.sizeBytes).`as`("size of the stored file").isEqualTo(2048L)
        }
    }

    @Test
    fun `a changed row reaches the enricher with the rebuild trigger`() {
        withHarness(directory, "enrich-changed") {
            val enricher = RecordingEnricher("app-id", setOf("jar"))
            seedRow("releases", jar(1), size = 100, indexedAt = clock.instant())
            entries.put("releases", jar(1), size = 500)
            clock.advance(Duration.ofMinutes(1))

            enrichingService(enricher).rebuild(RunTrigger.MANUAL)

            assertThat(enricher.requests.map { it.path }).`as`("paths that reached the enricher").containsExactly(jar(1))
            assertThat(enricher.requests.single().trigger).`as`("trigger of a rebuild call").isEqualTo(EnrichmentTrigger.REBUILD)
        }
    }

    @Test
    fun `an unchanged row with the same size and modification time does not reach the enricher`() {
        withHarness(directory, "enrich-unchanged") {
            val enricher = RecordingEnricher("app-id", setOf("jar"))
            seedRow("releases", jar(1), size = 1, indexedAt = clock.instant())
            entries.put("releases", jar(1), size = 1)
            clock.advance(Duration.ofMinutes(1))

            val run = enrichingService(enricher).rebuild(RunTrigger.MANUAL)

            assertThat(enricher.requests).`as`("calls for a row whose file did not change").isEmpty()
            assertThat(run?.counters?.upserted).`as`("the unchanged row is still upserted").isEqualTo(1L)
        }
    }

    @Test
    fun `a held row with a pending outbox entry does not reach the enricher`() {
        withHarness(directory, "enrich-held") {
            val enricher = RecordingEnricher("app-id", setOf("jar"))
            entries.put("releases", jar(1), size = 500)
            outbox.enqueue("releases", jar(1), OutboxOp.CHANGED_PATH)

            enrichingService(enricher).rebuild(RunTrigger.MANUAL)

            assertThat(enricher.requests).`as`("calls for a path left to the outbox worker").isEmpty()
        }
    }

    @Test
    fun `a failing enricher does not stop the page`() {
        withHarness(directory, "enrich-failing") {
            val warnings = mutableListOf<String>()
            val enricher = RecordingEnricher("app-id", setOf("jar")) { throw IllegalStateException("broken") }
            entries.put("releases", jar(1))
            entries.put("releases", jar(2))

            val run = enrichingService(enricher, warnings).rebuild(RunTrigger.MANUAL)

            assertThat(enricher.requests).`as`("calls of the failing enricher").hasSize(2)
            assertThat(indexedPaths("releases")).`as`("rows written despite the failing enricher").containsExactlyInAnyOrder(jar(1), jar(2))
            assertThat(run?.status).`as`("status of the run").isEqualTo(RunStatus.COMPLETED)
            assertThat(warnings).`as`("one warning per failed call").hasSize(2)
        }
    }

    private fun loader(): RecordingEnricher =
        RecordingEnricher("loader", setOf("jar")) { EnrichmentAttributes(mapOf("loader.name" to listOf("paper"))) }

    private fun FakeArtifactSearchBackend.upserts(): List<IndexAction.Upsert> = batches.flatten().filterIsInstance<IndexAction.Upsert>()

    @Test
    fun `a new row at a backend with attributes is written with the results and the enricher version`() {
        withHarness(directory, "attributes-new") {
            val backend = FakeArtifactSearchBackend(capabilities = setOf(SearchCapability.ATTRIBUTES))
            entries.put("releases", jar(1), size = 2048)

            enrichingService(loader(), backend = backend).rebuild(RunTrigger.MANUAL)

            assertThat(backend.upserts().single().attributes).`as`("attributes of the new row").isEqualTo(
                mapOf("loader.name" to listOf("paper"), "core.enricher.loader" to listOf("1")),
            )
        }
    }

    /** Seeds an unchanged row of [jar] 1 at [backend], with [attributes] stored, and the same file in storage. */
    private fun RebuildHarness.seedUnchanged(backend: FakeArtifactSearchBackend, attributes: Map<String, List<String>>) {
        val parsed = ArtifactPathParser.parse("releases", jar(1)) as ArtifactPathResult.Row
        val modified = Instant.parse("2026-10-01T00:00:00Z").toEpochMilli()
        backend.apply(listOf(IndexAction.Upsert(IndexRow(parsed.file, Provenance.HOSTED, 1, modified, null, null, clock.millis()), attributes)))
        entries.put("releases", jar(1), size = 1)
        clock.advance(Duration.ofMinutes(1))
    }

    @Test
    fun `an unchanged row keeps its stored attributes`() {
        withHarness(directory, "attributes-unchanged") {
            val backend = FakeArtifactSearchBackend(capabilities = setOf(SearchCapability.ATTRIBUTES))
            seedUnchanged(backend, mapOf("loader.name" to listOf("paper"), "core.enricher.loader" to listOf("1")))

            enrichingService(loader(), backend = backend).rebuild(RunTrigger.MANUAL)

            assertThat(backend.upserts().last().attributes).`as`("attributes sent for an unchanged row, null keeps the stored ones").isNull()
        }
    }

    @Test
    fun `an unchanged row with the stored version of every matching enricher does not reach the enricher`() {
        withHarness(directory, "version-current") {
            val backend = FakeArtifactSearchBackend(capabilities = setOf(SearchCapability.ATTRIBUTES))
            val enricher = loader()
            seedUnchanged(backend, mapOf("loader.name" to listOf("paper"), "core.enricher.loader" to listOf("1")))

            enrichingService(enricher, backend = backend).rebuild(RunTrigger.MANUAL)

            assertThat(enricher.requests).`as`("calls for a row enriched by the current version").isEmpty()
        }
    }

    @Test
    fun `an unchanged row without a stored version reaches the enricher with the rebuild trigger`() {
        withHarness(directory, "version-missing") {
            val backend = FakeArtifactSearchBackend(capabilities = setOf(SearchCapability.ATTRIBUTES))
            val enricher = loader()
            seedUnchanged(backend, emptyMap())

            enrichingService(enricher, backend = backend).rebuild(RunTrigger.MANUAL)

            assertThat(enricher.requests.map { it.trigger }).`as`("triggers of the calls for a row never enriched").containsExactly(EnrichmentTrigger.REBUILD)
            assertThat(backend.upserts().last().attributes).`as`("attributes written for the row").isEqualTo(
                mapOf("loader.name" to listOf("paper"), "core.enricher.loader" to listOf("1")),
            )
        }
    }

    @Test
    fun `an unchanged row with another stored version reaches the enricher with the rebuild trigger`() {
        withHarness(directory, "version-other") {
            val backend = FakeArtifactSearchBackend(capabilities = setOf(SearchCapability.ATTRIBUTES))
            val enricher = loader()
            seedUnchanged(backend, mapOf("loader.name" to listOf("spigot"), "core.enricher.loader" to listOf("0")))

            enrichingService(enricher, backend = backend).rebuild(RunTrigger.MANUAL)

            assertThat(enricher.requests.map { it.trigger }).`as`("triggers of the calls for a row of an older version").containsExactly(EnrichmentTrigger.REBUILD)
            assertThat(backend.upserts().last().attributes).`as`("attributes written for the row").isEqualTo(
                mapOf("loader.name" to listOf("paper"), "core.enricher.loader" to listOf("1")),
            )
        }
    }

    @Test
    fun `a held row without a stored version does not reach the enricher`() {
        withHarness(directory, "version-held") {
            val backend = FakeArtifactSearchBackend(capabilities = setOf(SearchCapability.ATTRIBUTES))
            val enricher = loader()
            seedUnchanged(backend, emptyMap())
            outbox.enqueue("releases", jar(1), OutboxOp.CHANGED_PATH)

            enrichingService(enricher, backend = backend).rebuild(RunTrigger.MANUAL)

            assertThat(enricher.requests).`as`("calls for a path left to the outbox worker").isEmpty()
        }
    }

    @Test
    fun `a changed row at a backend without attributes is written without attributes`() {
        withHarness(directory, "attributes-unsupported") {
            val backend = FakeArtifactSearchBackend()
            entries.put("releases", jar(1), size = 2048)

            enrichingService(loader(), backend = backend).rebuild(RunTrigger.MANUAL)

            assertThat(backend.upserts().single().attributes).`as`("attributes sent to a backend that declares none").isNull()
        }
    }

}
