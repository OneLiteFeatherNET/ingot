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

package net.onelitefeather.ingot.search.enrichment

import net.onelitefeather.ingot.search.ArtifactFileRecord
import net.onelitefeather.ingot.search.Provenance
import net.onelitefeather.ingot.search.outbox.StoredFile
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.Instant

/** The counters of the enrichment pipeline that the index status shows (artifact enrichment, D7 and 6.1). */
internal class ArtifactEnrichmentPipelineTest {

    private val jar = ArtifactFileRecord(
        repository = "releases",
        path = "com/example/app/1.0/app-1.0.jar",
        groupId = "com.example",
        artifactId = "app",
        version = "1.0",
        classifier = null,
        extension = "jar",
        snapshot = false,
    )

    private class StaticEnricher(override val id: String) : ArtifactEnricher {
        override val apiVersion: Int = ARTIFACT_ENRICHER_API_VERSION
        override val version: String = "1"
        override val extensions: Set<String> = setOf("jar")

        override fun enrich(artifact: ArtifactEnrichmentRequest): EnrichmentResult = EnrichmentCompleted
    }

    private object EmptyContent : ArtifactContent {
        override val sizeBytes: Long = 0
        override fun range(offset: Long, length: Long): InputStream = ByteArrayInputStream(ByteArray(0))
    }

    /** A pipeline whose calls answer the scripted outcomes in order, without running the enricher. */
    private fun pipeline(enrichers: List<ArtifactEnricher>, outcomes: List<EnrichmentResult>): ArtifactEnrichmentPipeline {
        val queue = ArrayDeque(outcomes)
        return ArtifactEnrichmentPipeline(enrichers = enrichers, call = { _, _ -> queue.removeFirst() }, warn = {})
    }

    private fun ArtifactEnrichmentPipeline.enrichOnce() =
        enrich(EnrichmentTrigger.CHANGED, jar, Provenance.HOSTED, StoredFile(0, Instant.EPOCH), null, null) { EmptyContent }

    @Test
    fun `a registered enricher that was never called is counted with zeros`() {
        val pipeline = pipeline(listOf(StaticEnricher("app-id")), emptyList())

        val tally = pipeline.counts()["app-id"]

        assertThat(tally).`as`("counters of an enricher registered but not yet called")
            .isEqualTo(EnrichmentTally(completed = 0, skipped = 0, failed = 0, busy = 0, failures = emptyMap()))
        assertThat(tally?.calls).`as`("calls of an enricher not yet called").isEqualTo(0L)
    }

    @Test
    fun `no enricher registered gives no counters`() {
        val pipeline = pipeline(emptyList(), emptyList())

        assertThat(pipeline.counts()).`as`("counters without a registered enricher").isEmpty()
    }

    @Test
    fun `two failures and one completion are three calls with the failures counted per reason`() {
        val pipeline = pipeline(
            listOf(StaticEnricher("app-id")),
            listOf(EnrichmentFailed("timeout"), EnrichmentCompleted, EnrichmentFailed("exception: IOException")),
        )

        repeat(3) { pipeline.enrichOnce() }

        val tally = pipeline.counts().getValue("app-id")
        assertThat(tally.calls).`as`("calls").isEqualTo(3L)
        assertThat(tally.completed).`as`("completed calls").isEqualTo(1L)
        assertThat(tally.failed).`as`("failed calls").isEqualTo(2L)
        assertThat(tally.failures).`as`("failures per reason")
            .isEqualTo(mapOf("timeout" to 1L, "exception: IOException" to 1L))
    }

    @Test
    fun `a busy skip counts as a skip and as busy`() {
        val pipeline = pipeline(
            listOf(StaticEnricher("app-id")),
            listOf(EnrichmentSkipped("busy"), EnrichmentSkipped("not a plugin jar")),
        )

        repeat(2) { pipeline.enrichOnce() }

        val tally = pipeline.counts().getValue("app-id")
        assertThat(tally.skipped).`as`("skipped calls, busy included").isEqualTo(2L)
        assertThat(tally.busy).`as`("calls skipped because the queue was full").isEqualTo(1L)
        assertThat(tally.calls).`as`("calls").isEqualTo(2L)
    }

    @Test
    fun `failure reasons beyond the bound are counted under other`() {
        val reasons = (1..ArtifactEnrichmentPipeline.MAX_FAILURE_REASONS + 2).map { "reason $it" }
        val pipeline = pipeline(listOf(StaticEnricher("app-id")), reasons.map(::EnrichmentFailed))

        repeat(reasons.size) { pipeline.enrichOnce() }

        val failures = pipeline.counts().getValue("app-id").failures
        assertThat(failures).`as`("distinct reasons kept, plus other").hasSize(ArtifactEnrichmentPipeline.MAX_FAILURE_REASONS + 1)
        assertThat(failures[ArtifactEnrichmentPipeline.OTHER_FAILURE_REASON]).`as`("failures beyond the bound").isEqualTo(2L)
        assertThat(failures.values.sum()).`as`("every failure is counted once").isEqualTo(reasons.size.toLong())
    }

}
